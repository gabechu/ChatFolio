package com.chatfolio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chatfolio.data.local.entity.PortfolioEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PortfolioDao {
    @Query("SELECT * FROM portfolios")
    fun getAllPortfolios(): Flow<List<PortfolioEntity>>

    @Query("SELECT * FROM portfolios LIMIT 1")
    suspend fun getDefaultPortfolio(): PortfolioEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPortfolio(portfolio: PortfolioEntity): Long
}
