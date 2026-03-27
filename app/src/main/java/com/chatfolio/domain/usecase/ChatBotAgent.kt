package com.chatfolio.domain.usecase

import com.chatfolio.domain.port.ChatMessage
import com.chatfolio.domain.port.LlmEngine
import com.chatfolio.domain.port.LlmTool
import com.chatfolio.domain.port.LlmToolParameter
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
        val price: Double
    )

    data class ParsedTransactions(val trades: List<ParsedTrade>) : ChatInteractionResult()
    
    data class DeleteTransaction(val ticker: String, val action: String) : ChatInteractionResult()
    data class UpdateTransaction(val ticker: String, val action: String, val newShares: Double, val newPrice: Double) : ChatInteractionResult()
    
    object ShowPortfolio : ChatInteractionResult()
    
    data class Error(val message: String) : ChatInteractionResult()
}

/**
 * The Domain Layer agent responsible for giving the LLM its tools 
 * and translating its raw responses into business outcomes.
 */
class ChatBotAgent @Inject constructor(
    private val llmEngine: LlmEngine
) {

    private val addTransactionTool = LlmTool(
        name = "addTransaction",
        description = "Extracts a financial transaction. IMPORTANT: If the user mentions multiple trades, you MUST call this tool multiple times in parallel to extract every single trade.",
        parameters = listOf(
            LlmToolParameter("ticker", "STRING", "The stock ticker symbol (e.g. AAPL)"),
            LlmToolParameter("action", "STRING", "Must be 'BUY' or 'SELL'"),
            LlmToolParameter("shares", "NUMBER", "The number of shares"),
            LlmToolParameter("price", "NUMBER", "The price per share")
        )
    )

    private val showPortfolioTool = LlmTool(
        name = "showPortfolio",
        description = "Displays the user's current stock holdings and portfolio summary.",
        parameters = emptyList()
    )

    private val deleteTransactionTool = LlmTool(
        name = "deleteTransaction",
        description = "Deletes the most recent transaction matching a ticker and action (BUY/SELL)",
        parameters = listOf(
            LlmToolParameter("ticker", "STRING", "The stock ticker to delete"),
            LlmToolParameter("action", "STRING", "Must be 'BUY' or 'SELL'")
        )
    )

    private val updateTransactionTool = LlmTool(
        name = "updateTransaction",
        description = "Updates the shares and price of the most recent transaction for a ticker",
        parameters = listOf(
            LlmToolParameter("ticker", "STRING", "The stock ticker to update"),
            LlmToolParameter("action", "STRING", "Must be 'BUY' or 'SELL'"),
            LlmToolParameter("newShares", "NUMBER", "The corrected number of shares"),
            LlmToolParameter("newPrice", "NUMBER", "The corrected price per share")
        )
    )

    suspend fun sendMessage(
        messageText: String, 
        conversationHistory: List<ChatMessage>
    ): ChatInteractionResult {
        try {
            // Append the new message to history
            val fullHistory = conversationHistory + ChatMessage("user", messageText)

            val response = llmEngine.sendMessage(
                messages = fullHistory,
                tools = listOf(addTransactionTool, showPortfolioTool, deleteTransactionTool, updateTransactionTool)
            )

            // See if the AI tried to invoke a tool
            val toolCalls = response.toolCalls
            
            val transactionCalls = toolCalls?.filter { it.name == "addTransaction" }
            if (!transactionCalls.isNullOrEmpty()) {
                val parsedTrades = transactionCalls.map {
                    ChatInteractionResult.ParsedTrade(
                        ticker = it.arguments["ticker"]?.toString() ?: "UNKNOWN",
                        action = it.arguments["action"]?.toString() ?: "BUY",
                        shares = it.arguments["shares"]?.toString()?.toDoubleOrNull() ?: 0.0,
                        price = it.arguments["price"]?.toString()?.toDoubleOrNull() ?: 0.0
                    )
                }
                return ChatInteractionResult.ParsedTransactions(parsedTrades)
            }

            val showPortfolioCall = toolCalls?.firstOrNull { it.name == "showPortfolio" }
            if (showPortfolioCall != null) {
                return ChatInteractionResult.ShowPortfolio
            }

            val deleteCall = toolCalls?.firstOrNull { it.name == "deleteTransaction" }
            if (deleteCall != null) {
                return ChatInteractionResult.DeleteTransaction(
                    ticker = deleteCall.arguments["ticker"]?.toString() ?: "UNKNOWN",
                    action = deleteCall.arguments["action"]?.toString() ?: "BUY"
                )
            }

            val updateCall = toolCalls?.firstOrNull { it.name == "updateTransaction" }
            if (updateCall != null) {
                return ChatInteractionResult.UpdateTransaction(
                    ticker = updateCall.arguments["ticker"]?.toString() ?: "UNKNOWN",
                    action = updateCall.arguments["action"]?.toString() ?: "BUY",
                    newShares = updateCall.arguments["newShares"]?.toString()?.toDoubleOrNull() ?: 0.0,
                    newPrice = updateCall.arguments["newPrice"]?.toString()?.toDoubleOrNull() ?: 0.0
                )
            }

            // Otherwise, it's a standard text reply
            return ChatInteractionResult.TextReply(response.textResponse ?: "")

        } catch (e: Exception) {
            return ChatInteractionResult.Error(e.message ?: "Unknown error occurred")
        }
    }
}
