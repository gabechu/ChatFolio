package com.chatfolio.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatfolio.data.repository.PortfolioRepository
import com.chatfolio.data.repository.SettingsRepository
import com.chatfolio.domain.usecase.ChatBotAgent
import com.chatfolio.domain.usecase.ChatInteractionResult
import com.chatfolio.domain.usecase.PortfolioManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatContent> = emptyList(),
    val isTyping: Boolean = false
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val portfolioRepository: PortfolioRepository,
    private val chatBotAgent: ChatBotAgent,
    private val settingsRepository: SettingsRepository,
    private val portfolioManager: PortfolioManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val _apiKey = MutableStateFlow(settingsRepository.getGeminiApiKey() ?: "")
    val apiKey: StateFlow<String> = _apiKey.asStateFlow()

    fun saveApiKey(key: String) {
        settingsRepository.saveGeminiApiKey(key)
        _apiKey.value = key
    }

    fun sendMessage(messageText: String) {
        val currentMessages = _uiState.value.messages.toMutableList()
        currentMessages.add(ChatContent.Text(markdown = messageText, isUser = true))
        _uiState.value = _uiState.value.copy(messages = currentMessages, isTyping = true)
        
        viewModelScope.launch {
            try {
                // Determine conversation history from UI State
                val history = _uiState.value.messages.mapNotNull { content ->
                    if (content is ChatContent.Text) {
                        com.chatfolio.domain.port.ChatMessage(
                            role = if (content.isUser) "user" else "model",
                            content = content.markdown
                        )
                    } else null
                }

                // Send the message to the AI via our Domain Agent
                val result = chatBotAgent.sendMessage(messageText, history)
                
                // Update UI with response
                val newMessages = _uiState.value.messages.toMutableList()
                
                when (result) {
                    is ChatInteractionResult.TextReply -> {
                        newMessages.add(ChatContent.Text(markdown = result.text, isUser = false))
                    }
                    is ChatInteractionResult.ParsedTransactions -> {
                        newMessages.add(
                            ChatContent.BatchTransactionConfirmCard(
                                trades = result.trades
                            )
                        )
                    }
                    is ChatInteractionResult.Error -> {
                        newMessages.add(ChatContent.Text(markdown = "System: Error - ${result.message}", isUser = false))
                    }
                    is ChatInteractionResult.ShowPortfolio -> {
                        val liveHoldings = portfolioManager.getLiveHoldings()
                        if (liveHoldings.isEmpty()) {
                            newMessages.add(ChatContent.Text(markdown = "Looks like your portfolio is currently empty. Try saving a trade first!", isUser = false))
                        } else {
                            val totalMarketValue = liveHoldings.sumOf { it.marketValue }
                            val totalCostBase = liveHoldings.sumOf { it.costBase }
                            val totalGainLoss = totalMarketValue - totalCostBase
                            val totalGainLossPercent = if (totalCostBase != 0.0) (totalGainLoss / totalCostBase) * 100.0 else 0.0
                            newMessages.add(
                                ChatContent.PortfolioSummaryCard(
                                    totalValue = totalMarketValue,
                                    dailyChangeValue = totalGainLoss,
                                    dailyChangePercent = totalGainLossPercent
                                )
                            )
                            val holdingsList = liveHoldings.joinToString("\n") {
                                val gainSign = if (it.gainLoss >= 0) "+" else ""
                                "- **${it.ticker}**: ${it.totalShares} shares @ ${it.currency} ${String.format("%.2f", it.currentPrice)} — $gainSign${String.format("%.2f", it.gainLossPercent)}% (as of ${it.priceAsOf})"
                            }
                            newMessages.add(ChatContent.Text(markdown = "Here's your current portfolio:\n$holdingsList", isUser = false))
                        }
                    }
                    is ChatInteractionResult.DeleteTransaction -> {
                        portfolioRepository.deleteLatestTransaction(result.ticker, result.action)
                        newMessages.add(ChatContent.Text(markdown = "🗑️ Successfully **deleted** your latest ${result.action} of ${result.ticker}.", isUser = false))
                    }
                    is ChatInteractionResult.UpdateTransaction -> {
                        portfolioRepository.updateLatestTransaction(result.ticker, result.action, result.newShares, result.newPrice)
                        newMessages.add(ChatContent.Text(markdown = "✏️ Successfully **updated** your latest ${result.action} of ${result.ticker} to ${result.newShares} shares at $${result.newPrice}.", isUser = false))
                    }
                }
                
                _uiState.value = _uiState.value.copy(messages = newMessages, isTyping = false)
            } catch (e: Exception) {
                val errorMessages = _uiState.value.messages.toMutableList()
                errorMessages.add(ChatContent.Text(markdown = "System: Error - ${e.message}", isUser = false))
                _uiState.value = _uiState.value.copy(messages = errorMessages, isTyping = false)
            }
        }
    }

    fun saveTransactions(trades: List<com.chatfolio.domain.usecase.ChatInteractionResult.ParsedTrade>) {
        viewModelScope.launch {
            try {
                // Delegate to the domain layer — ChatBotAgent owns the full trade lifecycle
                chatBotAgent.persistTrades(trades)

                val newMessages = _uiState.value.messages.toMutableList()
                newMessages.add(ChatContent.Text(markdown = "✅ Successfully saved **${trades.size}** trades.", isUser = false))
                _uiState.value = _uiState.value.copy(messages = newMessages)
            } catch (e: Exception) {
                val errorMessages = _uiState.value.messages.toMutableList()
                errorMessages.add(ChatContent.Text(markdown = "Error saving transactions: ${e.message}", isUser = false))
                _uiState.value = _uiState.value.copy(messages = errorMessages)
            }
        }
    }
}
