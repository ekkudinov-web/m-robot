package com.minfinrobot.domain.engine

import com.minfinrobot.domain.model.MinfinPublication
import com.minfinrobot.domain.model.Scenario
import com.minfinrobot.domain.model.ScenarioOperator

/**
 * Оценщик сценариев.
 *
 * КРИТИЧНО: при паузе (publication.isPaused == true) возвращает пустой список
 * НЕЗАВИСИМО от настроек сценариев. Требование пользователя.
 */
class ScenarioEvaluator {

    fun match(publication: MinfinPublication, scenarios: List<Scenario>): List<Scenario> {
        if (publication.isPaused) return emptyList()
        val value = publication.dailyVolumeRubBn
        return scenarios.filter { s ->
            when (s.operator) {
                ScenarioOperator.GREATER_THAN -> value > s.thresholdRubBn
                ScenarioOperator.LESS_THAN -> value < s.thresholdRubBn
            }
        }
    }
}
