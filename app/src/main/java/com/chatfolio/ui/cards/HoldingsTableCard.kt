package com.chatfolio.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chatfolio.domain.usecase.HoldingWithPrice
import java.text.NumberFormat
import java.util.Locale

@Composable
fun HoldingsTableCard(
    liveHoldings: List<HoldingWithPrice>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Current Holdings",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))

        liveHoldings.forEach { holding ->
            HoldingRow(holding)
            Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f))
        }
    }
}

@Composable
private fun HoldingRow(holding: HoldingWithPrice) {
    // Determine target locale safely
    val targetLocale =
        try {
            Locale.US // Default to standard US format to avoid string crashes
        } catch (e: Exception) {
            Locale.US
        }

    val currencyFormatter = NumberFormat.getCurrencyInstance(targetLocale)

    val profitColor = if (holding.gainLoss >= 0) Color(0xFF4CAF50) else Color(0xFFF44336)
    val profitPrefix = if (holding.gainLoss >= 0) "+" else ""

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = holding.ticker,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${holding.totalShares} shares @ ${currencyFormatter.format(holding.currentPrice)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }

        Column(horizontalAlignment = androidx.compose.ui.Alignment.End) {
            Text(
                text = currencyFormatter.format(holding.marketValue),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "$profitPrefix${currencyFormatter.format(holding.gainLoss)}",
                style = MaterialTheme.typography.bodySmall,
                color = profitColor,
            )
        }
    }
}
