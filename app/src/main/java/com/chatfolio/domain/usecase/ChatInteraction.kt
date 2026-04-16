package com.chatfolio.domain.usecase

import com.chatfolio.data.repository.MarketDataRepository
import com.chatfolio.data.repository.PortfolioRepository
import com.chatfolio.domain.port.ChatMessage
import com.chatfolio.domain.port.LlmEngine
import com.chatfolio.domain.port.LlmTool
import com.chatfolio.domain.port.LlmToolParameter
import timber.log.Timber
import javax.inject.Inject

/**
 * Represents the outcome of an AI chat interaction in pure business terms,
 * without knowing anything about Jetpack Compose or UI Cards.
 */
sealed class ChatInteractionResult {
    data class TextReply(val text: String) : ChatInteractionResult()

    data class ParsedTrade(
        val ticker: String,
        val action: String,
        val shares: Double,
        val price: Double,
        val date: Long?,
        val currency: String,
    )

    data class ParsedTransactions(val trades: List<ParsedTrade>) : ChatInteractionResult()

    data class DeleteTransaction(val ticker: String, val action: String) : ChatInteractionResult()

    data class UpdateTransaction(val ticker: String, val action: String, val newShares: Double, val newPrice: Double) : ChatInteractionResult()

    data class ShowPortfolio(val displayCurrency: String) : ChatInteractionResult()

    data class Error(val message: String) : ChatInteractionResult()
}

/**
 * The Domain Layer orchestrator responsible for defining AI tools
 * and routing raw AI decisions into clean business objects.
 */
class ChatInteraction
    @Inject
    constructor(
        private val llmEngine: LlmEngine,
        private val portfolioRepository: PortfolioRepository,
        private val marketDataRepository: MarketDataRepository,
    ) {
        companion object {
            private const val TOOL_ADD_TRANSACTION = "addTransaction"
            private const val TOOL_SHOW_PORTFOLIO = "showPortfolio"
            private const val TOOL_DELETE_TRANSACTION = "deleteTransaction"
            private const val TOOL_UPDATE_TRANSACTION = "updateTransaction"
        }

        private val addTransactionTool =
            LlmTool(
                name = TOOL_ADD_TRANSACTION,
                description =
                    "Extracts a financial transaction. IMPORTANT: If the user mentions multiple trades, " +
                        "you MUST call this tool multiple times in parallel to extract every single trade.",
                parameters =
                    listOf(
                        LlmToolParameter("ticker", "STRING", "The stock ticker symbol (e.g. AAPL)"),
                        LlmToolParameter("action", "STRING", "Must be 'BUY' or 'SELL'"),
                        LlmToolParameter("shares", "NUMBER", "The number of shares"),
                        LlmToolParameter("price", "NUMBER", "The price per share"),
                        LlmToolParameter("date", "STRING", "Optional ISO-8601 date of the transaction (e.g. 2023-10-01) if in the past"),
                        LlmToolParameter("currency", "STRING", "The currency of the transaction. Omit if unknown or unmentioned."),
                    ),
            )

        private val showPortfolioTool =
            LlmTool(
                name = TOOL_SHOW_PORTFOLIO,
                description = "Displays the user's current stock holdings and portfolio summary.",
                parameters =
                    listOf(
                        LlmToolParameter("currency", "STRING", "The currency to display the portfolio in. Default to 'AUD' if omit."),
                    ),
            )

        private val deleteTransactionTool =
            LlmTool(
                name = TOOL_DELETE_TRANSACTION,
                description = "Deletes the most recent transaction matching a ticker and action (BUY/SELL)",
                parameters =
                    listOf(
                        LlmToolParameter("ticker", "STRING", "The stock ticker to delete"),
                        LlmToolParameter("action", "STRING", "Must be 'BUY' or 'SELL'"),
                    ),
            )

        private val updateTransactionTool =
            LlmTool(
                name = TOOL_UPDATE_TRANSACTION,
                description = "Updates the shares and price of the most recent transaction for a ticker",
                parameters =
                    listOf(
                        LlmToolParameter("ticker", "STRING", "The stock ticker to update"),
                        LlmToolParameter("action", "STRING", "Must be 'BUY' or 'SELL'"),
                        LlmToolParameter("newShares", "NUMBER", "The corrected number of shares"),
                        LlmToolParameter("newPrice", "NUMBER", "The corrected price per share"),
                    ),
            )

        suspend fun sendMessage(
            messageText: String,
            conversationHistory: List<ChatMessage>,
        ): ChatInteractionResult {
            try {
                val fullHistory = conversationHistory + ChatMessage("user", messageText)

                val response =
                    llmEngine.sendMessage(
                        messages = fullHistory,
                        tools = listOf(addTransactionTool, showPortfolioTool, deleteTransactionTool, updateTransactionTool),
                    )

                val toolCalls = response.toolCalls

                // Route traffic directly to our isolated central ChatToolParsers

                val transactionCalls = toolCalls?.filter { it.name == TOOL_ADD_TRANSACTION }
                if (!transactionCalls.isNullOrEmpty()) {
                    return transactionCalls.parseAddTransactionCalls(marketDataRepository)
                }

                toolCalls?.firstOrNull { it.name == TOOL_SHOW_PORTFOLIO }?.let {
                    return it.parseShowPortfolioCall()
                }

                toolCalls?.firstOrNull { it.name == TOOL_DELETE_TRANSACTION }?.let {
                    return it.parseDeleteTransactionCall()
                }

                toolCalls?.firstOrNull { it.name == TOOL_UPDATE_TRANSACTION }?.let {
                    return it.parseUpdateTransactionCall()
                }

                // Standard text reply buffer
                return ChatInteractionResult.TextReply(response.textResponse ?: "")
            } catch (e: Exception) {
                Timber.e(e, "Error communicating with LLM")
                return ChatInteractionResult.Error(e.message ?: "Unknown error occurred")
            }
        }

        suspend fun persistTrades(trades: List<ChatInteractionResult.ParsedTrade>) {
            trades.forEach { trade ->
                portfolioRepository.addTransaction(
                    ticker = trade.ticker,
                    action = trade.action,
                    shares = trade.shares,
                    price = trade.price,
                    date = trade.date ?: System.currentTimeMillis(),
                    currency = trade.currency,
                )
            }
        }
    }
