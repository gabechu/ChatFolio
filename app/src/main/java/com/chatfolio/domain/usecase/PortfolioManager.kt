package com.chatfolio.domain.usecase

import com.chatfolio.data.local.dao.HoldingDao
import com.chatfolio.data.local.dao.PortfolioDao
import com.chatfolio.data.local.dao.PriceCacheDao
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

    suspend fun getLiveHoldings(): List<HoldingWithPrice> {
        val portfolio = portfolioDao.getDefaultPortfolio() ?: return emptyList()
        val holdings = holdingDao.getHoldingsSnapshot(portfolio.id)
        if (holdings.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()

        return holdings
            .filter { it.totalShares > 0.0 }
            .map { holding ->
                val cached = priceCacheDao.getLatestPrice(holding.ticker)
                val isStale = cached == null || (now - cached.date) > CACHE_TTL_MS

                val (price, currency, priceAsOf) = if (isStale) {
                    val result = yahooFinanceClient.fetchSymbolData(holding.ticker)
                    if (result.isSuccess) {
                        val meta = result.getOrNull()!!
                        val fetchedPrice = meta.regularMarketPrice ?: 0.0
                        val fetchedCurrency = meta.currency ?: "USD"
                        priceCacheDao.upsertPrice(
                            PriceCacheEntity(
                                ticker = holding.ticker,
                                date = now,
                                closePrice = fetchedPrice,
                                currency = fetchedCurrency
                            )
                        )
                        Triple(fetchedPrice, fetchedCurrency, now)
                    } else {
                        // Fall back to last cached value if fetch fails
                        Triple(cached?.closePrice ?: 0.0, cached?.currency ?: "USD", cached?.date ?: now)
                    }
                } else {
                    Triple(cached!!.closePrice, cached.currency, cached.date)
                }

                val marketValue = holding.totalShares * price
                val gainLoss = marketValue - holding.costBase
                val gainLossPercent = if (holding.costBase != 0.0) {
                    (gainLoss / holding.costBase) * 100.0
                } else 0.0

                HoldingWithPrice(
                    ticker = holding.ticker,
                    market = holding.market,
                    totalShares = holding.totalShares,
                    costBase = holding.costBase,
                    currentPrice = price,
                    currency = currency,
                    marketValue = marketValue,
                    gainLoss = gainLoss,
                    gainLossPercent = gainLossPercent,
                    priceAsOf = priceAsOf
                )
            }
    }
}
