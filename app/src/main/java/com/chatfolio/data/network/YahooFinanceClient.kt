package com.chatfolio.data.network

import com.chatfolio.data.network.model.Meta
import com.chatfolio.data.network.model.YahooChartResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YahooFinanceClient
    @Inject
    constructor(
        private val httpClient: HttpClient,
    ) {
        suspend fun fetchSymbolData(ticker: String): Result<Meta> {
            return try {
                withRetry(
                    times = 2,
                    // Retry on common server/timeout issues, not bad formatting etc.
                    shouldRetry = { error ->
                        error is java.io.IOException ||
                            (error is io.ktor.client.plugins.ServerResponseException && error.response.status.value in 500..504)
                    },
                ) {
                    val response: YahooChartResponse =
                        httpClient.get("https://query1.finance.yahoo.com/v8/finance/chart/$ticker") {
                            parameter("interval", "1d")
                            parameter("range", "1d")
                        }.body()

                    val error = response.chart?.error
                    if (error != null) {
                        return@withRetry Result.failure(Exception("Yahoo API Error: ${error.code} - ${error.description}"))
                    }

                    val meta = response.chart?.result?.firstOrNull()?.meta
                    if (meta != null) {
                        Result.success(meta)
                    } else {
                        Result.failure(Exception("No meta data found in Yahoo response for $ticker"))
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
