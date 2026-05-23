package com.minfinrobot.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.minfinrobot.domain.model.RobotRunState
import com.minfinrobot.domain.model.Scenario
import com.minfinrobot.domain.model.ScenarioOperator
import com.minfinrobot.domain.model.TradeAction
import com.minfinrobot.ui.MainUiState
import com.minfinrobot.ui.MainViewModel

@Composable
fun MainScreen(viewModel: MainViewModel, state: MainUiState) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatusCard(state)
        TargetMonthSelector(state, viewModel)
        AccountSelector(state, viewModel)
        ScenarioList(state, viewModel)
        AddScenarioForm(state, viewModel)
        ControlButtons(state, viewModel)
    }
}

@Composable
private fun StatusCard(state: MainUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (state.isSandbox)
                MaterialTheme.colorScheme.tertiaryContainer
            else
                MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Режим: ${if (state.isSandbox) "SANDBOX (учебные деньги)" else "PRODUCTION (БОЕВОЙ)"}",
                fontWeight = FontWeight.Bold
            )
            Text("Статус: ${formatState(state.robotState)}")
            if (state.errorMessage != null) {
                Spacer(Modifier.height(4.dp))
                Text(state.errorMessage, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun TargetMonthSelector(state: MainUiState, vm: MainViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Целевая дата публикации", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Месяц: ${state.targetMonth}")
            Text("3-й рабочий день: ${state.targetDate}")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.setTargetMonth(state.targetMonth.minusMonths(1)) }) {
                    Text("← месяц назад")
                }
                OutlinedButton(onClick = { vm.setTargetMonth(state.targetMonth.plusMonths(1)) }) {
                    Text("месяц вперёд →")
                }
            }
        }
    }
}

@Composable
private fun AccountSelector(state: MainUiState, vm: MainViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Счёт T-Invest", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (state.accounts.isEmpty()) {
                Text("Счета не загружены. Нажмите 'Загрузить справочник'.")
            } else {
                state.accounts.forEach { acc ->
                    OutlinedButton(
                        onClick = { vm.selectAccount(acc.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val mark = if (state.selectedAccountId == acc.id) "● " else "○ "
                        Text("$mark${acc.name} (${acc.type})")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.refreshReferences() },
                enabled = !state.isLoadingReferences
            ) {
                Text(if (state.isLoadingReferences) "Загрузка..." else "Загрузить справочник")
            }
        }
    }
}

