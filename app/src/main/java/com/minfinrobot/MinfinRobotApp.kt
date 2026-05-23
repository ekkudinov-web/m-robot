package com.minfinrobot

import android.app.Application
import com.minfinrobot.data.minfin.MinfinFetcher
import com.minfinrobot.data.settings.SecureSettingsStore
import com.minfinrobot.data.tass.TassFetcher
import com.minfinrobot.data.tbank.TBankRepository
import com.minfinrobot.domain.engine.ScenarioEvaluator
import com.minfinrobot.domain.usecase.StartRobotUseCase

/**
 * Контейнер для ручного DI (без Hilt/Dagger — слишком просто, чтоб их тащить).
 * Доступно через context.applicationContext as MinfinRobotApp.
 */
class MinfinRobotApp : Application() {
    lateinit var settings: SecureSettingsStore; private set
    lateinit var tbank: TBankRepository; private set
    lateinit var minfinFetcher: MinfinFetcher; private set
    lateinit var tassFetcher: TassFetcher; private set
    lateinit var startRobotUseCase: StartRobotUseCase; private set

    override fun onCreate() {
        super.onCreate()
        settings = SecureSettingsStore(this)
        tbank = TBankRepository(settings)
        minfinFetcher = MinfinFetcher()
        tassFetcher = TassFetcher()
        startRobotUseCase = StartRobotUseCase(
            minfinFetcher = minfinFetcher,
            tassFetcher = tassFetcher,
            tbank = tbank,
            settings = settings,
            evaluator = ScenarioEvaluator()
        )
    }
}
