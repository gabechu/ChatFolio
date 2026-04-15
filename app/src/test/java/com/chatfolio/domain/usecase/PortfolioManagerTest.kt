package com.chatfolio.domain.usecase

import com.chatfolio.data.local.dao.HoldingDao
import com.chatfolio.data.local.dao.PortfolioDao
import com.chatfolio.data.local.dao.PriceCacheDao
import com.chatfolio.data.local.entity.HoldingEntity
import com.chatfolio.data.local.entity.PortfolioEntity
import com.chatfolio.data.local.entity.PriceCacheEntity
import com.chatfolio.data.network.YahooFinanceClient
import com.chatfolio.data.network.model.Meta
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.time.Instant

class PortfolioManagerTest {
    private lateinit var portfolioDao: PortfolioDao
    private lateinit var holdingDao: HoldingDao
    private lateinit var priceCacheDao: PriceCacheDao
    private lateinit var yahooFinanceClient: YahooFinanceClient
    private lateinit var portfolioManager: PortfolioManager

    @Before
    fun setup() {
        portfolioDao = mockk(relaxed = true)
        holdingDao = mockk(relaxed = true)
        priceCacheDao = mockk(relaxed = true)
        yahooFinanceClient = mockk(relaxed = true)

        portfolioManager =
            PortfolioManager(
                portfolioDao = portfolioDao,
                holdingDao = holdingDao,
                priceCacheDao = priceCacheDao,
                yahooFinanceClient = yahooFinanceClient,
            )
    }

    @Test
    fun `getLiveHoldings ignores holdings with zero shares`() =
        runTest {
            val dummyPortfolio = PortfolioEntity(id = 1, name = "Default")
            coEvery { portfolioDao.getDefaultPortfolio() } returns dummyPortfolio

            // 1 holding with 0 shares, 1 holding with positive shares
            val holdings =
                listOf(
                    HoldingEntity(portfolioId = 1, ticker = "AAPL", market = "US", totalShares = 0.0, costBase = 0.0, currency = "USD"),
                    HoldingEntity(portfolioId = 1, ticker = "TSLA", market = "US", totalShares = 10.0, costBase = 2000.0, currency = "USD"),
                )
            coEvery { holdingDao.getHoldingsSnapshot(1) } returns holdings
            coEvery { priceCacheDao.getLatestPrice(any()) } returns null
            coEvery { yahooFinanceClient.fetchSymbolData(any()) } returns Result.failure(Exception("Network error"))

            val result = portfolioManager.getLiveHoldings()

            assertThat(result).hasSize(1)
            assertThat(result[0].ticker).isEqualTo("TSLA")
        }

    @Test
    fun `getLiveHoldings calculates profit correctly when Yahoo returns new data`() =
        runTest {
            val dummyPortfolio = PortfolioEntity(id = 1, name = "Default")
            coEvery { portfolioDao.getDefaultPortfolio() } returns dummyPortfolio

            // Holding cost base is $1500 (10 shares @ $150)
            val holding = HoldingEntity(portfolioId = 1, ticker = "AAPL", market = "US", totalShares = 10.0, costBase = 1500.0, currency = "USD")
            coEvery { holdingDao.getHoldingsSnapshot(1) } returns listOf(holding)

            // No cache
            coEvery { priceCacheDao.getLatestPrice("AAPL") } returns null

            // Yahoo returns $200 per share, so market value is $2000, gain is $500
            val fakeTime = Instant.parse("2026-04-10T20:00:00Z").epochSecond
            val yahooMeta =
                Meta(
                    currency = "USD",
                    symbol = "AAPL",
                    regularMarketPrice = 200.0,
                    previousClose = 190.0,
                    regularMarketTime = fakeTime,
                )
            coEvery { yahooFinanceClient.fetchSymbolData("AAPL") } returns Result.success(yahooMeta)

            val result = portfolioManager.getLiveHoldings()

            assertThat(result).hasSize(1)

            val tslaResult = result[0]
            assertThat(tslaResult.currentPrice).isEqualTo(200.0)
            assertThat(tslaResult.marketValue).isEqualTo(2000.0)

            // 2000 - 1500
            assertThat(tslaResult.gainLoss).isEqualTo(500.0)

            // (500 / 1500) * 100
            assertThat(tslaResult.gainLossPercent).isWithin(0.01).of(33.33)

            // Verify that the new data was saved to DB
            coVerify(exactly = 1) { priceCacheDao.upsertPrice(any()) }
        }

