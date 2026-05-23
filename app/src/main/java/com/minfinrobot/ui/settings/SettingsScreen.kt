package com.minfinrobot.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.minfinrobot.ui.MainUiState
import com.minfinrobot.ui.MainViewModel

@Composable
fun SettingsScreen(viewModel: MainViewModel, state: MainUiState) {
    var sandboxToken by remember { mutableStateOf("") }
    var productionToken by remember { mutableStateOf("") }
    var showProductionWarning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (state.isSandbox)
                    MaterialTheme.colorScheme.tertiaryContainer
                else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Режим работы", fontWeight = FontWeight.Bold)
                        Text(if (state.isSandbox) "SANDBOX (учебные)" else "PRODUCTION (БОЕВОЙ)")
                    }
                    Switch(
                        checked = !state.isSandbox,
                        onCheckedChange = { switchToProd ->
                            if (switchToProd) showProductionWarning = true
                            else viewModel.setSandboxMode(true)
                        }
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (state.isSandbox)
                        "Sandbox использует учебные деньги. Реальные ордера не отправляются."
                    else
                        "ВНИМАНИЕ: PRODUCTION. Все ордера будут реальными.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (showProductionWarning) {
            AlertDialog(
                onDismissRequest = { showProductionWarning = false },
                title = { Text("Перейти в PRODUCTION?") },
                text = { Text("В этом режиме ордера будут реальными. Подтвердите.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.setSandboxMode(false)
                        showProductionWarning = false
                    }) { Text("Да, в боевой") }
                },
                dismissButton = {
                    TextButton(onClick = { showProductionWarning = false }) { Text("Отмена") }
                }
            )
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sandbox-токен", fontWeight = FontWeight.Bold)
                Text(
                    "Сохранён: ${if (state.hasSandboxToken) "да" else "нет"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = sandboxToken,
                    onValueChange = { sandboxToken = it },
                    label = { Text("Sandbox-токен") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (sandboxToken.isNotBlank()) {
                            viewModel.setSandboxToken(sandboxToken.trim())
                            sandboxToken = ""
                        }
                    },
                    enabled = sandboxToken.isNotBlank()
                ) { Text("Сохранить") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Production-токен", fontWeight = FontWeight.Bold)
                Text(
                    "Сохранён: ${if (state.hasProductionToken) "да" else "нет"}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = productionToken,
                    onValueChange = { productionToken = it },
                    label = { Text("Production-токен") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (productionToken.isNotBlank()) {
                            viewModel.setProductionToken(productionToken.trim())
                            productionToken = ""
                        }
                    },
                    enabled = productionToken.isNotBlank()
                ) { Text("Сохранить") }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Как получить токен", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Production: my.tinkoff.ru → Инвестиции → Настройки → Токены T-Invest API → создать с правами на торговлю.")
                Spacer(Modifier.height(8.dp))
                Text("Sandbox: тот же раздел, тип «Sandbox». Перед первой работой создать sandbox-счёт через REST-эндпоинт SandboxService/OpenSandboxAccount (см. README).")
                Spacer(Modifier.height(8.dp))
                Text("Токены хранятся локально с AES-256 шифрованием. Не публикуйте APK с токеном внутри.")
            }
        }
    }
}