@Composable
private fun ScenarioList(state: MainUiState, vm: MainViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Сценарии (${state.scenarios.size})",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (state.scenarios.isNotEmpty()) {
                    OutlinedButton(onClick = { vm.clearScenarios() }) { Text("Очистить") }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (state.scenarios.isEmpty()) {
                Text("Нет сценариев. Добавьте ниже.")
            } else {
                state.scenarios.forEach { s ->
                    ScenarioRow(s, onDelete = { vm.removeScenario(s.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ScenarioRow(s: Scenario, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            val op = if (s.operator == ScenarioOperator.GREATER_THAN) ">" else "<"
            Text(
                "daily $op ${s.thresholdRubBn} млрд → ${s.action} ${s.quantity} ${s.instrumentDisplay}",
                fontWeight = FontWeight.Medium
            )
            Text(
                s.limitPrice?.let { "лимит $it" } ?: "по рынку",
                style = MaterialTheme.typography.bodySmall
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Удалить")
        }
    }
}

@Composable
private fun AddScenarioForm(state: MainUiState, vm: MainViewModel) {
    if (state.instruments.isEmpty()) return

    var operator by remember { mutableStateOf(ScenarioOperator.GREATER_THAN) }
    var threshold by remember { mutableStateOf("3.0") }
    var action by remember { mutableStateOf(TradeAction.BUY) }
    var quantity by remember { mutableStateOf("1") }
    var limitPrice by remember { mutableStateOf("") }
    var selectedUid by remember(state.instruments) {
        mutableStateOf(state.instruments.firstOrNull()?.uid ?: "")
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Новый сценарий", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { operator = ScenarioOperator.GREATER_THAN }) {
                    Text(if (operator == ScenarioOperator.GREATER_THAN) "● больше" else "○ больше")
                }
                OutlinedButton(onClick = { operator = ScenarioOperator.LESS_THAN }) {
                    Text(if (operator == ScenarioOperator.LESS_THAN) "● меньше" else "○ меньше")
                }
            }

            OutlinedTextField(
                value = threshold,
                onValueChange = { threshold = it },
                label = { Text("Порог, ₽ млрд/день (знаковый: +5.8 или -1.6)") },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { action = TradeAction.BUY }) {
                    Text(if (action == TradeAction.BUY) "● Купить" else "○ Купить")
                }
                OutlinedButton(onClick = { action = TradeAction.SELL }) {
                    Text(if (action == TradeAction.SELL) "● Продать" else "○ Продать")
                }
            }

            OutlinedTextField(
                value = quantity,
                onValueChange = { quantity = it.filter { c -> c.isDigit() } },
                label = { Text("Количество лотов") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = limitPrice,
                onValueChange = { limitPrice = it },
                label = { Text("Лимит цены (пусто = по рынку)") },
                modifier = Modifier.fillMaxWidth()
            )

            Text("Инструмент:", fontWeight = FontWeight.Medium)
            LazyColumn(modifier = Modifier.fillMaxWidth().height(140.dp)) {
                items(state.instruments) { ins ->
                    OutlinedButton(
                        onClick = { selectedUid = ins.uid },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val mark = if (selectedUid == ins.uid) "● " else "○ "
                        Text("$mark${ins.ticker} (${ins.basicAsset}, шаг ${ins.minPriceIncrement})")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val thr = threshold.replace(',', '.').toDoubleOrNull()
                    val qty = quantity.toIntOrNull()
                    val lim = limitPrice.replace(',', '.').toDoubleOrNull()
                    val instr = state.instruments.firstOrNull { it.uid == selectedUid }
                    if (thr != null && qty != null && qty > 0 && instr != null) {
                        vm.addScenario(Scenario(
                            operator = operator,
                            thresholdRubBn = thr,
                            action = action,
                            instrumentUid = instr.uid,
                            instrumentDisplay = instr.ticker,
                            quantity = qty,
                            limitPrice = lim
                        ))
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("+ Добавить сценарий") }
        }
    }
}

@Composable
private fun ControlButtons(state: MainUiState, vm: MainViewModel) {
    val isRunning = state.robotState in listOf(
        RobotRunState.WAITING_FOR_WINDOW,
        RobotRunState.POLLING_LISTING,
        RobotRunState.PUBLICATION_FOUND,
        RobotRunState.PLACING_ORDERS
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { vm.startRobot() },
            enabled = !isRunning && state.scenarios.isNotEmpty() && state.selectedAccountId != null,
            modifier = Modifier.weight(1f)
        ) { Text("ЗАПУСТИТЬ") }
        OutlinedButton(
            onClick = { vm.stopRobot() },
            enabled = isRunning,
            modifier = Modifier.weight(1f)
        ) { Text("Остановить") }
    }
}

private fun formatState(s: RobotRunState): String = when (s) {
    RobotRunState.IDLE -> "Ожидание"
    RobotRunState.WAITING_FOR_WINDOW -> "Ждём открытия окна (11:30 МСК)"
    RobotRunState.POLLING_LISTING -> "Мониторим сайт Минфина"
    RobotRunState.PUBLICATION_FOUND -> "Публикация найдена"
    RobotRunState.PLACING_ORDERS -> "Отправляем ордера"
    RobotRunState.DONE_SUCCESS -> "Завершено успешно"
    RobotRunState.DONE_PAUSE -> "Минфин объявил паузу (сделок нет)"
    RobotRunState.DONE_WINDOW_EXPIRED -> "Публикация не появилась"
    RobotRunState.ERROR -> "Завершено с ошибкой"
}
