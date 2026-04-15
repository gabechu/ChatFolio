package com.chatfolio.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chatfolio.ui.chat.ChatContent
import java.util.Locale

@Composable
fun PortfolioSummaryCard(content: ChatContent.PortfolioSummaryCard) {
    val audFormat = java.text.NumberFormat.getCurrencyInstance(Locale("en", "AU"))
    val usdFormat = java.text.NumberFormat.getCurrencyInstance(Locale("en", "US"))
    val percentFormat =
        java.text.NumberFormat.getPercentInstance().apply {
            minimumFractionDigits = 2
        }

    val isUsd = content.displayCurrency.equals("USD", ignoreCase = true)

    val totalValue = if (isUsd) content.totalValueUsd else content.totalValueAud
    val totalInvested = if (isUsd) content.totalInvestedUsd else content.totalInvestedAud
    val gainLoss = totalValue - totalInvested
    val gainLossPercent = if (totalInvested != 0.0) gainLoss / totalInvested else 0.0
    val format = if (isUsd) usdFormat else audFormat
    val currencyStr = if (isUsd) "USD" else "AUD"

    // Material colors adapted for basic P&L
    val profitColor = if (gainLoss >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
    val profitSign = if (gainLoss >= 0) "+" else ""

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Global Portfolio Valuation ($currencyStr)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    text = format.format(totalValue),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(
                        text = "Total Invested",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = format.format(totalInvested),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Total Profit/Loss",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "$profitSign${format.format(gainLoss)} ($profitSign${percentFormat.format(gainLossPercent)})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = profitColor,
                    )
                }
            }
        }
    }
}
