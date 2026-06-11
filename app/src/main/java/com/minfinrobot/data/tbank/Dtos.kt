package com.minfinrobot.data.tbank

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * T-Invest decimal type: units (int64 как строка) + nano (миллиардные доли).
 * Полное значение = units + nano / 1e9
 *
 * Пример: 78500.5 → Quotation(units="78500", nano=500_000_000)
 */
@Serializable
data class Quotation(
    val units: String = "0",
    val nano: Int = 0
) {
    fun toDouble(): Double = units.toLong().toDouble() + nano / 1_000_000_000.0

    companion object {
        fun fromDouble(value: Double): Quotation {
            // Используем Math.round для устранения накопления ошибки double:
            // 78500.001 без округления может дать nano=999999, что некорректно.
            val totalNano = Math.round(value * 1_000_000_000.0)
            val units = totalNano / 1_000_000_000L
            val nano = (totalNano - units * 1_000_000_000L).toInt()
            return Quotation(units = units.toString(), nano = nano)
        }
    }
}

// --- GetAccounts ---

@Serializable
class EmptyRequest

@Serializable
data class AccountDto(
    val id: String,
    @SerialName("type") val type: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("status") val status: String = ""
)

@Serializable
data class AccountsResponse(
    val accounts: List<AccountDto> = emptyList()
)

// --- Futures ---

@Serializable
data class GetInstrumentsRequest(
    @SerialName("instrument_status") val instrumentStatus: String = "INSTRUMENT_STATUS_BASE"
)

@Serializable
data class FutureDto(
    val uid: String = "",
    val figi: String = "",
    val ticker: String = "",
    val name: String = "",
    @SerialName("expiration_date") val expirationDate: String = "",
    @SerialName("min_price_increment") val minPriceIncrement: Quotation = Quotation(),
    val lot: Int = 1,
    @SerialName("basic_asset") val basicAsset: String = "",
    @SerialName("trading_status") val tradingStatus: String = ""
)

@Serializable
data class FuturesResponse(
    val instruments: List<FutureDto> = emptyList()
)

// --- Акции (InstrumentsService/Shares) ---

@Serializable
data class ShareDto(
    val uid: String = "",
    val figi: String = "",
    val ticker: String = "",
    val name: String = "",
    @SerialName("min_price_increment") val minPriceIncrement: Quotation = Quotation(),
    val lot: Int = 1,
    val currency: String = "",
    @SerialName("country_of_risk") val countryOfRisk: String = ""
)

@Serializable
data class SharesResponse(
    val instruments: List<ShareDto> = emptyList()
)

// --- LastPrices (опционально) ---

@Serializable
data class GetLastPricesRequest(
    @SerialName("instrument_id") val instrumentId: List<String>
)

@Serializable
data class LastPriceDto(
    val figi: String = "",
    val price: Quotation = Quotation(),
    val time: String = "",
    @SerialName("instrument_uid") val instrumentUid: String = ""
)

@Serializable
data class LastPricesResponse(
    @SerialName("last_prices") val lastPrices: List<LastPriceDto> = emptyList()
)

// --- PostOrder ---

@Serializable
data class PostOrderRequest(
    @SerialName("instrument_id") val instrumentId: String,
    val quantity: Int,
    @SerialName("account_id") val accountId: String,
    val direction: String,
    @SerialName("order_type") val orderType: String,
    @SerialName("order_id") val orderId: String,
    @SerialName("idempotency_key") val idempotencyKey: String = orderId,
    val price: Quotation? = null
)

@Serializable
data class PostOrderResponse(
    @SerialName("orderId") val orderId: String = "",
    @SerialName("executionReportStatus") val executionReportStatus: String = "",
    @SerialName("lotsRequested") val lotsRequested: String = "0",
    @SerialName("lotsExecuted") val lotsExecuted: String = "0",
    val message: String = ""
)

// --- Sandbox-специфичные DTOs ---

@Serializable
data class OpenSandboxAccountResponse(
    @SerialName("account_id") val accountId: String = ""
)

@Serializable
data class MoneyValue(
    val currency: String,
    val units: String = "0",
    val nano: Int = 0
)

@Serializable
data class SandboxPayInRequest(
    @SerialName("account_id") val accountId: String,
    val amount: MoneyValue
)

@Serializable
data class SandboxPayInResponse(
    val balance: MoneyValue? = null
)

// --- Portfolio (используется для проверки баланса) ---

@Serializable
data class PortfolioRequest(
    @SerialName("account_id") val accountId: String,
    val currency: String = "RUB"
)

@Serializable
data class PortfolioResponse(
    @SerialName("total_amount_portfolio") val totalAmountPortfolio: MoneyValue? = null,
    @SerialName("total_amount_currencies") val totalAmountCurrencies: MoneyValue? = null,
    @SerialName("total_amount_futures") val totalAmountFutures: MoneyValue? = null,
    val positions: List<PortfolioPosition> = emptyList()
)

@Serializable
data class PortfolioPosition(
    val figi: String = "",
    @SerialName("instrument_type") val instrumentType: String = "",
    val quantity: Quotation = Quotation(),
    @SerialName("current_price") val currentPrice: MoneyValue? = null
)

// --- GetOrders / GetOperations ---

@Serializable
data class GetOrdersRequest(
    @SerialName("account_id") val accountId: String
)

@Serializable
data class OrderState(
    @SerialName("orderId") val orderId: String = "",
    @SerialName("executionReportStatus") val executionReportStatus: String = "",
    @SerialName("lotsRequested") val lotsRequested: String = "0",
    @SerialName("lotsExecuted") val lotsExecuted: String = "0",
    val figi: String = "",
    val direction: String = "",
    @SerialName("instrumentUid") val instrumentUid: String = ""
)

@Serializable
data class GetOrdersResponse(
    val orders: List<OrderState> = emptyList()
)

@Serializable
data class GetOperationsRequest(
    @SerialName("account_id") val accountId: String,
    val from: String = "",
    val to: String = ""
)

@Serializable
data class OperationState(
    val id: String = "",
    val type: String = "",
    val state: String = "",
    val figi: String = "",
    val date: String = "",
    val payment: MoneyValue? = null
)

@Serializable
data class GetOperationsResponse(
    val operations: List<OperationState> = emptyList()
)
