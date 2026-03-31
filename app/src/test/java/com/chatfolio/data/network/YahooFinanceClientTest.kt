package com.chatfolio.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YahooFinanceClientTest {
    private fun createMockClient(
        responseContent: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpClient {
        val mockEngine =
            MockEngine { request ->
                respond(
                    content = responseContent,
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
        }
    }

    @Test
    fun fetchSymbolData_successfulResponse_returnsParsedMeta() =
        runTest {
            val sampleJson =
                """
                {
                  "chart": {
                    "result": [
                      {
                        "meta": {
                          "currency": "USD",
                          "symbol": "AAPL",
                          "regularMarketPrice": 150.5,
                          "previousClose": 149.0
                        }
                      }
                    ],
                    "error": null
                  }
                }
                """.trimIndent()

            val client = YahooFinanceClient(createMockClient(sampleJson))

            val result = client.fetchSymbolData("AAPL")

            assertTrue(result.isSuccess)
            val meta = result.getOrNull()!!
            assertEquals("AAPL", meta.symbol)
            assertEquals("USD", meta.currency)
            assertEquals(150.5, meta.regularMarketPrice!!, 0.0)
        }

    @Test
    fun fetchSymbolData_apiError_returnsFailure() =
        runTest {
            val sampleErrorJson =
                """
                {
                  "chart": {
                    "result": null,
                    "error": {
                      "code": "Not Found",
                      "description": "No data found, symbol may be delisted"
                    }
                  }
                }
                """.trimIndent()

            val client = YahooFinanceClient(createMockClient(sampleErrorJson))

            val result = client.fetchSymbolData("INVALID")

            assertTrue(result.isFailure)
            val exception = result.exceptionOrNull()
            assertTrue(exception?.message?.contains("Not Found") == true)
        }
}
