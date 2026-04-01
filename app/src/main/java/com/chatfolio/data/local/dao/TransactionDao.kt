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
