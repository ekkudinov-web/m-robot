package com.minfinrobot.domain.engine

import com.minfinrobot.domain.model.TradeAction
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Округление лимитной цены к шагу инструмента (minPriceIncrement).
 *
 * BUY  → округляем ВНИЗ (не платим больше чем готовы).
 * SELL → округляем ВВЕРХ (не продаём дешевле чем готовы).
 *
 * ВАЖНО: учитываем ошибку double-арифметики. Например 10.450/0.001
 * даёт 10449.999999998 из-за неточности IEEE754. Без коррекции floor() даёт
 * 10449 * 0.001 = 10.449, что неверно (юзер ввёл точное 10.450). Поэтому
 * перед floor/ceil добавляем небольшой epsilon в нужном направлении.
 *
 * Примеры:
 *   Si (шаг 1.0):     78500.7,  BUY  → 78500.0
 *   Si (шаг 1.0):     78500.7,  SELL → 78501.0
 *   CR (шаг 0.001):   10.4567,  BUY  → 10.456
 *   CR (шаг 0.001):   10.450,   BUY  → 10.450  (без epsilon была бы 10.449!)
 */
object PriceRoundingUtil {

    private const val EPSILON = 1e-9

    fun roundToIncrement(
        price: Double,
        minPriceIncrement: Double,
        action: TradeAction
    ): Double {
        if (minPriceIncrement <= 0.0) return price
        val steps = price / minPriceIncrement
        val rounded = when (action) {
            // Для BUY округляем вниз. Добавляем +EPSILON, чтобы цены точно кратные шагу
            // не уехали на шаг ниже из-за ошибки double.
            TradeAction.BUY -> floor(steps + EPSILON) * minPriceIncrement
            // Симметрично для SELL.
            TradeAction.SELL -> ceil(steps - EPSILON) * minPriceIncrement
        }
        // Финальная нормализация: округление накопленной ошибки double до 9 знаков
        // после запятой (запас на nano-quotation у T-Invest).
        return Math.round(rounded * 1_000_000_000.0) / 1_000_000_000.0
    }
}
