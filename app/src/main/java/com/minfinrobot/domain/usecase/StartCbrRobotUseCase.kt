package com.minfinrobot.domain.usecase

import com.minfinrobot.data.cbr.CbrFetcher
import com.minfinrobot.data.cbr.CbrRateParser
import com.minfinrobot.data.log.LogStore
import com.minfinrobot.data.settings.SecureSettingsStore
import com.minfinrobot.data.tbank.TBankRepository
import com.minfinrobot.domain.engine.CbrScenarioEvaluator
import com.minfinrobot.domain.model.CbrConfig
import com.minfinrobot.domain.model.CbrRateDecision
import com.minfinrobot.domain.model.CbrScenario
import com.minfinrobot.domain.model.InstrumentRef
import com.minfinrobot.domain.model.RobotRunState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Оркестратор ЦБ-робота.
 *
 *  1. URL пресс-релиза вычисляется заранее: cbr.ru/press/pr/?file=DDMMYYYY_133000key.htm
 *  2. Ждём начала окна (13:25 МСК) в meetingDate.
 *  3. Каждые pollIntervalMs опрашиваем URL. До публикации возвращает HTTP 404.
 *  4. Когда HTTP 200 — парсим ставку из <title>.
 *  5. Прогоняем через сценарии (поддержка α и γ режимов).
 *  6. Параллельная отправка ордеров.
 *  7. Если 14:00 МСК наступило без публикации — стоп с notification.
 */
