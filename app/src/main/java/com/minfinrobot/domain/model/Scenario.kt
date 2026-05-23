package com.minfinrobot.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Сценарий торговли.
 *
 * Сценарий срабатывает, если значение ежедневного объёма операций Минфина
 * удовлетворяет (operator, thresholdRubBn). При срабатывании отправляется ордер.
 *
 * @param thresholdRubBn  Порог в ₽ млрд (знаковый). + покупка, − продажа.
 * @param action          BUY или SELL — направление СДЕЛКИ на бирже
 *                        (определяет пользователь, не зависит от знака Минфина).
 * @param instrumentUid   UUID фьючерса из справочника Т-Инвестиций.
 *                        Получается через InstrumentsService/Futures.
 * @param instrumentDisplay  Тикер для UI (Si-6.26, CR-6.26, Eu-6.26).
 * @param quantity        Количество лотов фьючерса (для фьючерсов 1 лот = 1 контракт).
 * @param limitPrice      Опционально: лимитная цена. null = рыночный ордер.
 *                        При наличии будет отправлен ORDER_TYPE_LIMIT.
 *                        Округляется к minPriceIncrement (вниз для BUY, вверх для SELL).
 */
@Serializable
data class Scenario(
    val id: String = UUID.randomUUID().toString(),
    val operator: ScenarioOperator,
    val thresholdRubBn: Double,
    val action: TradeAction,
    val instrumentUid: String,
    val instrumentDisplay: String,
    val quantity: Int,
    val limitPrice: Double? = null
)
