package com.chatfolio.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatfolio.data.repository.PortfolioRepository
import com.chatfolio.data.repository.SettingsRepository
import com.chatfolio.domain.usecase.ChatInteraction
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
        private val chatInteraction: ChatInteraction,
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
            // Snapshot the history BEFORE appending the active query to the UI state
            val history = _uiState.value.messages.toLlmHistory()
            appendUserMessage(messageText)

            viewModelScope.launch {
                try {
                    val results = chatInteraction.sendMessage(messageText, history)

                    val uiCards = processInteractionResults(results)
                    appendBotMessages(uiCards)
                } catch (e: Exception) {
                    handleError(e)
                }
            }
        }

        private fun appendUserMessage(text: String) {
            val currentMessages = _uiState.value.messages.toMutableList()
            currentMessages.add(ChatContent.Text(markdown = text, isUser = true))
            _uiState.value = _uiState.value.copy(messages = currentMessages, isTyping = true)
        }

        private fun appendBotMessages(cards: List<ChatContent>) {
            val newMessages = _uiState.value.messages.toMutableList()
            newMessages.addAll(cards)
            _uiState.value = _uiState.value.copy(messages = newMessages, isTyping = false)
        }

        private fun handleError(e: Exception) {
            Timber.e(e, "Error processing message")
            val errorMessages = _uiState.value.messages.toMutableList()
            errorMessages.add(ChatContent.Text(markdown = "System: Error - ${e.message}", isUser = false))
            _uiState.value = _uiState.value.copy(messages = errorMessages, isTyping = false)
        }

        private fun List<ChatContent>.toLlmHistory(): List<com.chatfolio.domain.port.ChatMessage> {
            return this.mapNotNull { content ->
                when (content) {
                    is ChatContent.Text -> {
                        com.chatfolio.domain.port.ChatMessage(
                            role = if (content.isUser) com.chatfolio.domain.port.ChatRole.USER else com.chatfolio.domain.port.ChatRole.ASSISTANT,
                            content = content.markdown,
                        )
                    }
                    is ChatContent.BatchTransactionConfirmCard -> {
                        val tradesString = content.trades.joinToString { "${it.action} ${it.shares} ${it.ticker}" }
                        com.chatfolio.domain.port.ChatMessage(
                            role = com.chatfolio.domain.port.ChatRole.ASSISTANT,
                            content =
                                "I have presented a confirmation card for the following pending trades: " +
                                    "$tradesString. Awaiting user action.",
                        )
                    }
                    else -> null
                }
            }
        }

        private suspend fun processInteractionResults(results: List<ChatInteractionResult>): List<ChatContent> {
            val generatedCards = mutableListOf<ChatContent>()

            results.forEach { result ->
                when (result) {
                    is ChatInteractionResult.TextReply -> {
                        generatedCards.add(ChatContent.Text(markdown = result.text, isUser = false))
                    }
                    is ChatInteractionResult.ParsedTransactions -> {
                        generatedCards.add(
                            ChatContent.BatchTransactionConfirmCard(
                                trades = result.trades,
                            ),
                        )
                    }
                    is ChatInteractionResult.Error -> {
                        generatedCards.add(ChatContent.Text(markdown = "System: Error - ${result.message}", isUser = false))
                    }
                    is ChatInteractionResult.ShowPortfolio -> {
                        val liveHoldings = portfolioManager.getLiveHoldings()
                        if (liveHoldings.isEmpty()) {
                            generatedCards.add(
                                ChatContent.Text(
                                    markdown = "Looks like your portfolio is currently empty. Try saving a trade first!",
                                    isUser = false,
                                ),
                            )
                        } else {
                            val globalSummary = portfolioManager.getGlobalSummary(result.displayCurrency)
                            generatedCards.add(
                                ChatContent.PortfolioSummaryCard(
                                    totalValue = globalSummary.totalValue,
                                    totalInvested = globalSummary.totalInvested,
                                    displayCurrency = result.displayCurrency,
                                ),
                            )
                            generatedCards.add(
                                ChatContent.HoldingsTableCard(liveHoldings = liveHoldings),
                            )
                        }
                    }
                    is ChatInteractionResult.DeleteTransaction -> {
                        portfolioRepository.deleteLatestTransaction(result.ticker, result.action)
                        generatedCards.add(
                            ChatContent.Text(
                                markdown = "🗑️ Successfully **deleted** your latest ${result.action} of ${result.ticker}.",
                                isUser = false,
                            ),
                        )
                    }
                    is ChatInteractionResult.UpdateTransaction -> {
                        portfolioRepository.updateLatestTransaction(result.ticker, result.action, result.newShares, result.newPrice)
                        generatedCards.add(
                            ChatContent.Text(
                                markdown =
                                    "✏️ Successfully **updated** your latest ${result.action} of ${result.ticker} " +
                                        "to ${result.newShares} shares at $${result.newPrice}.",
                                isUser = false,
                            ),
                        )
                    }
                }
            }
            return generatedCards
        }

        fun saveTransactions(trades: List<com.chatfolio.domain.usecase.ChatInteractionResult.ParsedTrade>) {
            viewModelScope.launch {
                try {
                    // Delegate to the domain layer — ChatInteraction owns the full trade lifecycle
                    chatInteraction.persistTrades(trades)
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

        fun cancelTransactions() {
            val currentMessages = _uiState.value.messages.toMutableList()
            currentMessages.add(ChatContent.Text(markdown = "❌ Cancelled pending trades.", isUser = true))
            _uiState.value = _uiState.value.copy(messages = currentMessages)
        }
    }
