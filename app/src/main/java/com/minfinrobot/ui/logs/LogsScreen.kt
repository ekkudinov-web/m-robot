package com.minfinrobot.ui.logs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.minfinrobot.data.log.LogLevel
import com.minfinrobot.ui.MainViewModel

/**
 * Экран логов. Использует обычный Column (не LazyColumn), потому что весь
 * MainActivity уже завёрнут в verticalScroll — вложенный LazyColumn с
 * fillMaxSize() крашит приложение.
 */
@Composable
fun LogsScreen(viewModel: MainViewModel) {
    val logs by viewModel.logs.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.clearLog() }) { Text("Очистить логи") }
            Text(
                "Записей: ${logs.size} / 500",
                modifier = Modifier.padding(8.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (logs.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Лог пуст. Здесь появятся записи когда запустится робот.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            // Показываем последние записи внизу (как в консоли)
            logs.forEach { entry ->
                val color = when (entry.level) {
                    LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
                    LogLevel.WARN -> Color(0xFFB07F00)
                    LogLevel.ERROR -> MaterialTheme.colorScheme.error
                }
                Text(
                    entry.format(),
                    color = color,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
