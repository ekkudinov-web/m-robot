package com.minfinrobot

import android.app.Application
import com.minfinrobot.data.cbr.CbrFetcher
import com.minfinrobot.data.minfin.MinfinFetcher
import com.minfinrobot.data.settings.SecureSettingsStore
import com.minfinrobot.data.tass.TassFetcher
import com.minfinrobot.data.tbank.TBankRepository
import com.minfinrobot.domain.engine.CbrScenarioEvaluator
import com.minfinrobot.domain.engine.ScenarioEvaluator
import com.minfinrobot.domain.usecase.StartCbrRobotUseCase
import com.minfinrobot.domain.usecase.StartRobotUseCase

/**
 * DI-контейнер. Manual DI без Hilt — слишком просто чтобы тащить.
 */
class MinfinRobotApp : Application() {
    lateinit var settings: SecureSettingsStore; private set
    lateinit var tbank: TBankRepository; private set
    lateinit var minfinFetcher: MinfinFetcher; private set
    lateinit var tassFetcher: TassFetcher; private set
    lateinit var cbrFetcher: CbrFetcher; private set
    lateinit var startRobotUseCase: StartRobotUseCase; private set
    lateinit var startCbrRobotUseCase: StartCbrRobotUseCase; private set

    override fun onCreate() {
        super.onCreate()
        settings = SecureSettingsStore(this)
        tbank = TBankRepository(settings)
        minfinFetcher = MinfinFetcher()
        tassFetcher = TassFetcher()
        cbrFetcher = CbrFetcher()
        startRobotUseCase = StartRobotUseCase(
            minfinFetcher = minfinFetcher,
            tassFetcher = tassFetcher,
            tbank = tbank,
            settings = settings,
            evaluator = ScenarioEvaluator()
        )
        startCbrRobotUseCase = StartCbrRobotUseCase(
            cbrFetcher = cbrFetcher,
            tbank = tbank,
            settings = settings,
            evaluator = CbrScenarioEvaluator()
        )
    }
}
