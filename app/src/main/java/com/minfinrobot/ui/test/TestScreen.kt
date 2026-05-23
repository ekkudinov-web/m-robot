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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.minfinrobot.domain.engine.ScenarioEvaluator
import com.minfinrobot.domain.model.MinfinPublication
import com.minfinrobot.domain.model.Scenario
import com.minfinrobot.domain.model.ScenarioOperator
import com.minfinrobot.domain.model.TradeAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Тестовая вкладка для проверки парсеров и симуляции робота
 * БЕЗ необходимости иметь токен T-Invest.
 *
 * Секции:
 *   1. Парсинг произвольного текста (как Минфин / как ТАСС).
 *   2. Живые источники (Минфин / ТАСС): нормальный режим + диагностика сырого HTML.
 *   3. Самотест на 9 исторических кейсах.
 *   4. Симуляция сценария: ввод значения Минфина + сценарий → что робот сделал бы.
 */
@Composable
fun TestScreen() {
    var inputText by remember { mutableStateOf("") }
    var resultText by remember { mutableStateOf("Готов к тестам") }
    var isWorking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val minfinFetcher = remember { MinfinFetcher() }
    val tassFetcher = remember { TassFetcher() }
    val evaluator = remember { ScenarioEvaluator() }

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
                    "Тестирование робота",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Проверка парсера, источников и симуляция сценариев — " +
                        "без подключения к T-Invest.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            label = { Text("Текст публикации (вставь сюда)") },
            modifier = Modifier.fillMaxWidth().height(160.dp),
            placeholder = {
                Text("«…ежедневный объем покупки иностранной валюты… 5,8 млрд руб»")
            }
        )

        Text("Парсить текст:", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val pub = MinfinParser.parse(inputText, "test://manual", "Manual")
                    resultText = pub?.let { formatPub("MINFIN", it) }
                        ?: "MINFIN: не удалось распознать число."
                },
                enabled = inputText.isNotBlank() && !isWorking,
                modifier = Modifier.weight(1f)
            ) { Text("Парсить Минфин") }

            Button(
                onClick = {
                    val pub = TassParser.parse(inputText, "test://manual", "Manual")
                    resultText = pub?.let { formatPub("TASS", it) }
                        ?: "TASS: не удалось распознать число."
                },
                enabled = inputText.isNotBlank() && !isWorking,
                modifier = Modifier.weight(1f)
            ) { Text("Парсить ТАСС") }
        }

        Text("Живые источники:", fontWeight = FontWeight.Bold)
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
                            "MINFIN: ${e.javaClass.simpleName}: ${e.message}"
                        }
                        isWorking = false
                    }
                },
                enabled = !isWorking,
                modifier = Modifier.weight(1f)
            ) { Text("Минфин: ссылки") }

            Button(
                onClick = {
                    isWorking = true
                    resultText = "Запрашиваю tass.ru/ekonomika..."
                    scope.launch {
                        resultText = try {
                            val links = withContext(Dispatchers.IO) { tassFetcher.fetchListing() }
                            formatLinks("TASS", links.map { it.title to it.url })
                        } catch (e: Exception) {
                            "TASS: ${e.javaClass.simpleName}: ${e.message}"
                        }
                        isWorking = false
                    }
                },
                enabled = !isWorking,
                modifier = Modifier.weight(1f)
            ) { Text("ТАСС: ссылки") }
        }

        Text("Диагностика (сырой HTML):", fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    isWorking = true
                    resultText = "Скачиваю HTML Минфина..."
                    scope.launch {
                        resultText = try {
                            val html = withContext(Dispatchers.IO) { minfinFetcher.fetchListingRaw() }
                            formatRawHtml("MINFIN", html)
                        } catch (e: Exception) {
                            "MINFIN raw: ${e.javaClass.simpleName}: ${e.message}"
                        }
                        isWorking = false
                    }
                },
                enabled = !isWorking,
                modifier = Modifier.weight(1f)
            ) { Text("HTML Минфин") }

            OutlinedButton(
                onClick = {
                    isWorking = true
                    resultText = "Скачиваю HTML ТАСС..."
                    scope.launch {
                        resultText = try {
                            val html = withContext(Dispatchers.IO) { tassFetcher.fetchListingRaw() }
                            formatRawHtml("TASS", html)
                        } catch (e: Exception) {
                            "TASS raw: ${e.javaClass.simpleName}: ${e.message}"
                        }
                        isWorking = false
                    }
                },
                enabled = !isWorking,
                modifier = Modifier.weight(1f)
            ) { Text("HTML ТАСС") }
        }

        Text("Самотест:", fontWeight = FontWeight.Bold)
        Button(
            onClick = { resultText = runHistoricalSelfTest() },
            enabled = !isWorking,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Прогнать 9 исторических кейсов 2020-2026") }

        HorizontalDivider()

        ScenarioSimulator(
            evaluator = evaluator,
            onResult = { resultText = it }
        )

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

/**
 * Симулятор сценариев: задаёшь предполагаемое значение Минфина и сценарии,
 * жмёшь "Симулировать" — видишь какие ордера робот бы отправил.
 *
 * Никаких реальных запросов к T-Invest — только проверка логики решений.
 */
@Composable
private fun ScenarioSimulator(
    evaluator: ScenarioEvaluator,
    onResult: (String) -> Unit
) {
    var dailyValue by remember { mutableStateOf("5.8") }
    var isPaused by remember { mutableStateOf(false) }

    var operator by remember { mutableStateOf(ScenarioOperator.GREATER_THAN) }
    var threshold by remember { mutableStateOf("3.0") }
    var action by remember { mutableStateOf(TradeAction.BUY) }
    var ticker by remember { mutableStateOf("Si-6.26") }
    var quantity by remember { mutableStateOf("1") }
    var limitPrice by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("Симуляция сценария", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "Введи значение Минфина и сценарий — увидишь что робот сделал бы. " +
                    "Реальный ордер НЕ отправляется.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.height(12.dp))

            Text("Условный сигнал Минфина:", fontWeight = FontWeight.Medium)
            OutlinedTextField(
                value = dailyValue,
                onValueChange = { dailyValue = it },
                label = { Text("Ежедневный объём, ₽ млрд (знаковый: +5.8 / -1.6)") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isPaused
            )
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { isPaused = !isPaused },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isPaused) "● Минфин: ПАУЗА" else "○ Минфин: пауза")
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Сценарий:", fontWeight = FontWeight.Medium)

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
                label = { Text("Порог, ₽ млрд") },
                modifier = Modifier.fillMaxWidth()
            )

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
                value = ticker,
                onValueChange = { ticker = it },
                label = { Text("Тикер инструмента (Si-6.26 / CR-6.26 / Eu-6.26)") },
                modifier = Modifier.fillMaxWidth()
            )
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

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val thr = threshold.replace(',', '.').toDoubleOrNull()
                    val qty = quantity.toIntOrNull()
                    val daily = dailyValue.replace(',', '.').toDoubleOrNull()
                    val lim = limitPrice.replace(',', '.').toDoubleOrNull()

                    if (thr == null || qty == null || qty <= 0) {
                        onResult("Ошибка: проверь корректность чисел в сценарии.")
                        return@Button
                    }
                    if (!isPaused && daily == null) {
                        onResult("Ошибка: введи значение или отметь паузу.")
                        return@Button
                    }

                    val pub = MinfinPublication(
                        dailyVolumeRubBn = if (isPaused) 0.0 else daily!!,
                        publicationUrl = "test://simulation",
                        sourceLabel = "SIMULATION",
                        title = "Simulation",
                        isPaused = isPaused
                    )

                    val scenario = Scenario(
                        operator = operator,
                        thresholdRubBn = thr,
                        action = action,
                        instrumentUid = "simulated-uid",
                        instrumentDisplay = ticker,
                        quantity = qty,
                        limitPrice = lim
                    )

                    val matched = evaluator.match(pub, listOf(scenario))
                    onResult(formatSimulation(pub, scenario, matched.isNotEmpty()))
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Симулировать") }
        }
    }
}

