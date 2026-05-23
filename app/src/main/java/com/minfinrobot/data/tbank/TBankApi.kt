package com.minfinrobot.data.tbank

import retrofit2.http.Body
import retrofit2.http.POST

interface TBankApi {

    @POST("rest/tinkoff.public.invest.api.contract.v1.UsersService/GetAccounts")
    suspend fun getAccounts(@Body body: EmptyRequest = EmptyRequest()): AccountsResponse

    @POST("rest/tinkoff.public.invest.api.contract.v1.InstrumentsService/Futures")
    suspend fun getFutures(
        @Body body: GetInstrumentsRequest = GetInstrumentsRequest()
    ): FuturesResponse

    @POST("rest/tinkoff.public.invest.api.contract.v1.MarketDataService/GetLastPrices")
    suspend fun getLastPrices(@Body body: GetLastPricesRequest): LastPricesResponse

    @POST("rest/tinkoff.public.invest.api.contract.v1.OrdersService/PostOrder")
    suspend fun postOrder(@Body body: PostOrderRequest): PostOrderResponse
}
