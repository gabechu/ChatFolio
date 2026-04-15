package com.chatfolio.domain.usecase

import com.chatfolio.data.local.dao.HoldingDao
import com.chatfolio.data.local.dao.PortfolioDao
import com.chatfolio.data.local.entity.HoldingEntity
import com.chatfolio.data.local.entity.PortfolioEntity
import com.chatfolio.data.repository.MarketDataRepository
import com.chatfolio.data.repository.MarketPrice
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class PortfolioManagerTest {
    private lateinit var portfolioDao: PortfolioDao
    private lateinit var holdingDao: HoldingDao
    private lateinit var marketDataRepository: MarketDataRepository
    private lateinit var portfolioManager: PortfolioManager

    @Before
    fun setup() {
        portfolioDao = mockk(relaxed = true)
        holdingDao = mockk(relaxed = true)
        marketDataRepository = mockk(relaxed = true)

        portfolioManager =
            PortfolioManager(
                portfolioDao = portfolioDao,
                holdingDao = holdingDao,
                marketDataRepository = marketDataRepository,
            )
    }

    @Test
    fun `getLiveHoldings ignores holdings with zero shares`() =
        runTest {
            val dummyPortfolio = PortfolioEntity(id = 1, name = "Default")
            coEvery { portfolioDao.getDefaultPortfolio() } returns dummyPortfolio

            val holdings =
                listOf(
                    HoldingEntity(portfolioId = 1, ticker = "AAPL", market = "US", totalShares = 0.0, costBase = 0.0, currency = "USD"),
                    HoldingEntity(portfolioId = 1, ticker = "TSLA", market = "US", totalShares = 10.0, costBase = 2000.0, currency = "USD"),
                )
            coEvery { holdingDao.getHoldingsSnapshot(1) } returns holdings
            coEvery { marketDataRepository.getLatestPrice("TSLA") } returns MarketPrice(price = 250.0, currency = "USD", tradingDate = "2026-04-10")

            val result = portfolioManager.getLiveHoldings()

            assertThat(result).hasSize(1)
            assertThat(result[0].ticker).isEqualTo("TSLA")
        }

    @Test
    fun `getLiveHoldings calculates mathematical profit accurately`() =
        runTest {
            val dummyPortfolio = PortfolioEntity(id = 1, name = "Default")
            coEvery { portfolioDao.getDefaultPortfolio() } returns dummyPortfolio

            val holding = HoldingEntity(portfolioId = 1, ticker = "AAPL", market = "US", totalShares = 10.0, costBase = 1500.0, currency = "USD")
            coEvery { holdingDao.getHoldingsSnapshot(1) } returns listOf(holding)

            coEvery { marketDataRepository.getLatestPrice("AAPL") } returns MarketPrice(price = 200.0, currency = "USD", tradingDate = "2026-04-10")

            val result = portfolioManager.getLiveHoldings()
            val aaplResult = result.first()

            assertThat(aaplResult.currentPrice).isEqualTo(200.0)
            assertThat(aaplResult.marketValue).isEqualTo(2000.0)
            assertThat(aaplResult.gainLoss).isEqualTo(500.0)
            assertThat(aaplResult.gainLossPercent).isWithin(0.01).of(33.33)
        }

    @Test
    fun `getGlobalSummary accurately converts currencies using market data rates`() =
        runTest {
            val dummyPortfolio = PortfolioEntity(id = 1, name = "Default")
            coEvery { portfolioDao.getDefaultPortfolio() } returns dummyPortfolio

            val holdings =
                listOf(
                    HoldingEntity(portfolioId = 1, ticker = "AAPL", market = "US", totalShares = 10.0, costBase = 1000.0, currency = "USD"),
                    HoldingEntity(portfolioId = 1, ticker = "CBA.AX", market = "AU", totalShares = 10.0, costBase = 500.0, currency = "AUD"),
                )
            coEvery { holdingDao.getHoldingsSnapshot(1) } returns holdings

            // AAPL price: 200 USD
            coEvery { marketDataRepository.getLatestPrice("AAPL") } returns MarketPrice(200.0, "USD", "2026-04-10")
            // CBA price: 100 AUD
            coEvery { marketDataRepository.getLatestPrice("CBA.AX") } returns MarketPrice(100.0, "AUD", "2026-04-10")

            // FX Rates
            coEvery { marketDataRepository.getLatestPrice("AUDUSD=X") } returns MarketPrice(0.65, "USD", "2026-04-10")
            coEvery { marketDataRepository.getLatestPrice("USDAUD=X") } returns MarketPrice(1.0 / 0.65, "AUD", "2026-04-10")

            val summaryAud = portfolioManager.getGlobalSummary("AUD")

            // Total in AUD:
            // AAPL in AUD = 2000 * (1 / 0.65)
            // base AUD = 1000
            assertThat(summaryAud.totalValue).isWithin(0.01).of(2000.0 * (1.0 / 0.65) + 1000.0)
            assertThat(summaryAud.targetCurrency).isEqualTo("AUD")

            val summaryUsd = portfolioManager.getGlobalSummary("USD")

            // Total in USD:
            // CBA in USD = 1000 * 0.65 = 650
            // base USD = 2000
            assertThat(summaryUsd.totalValue).isWithin(0.01).of(2000.0 + 1000.0 * 0.65)

            // Total Invested US
            assertThat(summaryUsd.totalInvested).isWithin(0.01).of(1000.0 + 500.0 * 0.65)
        }
}
