package com.chatfolio.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "price_cache")
data class PriceCacheEntity(
    @PrimaryKey
    val ticker: String,
    // ISO date from Yahoo's regularMarketTime e.g. "2026-03-27"
    val tradingDate: String,
    val closePrice: Double,
    val currency: String,
)
