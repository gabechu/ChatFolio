package com.chatfolio.domain.usecase

data class HoldingWithPrice(
    val ticker: String,
    val market: String,
    val totalShares: Double,
    val costBase: Double,
    val currentPrice: Double,
    val currency: String,
    val marketValue: Double,
    val gainLoss: Double,
    val gainLossPercent: Double,
    val priceAsOf: String  // ISO date string, e.g. "2026-03-27"
)
