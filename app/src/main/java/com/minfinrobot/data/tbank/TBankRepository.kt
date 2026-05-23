package com.minfinrobot.data.tbank

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.minfinrobot.data.log.LogStore
import com.minfinrobot.data.settings.SecureSettingsStore
import com.minfinrobot.domain.engine.PriceRoundingUtil
import com.minfinrobot.domain.model.AccountRef
import com.minfinrobot.domain.model.InstrumentRef
import com.minfinrobot.domain.model.TradeAction
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Репозиторий T-Invest.
 * Прозрачно переключается между sandbox и production по settings.isSandbox.
 * Bearer-токен инжектится OkHttp-интерсептором.
 */
class TBankRepository(private val settings: SecureSettingsStore) {

    private var cachedClient: Pair<Boolean, TBankApi>? = null

    private fun api(): TBankApi {
        val isSandbox = settings.isSandbox
        cachedClient?.let { (cachedSandbox, api) ->
            if (cachedSandbox == isSandbox) return api
        }

        val baseUrl = if (isSandbox) SANDBOX_URL else PROD_URL
        val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
            encodeDefaults = true
        }

        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val token = settings.getToken()
                val req = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", "application/json")
                    .build()
                chain.proceed(req)
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .build()

        val api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(TBankApi::class.java)

        cachedClient = isSandbox to api
        return api
    }

    suspend fun loadAccounts(): List<AccountRef> {
        val resp = api().getAccounts()
        return resp.accounts.map {
            AccountRef(id = it.id, name = it.name.ifEmpty { it.id }, type = it.type)
        }
    }

    /**
     * Загрузить фьючерсы из справочника Т-Инвестиций.
     *
     * По умолчанию подгружаются:
     *   - валютные: Si (USD), CR (CNY), Eu (EUR)
     *   - индексные: MIX/MXI (IMOEX), RTS (RTSI), IMOEXF (вечный IMOEX)
     *   - облигационные: RGBI (гособлигации)
     *
     * Фильтрация двойная — по basicAsset И по prefix тикера, потому что
     * T-Invest может возвращать разные значения basicAsset для одного актива
     * (например MIX-9.26 может иметь basicAsset="IMOEX" или "MOEX").
     */
    suspend fun loadFutures(
        wantedBasicAssets: Set<String> = DEFAULT_BASIC_ASSETS,
        wantedTickerPrefixes: Set<String> = DEFAULT_TICKER_PREFIXES
    ): List<InstrumentRef> {
        val resp = api().getFutures()
        val upperAssets = wantedBasicAssets.map { it.uppercase() }.toSet()
        val upperPrefixes = wantedTickerPrefixes.map { it.uppercase() }.toSet()
        return resp.instruments
            .filter { dto ->
                val asset = dto.basicAsset.uppercase()
                val ticker = dto.ticker.uppercase()
                asset in upperAssets || upperPrefixes.any { ticker.startsWith(it) }
            }
            .map { dto ->
                InstrumentRef(
                    uid = dto.uid,
                    figi = dto.figi,
                    ticker = dto.ticker,
                    name = dto.name,
                    expirationDateMillis = parseInstant(dto.expirationDate),
                    minPriceIncrement = dto.minPriceIncrement.toDouble(),
                    lot = dto.lot,
                    basicAsset = dto.basicAsset
                )
            }
            .sortedBy { it.expirationDateMillis }
    }

    suspend fun getLastPrice(instrumentUid: String): Double? {
        val resp = api().getLastPrices(GetLastPricesRequest(instrumentId = listOf(instrumentUid)))
        return resp.lastPrices.firstOrNull()?.price?.toDouble()
    }

    /**
     * Разместить ордер.
     *  - limitPrice == null → market-ордер.
     *  - limitPrice != null → лимитный ордер с авто-округлением:
     *      BUY  → округляется ВНИЗ (не платим больше задуманного)
     *      SELL → округляется ВВЕРХ (не продаём дешевле задуманного)
     */
    suspend fun placeOrder(
        accountId: String,
        instrument: InstrumentRef,
        action: TradeAction,
        quantity: Int,
        limitPrice: Double?
    ): Result<String> = try {
        val direction = when (action) {
            TradeAction.BUY -> "ORDER_DIRECTION_BUY"
            TradeAction.SELL -> "ORDER_DIRECTION_SELL"
        }
        val orderId = UUID.randomUUID().toString()

        val (orderType, priceQuotation) = if (limitPrice != null) {
            val rounded = PriceRoundingUtil.roundToIncrement(
                price = limitPrice,
                minPriceIncrement = instrument.minPriceIncrement,
                action = action
            )
            if (kotlin.math.abs(rounded - limitPrice) > 1e-9) {
                LogStore.info(
                    "Лимит ${"%.6f".format(limitPrice)} округлён до " +
                        "${"%.6f".format(rounded)} (шаг ${instrument.minPriceIncrement})"
                )
            }
            "ORDER_TYPE_LIMIT" to Quotation.fromDouble(rounded)
        } else {
            "ORDER_TYPE_MARKET" to null
        }

        val request = PostOrderRequest(
            instrumentId = instrument.uid,
            quantity = quantity,
            accountId = accountId,
            direction = direction,
            orderType = orderType,
            orderId = orderId,
            idempotencyKey = orderId,
            price = priceQuotation
        )

        val resp = api().postOrder(request)
        val ok = resp.executionReportStatus.contains("FILL", true) ||
                resp.executionReportStatus.contains("NEW", true) ||
                resp.executionReportStatus.contains("PLACED", true)

        if (ok) Result.success(resp.orderId.ifEmpty { orderId })
        else Result.failure(RuntimeException(
            "T-Invest вернул статус: ${resp.executionReportStatus} ${resp.message}"
        ))
    } catch (e: Exception) {
        Result.failure(e)
    }

    private fun parseInstant(iso: String): Long {
        if (iso.isEmpty()) return 0L
        return try { Instant.parse(iso).toEpochMilli() } catch (e: Exception) { 0L }
    }

    companion object {
        const val PROD_URL = "https://invest-public-api.tinkoff.ru/"
        const val SANDBOX_URL = "https://sandbox-invest-public-api.tinkoff.ru/"

        // Базовые активы которые точно поддерживаем
        val DEFAULT_BASIC_ASSETS = setOf(
            "USD", "CNY", "EUR",
            "IMOEX", "MOEX", "RTSI", "RTS", "RGBI", "RGBITR"
        )

        // Префиксы тикеров на случай если basicAsset вернётся в другом формате
        val DEFAULT_TICKER_PREFIXES = setOf(
            "SI", "CR", "EU",     // валютные
            "MIX", "MX",          // IMOEX
            "RTS",                // RTSI
            "RGBI",               // гособлигации
            "IMOEXF"              // вечный IMOEX
        )
    }
}
