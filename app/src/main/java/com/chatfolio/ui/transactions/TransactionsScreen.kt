package com.chatfolio.ui.transactions

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.chatfolio.data.local.entity.TransactionWithTicker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionsScreen(
    modifier: Modifier = Modifier,
    viewModel: TransactionsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
    ) {
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (uiState.transactions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No transactions found. Chat with the AI to add some!",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
                items(uiState.transactions, key = { it.transaction.id }) { item ->
                    SwipeableTransactionCard(
                        item = item,
                        onDelete = { viewModel.deleteTransaction(item.transaction) },
                        onEdit = { newShares, newPrice ->
                            val updated =
                                item.transaction.copy(
                                    shares = newShares,
                                    pricePerShare = newPrice,
                                    totalValue = newShares * newPrice,
                                )
                            viewModel.updateTransaction(updated)
                        },
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableTransactionCard(
    item: TransactionWithTicker,
    onDelete: () -> Unit,
    onEdit: (Double, Double) -> Unit,
) {
    var showEditDialog by remember { mutableStateOf(false) }

    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { dismissValue ->
                if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                    onDelete()
                    true
                } else {
                    false
                }
            },
        )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromEndToStart = true,
        enableDismissFromStartToEnd = true,
        backgroundContent = {
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.Settled -> Color.Transparent
                    else -> MaterialTheme.colorScheme.error
                },
                label = "delete_color_anim",
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(color, MaterialTheme.shapes.medium)
                        .padding(horizontal = 20.dp),
                contentAlignment = if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                if (dismissState.targetValue != SwipeToDismissBoxValue.Settled) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onError,
                    )
                }
            }
        },
    ) {
        TransactionItemCard(
            item = item,
            modifier = Modifier.clickable { showEditDialog = true },
        )
    }

    if (showEditDialog) {
        var tempShares by remember { mutableStateOf(item.transaction.shares.toString()) }
        var tempPrice by remember { mutableStateOf(item.transaction.pricePerShare.toString()) }

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text("Edit Trade") },
            text = {
                Column {
                    OutlinedTextField(
                        value = tempShares,
                        onValueChange = { tempShares = it },
                        label = { Text("Shares") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempPrice,
                        onValueChange = { tempPrice = it },
                        label = { Text("Price per share") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val shares = tempShares.toDoubleOrNull()
                        val price = tempPrice.toDoubleOrNull()
                        if (shares != null && price != null) {
                            onEdit(shares, price)
                            showEditDialog = false
                        }
                    },
                    enabled = tempShares.toDoubleOrNull() != null && tempPrice.toDoubleOrNull() != null,
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
fun TransactionItemCard(
    item: TransactionWithTicker,
    modifier: Modifier = Modifier,
) {
    val t = item.transaction
    val dateFormatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    val formattedDate = dateFormatter.format(Date(t.date))

    val isBuy = t.type.equals("BUY", ignoreCase = true)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = if (isBuy) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Text(
                            text = t.type.uppercase(),
                            color = if (isBuy) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.ticker,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$formattedDate • ${t.source}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${t.shares} shares",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "@ $${t.pricePerShare}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
