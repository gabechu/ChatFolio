package com.chatfolio.domain.usecase

import com.chatfolio.data.repository.MarketDataRepository
import com.chatfolio.data.repository.MarketPrice
import com.chatfolio.data.repository.PortfolioRepository
import com.chatfolio.domain.port.LlmEngine
import com.chatfolio.domain.port.LlmResponse
import com.chatfolio.domain.port.ToolCall
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class ChatInteractionTest {
    private lateinit var llmEngine: LlmEngine
    private lateinit var portfolioRepository: PortfolioRepository
    private lateinit var marketDataRepository: MarketDataRepository
    private lateinit var chatInteraction: ChatInteraction

    @Before
    fun setup() {
        llmEngine = mockk(relaxed = true)
        portfolioRepository = mockk(relaxed = true)
        marketDataRepository = mockk(relaxed = true)

        // Mock a basic fallback constraint
        coEvery { marketDataRepository.getLatestPrice(any()) } returns MarketPrice(0.0, "AUD", "")

        chatInteraction =
            ChatInteraction(
                llmEngine = llmEngine,
                portfolioRepository = portfolioRepository,
                marketDataRepository = marketDataRepository,
            )
    }

    @Test
    fun `sendMessage resolves raw text accurately when absolutely no tools are invoked`() =
        runTest {
            coEvery { llmEngine.sendMessage(any(), any()) } returns
                LlmResponse(
                    textResponse = "Hello World! I am an AI.",
                    toolCalls = null,
                )

            val results = chatInteraction.sendMessage("hi", emptyList())

            assertThat(results).hasSize(1)
            assertThat(results[0]).isInstanceOf(ChatInteractionResult.TextReply::class.java)
            val reply = results[0] as ChatInteractionResult.TextReply
            assertThat(reply.text).isEqualTo("Hello World! I am an AI.")
        }

    @Test
    fun `sendMessage accurately reroutes to ShowPortfolio when asked`() =
        runTest {
            coEvery { llmEngine.sendMessage(any(), any()) } returns
                LlmResponse(
                    textResponse = null,
                    toolCalls =
                        listOf(
                            ToolCall(name = "showPortfolio", arguments = mapOf("currency" to "USD")),
                        ),
                )

            val results = chatInteraction.sendMessage("show me my portfolio", emptyList())

            assertThat(results).hasSize(1)
            assertThat(results[0]).isInstanceOf(ChatInteractionResult.ShowPortfolio::class.java)
            val showCall = results[0] as ChatInteractionResult.ShowPortfolio
            assertThat(showCall.displayCurrency).isEqualTo("USD")
        }

    @Test
    fun `sendMessage traps network crashes inside a Result Error gracefully`() =
        runTest {
            coEvery { llmEngine.sendMessage(any(), any()) } throws RuntimeException("Out of API credits!")

            val results = chatInteraction.sendMessage("buy AAPL", emptyList())

            assertThat(results).hasSize(1)
            assertThat(results[0]).isInstanceOf(ChatInteractionResult.Error::class.java)
            val errorCall = results[0] as ChatInteractionResult.Error
            assertThat(errorCall.message).isEqualTo("Out of API credits!")
        }

    @Test
    fun `sendMessage filters exactly for addTransaction and routes to parsed batch`() =
        runTest {
            coEvery { llmEngine.sendMessage(any(), any()) } returns
                LlmResponse(
                    textResponse = null,
                    toolCalls =
                        listOf(
                            ToolCall(name = "addTransaction", arguments = mapOf("ticker" to "AAPL", "currency" to "USD")),
                        ),
                )

            val results = chatInteraction.sendMessage("I bought apple", emptyList())

            assertThat(results).hasSize(1)
            assertThat(results[0]).isInstanceOf(ChatInteractionResult.ParsedTransactions::class.java)
            val parseCall = results[0] as ChatInteractionResult.ParsedTransactions
            assertThat(parseCall.trades.size).isEqualTo(1)
            assertThat(parseCall.trades[0].ticker).isEqualTo("AAPL")
        }
}
