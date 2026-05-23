package com.minfinrobot.ui.cbr

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
import com.minfinrobot.domain.model.CbrScenario
import com.minfinrobot.domain.model.RobotRunState
import com.minfinrobot.domain.model.ScenarioOperator
import com.minfinrobot.domain.model.TradeAction
import com.minfinrobot.ui.MainUiState
import com.minfinrobot.ui.MainViewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun CbrScreen(viewModel: MainViewModel, state: MainUiState) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CbrStatusCard(state)
        CbrDateSelector(state, viewModel)
        CbrAccountInfo(state)
        CbrScenarioList(state, viewModel)
        CbrAddScenarioForm(state, viewModel)
        CbrControls(state, viewModel)
    }
}

@Composable
private fun CbrStatusCard(state: MainUiState) {
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
            Text("Робот ключевой ставки ЦБ", fontWeight = FontWeight.Bold)
            Text(
                "Режим: ${if (state.isSandbox) "SANDBOX" else "PRODUCTION (БОЕВОЙ)"}",
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                "Статус: ${formatState(state.robotState)}",
                style = MaterialTheme.typography.bodySmall
            )
            if (state.errorMessage != null) {
                Spacer(Modifier.height(4.dp))
                Text(state.errorMessage, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun CbrDateSelector(state: MainUiState, vm: MainViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Дата заседания ЦБ", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("Текущая: ${state.cbrMeetingDate.format(FMT)}")
            Text(
                "Опрос: 13:25-14:00 МСК (пресс-релиз публикуется в 13:30)",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(8.dp))

            // Грубые сдвиги — без full date picker, чтобы не тянуть зависимости.
            // Пользователь правит до точной даты заседания.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.setCbrMeetingDate(state.cbrMeetingDate.minusDays(1)) }
                ) { Text("← день") }
                OutlinedButton(
                    onClick = { vm.setCbrMeetingDate(state.cbrMeetingDate.plusDays(1)) }
                ) { Text("день →") }
                OutlinedButton(
                    onClick = { vm.setCbrMeetingDate(state.cbrMeetingDate.plusWeeks(1)) }
                ) { Text("+1 нед") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { vm.setCbrMeetingDate(state.cbrMeetingDate.minusMonths(1)) }
                ) { Text("← месяц") }
                OutlinedButton(
                    onClick = { vm.setCbrMeetingDate(state.cbrMeetingDate.plusMonths(1)) }
                ) { Text("месяц →") }
                OutlinedButton(
                    onClick = { vm.setCbrMeetingDate(LocalDate.now()) }
                ) { Text("Сегодня") }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "URL пресс-релиза: cbr.ru/press/pr/?file=" +
                    state.cbrMeetingDate.format(URL_FMT) + "_133000key.htm",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CbrAccountInfo(state: MainUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Счёт T-Invest", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            val acc = state.accounts.firstOrNull { it.id == state.selectedAccountId }
            if (acc != null) {
                Text("Выбран: ${acc.name} (${acc.type})")
            } else {
                Text(
                    "Счёт не выбран. Перейди во вкладку Главная → загрузи справочник.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                "Загружено инструментов: ${state.instruments.size}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun CbrScenarioList(state: MainUiState, vm: MainViewModel) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Сценарии ЦБ (${state.cbrScenarios.size})",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (state.cbrScenarios.isNotEmpty()) {
                    OutlinedButton(onClick = { vm.clearCbrScenarios() }) { Text("Очистить") }
                }
            }
            Spacer(Modifier.height(8.dp))
            if (state.cbrScenarios.isEmpty()) {
                Text("Нет сценариев. Добавь ниже.")
            } else {
                state.cbrScenarios.forEach { s ->
                    CbrScenarioRow(s, onDelete = { vm.removeCbrScenario(s.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun CbrScenarioRow(s: CbrScenario, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            val op = if (s.operator == ScenarioOperator.GREATER_THAN) ">" else "<"
            val cmpValue = s.expectedRatePercent ?: s.thresholdPercent
            val cmpMode = if (s.expectedRatePercent != null) "vs прогноз" else "порог"
            Text(
                "ставка $op $cmpValue% ($cmpMode) → ${s.action} ${s.quantity} ${s.instrumentDisplay}",
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodySmall
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
private fun CbrAddScenarioForm(state: MainUiState, vm: MainViewModel) {
    if (state.instruments.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Чтобы добавить сценарий, сначала загрузи справочник " +
                        "инструментов во вкладке Главная.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        return
    }

    var operator by remember { mutableStateOf(ScenarioOperator.GREATER_THAN) }
    var threshold by remember { mutableStateOf("20.0") }
    var useExpected by remember { mutableStateOf(false) }
    var expected by remember { mutableStateOf("20.0") }
    var action by remember { mutableStateOf(TradeAction.BUY) }
    var quantity by remember { mutableStateOf("1") }
    var limitPrice by remember { mutableStateOf("") }
    var selectedUid by remember(state.instruments) {
        mutableStateOf(state.instruments.firstOrNull()?.uid ?: "")
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Новый сценарий ЦБ", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { operator = ScenarioOperator.GREATER_THAN },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (operator == ScenarioOperator.GREATER_THAN) "● больше" else "○ больше")
                }
                OutlinedButton(
                    onClick = { operator = ScenarioOperator.LESS_THAN },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (operator == ScenarioOperator.LESS_THAN) "● меньше" else "○ меньше")
                }
            }

            OutlinedTextField(
                value = threshold,
                onValueChange = { threshold = it },
                label = { Text("Порог ставки, % (например 20.0)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !useExpected
            )

            Spacer(Modifier.height(4.dp))
            OutlinedButton(
                onClick = { useExpected = !useExpected },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (useExpected)
                        "● Сравнивать с ожидаемой ставкой (γ-режим)"
                    else
                        "○ Сравнивать с ожидаемой ставкой (γ-режим)"
                )
            }

            if (useExpected) {
                OutlinedTextField(
                    value = expected,
                    onValueChange = { expected = it },
                    label = { Text("Ожидаемая ставка (прогноз), %") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "В этом режиме сравнение идёт с прогнозом, а не с порогом.",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { action = TradeAction.BUY },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (action == TradeAction.BUY) "● Купить" else "○ Купить")
                }
                OutlinedButton(
                    onClick = { action = TradeAction.SELL },
                    modifier = Modifier.weight(1f)
                ) {
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
            LazyColumn(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                items(state.instruments) { ins ->
                    OutlinedButton(
                        onClick = { selectedUid = ins.uid },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val mark = if (selectedUid == ins.uid) "● " else "○ "
                        Text(
                            "$mark${ins.ticker} (${ins.basicAsset}, шаг ${ins.minPriceIncrement})",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val thr = threshold.replace(',', '.').toDoubleOrNull()
                    val exp = if (useExpected) expected.replace(',', '.').toDoubleOrNull() else null
                    val qty = quantity.toIntOrNull()
                    val lim = limitPrice.replace(',', '.').toDoubleOrNull()
                    val instr = state.instruments.firstOrNull { it.uid == selectedUid }
                    if (thr != null && qty != null && qty > 0 && instr != null) {
                        if (useExpected && exp == null) return@Button
                        vm.addCbrScenario(
                            CbrScenario(
                                operator = operator,
                                thresholdPercent = thr,
                                expectedRatePercent = exp,
                                action = action,
                                instrumentUid = instr.uid,
                                instrumentDisplay = instr.ticker,
                                quantity = qty,
                                limitPrice = lim
                            )
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("+ Добавить сценарий ЦБ") }
        }
    }
}

@Composable
private fun CbrControls(state: MainUiState, vm: MainViewModel) {
    val isRunning = state.robotState in listOf(
        RobotRunState.WAITING_FOR_WINDOW,
        RobotRunState.POLLING_LISTING,
        RobotRunState.PUBLICATION_FOUND,
        RobotRunState.PLACING_ORDERS
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = { vm.startCbrRobot() },
            enabled = !isRunning &&
                state.cbrScenarios.isNotEmpty() &&
                state.selectedAccountId != null,
            modifier = Modifier.weight(1f)
        ) { Text("ЗАПУСТИТЬ ЦБ") }
        OutlinedButton(
            onClick = { vm.stopRobot() },
            enabled = isRunning,
            modifier = Modifier.weight(1f)
        ) { Text("Остановить") }
    }
}

private fun formatState(s: RobotRunState): String = when (s) {
    RobotRunState.IDLE -> "Ожидание"
    RobotRunState.WAITING_FOR_WINDOW -> "Ждём окна (13:25 МСК)"
    RobotRunState.POLLING_LISTING -> "Мониторим сайт ЦБ"
    RobotRunState.PUBLICATION_FOUND -> "Пресс-релиз найден"
    RobotRunState.PLACING_ORDERS -> "Отправляем ордера"
    RobotRunState.DONE_SUCCESS -> "Завершено успешно"
    RobotRunState.DONE_PAUSE -> "Пауза (Минфин)"
    RobotRunState.DONE_WINDOW_EXPIRED -> "Пресс-релиз не появился"
    RobotRunState.ERROR -> "Завершено с ошибкой"
}

private val FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy (EEE)")
private val URL_FMT = DateTimeFormatter.ofPattern("ddMMyyyy")
