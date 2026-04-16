package com.chatfolio.ui.transactions

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.chatfolio.data.local.entity.TransactionWithTicker
import com.chatfolio.ui.cards.TransactionItemCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableTransactionCard(
    item: TransactionWithTicker,
    onDelete: () -> Unit,
    onEdit: (Double, Double, String) -> Unit,
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
        var tempCurrency by remember { mutableStateOf(item.transaction.currency) }

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
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tempCurrency,
                        onValueChange = { tempCurrency = it.uppercase() },
                        label = { Text("Currency") },
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val shares = tempShares.toDoubleOrNull()
                        val price = tempPrice.toDoubleOrNull()
                        if (shares != null && price != null && tempCurrency.isNotBlank()) {
                            onEdit(shares, price, tempCurrency)
                            showEditDialog = false
                        }
                    },
                    enabled = tempShares.toDoubleOrNull() != null && tempPrice.toDoubleOrNull() != null && tempCurrency.isNotBlank(),
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
