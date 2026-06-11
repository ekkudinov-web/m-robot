package com.minfinrobot.data.tbank

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * Retrofit-интерфейс T-Invest API.
 *
 * Sandbox-эндпоинты (SandboxService/...) обязательны при работе через sandbox URL —
 * обычные UsersService/OrdersService на sandbox-домене возвращают ошибки.
 * См. https://developer.tbank.ru/invest/intro/developer/sandbox
 */
interface TBankApi {

    // === Production endpoints ===

    @POST("rest/tinkoff.public.invest.api.contract.v1.UsersService/GetAccounts")
    suspend fun getAccounts(@Body body: EmptyRequest = EmptyRequest()): AccountsResponse

    @POST("rest/tinkoff.public.invest.api.contract.v1.OrdersService/PostOrder")
    suspend fun postOrder(@Body body: PostOrderRequest): PostOrderResponse

    @POST("rest/tinkoff.public.invest.api.contract.v1.OperationsService/GetPortfolio")
    suspend fun getPortfolio(@Body body: PortfolioRequest): PortfolioResponse

    // === Sandbox endpoints (нужны при работе через sandbox URL) ===

    @POST("rest/tinkoff.public.invest.api.contract.v1.SandboxService/GetSandboxAccounts")
    suspend fun getSandboxAccounts(@Body body: EmptyRequest = EmptyRequest()): AccountsResponse

    @POST("rest/tinkoff.public.invest.api.contract.v1.SandboxService/OpenSandboxAccount")
    suspend fun openSandboxAccount(@Body body: EmptyRequest = EmptyRequest()): OpenSandboxAccountResponse

    @POST("rest/tinkoff.public.invest.api.contract.v1.SandboxService/SandboxPayIn")
    suspend fun sandboxPayIn(@Body body: SandboxPayInRequest): SandboxPayInResponse

    @POST("rest/tinkoff.public.invest.api.contract.v1.SandboxService/PostSandboxOrder")
    suspend fun postSandboxOrder(@Body body: PostOrderRequest): PostOrderResponse

    @POST("rest/tinkoff.public.invest.api.contract.v1.SandboxService/GetSandboxPortfolio")
    suspend fun getSandboxPortfolio(@Body body: PortfolioRequest): PortfolioResponse

    @POST("rest/tinkoff.public.invest.api.contract.v1.SandboxService/GetSandboxOrders")
    suspend fun getSandboxOrders(@Body body: GetOrdersRequest): GetOrdersResponse

    @POST("rest/tinkoff.public.invest.api.contract.v1.SandboxService/GetSandboxOperations")
    suspend fun getSandboxOperations(@Body body: GetOperationsRequest): GetOperationsResponse

    // === Общие методы (работают одинаково на обоих контурах) ===

    @POST("rest/tinkoff.public.invest.api.contract.v1.InstrumentsService/Futures")
    suspend fun getFutures(
        @Body body: GetInstrumentsRequest = GetInstrumentsRequest()
    ): FuturesResponse

    @POST("rest/tinkoff.public.invest.api.contract.v1.InstrumentsService/Shares")
    suspend fun getShares(
        @Body body: GetInstrumentsRequest = GetInstrumentsRequest()
    ): SharesResponse

    @POST("rest/tinkoff.public.invest.api.contract.v1.MarketDataService/GetLastPrices")
    suspend fun getLastPrices(@Body body: GetLastPricesRequest): LastPricesResponse
}
