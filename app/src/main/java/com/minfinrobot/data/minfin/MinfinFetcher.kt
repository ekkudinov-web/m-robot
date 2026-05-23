package com.minfinrobot.data.minfin

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

data class PressReleaseLink(val url: String, val title: String)

/**
 * HTTP-доступ к сайту Минфина: листинг + детальная страница.
 *
 * Минфин использует серверный рендеринг — простой OkHttp GET достаточен.
 * Если они переедут на SPA — потребуется WebView (закладку оставил в README).
 */
class MinfinFetcher {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    fun fetchListing(): List<PressReleaseLink> {
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
            if (!resp.isSuccessful) throw IOException("Листинг Минфина: HTTP ${resp.code}")
            resp.body?.string() ?: throw IOException("Пустой ответ листинга")
        }
    }

    fun fetchPublicationPage(url: String): String {
        val fullUrl = if (url.startsWith("http")) url else BASE_URL + url
        val request = Request.Builder()
            .url(fullUrl)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "ru,en;q=0.5")
            .header("Cache-Control", "no-cache")
            .build()
        return client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("Публикация Минфина: HTTP ${resp.code}")
            resp.body?.string() ?: throw IOException("Пустой ответ публикации")
        }
    }

    private fun parseListing(html: String): List<PressReleaseLink> {
        // Ссылки в листинге Минфина имеют вид:
        //   <a href="/ru/press-center/?id_4=40342-slug">текст ссылки</a>
        val regex = Regex(
            """<a[^>]*href=["']([^"']*?/ru/press-center/\?id_4=[^"']+)["'][^>]*>([^<]+)</a>""",
            RegexOption.IGNORE_CASE
        )
        return regex.findAll(html).map {
            val href = it.groupValues[1]
            val title = it.groupValues[2].trim()
            val full = if (href.startsWith("http")) href else BASE_URL + href
            PressReleaseLink(url = full, title = title)
        }.toList()
    }

    companion object {
        const val BASE_URL = "https://minfin.gov.ru"
        const val LISTING_URL = "https://minfin.gov.ru/ru/press-center/"
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