class StartCbrRobotUseCase(
    private val cbrFetcher: CbrFetcher,
    private val tbank: TBankRepository,
    private val settings: SecureSettingsStore,
    private val evaluator: CbrScenarioEvaluator
) {

    suspend fun invoke(
        config: CbrConfig,
        instruments: List<InstrumentRef>,
        onState: suspend (RobotRunState) -> Unit
    ): RobotRunState {
        try {
            val url = cbrFetcher.buildUrl(config.meetingDate)
            LogStore.info(
                "Запуск ЦБ-робота. Заседание: ${config.meetingDate}, " +
                    "сценариев: ${config.scenarios.size}, " +
                    "режим: ${if (settings.isSandbox) "SANDBOX" else "PRODUCTION"}"
            )
            LogStore.info("URL: $url")

            onState(RobotRunState.WAITING_FOR_WINDOW)
            if (!waitForWindowStart(config)) {
                LogStore.warn("Дата заседания прошла или окно закрыто")
                onState(RobotRunState.DONE_WINDOW_EXPIRED)
                return RobotRunState.DONE_WINDOW_EXPIRED
            }

            onState(RobotRunState.POLLING_LISTING)
            LogStore.info("Окно открыто, поллим ЦБ каждые ${config.pollIntervalMs} мс")

            val decision = pollUntilFound(url, config)
            if (decision == null) {
                LogStore.warn("Окно 14:00 МСК закрыто, пресс-релиз ЦБ не появился. Остановка.")
                onState(RobotRunState.DONE_WINDOW_EXPIRED)
                return RobotRunState.DONE_WINDOW_EXPIRED
            }

            onState(RobotRunState.PUBLICATION_FOUND)
            LogStore.info(
                "[ЦБ] Решение: ставка = ${decision.ratePercent}% годовых"
            )

            val matched = evaluator.match(decision, config.scenarios)
            LogStore.info("Сработало сценариев: ${matched.size} из ${config.scenarios.size}")
            if (matched.isEmpty()) {
                onState(RobotRunState.DONE_SUCCESS)
                return RobotRunState.DONE_SUCCESS
            }

            onState(RobotRunState.PLACING_ORDERS)
            placeOrdersParallel(matched, instruments, config.accountId)

            onState(RobotRunState.DONE_SUCCESS)
            return RobotRunState.DONE_SUCCESS
        } catch (e: kotlinx.coroutines.CancellationException) {
            LogStore.info("ЦБ-робот остановлен (отмена корутины)")
            onState(RobotRunState.DONE_WINDOW_EXPIRED)
            throw e
        } catch (e: Exception) {
            LogStore.error("Фатальная ошибка ЦБ-робота: ${e.javaClass.simpleName}: ${e.message ?: "(нет сообщения)"}")
            onState(RobotRunState.ERROR)
            return RobotRunState.ERROR
        }
    }

    private suspend fun waitForWindowStart(config: CbrConfig): Boolean {
        val now = ZonedDateTime.now(MOSCOW)
        val target = config.meetingDate
        val nowMinute = now.toLocalTime().toSecondOfDay() / 60

        if (now.toLocalDate().isAfter(target)) return false
        if (now.toLocalDate() == target && nowMinute >= config.windowEndMinuteOfDay) return false

        val startHour = config.windowStartMinuteOfDay / 60
        val startMinute = config.windowStartMinuteOfDay % 60
        val windowStart = ZonedDateTime.of(target, LocalTime.of(startHour, startMinute), MOSCOW)
        val msToWait = windowStart.toInstant().toEpochMilli() - now.toInstant().toEpochMilli()
        if (msToWait > 0) {
            LogStore.info(
                "Ждём начала окна (${windowStart.toLocalTime()} МСК), " +
                    "осталось ${msToWait / 1000} сек"
            )
            delay(msToWait)
        }
        return true
    }

    private suspend fun pollUntilFound(url: String, config: CbrConfig): CbrRateDecision? {
        var pollCount = 0
        while (true) {
            val now = ZonedDateTime.now(MOSCOW)
            val nowMinute = now.toLocalTime().toSecondOfDay() / 60
            if (now.toLocalDate() != config.meetingDate ||
                nowMinute >= config.windowEndMinuteOfDay
            ) {
                return null
            }
            pollCount++

            val decision = withContext(Dispatchers.IO) {
                try {
                    val html = cbrFetcher.fetchPressRelease(url)
                    val parsed = CbrRateParser.parse(html, url)
                    if (parsed == null) {
                        LogStore.warn("[ЦБ] Страница получена, но число не распарсилось")
                    }
                    parsed
                } catch (e: Exception) {
                    // До 13:30 — это нормально, страница даёт 404. Тихо игнорируем.
                    null
                }
            }
            if (decision != null) return decision

            if (pollCount % 30 == 0) {
                LogStore.info("Опрос #$pollCount: пресс-релиз ЦБ ещё не опубликован")
            }
            delay(config.pollIntervalMs)
        }
    }

    private suspend fun placeOrdersParallel(
        scenarios: List<CbrScenario>,
        instruments: List<InstrumentRef>,
        accountId: String
    ) = coroutineScope {
        val byUid = instruments.associateBy { it.uid }
        scenarios.map { scenario ->
            async {
                val instrument = byUid[scenario.instrumentUid]
                if (instrument == null) {
                    LogStore.error(
                        "Сценарий ${scenario.id}: инструмент ${scenario.instrumentUid} " +
                            "не найден в справочнике"
                    )
                    return@async scenario.id to Result.failure<String>(
                        RuntimeException("instrument not found")
                    )
                }
                val cmpInfo = if (scenario.expectedRatePercent != null)
                    "vs прогноз ${scenario.expectedRatePercent}%"
                else
                    "vs порог ${scenario.thresholdPercent}%"
                LogStore.info(
                    "Ордер: ${scenario.action} ${scenario.quantity} " +
                        scenario.instrumentDisplay +
                        " ($cmpInfo)" +
                        (scenario.limitPrice?.let { " по лимиту $it" } ?: " по рынку")
                )
                val r = tbank.placeOrder(
                    accountId = accountId,
                    instrument = instrument,
                    action = scenario.action,
                    quantity = scenario.quantity,
                    limitPrice = scenario.limitPrice
                )
                r.fold(
                    onSuccess = { id: String -> LogStore.info("Отправлен, id=$id") },
                    onFailure = { err: Throwable -> LogStore.error("Отказ: ${err.message}") }
                )
                scenario.id to r
            }
        }.awaitAll().also { results ->
            LogStore.info("Все ордера обработаны. Успешно: ${results.count { it.second.isSuccess }}")
        }
    }

    companion object {
        private val MOSCOW: ZoneId = ZoneId.of("Europe/Moscow")
    }
}
