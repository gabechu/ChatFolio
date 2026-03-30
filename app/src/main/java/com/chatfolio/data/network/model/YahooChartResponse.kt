package com.chatfolio.data.network.model

import kotlinx.serialization.Serializable

@Serializable
data class YahooChartResponse(
    val chart: Chart? = null
)

@Serializable
data class Chart(
    val result: List<Result>? = null,
    val error: ChartError? = null
)

@Serializable
data class Result(
    val meta: Meta? = null
)

@Serializable
data class Meta(
    val currency: String? = null,
    val symbol: String? = null,
    val regularMarketPrice: Double? = null,
    val regularMarketTime: Long? = null,
    val previousClose: Double? = null
)

@Serializable
data class ChartError(
    val code: String? = null,
    val description: String? = null
)
