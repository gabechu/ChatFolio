package com.chatfolio.domain.usecase

data class TradeRecord(
    val type: String,
    val shares: Double,
    val price: Double,
    val date: Long,
)

data class HoldingCalculatedState(
    val totalShares: Double,
    val costBase: Double,
    val realizedProfit: Double,
)

object PortfolioCalculator {
    /**
     * Replays a chronologically ordered ledger of transactions to calculate
     * the final total shares, the current average cost base, and total realized profit.
     *
     * @param trades A list of transactions. MUST BE SORTED BY DATE ASCENDING.
     * @throws IllegalArgumentException if selling results in a negative share balance.
     */
    fun calculate(trades: List<TradeRecord>): HoldingCalculatedState {
        var currentShares = 0.0
        var currentCostBase = 0.0
        var currentRealizedProfit = 0.0

        for (trade in trades) {
            val isBuy = trade.type.equals("BUY", ignoreCase = true)

            if (isBuy) {
                currentShares += trade.shares
                currentCostBase += (trade.shares * trade.price)
            } else {
                // It's a SELL
                if (currentShares < trade.shares) {
                    throw IllegalArgumentException("Short selling is not supported. Attempted to sell ${trade.shares} shares, but only $currentShares are owned.")
                }

                // Average cost basis at the time of the sale
                val avgCostPerShare = if (currentShares > 0) currentCostBase / currentShares else 0.0

                // Reduce the active cost base by the portion being sold
                val costBaseRemoved = trade.shares * avgCostPerShare
                currentCostBase -= costBaseRemoved

                // Calculate the profit actually realized by this sale
                val saleProceeds = trade.shares * trade.price
                val profitFromSale = saleProceeds - costBaseRemoved

                currentShares -= trade.shares
                currentRealizedProfit += profitFromSale

                // Prevent floating point anomalies causing slight negative cost bases on total liquidations
                if (currentShares == 0.0) {
                    currentCostBase = 0.0
                }
            }
        }

        return HoldingCalculatedState(
            totalShares = currentShares,
            costBase = currentCostBase,
            realizedProfit = currentRealizedProfit,
        )
    }
}
