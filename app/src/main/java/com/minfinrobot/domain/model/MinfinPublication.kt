package com.minfinrobot.domain.model

/**
 * Результат парсинга публикации Минфина (или fallback-источника).
 *
 * @param dailyVolumeRubBn  Ежедневный объём операций в ₽ млрд (знаковый).
 *                          + покупка валюты (RUB-негативно)
 *                          − продажа валюты (RUB-позитивно)
 *                          0 — пауза операций (НЕ ТОРГОВАТЬ)
 * @param totalVolumeRubBn  Совокупный объём за период (для лога).
 * @param periodStart       Начало периода операций.
 * @param periodEnd         Конец периода операций.
 * @param publicationUrl    URL публикации (для дедупликации и лога).
 * @param sourceLabel       MINFIN / TASS — какой источник дал данные.
 * @param title             Заголовок (для лога).
 * @param isPaused          true → "не проводить операций" → 0.0, не торгуем.
 */
data class MinfinPublication(
    val dailyVolumeRubBn: Double,
    val totalVolumeRubBn: Double? = null,
    val periodStart: String? = null,
    val periodEnd: String? = null,
    val publicationUrl: String,
    val sourceLabel: String,
    val title: String = "",
    val isPaused: Boolean = false
)
