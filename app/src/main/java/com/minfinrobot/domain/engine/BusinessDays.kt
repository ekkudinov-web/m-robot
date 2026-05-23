package com.minfinrobot.domain.engine

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Вычисление N-го рабочего дня месяца.
 * Внимание: не учитывает праздники РФ — только выходные.
 */
object BusinessDays {

    fun nthBusinessDayOfMonth(year: Int, month: Int, n: Int): LocalDate {
        require(n in 1..28) { "n должно быть от 1 до 28" }
        var date = LocalDate.of(year, month, 1)
        var count = 0
        while (true) {
            if (date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY) {
                count++
                if (count == n) return date
            }
            date = date.plusDays(1)
        }
    }

    fun thirdBusinessDay(year: Int, month: Int): LocalDate =
        nthBusinessDayOfMonth(year, month, 3)
}
