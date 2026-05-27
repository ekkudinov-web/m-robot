package com.minfinrobot.domain.usecase

import com.minfinrobot.data.log.LogStore
import com.minfinrobot.data.minfin.MinfinFetcher
import com.minfinrobot.data.minfin.MinfinParser
import com.minfinrobot.data.settings.SecureSettingsStore
import com.minfinrobot.data.tass.TassFetcher
import com.minfinrobot.data.tass.TassParser
import com.minfinrobot.data.tbank.TBankRepository
import com.minfinrobot.domain.engine.ScenarioEvaluator
import com.minfinrobot.domain.model.InstrumentRef
import com.minfinrobot.domain.model.MinfinPublication
import com.minfinrobot.domain.model.RobotConfig
import com.minfinrobot.domain.model.RobotRunState
import com.minfinrobot.domain.model.Scenario
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
 * Главный оркестратор робота.
 *
 *  1. Ждём начала окна 11:30 МСК в targetDate.
 *  2. Каждые pollIntervalMs параллельно опрашиваем minfin.gov.ru и tass.ru.
 *  3. Скачиваем детальную страницу, парсим число.
 *  4. При паузе (число=0) — стоп, без сделок.
 *  5. Иначе — параллельная отправка ордеров по сработавшим сценариям.
 *  6. Если 14:00 МСК наступило без публикации — стоп с notification.
 */
