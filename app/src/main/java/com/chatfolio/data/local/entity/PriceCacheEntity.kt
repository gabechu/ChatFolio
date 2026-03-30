package com.chatfolio.data.local.entity

import androidx.room.Entity

@Entity(
    tableName = "price_cache",
    primaryKeys = ["ticker", "tradingDate"]
)
data class PriceCacheEntity(
    val ticker: String,
    val tradingDate: String, // ISO date string from Yahoo regularMarketTime, e.g. "2026-03-27"
    val closePrice: Double,
    val currency: String
)
