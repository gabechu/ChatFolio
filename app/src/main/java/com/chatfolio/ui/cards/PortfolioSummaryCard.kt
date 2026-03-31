package com.chatfolio.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chatfolio.ui.chat.ChatContent
import java.util.Locale

@Composable
fun PortfolioSummaryCard(content: ChatContent.PortfolioSummaryCard) {
    val currencyFormat = java.text.NumberFormat.getCurrencyInstance(Locale("en", "AU"))

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
                text = "Portfolio Summary",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = currencyFormat.format(content.totalValue),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )

            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val isPositive = content.dailyChangeValue >= 0
                val color = if (isPositive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                val sign = if (isPositive) "+" else ""

                Text(
                    text = "$sign${currencyFormat.format(content.dailyChangeValue)}",
                    color = color,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = "($sign${String.format("%.2f", content.dailyChangePercent)}%)",
                    color = color,
                )
            }

            // TODO: Integrate Vico Chart here in Phase 3
        }
    }
}
