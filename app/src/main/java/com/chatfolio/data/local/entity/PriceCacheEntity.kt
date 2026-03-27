package com.chatfolio.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "price_cache",
    primaryKeys = ["ticker", "date"]
)
data class PriceCacheEntity(
    val ticker: String,
    val date: Long, // EOD timestamp
    val closePrice: Double,
    val currency: String
)
