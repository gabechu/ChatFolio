package com.chatfolio.domain.usecase

import com.chatfolio.data.local.dao.HoldingDao
import com.chatfolio.data.local.dao.PortfolioDao
import com.chatfolio.data.local.dao.PriceCacheDao
import com.chatfolio.data.local.entity.HoldingEntity
import com.chatfolio.data.local.entity.PriceCacheEntity
import com.chatfolio.data.network.YahooFinanceClient
import java.time.Instant
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PortfolioManager @Inject constructor(
    private val portfolioDao: PortfolioDao,
    private val holdingDao: HoldingDao,
    private val priceCacheDao: PriceCacheDao,
    private val yahooFinanceClient: YahooFinanceClient
) {
    private data class PriceData(
        val price: Double,
        val currency: String,
        val tradingDate: String
    )

    suspend fun getLiveHoldings(): List<HoldingWithPrice> {
        val portfolio = portfolioDao.getDefaultPortfolio() ?: return emptyList()
        val holdings = holdingDao.getHoldingsSnapshot(portfolio.id)
        return holdings
            .filter { it.totalShares > 0.0 }
            .map { holding ->
                val priceData = resolvePrice(holding)
                buildHoldingWithPrice(holding, priceData)
            }
    }

    /**
     * Resolution strategy (Yahoo is the source of truth for trading dates):
     * 1. Check DB — if no entry, fetch Yahoo and store it.
     * 2. Fetch Yahoo — compare its tradingDate with what's in DB.
     *    - New tradingDate (market has closed since last fetch) → update DB.
     *    - Same tradingDate (weekend, holiday, same day) → DB is current, skip write.
     *
     * No hardcoded day-of-week or holiday logic needed.
     */
    private suspend fun resolvePrice(holding: HoldingEntity): PriceData {
        val cached = priceCacheDao.getLatestPrice(holding.ticker)

        val fetched = fetchFromYahoo(holding.ticker)
            ?: return fallback(cached)

        return if (cached == null || fetched.tradingDate != cached.tradingDate) {
            // New trading data available — persist it
            priceCacheDao.upsertPrice(
                PriceCacheEntity(holding.ticker, fetched.tradingDate, fetched.price, fetched.currency)
            )
            fetched
        } else {
            // Yahoo and DB agree on the trading date — no write needed
            PriceData(cached.closePrice, cached.currency, cached.tradingDate)
        }
    }

    private suspend fun fetchFromYahoo(ticker: String): PriceData? {
        val meta = yahooFinanceClient.fetchSymbolData(ticker).getOrNull() ?: return null
        val price = meta.regularMarketPrice ?: return null
        val currency = meta.currency ?: "USD"
        val tradingDate = meta.regularMarketTime
            ?.let { Instant.ofEpochSecond(it).atOffset(ZoneOffset.UTC).toLocalDate().toString() }
            ?: return null  // If Yahoo can't tell us the trading date, treat as failed fetch
        return PriceData(price, currency, tradingDate)
    }

    private fun fallback(cached: PriceCacheEntity?): PriceData {
        return PriceData(
            price = cached?.closePrice ?: 0.0,
            currency = cached?.currency ?: "USD",
            tradingDate = cached?.tradingDate ?: ""
        )
    }

    private fun buildHoldingWithPrice(holding: HoldingEntity, priceData: PriceData): HoldingWithPrice {
        val marketValue = holding.totalShares * priceData.price
        val gainLoss = marketValue - holding.costBase
        val gainLossPercent = if (holding.costBase != 0.0) (gainLoss / holding.costBase) * 100.0 else 0.0
        return HoldingWithPrice(
            ticker = holding.ticker,
            market = holding.market,
            totalShares = holding.totalShares,
            costBase = holding.costBase,
            currentPrice = priceData.price,
            currency = priceData.currency,
            marketValue = marketValue,
            gainLoss = gainLoss,
            gainLossPercent = gainLossPercent,
            priceAsOf = priceData.tradingDate
        )
    }
}