    @Test
    fun `getLiveHoldings uses cache and avoids DB write when Yahoo returns same date`() =
        runTest {
            val dummyPortfolio = PortfolioEntity(id = 1, name = "Default")
            coEvery { portfolioDao.getDefaultPortfolio() } returns dummyPortfolio

            val holding = HoldingEntity(portfolioId = 1, ticker = "AAPL", market = "US", totalShares = 10.0, costBase = 1500.0, currency = "USD")
            coEvery { holdingDao.getHoldingsSnapshot(1) } returns listOf(holding)

            val dateString = "2026-04-10" // The date the Instant defaults to natively
            val cachedPrice = PriceCacheEntity(ticker = "AAPL", tradingDate = dateString, closePrice = 180.0, currency = "USD")
            coEvery { priceCacheDao.getLatestPrice("AAPL") } returns cachedPrice

            val fakeTime = Instant.parse("2026-04-10T20:00:00Z").epochSecond

            // Let's pretend Yahoo drifted slightly during close, but date is same
            val yahooMeta =
                Meta(
                    currency = "USD",
                    symbol = "AAPL",
                    regularMarketPrice = 185.0,
                    previousClose = 180.0,
                    regularMarketTime = fakeTime,
                )
            coEvery { yahooFinanceClient.fetchSymbolData("AAPL") } returns Result.success(yahooMeta)

            val result = portfolioManager.getLiveHoldings()

            // It should use the cache value because dates are identical
            assertThat(result[0].currentPrice).isEqualTo(180.0)

            // Verify DB write was completely skipped
            coVerify(exactly = 0) { priceCacheDao.upsertPrice(any()) }
        }

    @Test
    fun `getGlobalSummary accurately converts currency using live FX rate`() =
        runTest {
            val dummyPortfolio = PortfolioEntity(id = 1, name = "Default")
            coEvery { portfolioDao.getDefaultPortfolio() } returns dummyPortfolio

            val holdings =
                listOf(
                    // Cost base $1000 USD, Current Price $200 USD -> Market value $2000 USD
                    HoldingEntity(portfolioId = 1, ticker = "AAPL", market = "US", totalShares = 10.0, costBase = 1000.0, currency = "USD"),
                    // Cost base $500 AUD, Current Price $100 AUD -> Market value $1000 AUD
                    HoldingEntity(portfolioId = 1, ticker = "CBA.AX", market = "AU", totalShares = 10.0, costBase = 500.0, currency = "AUD"),
                )
            coEvery { holdingDao.getHoldingsSnapshot(1) } returns holdings

            // Mock prices for holdings (just passing through using fallback logic for brevity)
            coEvery { priceCacheDao.getLatestPrice("AAPL") } returns PriceCacheEntity("AAPL", "2026-04-10", 200.0, "USD")
            coEvery { priceCacheDao.getLatestPrice("CBA.AX") } returns PriceCacheEntity("CBA.AX", "2026-04-10", 100.0, "AUD")
            // Always fail main fetches so it relies on exact caches mocked above
            coEvery { yahooFinanceClient.fetchSymbolData(any()) } returns Result.failure(Exception("simulate failure to force cache"))

            // For the FX Rate, provide:
            // AUD to USD -> 0.65
            coEvery { priceCacheDao.getLatestPrice("AUDUSD=X") } returns PriceCacheEntity("AUDUSD=X", "2026-04-10", 0.65, "USD")
            // USD to AUD -> ~1.538
            coEvery { priceCacheDao.getLatestPrice("USDAUD=X") } returns PriceCacheEntity("USDAUD=X", "2026-04-10", 1.0 / 0.65, "AUD")

            val summaryAud = portfolioManager.getGlobalSummary("AUD")

            // Calculations:
            // AAPL (USD): cost = 1000, value = 2000
            // CBA (AUD): cost = 500, value = 1000

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
