package com.minfinrobot.ui.logs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.minfinrobot.data.log.LogLevel
import com.minfinrobot.data.log.LogStore
import com.minfinrobot.ui.MainViewModel
import com.minfinrobot.ui.theme.AppColors
import com.minfinrobot.ui.theme.GoldCard

/**
 * Экран логов. Обычный Column (не LazyColumn), т.к. MainActivity уже в verticalScroll.
 * Кнопки: Копировать (буфер обмена), Поделиться (системный share → файл/мессенджер),
 * Очистить.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogsScreen(viewModel: MainViewModel) {
    val logs by viewModel.logs.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { copyLog(context) }) { Text("Копировать") }
            OutlinedButton(onClick = { shareLog(context) }) { Text("Поделиться") }
            OutlinedButton(onClick = { viewModel.clearLog() }) { Text("Очистить") }
        }
        Text(
            "Записей: ${logs.size} / 500",
            style = MaterialTheme.typography.bodySmall,
            color = AppColors.TextSecondary
        )

        if (logs.isEmpty()) {
            GoldCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Лог пуст. Здесь появятся записи когда запустится робот.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            logs.forEach { entry ->
                val color = when (entry.level) {
                    LogLevel.INFO -> MaterialTheme.colorScheme.onSurface
                    LogLevel.WARN -> AppColors.Warn
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

private fun copyLog(context: Context) {
    val text = LogStore.dumpText()
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Минфин-робот лог", text))
    Toast.makeText(context, "Лог скопирован", Toast.LENGTH_SHORT).show()
}

private fun shareLog(context: Context) {
    val text = LogStore.dumpText()
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Минфин-робот: лог")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Поделиться логом"))
}