private fun formatPub(label: String, p: MinfinPublication): String {
    val signedHuman = when {
        p.isPaused -> "ПАУЗА (0.0 — не торгуем)"
        p.dailyVolumeRubBn > 0 -> "+${p.dailyVolumeRubBn} млрд/день (покупка)"
        p.dailyVolumeRubBn < 0 -> "${p.dailyVolumeRubBn} млрд/день (продажа)"
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
        return "$label: подключение успешно, но 0 ссылок найдено.\n" +
            "Возможно вёрстка изменилась — нажми 'HTML $label' для диагностики."
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

private fun formatRawHtml(label: String, html: String): String {
    val size = html.length
    val preview = html.take(1500)
    val anchorCount = Regex("""<a\s""", RegexOption.IGNORE_CASE).findAll(html).count()
    return buildString {
        appendLine("$label HTML:")
        appendLine("  размер: $size симв.")
        appendLine("  тегов <a>: $anchorCount")
        appendLine()
        appendLine("Первые 1500 символов:")
        appendLine("---")
        appendLine(preview)
        appendLine("---")
        appendLine("(скрин этого результата пришли мне для починки regex)")
    }
}

private fun formatSimulation(pub: MinfinPublication, sc: Scenario, matched: Boolean): String {
    val sb = StringBuilder()
    sb.appendLine("=== СИМУЛЯЦИЯ ===")
    sb.appendLine()
    sb.appendLine("Сигнал Минфина:")
    if (pub.isPaused) {
        sb.appendLine("  ПАУЗА — операций нет")
    } else {
        sb.appendLine("  ${pub.dailyVolumeRubBn} млрд/день")
    }
    sb.appendLine()
    sb.appendLine("Сценарий:")
    val op = if (sc.operator == ScenarioOperator.GREATER_THAN) ">" else "<"
    sb.appendLine("  daily $op ${sc.thresholdRubBn} → ${sc.action} ${sc.quantity} ${sc.instrumentDisplay}")
    sb.appendLine("  Тип ордера: " + if (sc.limitPrice != null) "LIMIT @ ${sc.limitPrice}" else "MARKET")
    sb.appendLine()
    sb.appendLine("Результат:")
    when {
        pub.isPaused -> {
            sb.appendLine("  ОРДЕР НЕ ОТПРАВЛЕН")
            sb.appendLine("  Причина: Минфин объявил паузу — защита от паузы сработала.")
            sb.appendLine("  (это поведение зафиксировано в спецификации)")
        }
        matched -> {
            sb.appendLine("  ОРДЕР БУДЕТ ОТПРАВЛЕН:")
            sb.appendLine("    Тикер:        ${sc.instrumentDisplay}")
            sb.appendLine("    Направление:  ${sc.action}")
            sb.appendLine("    Количество:   ${sc.quantity} лотов")
            sb.appendLine("    Тип:          " + if (sc.limitPrice != null) "LIMIT @ ${sc.limitPrice}" else "MARKET")
            sb.appendLine()
            sb.appendLine("  В реальном запуске здесь был бы POST в T-Invest API.")
        }
        else -> {
            sb.appendLine("  ОРДЕР НЕ ОТПРАВЛЕН")
            val cond = "${pub.dailyVolumeRubBn} $op ${sc.thresholdRubBn}"
            sb.appendLine("  Причина: условие НЕ выполнено ($cond — ложь).")
        }
    }
    return sb.toString()
}

/**
 * Самотест на 9 реальных публикациях Минфина (без сети).
 */
private fun runHistoricalSelfTest(): String {
    val cases = listOf(
        Triple("Январь 2020 покупка +18.2", 18.2,
            "Операции будут проводиться в период с 15 января 2020 года по 6 февраля 2020 " +
                "года, соответственно, ежедневный объем покупки иностранной валюты составит " +
                "в эквиваленте 18,2 млрд руб."),
        Triple("Декабрь 2023 покупка +11.7", 11.7,
            "Совокупный объем средств, направляемых на покупку иностранной валюты и золота, " +
                "составляет 244,8 млрд руб. Операции будут проводиться в период с 7 декабря " +
                "2023 года по 12 января 2024 года, соответственно, ежедневный объем покупки " +
                "иностранной валюты и золота составит в эквиваленте 11,7 млрд руб."),
        Triple("Январь 2024 продажа -4.1", -4.1,
            "Таким образом, совокупный объем средств, направляемых на продажу иностранной " +
                "валюты и золота, составляет 69,1 млрд руб. Операции будут проводиться в " +
                "период с 15 января 2024 года по 6 февраля 2024 года, соответственно, " +
                "ежедневный объем продажи иностранной валюты и золота составит в " +
                "эквиваленте 4,1 млрд руб."),
        Triple("Ноябрь 2024 покупка +4.2", 4.2,
            "Таким образом, совокупный объем средств, направляемых на покупку иностранной " +
                "валюты и золота, составляет 87,5 млрд руб. Операции будут проводиться в " +
                "период с 7 ноября 2024 года по 5 декабря 2024 года, соответственно, " +
                "ежедневный объем покупки иностранной валюты и золота составит в " +
                "эквиваленте 4,2 млрд руб."),
        Triple("Апрель 2025 продажа -1.6", -1.6,
            "Совокупный объем средств, направляемых на продажу ранее приобретенных " +
                "иностранной валюты и золота, составляет 35,9 млрд руб. Операции будут " +
                "проводиться в период с 7 апреля по 12 мая 2025 года, соответственно, " +
                "ежедневный объем продажи иностранной валюты и золота составит в " +
                "эквиваленте 1,6 млрд руб."),
        Triple("Май 2025 покупка +2.3", 2.3,
            "Таким образом, совокупный объем средств, направляемых на покупку иностранной " +
                "валюты и золота, составляет 41,6 млрд руб. Операции будут проводиться в " +
                "период с 13 мая 2025 года по 5 июня 2025 года, соответственно, ежедневный " +
                "объем покупки иностранной валюты и золота составит в эквиваленте 2,3 млрд руб."),
        Triple("Декабрь 2025 продажа -5.6", -5.6,
            "Таким образом, совокупный объем средств от продажи ранее приобретенных " +
                "иностранной валюты и золота составит 123,4 млрд руб. Операции будут " +
                "проводиться в период с 5 декабря 2025 года по 15 января 2026 года, " +
                "соответственно, ежедневный объем продажи иностранной валюты и золота " +
                "составит в эквиваленте 5,6 млрд руб."),
        Triple("Март 2026 ПАУЗА", 0.0,
            "В связи с планируемыми изменениями параметра базовой цены на нефть в " +
                "бюджетном законодательстве Минфин России принял решение не проводить " +
                "операций по покупке/продаже иностранной валюты и золота на внутреннем " +
                "валютном рынке в рамках бюджетного правила в марте 2026 года."),
        Triple("Май 2026 покупка +5.8", 5.8,
            "Совокупный объем средств, направляемых на покупку иностранной валюты и " +
                "золота, в пределах дополнительных нефтегазовых доходов федерального " +
                "бюджета в мае 2026 года с учетом объема отложенных операций за март и " +
                "апрель 2026 года составляет 110,3 млрд рублей. Операции будут проводиться " +
                "в период с 8 мая 2026 года по 4 июня 2026 года, соответственно, " +
                "ежедневный объем покупки иностранной валюты и золота составит в " +
                "эквиваленте 5,8 млрд рублей.")
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
    if (failed == 0) sb.appendLine("✓ Парсер корректен.")
    else sb.appendLine("✗ ВНИМАНИЕ: regex сломан.")
    return sb.toString()
}