class StartRobotUseCase(
    private val minfinFetcher: MinfinFetcher,
    private val tassFetcher: TassFetcher,
    private val tbank: TBankRepository,
    private val settings: SecureSettingsStore,
    private val evaluator: ScenarioEvaluator
) {

    suspend fun invoke(
        config: RobotConfig,
        instruments: List<InstrumentRef>,
        onState: suspend (RobotRunState) -> Unit
    ): RobotRunState {
        try {
            LogStore.info(
                "Запуск робота. Дата: ${config.targetDate}, " +
                    "сценариев: ${config.scenarios.size}, " +
                    "режим: ${if (settings.isSandbox) "SANDBOX" else "PRODUCTION"}" +
                    if (config.bypassDateCheck) " [BYPASS DATE — SANDBOX TEST]" else ""
            )

            if (config.bypassDateCheck) {
                LogStore.warn("ВНИМАНИЕ: режим тестового запуска — игнорируем дату и окно")
            } else {
                onState(RobotRunState.WAITING_FOR_WINDOW)
                if (!waitForWindowStart(config)) {
                    LogStore.warn("Целевая дата уже прошла или окно закрыто")
                    onState(RobotRunState.DONE_WINDOW_EXPIRED)
                    return RobotRunState.DONE_WINDOW_EXPIRED
                }
            }

            // В режиме теста игнорируем baseline (хотим найти текущую публикацию,
            // даже если она уже была обработана раньше).
            val baselineUrl = if (config.bypassDateCheck) null
                              else settings.lastProcessedPublicationUrl
            LogStore.info(
                "Поллим Минфин+ТАСС каждые ${config.pollIntervalMs} мс. " +
                    "Baseline: ${baselineUrl ?: "нет (тестовый запуск)"}"
            )

            onState(RobotRunState.POLLING_LISTING)
            val publication = pollUntilFound(config, baselineUrl)
            if (publication == null) {
                LogStore.warn("Окно 14:00 МСК закрыто, публикация не найдена. Остановка.")
                onState(RobotRunState.DONE_WINDOW_EXPIRED)
                return RobotRunState.DONE_WINDOW_EXPIRED
            }

            onState(RobotRunState.PUBLICATION_FOUND)
            LogStore.info(
                "[${publication.sourceLabel}] Публикация: " +
                    "daily=${publication.dailyVolumeRubBn} млрд " +
                    "(${if (publication.isPaused) "ПАУЗА" else "торгуем"}), " +
                    "period=${publication.periodStart}…${publication.periodEnd}, " +
                    "total=${publication.totalVolumeRubBn ?: "?"}"
            )

            if (!config.bypassDateCheck) {
                settings.lastProcessedPublicationUrl = publication.publicationUrl
            }

            if (publication.isPaused) {
                LogStore.warn("Минфин приостановил операции — НЕ ТОРГУЕМ")
                onState(RobotRunState.DONE_PAUSE)
                return RobotRunState.DONE_PAUSE
            }

            val matched = evaluator.match(publication, config.scenarios)
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
            LogStore.info("Робот остановлен (отмена корутины)")
            onState(RobotRunState.DONE_WINDOW_EXPIRED)
            throw e  // обязательно пробрасываем, иначе корутина не завершится корректно
        } catch (e: Exception) {
            LogStore.error("Фатальная ошибка робота: ${e.javaClass.simpleName}: ${e.message ?: "(нет сообщения)"}")
            onState(RobotRunState.ERROR)
            return RobotRunState.ERROR
        }
    }

    private suspend fun waitForWindowStart(config: RobotConfig): Boolean {
        val now = ZonedDateTime.now(MOSCOW)
        val target = config.targetDate
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

    /**
     * Опрашивает оба источника параллельно. Возвращает первую найденную
     * публикацию или null если окно истекло.
     *
     * В тестовом режиме (bypassDateCheck) делаем ВСЕГО 3 попытки —
     * публикация уже на сайте, должна найтись с первого запроса.
     * Если за 3 попытки нет — значит проблема (сеть/парсер/фильтр).
     */
    private suspend fun pollUntilFound(
        config: RobotConfig,
        baselineUrl: String?
    ): MinfinPublication? {
        var pollCount = 0
        val maxPollsForBypass = 3
        while (true) {
            if (config.bypassDateCheck) {
                if (pollCount >= maxPollsForBypass) {
                    LogStore.warn(
                        "Тестовый запуск: за $maxPollsForBypass попытки публикация " +
                            "не найдена. Возможные причины:"
                    )
                    LogStore.warn("  1. Сеть блокирует Минфин/ТАСС с твоего IP")
                    LogStore.warn("  2. isTargetTitle() не распознаёт текущий заголовок")
                    LogStore.warn("  3. Парсер числа не справился — см. логи выше")
                    return null
                }
            } else {
                val now = ZonedDateTime.now(MOSCOW)
                val nowMinute = now.toLocalTime().toSecondOfDay() / 60
                if (now.toLocalDate() != config.targetDate ||
                    nowMinute >= config.windowEndMinuteOfDay
                ) {
                    return null
                }
            }
            pollCount++

            // Опрашиваем оба источника параллельно.
            val publication = withContext(Dispatchers.IO) {
                coroutineScope {
                    val minfin = async { tryMinfin(baselineUrl) }
                    val tass = async { tryTass(baselineUrl) }
                    val results = listOf(minfin.await(), tass.await())
                    results.firstOrNull { it != null }
                }
            }
            if (publication != null) return publication

            if (pollCount % 30 == 0) {
                LogStore.info("Опрос #$pollCount: новых публикаций нет")
            }
            delay(config.pollIntervalMs)
        }
    }

    private fun tryMinfin(baselineUrl: String?): MinfinPublication? {
        return try {
            LogStore.info("→ запрос Минфина...")
            val links = minfinFetcher.fetchListing()
            LogStore.info("← Минфин отдал ${links.size} ссылок")
            val candidate = links.firstOrNull {
                MinfinParser.isTargetTitle(it.title) && it.url != baselineUrl
            }
            if (candidate != null) {
                LogStore.info("[MINFIN] Найдена публикация: ${candidate.title}")
                val html = minfinFetcher.fetchPublicationPage(candidate.url)
                LogStore.info("[MINFIN] Скачано ${html.length} символов, парсим")
                val parsed = MinfinParser.parse(html, candidate.url, candidate.title)
                if (parsed == null) {
                    LogStore.warn("[MINFIN] Страница найдена, но число не распарсилось")
                }
                parsed
            } else {
                null
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Не подавляем, иначе корутина не отменится. Не логируем как ошибку.
            throw e
        } catch (e: Exception) {
            LogStore.error("[MINFIN] ${e.javaClass.simpleName}: ${e.message ?: "(нет сообщения)"}")
            null
        }
    }

    private fun tryTass(baselineUrl: String?): MinfinPublication? {
        return try {
            LogStore.info("→ запрос ТАСС...")
            val links = tassFetcher.fetchListing()
            LogStore.info("← ТАСС отдал ${links.size} ссылок")
            val candidate = links.firstOrNull {
                TassParser.isTargetTitle(it.title) && it.url != baselineUrl
            }
            if (candidate != null) {
                LogStore.info("[TASS] Найдена публикация: ${candidate.title}")
                val html = tassFetcher.fetchPublicationPage(candidate.url)
                LogStore.info("[TASS] Скачано ${html.length} символов, парсим")
                val parsed = TassParser.parse(html, candidate.url, candidate.title)
                if (parsed == null) {
                    LogStore.warn("[TASS] Страница найдена, но число не распарсилось")
                }
                parsed
            } else {
                null
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            LogStore.error("[TASS] ${e.javaClass.simpleName}: ${e.message ?: "(нет сообщения)"}")
            null
        }
    }

    private suspend fun placeOrdersParallel(
        scenarios: List<Scenario>,
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
                LogStore.info(
                    "Ордер: ${scenario.action} ${scenario.quantity} " +
                        scenario.instrumentDisplay +
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
