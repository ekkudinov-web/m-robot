package com.minfinrobot.domain.model

import java.time.LocalDate

/**
 * Конфигурация одного запуска робота.
 *
 * @param targetDate           Дата ожидаемой публикации (3-й рабочий день месяца),
 *                             вычисляется во ViewModel и передаётся сюда готовой.
 * @param windowStartMinuteOfDay  Начало окна мониторинга в минутах от 00:00 МСК.
 *                             По умолчанию 11:30 = 690.
 * @param windowEndMinuteOfDay Конец окна мониторинга, по умолчанию 14:00 = 840.
 * @param accountId            ID торгового счёта в Т-Инвестициях.
 * @param scenarios            Сценарии (могут срабатывать параллельно).
 * @param pollIntervalMs       Интервал опроса в миллисекундах. По умолчанию 2000.
 */
data class RobotConfig(
    val targetDate: LocalDate,
    val accountId: String,
    val scenarios: List<Scenario>,
    val windowStartMinuteOfDay: Int = 11 * 60 + 30,   // 11:30 МСК
    val windowEndMinuteOfDay: Int = 14 * 60,          // 14:00 МСК
    val pollIntervalMs: Long = 2000L
)

/**
 * Состояние работающего робота.
 */
enum class RobotRunState {
    IDLE,
    WAITING_FOR_WINDOW,
    POLLING_LISTING,
    PUBLICATION_FOUND,
    PLACING_ORDERS,
    DONE_SUCCESS,
    DONE_PAUSE,
    DONE_WINDOW_EXPIRED,
    ERROR
}
