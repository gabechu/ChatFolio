package com.chatfolio.data.repository

import androidx.room.withTransaction
import com.chatfolio.data.local.dao.HoldingDao
import com.chatfolio.data.local.dao.PortfolioDao
import com.chatfolio.data.local.dao.TransactionDao
import com.chatfolio.data.local.entity.PortfolioEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PortfolioRepository
    @Inject
    constructor(
        private val db: com.chatfolio.data.local.AppDatabase,
        private val portfolioDao: PortfolioDao,
        private val holdingDao: HoldingDao,
        private val transactionDao: TransactionDao,
    ) {
        fun getAllPortfolios(): Flow<List<PortfolioEntity>> = portfolioDao.getAllPortfolios()

        fun getGlobalLedger(): Flow<List<com.chatfolio.data.local.entity.TransactionWithTicker>> = transactionDao.getGlobalLedger()

        suspend fun searchTransactions(
            ticker: String?,
            startDate: Long?,
            endDate: Long?,
        ): List<com.chatfolio.data.local.entity.TransactionWithTicker> {
            return transactionDao.searchTransactions(ticker?.uppercase(), startDate, endDate)
        }

        suspend fun createPortfolio(name: String) {
            portfolioDao.insertPortfolio(PortfolioEntity(name = name))
        }

        suspend fun getHoldingsSnapshot(): List<com.chatfolio.data.local.entity.HoldingEntity> {
            val portfolio = portfolioDao.getDefaultPortfolio() ?: return emptyList()
            return holdingDao.getHoldingsSnapshot(portfolio.id)
        }

        suspend fun addTransaction(
            ticker: String,
            action: String,
            shares: Double,
            price: Double,
            date: Long = System.currentTimeMillis(),
            currency: String = "AUD",
        ) {
            db.withTransaction {
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
                    holdingDao.insertHolding(
                        com.chatfolio.data.local.entity.HoldingEntity(
                            portfolioId = portfolio.id,
                            ticker = upperTicker,
                            market = "US",
                            totalShares = 0.0,
                            costBase = 0.0,
                            realizedProfit = 0.0,
                            currency = currency,
                        ),
                    )
                    holding = holdingDao.getHoldingByTicker(portfolio.id, upperTicker) ?: return@withTransaction
                }

                val isBuy = action.equals("BUY", ignoreCase = true)
                // 3. Insert transaction record
                transactionDao.insertTransaction(
                    com.chatfolio.data.local.entity.TransactionEntity(
                        holdingId = holding.id,
                        type = if (isBuy) "BUY" else "SELL",
                        date = date,
                        shares = shares,
                        pricePerShare = price,
                        totalValue = shares * price,
                        currency = currency,
                        source = "CHAT",
                    ),
                )

                // 4. Update holding via Ledger Replay
                recalculateHoldingState(holding)
            }
        }

        suspend fun deleteLatestTransaction(
            ticker: String,
            action: String,
        ) {
            db.withTransaction {
                val portfolio = portfolioDao.getDefaultPortfolio() ?: return@withTransaction
                val holding = holdingDao.getHoldingByTicker(portfolio.id, ticker.uppercase()) ?: return@withTransaction

                val transaction = transactionDao.getLatestTransaction(holding.id, action.uppercase()) ?: return@withTransaction

                transactionDao.deleteTransaction(transaction)

                recalculateHoldingState(holding)
            }
        }

        suspend fun updateLatestTransaction(
            ticker: String,
            action: String,
            newShares: Double,
            newPrice: Double,
        ) {
            db.withTransaction {
                val portfolio = portfolioDao.getDefaultPortfolio() ?: return@withTransaction
                val holding = holdingDao.getHoldingByTicker(portfolio.id, ticker.uppercase()) ?: return@withTransaction
                val transaction = transactionDao.getLatestTransaction(holding.id, action.uppercase()) ?: return@withTransaction

                transactionDao.updateTransaction(
                    transaction.copy(
                        shares = newShares,
                        pricePerShare = newPrice,
                        totalValue = newShares * newPrice,
                    ),
                )

                // Trigger domain recalculation
                recalculateHoldingState(holding)
            }
        }

        suspend fun deleteTransaction(transaction: com.chatfolio.data.local.entity.TransactionEntity) {
            db.withTransaction {
                val holding = holdingDao.getHoldingById(transaction.holdingId) ?: return@withTransaction
                transactionDao.deleteTransaction(transaction)
                recalculateHoldingState(holding)
            }
        }

        suspend fun updateTransaction(transaction: com.chatfolio.data.local.entity.TransactionEntity) {
            db.withTransaction {
                val holding = holdingDao.getHoldingById(transaction.holdingId) ?: return@withTransaction
                transactionDao.updateTransaction(transaction)
                recalculateHoldingState(holding)
            }
        }

        private suspend fun recalculateHoldingState(holding: com.chatfolio.data.local.entity.HoldingEntity) {
            val allTransactions = transactionDao.getAllTransactionsAsc(holding.id)

            // Map to domain abstraction
            val domainTrades =
                allTransactions.map {
                    com.chatfolio.domain.usecase.TradeRecord(
                        type = it.type,
                        shares = it.shares,
                        price = it.pricePerShare,
                        date = it.date,
                    )
                }

            // Pure domain calculation
            val calculatedState = com.chatfolio.domain.usecase.PortfolioCalculator.calculate(domainTrades)

            // Persist derived totals
            holdingDao.updateHolding(
                holding.copy(
                    totalShares = calculatedState.totalShares,
                    costBase = calculatedState.costBase,
                    realizedProfit = calculatedState.realizedProfit,
                ),
            )
        }
    }
