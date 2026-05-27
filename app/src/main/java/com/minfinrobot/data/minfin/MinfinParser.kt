package com.minfinrobot.data.minfin

import com.minfinrobot.domain.model.MinfinPublication

/**
 * Парсер пресс-релиза Минфина.
 *
 * Структура релиза (стабильна с 2020 г., проверена на 9 публикациях):
 *   "...совокупный объем средств, направляемых на ПОКУПКУ/ПРОДАЖУ
 *    иностранной валюты и золота, составляет X млрд руб..."
 *   "...Операции будут проводиться в период с DD.MM.YYYY по DD.MM.YYYY,
 *    соответственно, ежедневный объем ПОКУПКИ/ПРОДАЖИ иностранной валюты
 *    и золота составит в эквиваленте Y млрд руб..."
 *
 * ОСНОВНАЯ ЦЕЛЬ парсера — число Y (ежедневный объём), знаковое:
 *   + покупка, − продажа.
 *
 * Также распознаём паузу:
 *   "...принято решение НЕ ПРОВОДИТЬ ОПЕРАЦИЙ..."
 *   → возвращаем dailyVolumeRubBn=0.0, isPaused=true.
 *
 * ВАЖНО: в Java regex класс \w НЕ матчит кириллицу,
 * поэтому везде используем явный [а-яёА-ЯЁ].
 */
object MinfinParser {

    private const val CYR = "а-яёА-ЯЁ"

    private val DAILY_BUY_REGEX = Regex(
        """ежедневн[$CYR]*\s+объ[её]м\s+покуп[$CYR]+[^.]{0,120}?(\d+[.,]?\d*)\s*млрд""",
        RegexOption.IGNORE_CASE
    )

    private val DAILY_SELL_REGEX = Regex(
        """ежедневн[$CYR]*\s+объ[её]м\s+продаж[$CYR]+[^.]{0,120}?(\d+[.,]?\d*)\s*млрд""",
        RegexOption.IGNORE_CASE
    )

    private val TOTAL_REGEX = Regex(
        """совокупн[$CYR]+\s+объ[её]м[^.]{0,200}?(\d+[.,]?\d*)\s*млрд""",
        RegexOption.IGNORE_CASE
    )

    private val PERIOD_REGEX = Regex(
        """в\s+период\s+с\s+(\d{1,2}[.\s][^,]+?\s*\d{4})\s+(?:года\s+)?по\s+(\d{1,2}[.\s][^,]+?\s*\d{4})""",
        RegexOption.IGNORE_CASE
    )

    private val PAUSE_REGEX = Regex(
        """не\s+проводить\s+операц[$CYR]+\s+по\s+покуп""",
        RegexOption.IGNORE_CASE
    )

    // Для распознавания заголовка в листинге: хотя бы 2 из 3 ключевых фраз.
    private val TITLE_KEYWORDS = listOf(
        Regex("""нефтегазов[$CYR]+\s+доход""", RegexOption.IGNORE_CASE),
        Regex("""операц[$CYR]+\s+по\s+покупк""", RegexOption.IGNORE_CASE),
        Regex("""операц[$CYR]+\s+по\s+продаж""", RegexOption.IGNORE_CASE),
        Regex("""бюджетн[$CYR]+\s+правил""", RegexOption.IGNORE_CASE),
        Regex("""покупк[$CYR]*\s+иностранн[$CYR]+\s+валют""", RegexOption.IGNORE_CASE),
        Regex("""продаж[$CYR]*\s+иностранн[$CYR]+\s+валют""", RegexOption.IGNORE_CASE)
    )

    /**
     * Проверка заголовка из листинга на соответствие целевой публикации.
     * Достаточно совпадения хотя бы 1 ключевой фразы (раньше требовали 2 из 3,
     * но формулировки Минфина варьируются — упускали реальные публикации).
     */
    fun isTargetTitle(title: String): Boolean {
        return TITLE_KEYWORDS.any { it.containsMatchIn(title) }
    }

    /**
     * Парсит HTML/текст публикации.
     */
    fun parse(html: String, publicationUrl: String, title: String): MinfinPublication? {
        val text = html
            .replace("&nbsp;", " ")
            .replace("\u00A0", " ")
            .replace(Regex("\\s+"), " ")

        if (PAUSE_REGEX.containsMatchIn(text)) {
            return MinfinPublication(
                dailyVolumeRubBn = 0.0,
                isPaused = true,
                publicationUrl = publicationUrl,
                sourceLabel = "MINFIN",
                title = title
            )
        }

        val buyMatch = DAILY_BUY_REGEX.find(text)
        val sellMatch = DAILY_SELL_REGEX.find(text)

        val (signedDaily, foundType) = when {
            buyMatch != null && sellMatch == null -> parseNumber(buyMatch.groupValues[1]) to "BUY"
            sellMatch != null && buyMatch == null -> {
                val n = parseNumber(sellMatch.groupValues[1]) ?: return null
                -n to "SELL"
            }
            buyMatch != null && sellMatch != null -> {
                if (buyMatch.range.first > sellMatch.range.first) {
                    parseNumber(buyMatch.groupValues[1]) to "BUY"
                } else {
                    val n = parseNumber(sellMatch.groupValues[1]) ?: return null
                    -n to "SELL"
                }
            }
            else -> return null
        }

        val daily = signedDaily ?: return null

        val total = TOTAL_REGEX.find(text)?.groupValues?.get(1)?.let { parseNumber(it) }
        val signedTotal = total?.let { if (foundType == "SELL") -it else it }

        val periodMatch = PERIOD_REGEX.find(text)
        val periodStart = periodMatch?.groupValues?.get(1)?.trim()
        val periodEnd = periodMatch?.groupValues?.get(2)?.trim()

        return MinfinPublication(
            dailyVolumeRubBn = daily,
            totalVolumeRubBn = signedTotal,
            periodStart = periodStart,
            periodEnd = periodEnd,
            publicationUrl = publicationUrl,
            sourceLabel = "MINFIN",
            title = title
        )
    }

    private fun parseNumber(raw: String): Double? =
        raw.replace(',', '.').trim().toDoubleOrNull()
}
