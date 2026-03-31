package com.chatfolio.ui.chat

/**
 * Represents the structured content that can be rendered in the chat stream.
 */
sealed class ChatContent {
    data class Text(
        val markdown: String,
        val isUser: Boolean,
    ) : ChatContent()

    data class PortfolioSummaryCard(
        val totalValue: Double,
        val dailyChangeValue: Double,
        val dailyChangePercent: Double,
    ) : ChatContent()

    data class BatchTransactionConfirmCard(
        val trades: List<com.chatfolio.domain.usecase.ChatInteractionResult.ParsedTrade>,
    ) : ChatContent()
}
