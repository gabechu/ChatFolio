package com.chatfolio.data.repository

import com.chatfolio.data.local.dao.HoldingDao
import com.chatfolio.data.local.dao.PortfolioDao
import com.chatfolio.data.local.dao.TransactionDao
import com.chatfolio.data.local.entity.PortfolioEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PortfolioRepository @Inject constructor(
    private val portfolioDao: PortfolioDao,
    private val holdingDao: HoldingDao,
    private val transactionDao: TransactionDao
) {
    fun getAllPortfolios(): Flow<List<PortfolioEntity>> = portfolioDao.getAllPortfolios()

    suspend fun createPortfolio(name: String) {
        portfolioDao.insertPortfolio(PortfolioEntity(name = name))
    }

    suspend fun getHoldingsSnapshot(): List<com.chatfolio.data.local.entity.HoldingEntity> {
        val portfolio = portfolioDao.getDefaultPortfolio() ?: return emptyList()
        return holdingDao.getHoldingsSnapshot(portfolio.id)
    }

    suspend fun addTransaction(ticker: String, action: String, shares: Double, price: Double) {
        // 1. Get or create default portfolio
        var portfolio = portfolioDao.getDefaultPortfolio()
        if (portfolio == null) {
            val id = portfolioDao.insertPortfolio(PortfolioEntity(name = "My Portfolio"))
            portfolio = PortfolioEntity(id = id.toInt(), name = "My Portfolio")
        }

        // 2. Get or create holding
        val upperTicker = ticker.uppercase()
        var holding = holdingDao.getHoldingByTicker(portfolio.id, upperTicker)
        
        if (holding == null) {
            val hId = holdingDao.insertHolding(
                com.chatfolio.data.local.entity.HoldingEntity(
                    portfolioId = portfolio.id, 
                    ticker = upperTicker, 
                    market = "US", // Defaulting to US for now
                    totalShares = 0.0, 
                    costBase = 0.0
                )
            )
            holding = com.chatfolio.data.local.entity.HoldingEntity(
                id = hId.toInt(), 
                portfolioId = portfolio.id, 
                ticker = upperTicker, 
                market = "US", 
                totalShares = 0.0, 
                costBase = 0.0
            )
        }

        // 3. Update holding totals
        val isBuy = action.equals("BUY", ignoreCase = true)
        val shareDelta = if (isBuy) shares else -shares
        val newTotalShares = holding.totalShares + shareDelta
        
        // Simplified cost base calculation for MVP
        val valueDelta = if (isBuy) (shares * price) else -(shares * price)
        val newCostBase = holding.costBase + valueDelta

        holdingDao.updateHolding(holding.copy(totalShares = newTotalShares, costBase = newCostBase))

        // 4. Insert transaction record
        transactionDao.insertTransaction(
            com.chatfolio.data.local.entity.TransactionEntity(
                holdingId = holding.id,
                type = if (isBuy) "BUY" else "SELL",
                date = System.currentTimeMillis(),
                shares = shares,
                pricePerShare = price,
                totalValue = shares * price,
                source = "CHAT"
            )
        )
    }

    suspend fun deleteLatestTransaction(ticker: String, action: String) {
        val portfolio = portfolioDao.getDefaultPortfolio() ?: return
        val holding = holdingDao.getHoldingByTicker(portfolio.id, ticker.uppercase()) ?: return
        
        val transaction = transactionDao.getLatestTransaction(holding.id, action.uppercase()) ?: return
        
        // Reverse the totalShares and costBase impact
        val isBuy = transaction.type == "BUY"
        val shareDelta = if (isBuy) -transaction.shares else transaction.shares
        val valueDelta = if (isBuy) -transaction.totalValue else transaction.totalValue
        
        holdingDao.updateHolding(holding.copy(
            totalShares = holding.totalShares + shareDelta, 
            costBase = holding.costBase + valueDelta
        ))
        
        transactionDao.deleteTransaction(transaction)
    }

    // Simplified update method for MVP
    suspend fun updateLatestTransaction(ticker: String, action: String, newShares: Double, newPrice: Double) {
        deleteLatestTransaction(ticker, action)
        addTransaction(ticker, action, newShares, newPrice)
    }
}
