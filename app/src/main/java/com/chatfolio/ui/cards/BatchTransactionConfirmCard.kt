package com.chatfolio.ui.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.chatfolio.ui.chat.ChatContent

@Composable
fun BatchTransactionConfirmCard(
    content: ChatContent.BatchTransactionConfirmCard,
    onSave: () -> Unit = {},
    onCancel: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Pending Trades (${content.trades.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            content.trades.forEach { trade ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val isBuy = trade.action.equals("BUY", ignoreCase = true)
                    val actionColor = if (isBuy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    
                    Text(
                        text = "${if (isBuy) "🟢 BUY" else "🔴 SELL"} ${trade.shares} ${trade.ticker}",
                        fontWeight = FontWeight.Bold,
                        color = actionColor
                    )
                    Text(text = "@ $${trade.price}")
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text("Cancel")
                }
                Button(onClick = onSave) {
                    Text("Save All Trades")
                }
            }
        }
    }
}
