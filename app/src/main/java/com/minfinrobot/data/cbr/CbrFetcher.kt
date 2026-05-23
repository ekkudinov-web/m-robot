package com.minfinrobot.data.cbr

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * HTTP-клиент для пресс-релиза ЦБ о ключевой ставке.
 *
 * URL заранее известен из даты заседания:
 *   https://www.cbr.ru/press/pr/?file=DDMMYYYY_133000key.htm
 *
 * До 13:30 МСК URL возвращает 404. В 13:30 — ЦБ публикует пресс-релиз
 * с заголовком вида:
 *   "Совет директоров Банка России 25 июля 2025 года принял решение
 *    снизить ключевую ставку на 200 б.п., до 18,00% годовых."
 */
class CbrFetcher {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    /**
     * Построить URL пресс-релиза по дате заседания.
     */
    fun buildUrl(meetingDate: LocalDate): String {
        val ddmmyyyy = meetingDate.format(DateTimeFormatter.ofPattern("ddMMyyyy"))
        return "https://www.cbr.ru/press/pr/?file=${ddmmyyyy}_133000key.htm"
    }

    /**
     * Запрос пресс-релиза. До публикации возвращает 404 — это нормально,
     * выбрасываем исключение, наверху это обрабатывается.
     */
    fun fetchPressRelease(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "ru,en;q=0.5")
            .header("Cache-Control", "no-cache")
            .build()
        return client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("ЦБ: HTTP ${resp.code}")
            resp.body?.string() ?: throw IOException("ЦБ: пустой ответ")
        }
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    }
}
