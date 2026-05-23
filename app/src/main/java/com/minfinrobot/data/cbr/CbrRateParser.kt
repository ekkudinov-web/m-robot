package com.minfinrobot.data.cbr

import com.minfinrobot.domain.model.CbrRateDecision

/**
 * Парсер пресс-релиза ЦБ.
 *
 * Структура заголовка стабильна годами:
 *   <title>Банк России принял решение сохранить ключевую ставку на уровне 20,00% годовых
 *          | Банк России</title>
 *   <title>...снизить ключевую ставку на 200 б.п., до 18,00% годовых | Банк России</title>
 *
 * Цель: первое число с "%" в <title>. Это и есть новая ставка.
 *
 * Подход (как в оригинальном приложении):
 *   1. Извлечь содержимое <title>...</title>
 *   2. Regex `(\d+(?:[.,]\d+)?)\s*%` — первое совпадение
 *   3. Заменить запятую на точку, parseDouble
 */
object CbrRateParser {

    private val TITLE_REGEX = Regex(
        """<title[^>]*>(.*?)</title>""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )

    private val RATE_REGEX = Regex("""(\d+(?:[.,]\d+)?)\s*%""")

    fun parse(html: String, sourceUrl: String): CbrRateDecision? {
        val title = TITLE_REGEX.find(html)?.groupValues?.get(1)?.trim() ?: return null
        val match = RATE_REGEX.find(title) ?: return null
        val rate = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        return CbrRateDecision(ratePercent = rate, sourceUrl = sourceUrl, title = title)
    }
}
