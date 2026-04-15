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
        val totalInvested: Double,
        val displayCurrency: String,
    ) : ChatContent()

    data class BatchTransactionConfirmCard(
        val trades: List<com.chatfolio.domain.usecase.ChatInteractionResult.ParsedTrade>,
    ) : ChatContent()

    data class HoldingsTableCard(
        val liveHoldings: List<com.chatfolio.domain.usecase.HoldingWithPrice>,
    ) : ChatContent()
}
