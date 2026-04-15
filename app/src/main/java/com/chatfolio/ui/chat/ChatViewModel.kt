package com.chatfolio.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatfolio.data.repository.PortfolioRepository
import com.chatfolio.data.repository.SettingsRepository
import com.chatfolio.domain.usecase.ChatBotAgent
import com.chatfolio.domain.usecase.ChatInteractionResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatContent> = emptyList(),
    val isTyping: Boolean = false,
)

@HiltViewModel
class ChatViewModel
    @Inject
    constructor(
        private val portfolioRepository: PortfolioRepository,
        private val chatBotAgent: ChatBotAgent,
        private val settingsRepository: SettingsRepository,
        private val portfolioManager: com.chatfolio.domain.usecase.PortfolioManager,
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
                    val history =
                        _uiState.value.messages.mapNotNull { content ->
                            if (content is ChatContent.Text) {
                                com.chatfolio.domain.port.ChatMessage(
                                    role = if (content.isUser) "user" else "model",
                                    content = content.markdown,
                                )
                            } else {
                                null
                            }
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
                                    trades = result.trades,
                                ),
                            )
                        }
                        is ChatInteractionResult.Error -> {
                            newMessages.add(ChatContent.Text(markdown = "System: Error - ${result.message}", isUser = false))
                        }
                        is ChatInteractionResult.ShowPortfolio -> {
                            val liveHoldings = portfolioManager.getLiveHoldings()
                            if (liveHoldings.isEmpty()) {
                                newMessages.add(
                                    ChatContent.Text(
                                        markdown = "Looks like your portfolio is currently empty. Try saving a trade first!",
                                        isUser = false,
                                    ),
                                )
                            } else {
                                val globalSummary = portfolioManager.getGlobalSummary(result.displayCurrency)
                                newMessages.add(
                                    ChatContent.PortfolioSummaryCard(
                                        totalValue = globalSummary.totalValue,
                                        totalInvested = globalSummary.totalInvested,
                                        displayCurrency = result.displayCurrency,
                                    ),
                                )
                                val holdingsList =
                                    liveHoldings.joinToString("\n") {
                                        val profitStr =
                                            if (it.gainLoss >= 0) {
                                                "+$${String.format(
                                                    java.util.Locale.US,
                                                    "%.2f",
                                                    it.gainLoss,
                                                )}"
                                            } else {
                                                "-$${String.format(java.util.Locale.US, "%.2f", -it.gainLoss)}"
                                            }
                                        "- **${it.ticker}**: ${it.totalShares} shares @ $${String.format(java.util.Locale.US, "%.2f", it.currentPrice)} (P&L: $profitStr)"
                                    }
                                newMessages.add(
                                    ChatContent.Text(markdown = "Here is what you are currently holding:\n$holdingsList", isUser = false),
                                )
                            }
                        }
                        is ChatInteractionResult.DeleteTransaction -> {
                            portfolioRepository.deleteLatestTransaction(result.ticker, result.action)
                            newMessages.add(
                                ChatContent.Text(
                                    markdown = "🗑️ Successfully **deleted** your latest ${result.action} of ${result.ticker}.",
                                    isUser = false,
                                ),
                            )
                        }
                        is ChatInteractionResult.UpdateTransaction -> {
                            portfolioRepository.updateLatestTransaction(result.ticker, result.action, result.newShares, result.newPrice)
                            newMessages.add(
                                ChatContent.Text(
                                    markdown = "✏️ Successfully **updated** your latest ${result.action} of ${result.ticker} to ${result.newShares} shares at $${result.newPrice}.",
                                    isUser = false,
                                ),
                            )
                        }
                    }

                    _uiState.value = _uiState.value.copy(messages = newMessages, isTyping = false)
                } catch (e: Exception) {
                    Timber.e(e, "Error processing message")
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
                    Timber.e(e, "Error saving transactions")
                    val errorMessages = _uiState.value.messages.toMutableList()
                    errorMessages.add(ChatContent.Text(markdown = "Error saving transactions: ${e.message}", isUser = false))
                    _uiState.value = _uiState.value.copy(messages = errorMessages)
                }
            }
        }
    }
