package com.chatfolio.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chatfolio.data.local.entity.PriceCacheEntity

@Dao
interface PriceCacheDao {
    @Query("SELECT * FROM price_cache WHERE ticker = :ticker ORDER BY tradingDate DESC LIMIT 1")
    suspend fun getLatestPrice(ticker: String): PriceCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPrice(priceCache: PriceCacheEntity)
}
