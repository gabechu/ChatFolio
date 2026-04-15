package com.chatfolio.domain.usecase

import com.chatfolio.data.repository.MarketDataRepository
import com.chatfolio.domain.port.ToolCall

/**
 * Extracts multiple 'addTransaction' ToolCalls into a standardized Domain representation.
 */
suspend fun List<ToolCall>.parseAddTransactionCalls(marketDataRepository: MarketDataRepository): ChatInteractionResult.ParsedTransactions {
    val parsedTrades =
        this.map {
            val parsedTicker = it.arguments["ticker"]?.toString() ?: "UNKNOWN"
            val parsedCurrency = it.arguments["currency"]?.toString()

            val resolvedCurrency =
                if (parsedCurrency.isNullOrBlank() && parsedTicker != "UNKNOWN") {
                    marketDataRepository.getLatestPrice(parsedTicker).currency
                } else {
                    parsedCurrency ?: "AUD"
                }

            ChatInteractionResult.ParsedTrade(
                ticker = parsedTicker,
                action = it.arguments["action"]?.toString() ?: "BUY",
                shares = it.arguments["shares"]?.toString()?.toDoubleOrNull() ?: 0.0,
                price = it.arguments["price"]?.toString()?.toDoubleOrNull() ?: 0.0,
                date = parseIsoDate(it.arguments["date"]?.toString()),
                currency = resolvedCurrency,
            )
        }
    return ChatInteractionResult.ParsedTransactions(parsedTrades)
}

/**
 * Extracts the single 'showPortfolio' ToolCall into a standardized Domain representation.
 */
fun ToolCall.parseShowPortfolioCall(): ChatInteractionResult.ShowPortfolio {
    val displayCurrency = this.arguments["currency"]?.toString() ?: "AUD"
    return ChatInteractionResult.ShowPortfolio(displayCurrency)
}

/**
 * Extracts the single 'deleteTransaction' ToolCall into a standardized Domain representation.
 */
fun ToolCall.parseDeleteTransactionCall(): ChatInteractionResult.DeleteTransaction {
    return ChatInteractionResult.DeleteTransaction(
        ticker = this.arguments["ticker"]?.toString() ?: "UNKNOWN",
        action = this.arguments["action"]?.toString() ?: "BUY",
    )
}

/**
 * Extracts the single 'updateTransaction' ToolCall into a standardized Domain representation.
 */
fun ToolCall.parseUpdateTransactionCall(): ChatInteractionResult.UpdateTransaction {
    return ChatInteractionResult.UpdateTransaction(
        ticker = this.arguments["ticker"]?.toString() ?: "UNKNOWN",
        action = this.arguments["action"]?.toString() ?: "BUY",
        newShares = this.arguments["newShares"]?.toString()?.toDoubleOrNull() ?: 0.0,
        newPrice = this.arguments["newPrice"]?.toString()?.toDoubleOrNull() ?: 0.0,
    )
}

/**
 * Helper strictly designed to parse ISO-8601 Strings ("2023-10-01") to Epoch Milliseconds.
 * Returns null gracefully if parsing fails.
 */
private fun parseIsoDate(dateString: String?): Long? {
    if (dateString == null) return null
    return try {
        java.time.LocalDate.parse(dateString).atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond() * 1000
    } catch (e: Exception) {
        null
    }
}
