package com.minfinrobot.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.minfinrobot.MinfinRobotApp
import com.minfinrobot.background.RobotExecutionService
import com.minfinrobot.data.log.LogStore
import com.minfinrobot.domain.engine.BusinessDays
import com.minfinrobot.domain.model.AccountRef
import com.minfinrobot.domain.model.CbrConfig
import com.minfinrobot.domain.model.CbrScenario
import com.minfinrobot.domain.model.InstrumentRef
import com.minfinrobot.domain.model.RobotConfig
import com.minfinrobot.domain.model.RobotRunState
import com.minfinrobot.domain.model.Scenario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

data class MainUiState(
    val isSandbox: Boolean = true,
    val hasSandboxToken: Boolean = false,
    val hasProductionToken: Boolean = false,
    val accounts: List<AccountRef> = emptyList(),
    val instruments: List<InstrumentRef> = emptyList(),
    val selectedAccountId: String? = null,
    // Минфин-робот
    val scenarios: List<Scenario> = emptyList(),
    val targetMonth: YearMonth = YearMonth.now(),
    val targetDate: LocalDate = BusinessDays.thirdBusinessDay(
        YearMonth.now().year, YearMonth.now().monthValue
    ),
    // ЦБ-робот
    val cbrScenarios: List<CbrScenario> = emptyList(),
    val cbrMeetingDate: LocalDate = LocalDate.now(),
    // Общее
    val robotState: RobotRunState = RobotRunState.IDLE,
    val isLoadingReferences: Boolean = false,
    val errorMessage: String? = null
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val appInstance = app as MinfinRobotApp

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val logs = LogStore.entries
    val robotState = RobotExecutionService.State.currentState

    init {
        _uiState.value = _uiState.value.copy(
            isSandbox = appInstance.settings.isSandbox,
            hasSandboxToken = appInstance.settings.hasSandboxToken(),
            hasProductionToken = appInstance.settings.hasProductionToken()
        )
    }

    fun setSandboxMode(sandbox: Boolean) {
        appInstance.settings.isSandbox = sandbox
        _uiState.value = _uiState.value.copy(
            isSandbox = sandbox,
            accounts = emptyList(),
            instruments = emptyList(),
            selectedAccountId = null
        )
        LogStore.info("Режим: ${if (sandbox) "SANDBOX" else "PRODUCTION"}")
    }

    fun setSandboxToken(token: String) {
        appInstance.settings.setSandboxToken(token)
        _uiState.value = _uiState.value.copy(hasSandboxToken = token.isNotEmpty())
    }

    fun setProductionToken(token: String) {
        appInstance.settings.setProductionToken(token)
        _uiState.value = _uiState.value.copy(hasProductionToken = token.isNotEmpty())
    }

    fun refreshReferences() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingReferences = true, errorMessage = null)
            try {
                val accounts = appInstance.tbank.loadAccounts()
                val instruments = appInstance.tbank.loadFutures()
                _uiState.value = _uiState.value.copy(
                    accounts = accounts,
                    instruments = instruments,
                    selectedAccountId = accounts.firstOrNull()?.id,
                    isLoadingReferences = false
                )
                LogStore.info("Загружено: ${accounts.size} счетов, ${instruments.size} фьючерсов")
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingReferences = false,
                    errorMessage = "Ошибка справочника: ${e.message}"
                )
                LogStore.error("Справочник: ${e.message}")
            }
        }
    }

    fun selectAccount(id: String) {
        _uiState.value = _uiState.value.copy(selectedAccountId = id)
    }

    // === Минфин-робот ===

    fun setTargetMonth(month: YearMonth) {
        _uiState.value = _uiState.value.copy(
            targetMonth = month,
            targetDate = BusinessDays.thirdBusinessDay(month.year, month.monthValue)
        )
    }

    fun addScenario(scenario: Scenario) {
        _uiState.value = _uiState.value.copy(scenarios = _uiState.value.scenarios + scenario)
    }

    fun removeScenario(id: String) {
        _uiState.value = _uiState.value.copy(
            scenarios = _uiState.value.scenarios.filter { it.id != id }
        )
    }

    fun clearScenarios() {
        _uiState.value = _uiState.value.copy(scenarios = emptyList())
    }

    fun startRobot() {
        val state = _uiState.value
        if (RobotExecutionService.State.isBusy()) {
            _uiState.value = state.copy(errorMessage = "Уже запущен другой робот — остановите его")
            LogStore.warn("Запуск отклонён: уже запущен другой робот")
            return
        }
        val accountId = state.selectedAccountId ?: run {
            LogStore.error("Не выбран счёт"); return
        }
        if (state.scenarios.isEmpty()) {
            LogStore.error("Нет ни одного сценария"); return
        }
        val config = RobotConfig(
            targetDate = state.targetDate,
            accountId = accountId,
            scenarios = state.scenarios
        )
        val ok = RobotExecutionService.startMinfin(getApplication(), config, state.instruments)
        if (!ok) {
            _uiState.value = state.copy(errorMessage = "Не удалось запустить Минфин-робот")
        }
    }

    // === ЦБ-робот ===

    fun setCbrMeetingDate(date: LocalDate) {
        _uiState.value = _uiState.value.copy(cbrMeetingDate = date)
    }

    fun addCbrScenario(scenario: CbrScenario) {
        _uiState.value = _uiState.value.copy(cbrScenarios = _uiState.value.cbrScenarios + scenario)
    }

    fun removeCbrScenario(id: String) {
        _uiState.value = _uiState.value.copy(
            cbrScenarios = _uiState.value.cbrScenarios.filter { it.id != id }
        )
    }

    fun clearCbrScenarios() {
        _uiState.value = _uiState.value.copy(cbrScenarios = emptyList())
    }

    fun startCbrRobot() {
        val state = _uiState.value
        if (RobotExecutionService.State.isBusy()) {
            _uiState.value = state.copy(errorMessage = "Уже запущен другой робот — остановите его")
            LogStore.warn("Запуск отклонён: уже запущен другой робот")
            return
        }
        val accountId = state.selectedAccountId ?: run {
            LogStore.error("Не выбран счёт"); return
        }
        if (state.cbrScenarios.isEmpty()) {
            LogStore.error("Нет ни одного сценария ЦБ"); return
        }
        val config = CbrConfig(
            meetingDate = state.cbrMeetingDate,
            accountId = accountId,
            scenarios = state.cbrScenarios
        )
        val ok = RobotExecutionService.startCbr(getApplication(), config, state.instruments)
        if (!ok) {
            _uiState.value = state.copy(errorMessage = "Не удалось запустить ЦБ-робот")
        }
    }

    // === Общее ===

    fun stopRobot() {
        RobotExecutionService.stop(getApplication())
    }

    fun clearLog() = LogStore.clear()
}
