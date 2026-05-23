package com.minfinrobot.ui.test

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.minfinrobot.data.minfin.MinfinFetcher
import com.minfinrobot.data.minfin.MinfinParser
import com.minfinrobot.data.tass.TassFetcher
import com.minfinrobot.data.tass.TassParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Тестовая вкладка для проверки парсеров и подключения к источникам
 * БЕЗ необходимости иметь токен T-Invest.
 *
 * Возможности:
 *   1. Вставить текст и распарсить как Минфин или ТАСС.
 *   2. Сделать живой запрос на minfin.gov.ru или tass.ru и увидеть листинг.
 *   3. Прогнать встроенные 9 исторических тестов (2020-2026) — без сети.
 */
@Composable
fun TestScreen() {
    var inputText by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("Готов к тестам") }
    var isWorking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // Ленивая инициализация fetcher'ов — создаём только если понадобятся.
    val minfinFetcher = remember { MinfinFetcher() }
    val tassFetcher = remember { TassFetcher() }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    "Тестирование парсера",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Здесь можно проверить парсер без подключения к T-Invest. " +
                        "Вставь текст публикации Минфина или проверь живое подключение к сайтам.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Текст публикации (вставь сюда)") },
            modifier = Modifier.fillMaxWidth().height(180.dp),
            placeholder = {
                Text(
                    "Пример: «Совокупный объем средств, направляемых на покупку... " +
                        "ежедневный объем покупки... составит в эквиваленте 5,8 млрд рублей»"
                )
            }
        )

        Text("Парсить текст:", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val pub = MinfinParser.parse(
                        html = inputText,
                        publicationUrl = "test://manual",
                        title = "Manual test"
                    )
                    resultText = if (pub != null) {
                        formatPub("MINFIN", pub)
                    } else {
                        "MINFIN: не удалось распознать число в тексте.\n" +
                            "Проверь что есть фраза «ежедневный объем покупки/продажи»."
                    }
                },
                enabled = inputText.isNotBlank() && !isWorking,
                modifier = Modifier.weight(1f)
            ) { Text("Парсить Минфин") }

            Button(
                onClick = {
                    val pub = TassParser.parse(
                        html = inputText,
                        publicationUrl = "test://manual",
                        title = "Manual test"
                    )
                    resultText = if (pub != null) {
                        formatPub("TASS", pub)
                    } else {
                        "TASS: не удалось распознать число."
                    }
                },
                enabled = inputText.isNotBlank() && !isWorking,
                modifier = Modifier.weight(1f)
            ) { Text("Парсить ТАСС") }
        }

        Text("Проверка живых источников:", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    isWorking = true
                    resultText = "Запрашиваю minfin.gov.ru..."
                    scope.launch {
                        resultText = try {
                            val links = withContext(Dispatchers.IO) { minfinFetcher.fetchListing() }
                            formatLinks("MINFIN", links.map { it.title to it.url })
                        } catch (e: Exception) {
                            "MINFIN: ошибка подключения\n${e.javaClass.simpleName}: ${e.message}"
                        }
                        isWorking = false
                    }
                },
                enabled = !isWorking,
                modifier = Modifier.weight(1f)
            ) { Text("Проверить Минфин") }

            Button(
                onClick = {
                    isWorking = true
                    resultText = "Запрашиваю tass.ru/ekonomika..."
                    scope.launch {
                        resultText = try {
                            val links = withContext(Dispatchers.IO) { tassFetcher.fetchListing() }
                            formatLinks("TASS", links.map { it.title to it.url })
                        } catch (e: Exception) {
                            "TASS: ошибка подключения\n${e.javaClass.simpleName}: ${e.message}"
                        }
                        isWorking = false
                    }
                },
                enabled = !isWorking,
                modifier = Modifier.weight(1f)
            ) { Text("Проверить ТАСС") }
        }

        Text("Самотест парсера:", fontWeight = FontWeight.Bold)
        Button(
            onClick = {
                resultText = runHistoricalSelfTest()
            },
            enabled = !isWorking,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Прогнать 9 исторических кейсов 2020-2026") }

        OutlinedButton(
            onClick = { resultText = "Готов к тестам"; inputText = "" },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Очистить") }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Результат:", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    resultText,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

private fun formatPub(label: String, p: com.minfinrobot.domain.model.MinfinPublication): String {
    val signedHuman = when {
        p.isPaused -> "ПАУЗА (0.0 — не торгуем)"
        p.dailyVolumeRubBn > 0 -> "+${p.dailyVolumeRubBn} млрд/день (покупка валюты)"
        p.dailyVolumeRubBn < 0 -> "${p.dailyVolumeRubBn} млрд/день (продажа валюты)"
        else -> "0.0"
    }
    return buildString {
        appendLine("$label: УСПЕХ")
        appendLine("  daily = $signedHuman")
        p.totalVolumeRubBn?.let { appendLine("  total = $it млрд") }
        p.periodStart?.let { appendLine("  период: $it") }
        p.periodEnd?.let { appendLine("  по:     $it") }
    }
}

private fun formatLinks(label: String, links: List<Pair<String, String>>): String {
    if (links.isEmpty()) {
        return "$label: подключение успешно, но 0 ссылок найдено в листинге.\n" +
            "Это может означать что вёрстка сайта изменилась — нужно обновить regex."
    }
    return buildString {
        appendLine("$label: получено ${links.size} ссылок")
        appendLine("Первые 10:")
        links.take(10).forEachIndexed { i, (title, url) ->
            appendLine("${i + 1}. $title")
            appendLine("   $url")
        }
    }
}

/**
 * Самотест на 9 реальных публикациях Минфина (без сети).
 * Покрывает покупку, продажу, паузу и редкий вариант с покупкой+продажей в одном тексте.
 */
private fun runHistoricalSelfTest(): String {
    val cases = listOf(
        Triple(
            "Январь 2020 покупка +18.2",
            18.2,
            "Операции будут проводиться в период с 15 января 2020 года по 6 февраля 2020 " +
                "года, соответственно, ежедневный объем покупки иностранной валюты составит " +
                "в эквиваленте 18,2 млрд руб."
        ),
        Triple(
            "Декабрь 2023 покупка +11.7",
            11.7,
            "Совокупный объем средств, направляемых на покупку иностранной валюты и золота, " +
                "составляет 244,8 млрд руб. Операции будут проводиться в период с 7 декабря " +
                "2023 года по 12 января 2024 года, соответственно, ежедневный объем покупки " +
                "иностранной валюты и золота составит в эквиваленте 11,7 млрд руб."
        ),
        Triple(
            "Январь 2024 продажа -4.1",
            -4.1,
            "Таким образом, совокупный объем средств, направляемых на продажу иностранной " +
                "валюты и золота, составляет 69,1 млрд руб. Операции будут проводиться в " +
                "период с 15 января 2024 года по 6 февраля 2024 года, соответственно, " +
                "ежедневный объем продажи иностранной валюты и золота составит в " +
                "эквиваленте 4,1 млрд руб."
        ),
        Triple(
            "Ноябрь 2024 покупка +4.2",
            4.2,
            "Таким образом, совокупный объем средств, направляемых на покупку иностранной " +
                "валюты и золота, составляет 87,5 млрд руб. Операции будут проводиться в " +
                "период с 7 ноября 2024 года по 5 декабря 2024 года, соответственно, " +
                "ежедневный объем покупки иностранной валюты и золота составит в " +
                "эквиваленте 4,2 млрд руб."
        ),
        Triple(
            "Апрель 2025 продажа -1.6",
            -1.6,
            "Совокупный объем средств, направляемых на продажу ранее приобретенных " +
                "иностранной валюты и золота, составляет 35,9 млрд руб. Операции будут " +
                "проводиться в период с 7 апреля по 12 мая 2025 года, соответственно, " +
                "ежедневный объем продажи иностранной валюты и золота составит в " +
                "эквиваленте 1,6 млрд руб."
        ),
        Triple(
            "Май 2025 покупка +2.3",
            2.3,
            "Таким образом, совокупный объем средств, направляемых на покупку иностранной " +
                "валюты и золота, составляет 41,6 млрд руб. Операции будут проводиться в " +
                "период с 13 мая 2025 года по 5 июня 2025 года, соответственно, ежедневный " +
                "объем покупки иностранной валюты и золота составит в эквиваленте 2,3 млрд руб."
        ),
        Triple(
            "Декабрь 2025 продажа -5.6",
            -5.6,
            "Таким образом, совокупный объем средств от продажи ранее приобретенных " +
                "иностранной валюты и золота составит 123,4 млрд руб. Операции будут " +
                "проводиться в период с 5 декабря 2025 года по 15 января 2026 года, " +
                "соответственно, ежедневный объем продажи иностранной валюты и золота " +
                "составит в эквиваленте 5,6 млрд руб."
        ),
        Triple(
            "Март 2026 ПАУЗА",
            0.0,
            "В связи с планируемыми изменениями параметра базовой цены на нефть в " +
                "бюджетном законодательстве Минфин России принял решение не проводить " +
                "операций по покупке/продаже иностранной валюты и золота на внутреннем " +
                "валютном рынке в рамках бюджетного правила в марте 2026 года."
        ),
        Triple(
            "Май 2026 покупка +5.8",
            5.8,
            "Совокупный объем средств, направляемых на покупку иностранной валюты и " +
                "золота, в пределах дополнительных нефтегазовых доходов федерального " +
                "бюджета в мае 2026 года с учетом объема отложенных операций за март и " +
                "апрель 2026 года составляет 110,3 млрд рублей. Операции будут проводиться " +
                "в период с 8 мая 2026 года по 4 июня 2026 года, соответственно, " +
                "ежедневный объем покупки иностранной валюты и золота составит в " +
                "эквиваленте 5,8 млрд рублей."
        )
    )

    var passed = 0
    var failed = 0
    val sb = StringBuilder()
    cases.forEach { (name, expected, text) ->
        val pub = MinfinParser.parse(text, "test://historical", name)
        val actual = pub?.dailyVolumeRubBn
        val ok = actual == expected
        val mark = if (ok) "OK  " else "FAIL"
        sb.appendLine("$mark $name → ожидалось $expected, получено $actual")
        if (ok) passed++ else failed++
    }
    sb.appendLine("---")
    sb.appendLine("Прошло: $passed из ${cases.size}, упало: $failed")
    if (failed == 0) {
        sb.appendLine("✓ Парсер работает корректно на всех исторических данных.")
    } else {
        sb.appendLine("✗ ВНИМАНИЕ: парсер сломан, нужно чинить regex.")
    }
    return sb.toString()
}
