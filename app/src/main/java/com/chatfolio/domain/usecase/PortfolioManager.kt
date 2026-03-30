package com.chatfolio.domain.usecase

import com.chatfolio.data.local.dao.HoldingDao
import com.chatfolio.data.local.dao.PortfolioDao
import com.chatfolio.data.local.dao.PriceCacheDao
import com.chatfolio.data.local.entity.HoldingEntity
import com.chatfolio.data.local.entity.PriceCacheEntity
import com.chatfolio.data.network.YahooFinanceClient
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
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

    private suspend fun resolvePrice(holding: HoldingEntity): PriceData {
        val cached = priceCacheDao.getLatestPrice(holding.ticker)
        return if (isFresh(cached)) {
            PriceData(cached!!.closePrice, cached.currency, cached.tradingDate)
        } else {
            fetchAndCache(holding.ticker) ?: fallback(cached)
        }
    }

    /**
     * A cached price is fresh if its trading date matches the most recent
     * trading day. This correctly handles weekends and avoids re-fetching
     * when no new market data exists (e.g. Saturday cached with Friday's close).
     */
    private fun isFresh(cached: PriceCacheEntity?): Boolean {
        if (cached == null) return false
        return cached.tradingDate == lastTradingDay().toString()
    }

    /**
     * Returns the most recent weekday relative to today.
     * Saturday → Friday, Sunday → Friday, Mon–Fri → today.
     * Holidays are not modelled: an extra fetch on a holiday is harmless
     * (Yahoo returns the same trading date, so the cache updates with identical data).
     */
    private fun lastTradingDay(): LocalDate {
        val today = LocalDate.now()
        return when (today.dayOfWeek) {
            DayOfWeek.SATURDAY -> today.minusDays(1)
            DayOfWeek.SUNDAY   -> today.minusDays(2)
            else               -> today
        }
    }

    private suspend fun fetchAndCache(ticker: String): PriceData? {
        val meta = yahooFinanceClient.fetchSymbolData(ticker).getOrNull() ?: return null
        val price = meta.regularMarketPrice ?: return null
        val currency = meta.currency ?: "USD"
        // Use Yahoo's own market time as the authoritative trading date.
        // Falls back to lastTradingDay() if the field is missing.
        val tradingDate = meta.regularMarketTime
            ?.let { Instant.ofEpochSecond(it).atOffset(ZoneOffset.UTC).toLocalDate().toString() }
            ?: lastTradingDay().toString()
        priceCacheDao.upsertPrice(PriceCacheEntity(ticker, tradingDate, price, currency))
        return PriceData(price, currency, tradingDate)
    }

    private fun fallback(cached: PriceCacheEntity?): PriceData {
        return PriceData(
            price = cached?.closePrice ?: 0.0,
            currency = cached?.currency ?: "USD",
            tradingDate = cached?.tradingDate ?: lastTradingDay().toString()
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
