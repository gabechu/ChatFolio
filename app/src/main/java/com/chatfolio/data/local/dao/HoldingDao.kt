package com.chatfolio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.chatfolio.data.local.entity.HoldingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HoldingDao {
    @Query("SELECT * FROM holdings WHERE portfolioId = :portfolioId")
    fun getHoldingsForPortfolio(portfolioId: Int): Flow<List<HoldingEntity>>

    @Query("SELECT * FROM holdings WHERE portfolioId = :portfolioId")
    suspend fun getHoldingsSnapshot(portfolioId: Int): List<HoldingEntity>

    @Query("SELECT * FROM holdings WHERE portfolioId = :portfolioId AND ticker = :ticker LIMIT 1")
    suspend fun getHoldingByTicker(portfolioId: Int, ticker: String): HoldingEntity?

    @androidx.room.Update
    suspend fun updateHolding(holding: HoldingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHolding(holding: HoldingEntity): Long
}
