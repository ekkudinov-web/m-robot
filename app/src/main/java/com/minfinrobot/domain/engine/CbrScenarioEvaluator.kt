package com.minfinrobot.domain.engine

import com.minfinrobot.domain.model.CbrRateDecision
import com.minfinrobot.domain.model.CbrScenario
import com.minfinrobot.domain.model.ScenarioOperator

/**
 * Оценщик сценариев ЦБ.
 *
 * Два режима сравнения, выбираются автоматически по полю expectedRatePercent:
 *
 *   α (абсолютный) — expectedRatePercent == null:
 *     compareValue = scenario.thresholdPercent
 *     Пример: ставка 20.0 > 19.0 (threshold) → сработал
 *
 *   γ (vs прогноз) — expectedRatePercent != null:
 *     compareValue = scenario.expectedRatePercent
 *     Пример: ставка 18.0 < 20.0 (expected) → ЦБ оказался мягче ожидаемого → сработал
 *
 * В обоих режимах оператор может быть GREATER_THAN или LESS_THAN.
 */
class CbrScenarioEvaluator {

    fun match(decision: CbrRateDecision, scenarios: List<CbrScenario>): List<CbrScenario> {
        val rate = decision.ratePercent
        return scenarios.filter { scenario ->
            val compareValue = scenario.expectedRatePercent ?: scenario.thresholdPercent
            when (scenario.operator) {
                ScenarioOperator.GREATER_THAN -> rate > compareValue
                ScenarioOperator.LESS_THAN -> rate < compareValue
            }
        }
    }
}
