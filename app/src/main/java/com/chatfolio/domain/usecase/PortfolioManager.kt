package com.chatfolio.domain.usecase

import com.chatfolio.data.local.dao.HoldingDao
import com.chatfolio.data.local.dao.PortfolioDao
import com.chatfolio.data.local.entity.HoldingEntity
import com.chatfolio.data.repository.MarketDataRepository
import com.chatfolio.data.repository.MarketPrice
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PortfolioManager
    @Inject
    constructor(
        private val portfolioDao: PortfolioDao,
        private val holdingDao: HoldingDao,
        private val marketDataRepository: MarketDataRepository,
    ) {
        data class GlobalPortfolioSummary(
            val totalValue: Double,
            val totalInvested: Double,
            val targetCurrency: String,
        )

        suspend fun getGlobalSummary(targetCurrency: String = "AUD"): GlobalPortfolioSummary {
            val liveHoldings = getLiveHoldings()

            var totalValue = 0.0
            var totalInvested = 0.0

            for (holding in liveHoldings) {
                val rate =
                    if (holding.currency.uppercase() == targetCurrency.uppercase()) {
                        1.0
                    } else {
                        marketDataRepository.getLatestPrice("${holding.currency.uppercase()}${targetCurrency.uppercase()}=X").price
                    }

                totalValue += holding.marketValue * rate
                totalInvested += holding.costBase * rate
            }

            return GlobalPortfolioSummary(
                totalValue = totalValue,
                totalInvested = totalInvested,
                targetCurrency = targetCurrency,
            )
        }

        suspend fun getLiveHoldings(): List<HoldingWithPrice> {
            val portfolio = portfolioDao.getDefaultPortfolio() ?: return emptyList()
            val holdings = holdingDao.getHoldingsSnapshot(portfolio.id)
            return holdings
                .filter { it.totalShares > 0.0 }
                .map { holding ->
                    val priceData = marketDataRepository.getLatestPrice(holding.ticker)
                    buildHoldingWithPrice(holding, priceData)
                }
        }

        private fun buildHoldingWithPrice(
            holding: HoldingEntity,
            priceData: MarketPrice,
        ): HoldingWithPrice {
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
                realizedProfit = holding.realizedProfit,
                priceAsOf = priceData.tradingDate,
            )
        }
    }
