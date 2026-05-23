package com.minfinrobot.data.tass

import com.minfinrobot.data.minfin.MinfinParser
import com.minfinrobot.domain.model.MinfinPublication
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class TassListingLink(val url: String, val title: String)

/**
 * Fallback-источник: tass.ru/ekonomika
 *
 * ТАСС использует ту же формулировку Минфина в своих новостях ("ежедневный объем
 * покупки/продажи иностранной валюты ... составит в эквиваленте N млрд руб"),
 * поэтому MinfinParser работает по их HTML тоже.
 *
 * Задержка обычно 30-60 сек от Минфина — нас устраивает как страховка от
 * блокировки/недоступности minfin.gov.ru с конкретного IP.
 *
 * Используем раздел "Экономика": https://tass.ru/ekonomika
 * Ищем заголовки содержащие "Минфин" + ("покуп" | "прода") + "валют".
 */
class TassFetcher {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    fun fetchListing(): List<TassListingLink> {
        val html = fetchListingRaw()
        return parseListing(html)
    }

    /**
     * Получить сырой HTML листинга — для диагностики ("Что видит парсер").
     */
    fun fetchListingRaw(): String {
        val request = Request.Builder()
            .url(LISTING_URL)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "ru,en;q=0.5")
            .header("Cache-Control", "no-cache")
            .build()
        return client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("ТАСС: HTTP ${resp.code}")
            resp.body?.string() ?: throw IOException("ТАСС: пустой ответ")
        }
    }

    fun fetchPublicationPage(url: String): String {
        val fullUrl = if (url.startsWith("http")) url else BASE_URL + url
        val request = Request.Builder()
            .url(fullUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "ru,en;q=0.5")
            .build()
        return client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("ТАСС: HTTP ${resp.code}")
            resp.body?.string() ?: throw IOException("ТАСС: пустой ответ")
        }
    }

    /**
     * Парсит листинг ТАСС.
     *
     * Реальная вёрстка (май 2026):
     *   <a class="NonMediaMaterialCardLayout_link__..." href="/ekonomika/27510619">
     *     <div class="NonMediaMaterialCardLayout_wrapper__...">
     *       <div class="NonMediaMaterialCardLayout_content__...">
     *         <div class="NonMediaMaterialCardLayout_text_container__...">
     *           <div class="NonMediaMaterialCardLayout_text__...">
     *             Заголовок новости
     *           </div>
     *           ...
     *
     * Заголовок упрятан в 4 уровня div'ов. Делаем двухшаговый парсинг:
     *   1. Извлекаем все блоки <a ... href="/ekonomika/NNNNN">...</a> через
     *      балансировку открывающих/закрывающих тегов.
     *   2. Внутри каждого блока ищем текст в div с классом *MaterialCardLayout_text*
     *      (или fallback: все буквы между тегами).
     */
    private fun parseListing(html: String): List<TassListingLink> {
        val results = mutableListOf<TassListingLink>()
        val seen = mutableSetOf<String>()

        // 1. Найти все href="/ekonomika/NNNN..." и позицию начала каждого <a>
        val hrefRegex = Regex(
            """<a\s[^>]*?href=["'](/ekonomika/\d+[^"']*)["']""",
            RegexOption.IGNORE_CASE
        )
        // Текст внутри div'ов с MaterialCardLayout_text - основное место заголовка
        val titleClassRegex = Regex(
            """class=["'][^"']*MaterialCardLayout_text[^"']*["'][^>]*>([^<]+)<""",
            RegexOption.IGNORE_CASE
        )

        hrefRegex.findAll(html).forEach { hrefMatch ->
            val href = hrefMatch.groupValues[1]
            if (href in seen) return@forEach

            // Окно поиска: от текущего <a> до 3000 символов вперёд
            // (одна карточка обычно ~500-1500 символов).
            val from = hrefMatch.range.first
            val to = minOf(from + 3000, html.length)
            val window = html.substring(from, to)

            // Закрытие текущего <a> — берём только до него
            val closeIdx = window.indexOf("</a>", ignoreCase = true)
            val card = if (closeIdx > 0) window.substring(0, closeIdx) else window

            // Ищем заголовок в card
            val titleMatch = titleClassRegex.find(card)
            val title = if (titleMatch != null) {
                cleanTitle(titleMatch.groupValues[1])
            } else {
                // Fallback: вытащить весь текст между тегами и склеить
                extractAnyText(card)
            }

            if (title.length >= 10) {
                seen.add(href)
                results.add(makeLink(href, title))
            }
        }
        return results
    }

    private fun makeLink(href: String, title: String): TassListingLink {
        val full = if (href.startsWith("http")) href else BASE_URL + href
        return TassListingLink(url = full, title = title)
    }

    private fun cleanTitle(raw: String): String =
        raw.replace("&nbsp;", " ")
            .replace("&quot;", "\"")
            .replace("&amp;", "&")
            .replace("&laquo;", "«")
            .replace("&raquo;", "»")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace(Regex("\\s+"), " ")
            .trim()

    private fun extractAnyText(html: String): String {
        // Срезаем все теги и собираем текст. Берём первый осмысленный кусок (>=15 символов).
        val stripped = Regex("""<[^>]+>""").replace(html, " ")
        val cleaned = cleanTitle(stripped)
        // Иногда после полной очистки получается длинная строка из нескольких текстов —
        // берём первое предложение / первый осмысленный кусок до 200 символов.
        return cleaned.take(200)
    }

    companion object {
        const val BASE_URL = "https://tass.ru"
        const val LISTING_URL = "https://tass.ru/ekonomika"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}

/**
 * Распознавание заголовка ТАСС как нужной публикации.
 *
 * Целевые заголовки выглядят так:
 *   "Минфин РФ: ежедневный объем покупки валюты и золота составит ..."
 *   "Минфин в мае будет ежедневно покупать валюту и золото на 5,8 млрд рублей"
 *   "Покупки валюты по бюджетному правилу с 7 декабря снизятся ..."
 *
 * Достаточно совпадения "Минфин" + ("покуп"|"прода"|"бюджетн правил") + "валют".
 */
object TassParser {

    private const val CYR = "а-яёА-ЯЁ"

    private val KW_MINFIN = Regex("Минфин", RegexOption.IGNORE_CASE)
    private val KW_OPERATION =
        Regex("покуп[$CYR]+|прода[$CYR]+|бюджетн[$CYR]+\\s+правил", RegexOption.IGNORE_CASE)
    private val KW_CURRENCY = Regex("валют[$CYR]+", RegexOption.IGNORE_CASE)

    fun isTargetTitle(title: String): Boolean {
        return KW_MINFIN.containsMatchIn(title) &&
                KW_OPERATION.containsMatchIn(title) &&
                KW_CURRENCY.containsMatchIn(title)
    }

    /**
     * Использует MinfinParser для извлечения числа — формулировка идентичная.
     * Возвращает MinfinPublication с sourceLabel="TASS".
     */
    fun parse(html: String, publicationUrl: String, title: String): MinfinPublication? {
        val base = MinfinParser.parse(html, publicationUrl, title) ?: return null
        return base.copy(sourceLabel = "TASS")
    }
}
