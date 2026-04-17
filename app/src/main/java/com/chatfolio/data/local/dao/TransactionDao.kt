package com.chatfolio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chatfolio.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE holdingId = :holdingId ORDER BY date DESC")
    fun getTransactionsForHolding(holdingId: Int): Flow<List<TransactionEntity>>

    @Query(
        "SELECT transactions.*, holdings.ticker " +
            "FROM transactions INNER JOIN holdings ON transactions.holdingId = holdings.id " +
            "ORDER BY transactions.date DESC",
    )
    fun getGlobalLedger(): Flow<List<com.chatfolio.data.local.entity.TransactionWithTicker>>

    @Query(
        "SELECT transactions.*, holdings.ticker " +
            "FROM transactions INNER JOIN holdings ON transactions.holdingId = holdings.id " +
            "WHERE (:ticker IS NULL OR holdings.ticker = :ticker) " +
            "AND (:startDate IS NULL OR transactions.date >= :startDate) " +
            "AND (:endDate IS NULL OR transactions.date <= :endDate) " +
            "ORDER BY transactions.date DESC",
    )
    suspend fun searchTransactions(
        ticker: String?,
        startDate: Long?,
        endDate: Long?,
    ): List<com.chatfolio.data.local.entity.TransactionWithTicker>

    @Query("SELECT * FROM transactions WHERE holdingId = :holdingId ORDER BY date ASC")
    suspend fun getAllTransactionsAsc(holdingId: Int): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE holdingId = :holdingId AND type = :type ORDER BY date DESC LIMIT 1")
    suspend fun getLatestTransaction(
        holdingId: Int,
        type: String,
    ): TransactionEntity?

    @androidx.room.Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @androidx.room.Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long
}
