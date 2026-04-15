package com.chatfolio.domain.usecase

import com.chatfolio.data.repository.MarketDataRepository
import com.chatfolio.data.repository.MarketPrice
import com.chatfolio.domain.port.ToolCall
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ChatToolParsersTest {
    @Test
    fun `parseShowPortfolioCall gracefully handles missing currency and routes to AUD`() {
        val toolCall = ToolCall(name = "showPortfolio", arguments = emptyMap())
        val result = toolCall.parseShowPortfolioCall()
        assertThat(result.displayCurrency).isEqualTo("AUD")
    }

    @Test
    fun `parseShowPortfolioCall correctly extracts display currency`() {
        val toolCall = ToolCall(name = "showPortfolio", arguments = mapOf("currency" to "USD"))
        val result = toolCall.parseShowPortfolioCall()
        assertThat(result.displayCurrency).isEqualTo("USD")
    }

    @Test
    fun `parseDeleteTransactionCall heavily defaults missing args but survives execution`() {
        val toolCall = ToolCall(name = "deleteTransaction", arguments = emptyMap())
        val result = toolCall.parseDeleteTransactionCall()

        assertThat(result.ticker).isEqualTo("UNKNOWN")
        assertThat(result.action).isEqualTo("BUY")
    }

    @Test
    fun `parseUpdateTransactionCall processes double conversions safely without crashing`() {
        val toolCall =
            ToolCall(
                name = "updateTransaction",
                arguments =
                    mapOf(
                        "ticker" to "META",
                        "action" to "SELL",
                        "newShares" to "150.5",
                        // Passed as raw number instead of string from LLM parser sometimes
                        "newPrice" to 420.69,
                    ),
            )
        val result = toolCall.parseUpdateTransactionCall()

        assertThat(result.ticker).isEqualTo("META")
        assertThat(result.action).isEqualTo("SELL")
        assertThat(result.newShares).isEqualTo(150.5)
        assertThat(result.newPrice).isEqualTo(420.69)
    }

    @Test
    fun `parseAddTransactionCalls gracefully parses dates and numbers in list structure`() =
        runTest {
            val mockMarketRepo = mockk<MarketDataRepository>()
            coEvery { mockMarketRepo.getLatestPrice(any()) } returns MarketPrice(0.0, "EUR", "")

            val toolCallList =
                listOf(
                    ToolCall(
                        name = "addTransaction",
                        arguments =
                            mapOf(
                                "ticker" to "AMZN",
                                "action" to "BUY",
                                "shares" to "10",
                                "price" to "150.0",
                                "date" to "2023-10-01",
                                "currency" to "USD",
                            ),
                    ),
                    ToolCall(
                        name = "addTransaction",
                        arguments =
                            mapOf(
                                // Missing parameters
                                "ticker" to "CBA.AX",
                            ),
                    ),
                )

            val result = toolCallList.parseAddTransactionCalls(mockMarketRepo)

            assertThat(result.trades).hasSize(2)

            val firstTrade = result.trades[0]
            assertThat(firstTrade.ticker).isEqualTo("AMZN")
            assertThat(firstTrade.action).isEqualTo("BUY")
            assertThat(firstTrade.shares).isEqualTo(10.0)
            assertThat(firstTrade.price).isEqualTo(150.0)
            assertThat(firstTrade.currency).isEqualTo("USD")

            // 2023-10-01 in Epoch Ms is roughly 1696118400000
            val expectedDateLong = java.time.LocalDate.parse("2023-10-01").atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond() * 1000
            assertThat(firstTrade.date).isEqualTo(expectedDateLong)

            val secondTrade = result.trades[1]
            assertThat(secondTrade.ticker).isEqualTo("CBA.AX")
            assertThat(secondTrade.action).isEqualTo("BUY") // Default
            assertThat(secondTrade.shares).isEqualTo(0.0) // Corrupted/Missing
            assertThat(secondTrade.price).isEqualTo(0.0)
            assertThat(secondTrade.date).isNull()
            assertThat(secondTrade.currency).isEqualTo("EUR") // From the mocked fallback!
        }

    @Test
    fun `parseAddTransactionCalls completely swallows wildly corrupted date strings returning null`() =
        runTest {
            val mockMarketRepo = mockk<MarketDataRepository>()
            coEvery { mockMarketRepo.getLatestPrice(any()) } returns MarketPrice(0.0, "AUD", "")

            val toolCallList =
                listOf(
                    ToolCall(
                        name = "addTransaction",
                        arguments =
                            mapOf(
                                "ticker" to "INVALID_DATE",
                                "date" to "Not a real date format at all",
                            ),
                    ),
                )

            val result = toolCallList.parseAddTransactionCalls(mockMarketRepo)
            val trade = result.trades.first()

            assertThat(trade.date).isNull()
        }
}
