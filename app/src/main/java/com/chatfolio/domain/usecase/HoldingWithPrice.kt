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
    val priceAsOf: Long  // epoch millis of the cached price
)
