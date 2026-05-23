package com.minfinrobot.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Сценарий торговли по решению ЦБ о ключевой ставке.
 *
 * Два режима сравнения:
 *
 *  (α) АБСОЛЮТНЫЙ — expectedRatePercent == null:
 *      Сравнение новой ставки с фиксированным порогом thresholdPercent.
 *      Пример: "если ставка > 19% → купить Si"
 *              operator=GREATER_THAN, thresholdPercent=19.0
 *
 *  (γ) ОТНОСИТЕЛЬНЫЙ (vs прогноз) — expectedRatePercent != null:
 *      Сравнение новой ставки с ожидаемым значением. thresholdPercent игнорируется.
 *      Пример: "если ставка ниже ожидаемых 20% (значит ЦБ мягче) → купить RGBI"
 *              operator=LESS_THAN, expectedRatePercent=20.0
 *
 * @param expectedRatePercent  null = режим α. Число = режим γ.
 *                             Сценарий сравнивает новую ставку с этим значением.
 */
@Serializable
data class CbrScenario(
    val id: String = UUID.randomUUID().toString(),
    val operator: ScenarioOperator,
    val thresholdPercent: Double,
    val expectedRatePercent: Double? = null,
    val action: TradeAction,
    val instrumentUid: String,
    val instrumentDisplay: String,
    val quantity: Int,
    val limitPrice: Double? = null
)
