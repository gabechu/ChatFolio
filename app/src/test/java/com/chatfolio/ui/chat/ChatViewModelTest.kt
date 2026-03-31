package com.chatfolio.ui.chat

import app.cash.turbine.test
import com.chatfolio.data.repository.PortfolioRepository
import com.chatfolio.data.repository.SettingsRepository
import com.chatfolio.domain.usecase.ChatBotAgent
import com.chatfolio.domain.usecase.ChatInteractionResult
import com.chatfolio.domain.usecase.HoldingWithPrice
import com.chatfolio.domain.usecase.PortfolioManager
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    
    private lateinit var portfolioRepository: PortfolioRepository
    private lateinit var chatBotAgent: ChatBotAgent
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var portfolioManager: PortfolioManager
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        portfolioRepository = mockk(relaxed = true)
        chatBotAgent = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        portfolioManager = mockk(relaxed = true)

        every { settingsRepository.getGeminiApiKey() } returns "fake-key"
        
        viewModel = ChatViewModel(
            portfolioRepository,
            chatBotAgent,
            settingsRepository,
            portfolioManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `sendMessage with ShowPortfolio triggers live data summary card`() = runTest(testDispatcher) {
        // Arrange
        coEvery { chatBotAgent.sendMessage(any(), any()) } returns ChatInteractionResult.ShowPortfolio
        
        val liveHoldings = listOf(
            HoldingWithPrice(
                ticker = "AAPL",
                market = "US",
                totalShares = 10.0,
                costBase = 1500.0, // Avg $150
                currentPrice = 175.0,
                currency = "USD",
                priceAsOf = "2026-03-31",
                marketValue = 1750.0,
                gainLoss = 250.0,
                gainLossPercent = 16.67
            ),
            HoldingWithPrice(
                ticker = "VTS",
                market = "US",
                totalShares = 5.0,
                costBase = 1000.0, // Avg $200
                currentPrice = 190.0,
                currency = "USD",
                priceAsOf = "2026-03-31",
                marketValue = 950.0,
                gainLoss = -50.0,
                gainLossPercent = -5.0
            )
        )
        
        coEvery { portfolioManager.getLiveHoldings() } returns liveHoldings

        // Act
        viewModel.sendMessage("show my portfolio")
        advanceUntilIdle() // Wait for coroutines to finish

        // Assert
        viewModel.uiState.test {
            val state = awaitItem()
            
            // The message list should contain
            // 1. The user's input text
            // 2. The PortfolioSummaryCard
            // 3. The markdown table text
            val messages = state.messages
            assertTrue("Expect at least 3 messages (User prompt, Card, Holding table markdown)", messages.size >= 3)
            
            val summaryCard = messages.find { it is ChatContent.PortfolioSummaryCard } as? ChatContent.PortfolioSummaryCard
            assertTrue("PortfolioSummaryCard should be present", summaryCard != null)
            
            // Validate calculated totals for live-data-summary-card
            // Total market value = 1750 + 950 = 2700
            // Total cost = 1500 + 1000 = 2500
            // Gain = 200, Percent = 200 / 2500 = 8.0%
            assertEquals(2700.0, summaryCard!!.totalValue, 0.01)
            assertEquals(200.0, summaryCard.dailyChangeValue, 0.01)
            assertEquals(8.0, summaryCard.dailyChangePercent, 0.01)
        }
    }
}
