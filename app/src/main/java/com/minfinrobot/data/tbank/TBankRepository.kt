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
import retrofit2.HttpException
import retrofit2.Retrofit
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Репозиторий T-Invest.
 *
 * Переключается между sandbox и production по settings.isSandbox.
 * В sandbox-режиме использует специальные эндпоинты "SandboxService" (см. документацию)
 * (требование официального API — обычные методы на sandbox-домене не работают).
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
                val resp = chain.proceed(req)
                // Лог любых HTTP-ошибок T-Invest. Тело важно — там описание.
                if (!resp.isSuccessful) {
                    val body = try { resp.peekBody(4096).string() } catch (e: Exception) { "?" }
                    val path = req.url.encodedPath.substringAfterLast("/")
                    LogStore.error("T-Invest HTTP ${resp.code} на $path: $body")
                }
                resp
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
        LogStore.info(
            "T-Invest клиент пересоздан: ${if (isSandbox) "SANDBOX" else "PRODUCTION"} ($baseUrl)"
        )
        return api
    }

    /**
     * Загрузка счетов. В sandbox использует GetSandboxAccounts.
     */
    suspend fun loadAccounts(): List<AccountRef> {
        val resp = if (settings.isSandbox) api().getSandboxAccounts()
                   else api().getAccounts()
        return resp.accounts.map {
            AccountRef(id = it.id, name = it.name.ifEmpty { it.id }, type = it.type)
        }
    }

    /**
     * Загрузить фьючерсы. Этот метод одинаков для sandbox и production.
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
     * Открыть sandbox-счёт. Возвращает ID нового счёта или ошибку.
     * Можно вызывать многократно — каждый вызов создаёт новый счёт.
     */
    suspend fun openSandboxAccount(): Result<String> = try {
        if (!settings.isSandbox) {
            Result.failure(IllegalStateException("openSandboxAccount доступен только в режиме SANDBOX"))
        } else {
            val resp = api().openSandboxAccount()
            LogStore.info("Открыт sandbox-счёт: ${resp.accountId}")
            Result.success(resp.accountId)
        }
    } catch (e: Exception) {
        LogStore.error("openSandboxAccount: ${e.message}")
        Result.failure(e)
    }

    /**
     * Пополнить sandbox-счёт.
     */
    suspend fun sandboxPayIn(accountId: String, rubAmount: Long): Result<Unit> = try {
        if (!settings.isSandbox) {
            Result.failure(IllegalStateException("sandboxPayIn доступен только в режиме SANDBOX"))
        } else {
            api().sandboxPayIn(
                SandboxPayInRequest(
                    accountId = accountId,
                    amount = MoneyValue(currency = "rub", units = rubAmount.toString(), nano = 0)
                )
            )
            LogStore.info("Sandbox счёт $accountId пополнен на $rubAmount ₽")
            Result.success(Unit)
        }
    } catch (e: Exception) {
        LogStore.error("sandboxPayIn: ${e.message}")
        Result.failure(e)
    }

    /**
     * Проверка состояния счёта перед стартом робота.
     *  - Загружает портфель.
     *  - Возвращает доступные средства в RUB.
     */
    suspend fun checkAccountReadiness(accountId: String): Result<AccountStatus> = try {
        val resp = if (settings.isSandbox)
            api().getSandboxPortfolio(PortfolioRequest(accountId = accountId, currency = "RUB"))
        else
            api().getPortfolio(PortfolioRequest(accountId = accountId, currency = "RUB"))

        val total = resp.totalAmountPortfolio?.let { moneyToDouble(it) } ?: 0.0
        val cash = resp.totalAmountCurrencies?.let { moneyToDouble(it) } ?: 0.0
        val positions = resp.positions.size

        Result.success(AccountStatus(
            accountId = accountId,
            totalRub = total,
            cashRub = cash,
            positionsCount = positions,
            sandbox = settings.isSandbox
        ))
    } catch (e: HttpException) {
        // 404 в sandbox = счёт не существует (нужен OpenSandboxAccount).
        Result.failure(RuntimeException("Счёт не найден или нет доступа: ${e.message}"))
    } catch (e: Exception) {
        Result.failure(e)
    }

    /**
     * Разместить ордер. В sandbox использует PostSandboxOrder.
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

        LogStore.info(
            "→ T-Invest ${if (settings.isSandbox) "[SBX]" else "[PROD]"} PostOrder: " +
                "${action} ${quantity} ${instrument.ticker} ($orderType)"
        )

        val resp = if (settings.isSandbox) api().postSandboxOrder(request)
                   else api().postOrder(request)

        LogStore.info("← статус: ${resp.executionReportStatus}, " +
            "исполнено лотов: ${resp.lotsExecuted}/${resp.lotsRequested}")

        val ok = resp.executionReportStatus.contains("FILL", true) ||
                resp.executionReportStatus.contains("NEW", true) ||
                resp.executionReportStatus.contains("PLACED", true)

        if (ok) Result.success(resp.orderId.ifEmpty { orderId })
        else Result.failure(RuntimeException(
            "T-Invest отказал: ${resp.executionReportStatus} ${resp.message}"
        ))
    } catch (e: Exception) {
        LogStore.error("placeOrder: ${e.message}")
        Result.failure(e)
    }

    private fun parseInstant(iso: String): Long {
        if (iso.isEmpty()) return 0L
        return try { Instant.parse(iso).toEpochMilli() } catch (e: Exception) { 0L }
    }

    private fun moneyToDouble(m: MoneyValue): Double =
        m.units.toLongOrNull()?.let { it + m.nano / 1_000_000_000.0 } ?: 0.0

    /**
     * Состояние счёта (для проверки готовности перед стартом робота).
     */
    data class AccountStatus(
        val accountId: String,
        val totalRub: Double,
        val cashRub: Double,
        val positionsCount: Int,
        val sandbox: Boolean
    ) {
        fun describe(): String = buildString {
            append("Счёт ${if (sandbox) "[SBX]" else "[PROD]"} ${accountId.takeLast(8)}: ")
            append("портфель=${"%.2f".format(totalRub)} ₽, ")
            append("кеш=${"%.2f".format(cashRub)} ₽, ")
            append("позиций=$positionsCount")
        }
    }

    companion object {
        const val PROD_URL = "https://invest-public-api.tinkoff.ru/"
        const val SANDBOX_URL = "https://sandbox-invest-public-api.tinkoff.ru/"

        val DEFAULT_BASIC_ASSETS = setOf(
            "USD", "CNY", "EUR",
            "IMOEX", "MOEX", "RTSI", "RTS", "RGBI", "RGBITR"
        )
        val DEFAULT_TICKER_PREFIXES = setOf(
            "SI", "CR", "EU",
            "MIX", "MX",
            "RTS",
            "RGBI",
            "IMOEXF"
        )
    }
}
