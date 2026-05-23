package com.minfinrobot.domain.model

import kotlinx.serialization.Serializable

/**
 * Фьючерсный контракт из справочника Т-Инвестиций.
 *
 * @param uid    UUID инструмента — основной идентификатор для постановки ордеров.
 * @param figi   Альтернативный код (используется в некоторых endpoint'ах).
 * @param ticker Биржевой тикер (Si-6.26, CRM6, и т.д.).
 * @param name   Полное название.
 * @param minPriceIncrement  Шаг цены — нужен для округления лимитных цен.
 *                           Si=1.0, CR=0.001, Eu=1.0 (обычно).
 * @param lot    Размер лота (для фьючерсов обычно 1).
 * @param basicAsset Базовый актив: USD/CNY/EUR.
 * @param expirationDateMillis ISO дата экспирации в миллисекундах.
 */
@Serializable
data class InstrumentRef(
    val uid: String,
    val figi: String,
    val ticker: String,
    val name: String,
    val minPriceIncrement: Double,
    val lot: Int,
    val basicAsset: String = "",
    val expirationDateMillis: Long = 0L
)
