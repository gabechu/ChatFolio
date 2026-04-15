package com.chatfolio.ui.cards

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chatfolio.ui.chat.ChatContent
import java.util.Locale

@Composable
fun PortfolioSummaryCard(content: ChatContent.PortfolioSummaryCard) {
    val audFormat = java.text.NumberFormat.getCurrencyInstance(Locale("en", "AU"))
    val usdFormat = java.text.NumberFormat.getCurrencyInstance(Locale("en", "US"))

    val isUsd = content.displayCurrency.equals("USD", ignoreCase = true)

    val totalValue = if (isUsd) content.totalValueUsd else content.totalValueAud
    val totalInvested = if (isUsd) content.totalInvestedUsd else content.totalInvestedAud
    val format = if (isUsd) usdFormat else audFormat
    val currencyStr = if (isUsd) "USD" else "AUD"

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
        }
    }
}
