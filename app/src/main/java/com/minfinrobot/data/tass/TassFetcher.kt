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
        val request = Request.Builder()
            .url(LISTING_URL)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "ru,en;q=0.5")
            .header("Cache-Control", "no-cache")
            .build()
        val html = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("ТАСС: HTTP ${resp.code}")
            resp.body?.string() ?: throw IOException("ТАСС: пустой ответ")
        }
        return parseListing(html)
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
     * Парсит листинг ТАСС, ищет статьи с ID вида /ekonomika/NNNNNNN.
     */
    private fun parseListing(html: String): List<TassListingLink> {
        val regex = Regex(
            """<a[^>]*href=["']([^"']*?/ekonomika/\d+)["'][^>]*>([^<]{15,300})</a>""",
            RegexOption.IGNORE_CASE
        )
        return regex.findAll(html)
            .map {
                val href = it.groupValues[1]
                val title = it.groupValues[2]
                    .replace("&nbsp;", " ")
                    .replace("&quot;", "\"")
                    .replace("&amp;", "&")
                    .replace(Regex("\\s+"), " ")
                    .trim()
                val full = if (href.startsWith("http")) href else BASE_URL + href
                TassListingLink(url = full, title = title)
            }
            .distinctBy { it.url }
            .toList()
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
