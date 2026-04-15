package com.chatfolio.data.repository

import com.chatfolio.data.local.dao.PriceCacheDao
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

class MarketDataRepositoryTest {
    private lateinit var priceCacheDao: PriceCacheDao
    private lateinit var yahooFinanceClient: YahooFinanceClient
    private lateinit var repository: MarketDataRepository

    @Before
    fun setup() {
        priceCacheDao = mockk(relaxed = true)
        yahooFinanceClient = mockk(relaxed = true)

        repository =
            MarketDataRepository(
                priceCacheDao = priceCacheDao,
                yahooFinanceClient = yahooFinanceClient,
            )
    }

    @Test
    fun `getLatestPrice saves to db when yahoo returns new trading date`() =
        runTest {
            coEvery { priceCacheDao.getLatestPrice("AAPL") } returns null

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

            val result = repository.getLatestPrice("AAPL")

            assertThat(result.price).isEqualTo(200.0)
            assertThat(result.currency).isEqualTo("USD")
            assertThat(result.tradingDate).isEqualTo("2026-04-10")

            coVerify(exactly = 1) { priceCacheDao.upsertPrice(any()) }
        }

    @Test
    fun `getLatestPrice avoids db save when dates match`() =
        runTest {
            val cachedPrice = PriceCacheEntity(ticker = "AAPL", tradingDate = "2026-04-10", closePrice = 180.0, currency = "USD")
            coEvery { priceCacheDao.getLatestPrice("AAPL") } returns cachedPrice

            val fakeTime = Instant.parse("2026-04-10T20:00:00Z").epochSecond
            val yahooMeta =
                Meta(
                    currency = "USD",
                    symbol = "AAPL",
                    // slight drift during close
                    regularMarketPrice = 185.0,
                    previousClose = 180.0,
                    regularMarketTime = fakeTime,
                )
            coEvery { yahooFinanceClient.fetchSymbolData("AAPL") } returns Result.success(yahooMeta)

            val result = repository.getLatestPrice("AAPL")

            assertThat(result.price).isEqualTo(180.0) // Uses cache due to same date
            coVerify(exactly = 0) { priceCacheDao.upsertPrice(any()) }
        }

    @Test
    fun `getLatestPrice falls back to cache if yahoo fails`() =
        runTest {
            val cachedPrice = PriceCacheEntity(ticker = "AAPL", tradingDate = "2026-04-09", closePrice = 175.0, currency = "USD")
            coEvery { priceCacheDao.getLatestPrice("AAPL") } returns cachedPrice

            coEvery { yahooFinanceClient.fetchSymbolData("AAPL") } returns Result.failure(Exception("Network error"))

            val result = repository.getLatestPrice("AAPL")

            assertThat(result.price).isEqualTo(175.0)
            assertThat(result.tradingDate).isEqualTo("2026-04-09")
        }
}
