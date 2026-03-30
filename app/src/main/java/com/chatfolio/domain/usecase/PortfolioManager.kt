package com.chatfolio.domain.usecase

import com.chatfolio.data.local.dao.HoldingDao
import com.chatfolio.data.local.dao.PortfolioDao
import com.chatfolio.data.local.dao.PriceCacheDao
import com.chatfolio.data.local.entity.HoldingEntity
import com.chatfolio.data.local.entity.PriceCacheEntity
import com.chatfolio.data.network.YahooFinanceClient
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PortfolioManager @Inject constructor(
    private val portfolioDao: PortfolioDao,
    private val holdingDao: HoldingDao,
    private val priceCacheDao: PriceCacheDao,
    private val yahooFinanceClient: YahooFinanceClient
) {
    companion object {
        private val CACHE_TTL_MS = TimeUnit.HOURS.toMillis(24)
    }

    private data class PriceData(
        val price: Double,
        val currency: String,
        val asOf: Long
    )

    suspend fun getLiveHoldings(): List<HoldingWithPrice> {
        val portfolio = portfolioDao.getDefaultPortfolio() ?: return emptyList()
        val holdings = holdingDao.getHoldingsSnapshot(portfolio.id)

        return holdings
            .filter { it.totalShares > 0.0 }
            .map { holding ->
                val priceData = resolvePrice(holding, now = System.currentTimeMillis())
                buildHoldingWithPrice(holding, priceData)
            }
    }

    private suspend fun resolvePrice(holding: HoldingEntity, now: Long): PriceData {
        val cached = priceCacheDao.getLatestPrice(holding.ticker)
        return if (isFresh(cached, now)) {
            PriceData(cached!!.closePrice, cached.currency, cached.date)
        } else {
            fetchAndCache(holding.ticker, now) ?: fallback(cached, now)
        }
    }

    private fun isFresh(cached: PriceCacheEntity?, now: Long): Boolean {
        return cached != null && (now - cached.date) <= CACHE_TTL_MS
    }

    private suspend fun fetchAndCache(ticker: String, now: Long): PriceData? {
        val meta = yahooFinanceClient.fetchSymbolData(ticker).getOrNull() ?: return null
        val price = meta.regularMarketPrice ?: return null
        val currency = meta.currency ?: "USD"
        priceCacheDao.upsertPrice(PriceCacheEntity(ticker, date = now, closePrice = price, currency = currency))
        return PriceData(price, currency, now)
    }

    private fun fallback(cached: PriceCacheEntity?, now: Long): PriceData {
        return PriceData(
            price = cached?.closePrice ?: 0.0,
            currency = cached?.currency ?: "USD",
            asOf = cached?.date ?: now
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
            priceAsOf = priceData.asOf
        )
    }
}
