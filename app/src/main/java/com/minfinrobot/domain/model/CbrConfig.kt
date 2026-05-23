package com.minfinrobot.domain.model

import java.time.LocalDate

/**
 * Конфигурация запуска ЦБ-робота.
 *
 * @param meetingDate          Точная дата заседания ЦБ.
 *                             URL пресс-релиза: cbr.ru/press/pr/?file=DDMMYYYY_133000key.htm
 * @param windowStartMinuteOfDay  Когда начать опрос. По умолчанию 13:25 МСК = 805.
 *                             ЦБ публикует ровно в 13:30, поэтому начинаем за 5 минут.
 * @param windowEndMinuteOfDay Конец окна, по умолчанию 14:00 МСК = 840.
 * @param accountId            ID торгового счёта Т-Инвестиций.
 * @param scenarios            Список сценариев (параллельная отправка ордеров).
 * @param pollIntervalMs       Интервал поллинга в мс (по умолчанию 2000).
 */
data class CbrConfig(
    val meetingDate: LocalDate,
    val accountId: String,
    val scenarios: List<CbrScenario>,
    val windowStartMinuteOfDay: Int = 13 * 60 + 25,
    val windowEndMinuteOfDay: Int = 14 * 60,
    val pollIntervalMs: Long = 2000L
)

/**
 * Результат парсинга пресс-релиза ЦБ.
 */
data class CbrRateDecision(
    val ratePercent: Double,
    val sourceUrl: String,
    val title: String = ""
)
