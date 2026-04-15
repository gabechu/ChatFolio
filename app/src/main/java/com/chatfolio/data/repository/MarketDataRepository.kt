package com.chatfolio.data.repository

import com.chatfolio.data.local.dao.PriceCacheDao
import com.chatfolio.data.local.entity.PriceCacheEntity
import com.chatfolio.data.network.YahooFinanceClient
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

data class MarketPrice(
    val price: Double,
    val currency: String,
    val tradingDate: String,
)

@Singleton
class MarketDataRepository
    @Inject
    constructor(
        private val priceCacheDao: PriceCacheDao,
        private val yahooFinanceClient: YahooFinanceClient,
    ) {
        suspend fun getLatestPrice(ticker: String): MarketPrice {
            val cached = priceCacheDao.getLatestPrice(ticker)
            val fetched = fetchFromYahoo(ticker)

            if (fetched == null) return fallback(cached)

            return if (cached == null || fetched.tradingDate != cached.tradingDate) {
                // New trading data available — persist it
                priceCacheDao.upsertPrice(
                    PriceCacheEntity(ticker, fetched.tradingDate, fetched.price, fetched.currency),
                )
                fetched
            } else {
                // Yahoo and DB agree on the trading date — no write needed
                MarketPrice(cached.closePrice, cached.currency, cached.tradingDate)
            }
        }

        private suspend fun fetchFromYahoo(ticker: String): MarketPrice? {
            val meta = yahooFinanceClient.fetchSymbolData(ticker).getOrNull() ?: return null
            val price = meta.regularMarketPrice ?: return null
            val currency = meta.currency ?: "USD"
            val tradingDate =
                meta.regularMarketTime
                    ?.let { Instant.ofEpochSecond(it).atOffset(ZoneOffset.UTC).toLocalDate().toString() }
                    ?: return null // If Yahoo can't tell us the trading date, treat as failed fetch
            return MarketPrice(price, currency, tradingDate)
        }

        private fun fallback(cached: PriceCacheEntity?): MarketPrice {
            return MarketPrice(
                price = cached?.closePrice ?: 0.0,
                currency = cached?.currency ?: "USD",
                tradingDate = cached?.tradingDate ?: "",
            )
        }
    }
