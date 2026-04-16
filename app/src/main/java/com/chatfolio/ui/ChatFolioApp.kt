package com.chatfolio.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.chatfolio.ui.chat.ChatScreen
import com.chatfolio.ui.chat.ChatViewModel
import com.chatfolio.ui.transactions.TransactionsScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatFolioApp(viewModel: ChatViewModel = hiltViewModel()) {
    var showSettings by remember { mutableStateOf(false) }
    val currentApiKey by viewModel.apiKey.collectAsState()

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: "chat"

    // Auto-prompt on first launch
    LaunchedEffect(currentApiKey) {
        if (currentApiKey.isBlank()) {
            showSettings = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (currentRoute == "chat") "ChatFolio" else "Transactions") },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                        )
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Chat") },
                    label = { Text("Chat") },
                    selected = currentRoute == "chat",
                    onClick = {
                        navController.navigate("chat") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.List, contentDescription = "Ledger") },
                    label = { Text("Ledger") },
                    selected = currentRoute == "ledger",
                    onClick = {
                        navController.navigate("ledger") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        },
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "chat",
            modifier = Modifier.padding(paddingValues),
        ) {
            composable("chat") {
                ChatScreen(viewModel = viewModel)
            }
            composable("ledger") {
                TransactionsScreen()
            }
        }

        if (showSettings) {
            var tempKey by remember { mutableStateOf(currentApiKey) }
            AlertDialog(
                onDismissRequest = {
                    if (currentApiKey.isNotBlank()) showSettings = false
                },
                title = { Text("API Configuration") },
                text = {
                    Column {
                        Text(
                            text =
                                "ChatFolio requires a Google Gemini API Key to run its AI engine securely on your device.\n\n" +
                                    "Your key is encrypted locally and never leaves your phone.",
                            modifier = Modifier.padding(bottom = 16.dp),
                        )
                        OutlinedTextField(
                            value = tempKey,
                            onValueChange = { tempKey = it },
                            label = { Text("Gemini API Key") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.saveApiKey(tempKey)
                            showSettings = false
                        },
                        enabled = tempKey.isNotBlank(),
                    ) {
                        Text("Save Key")
                    }
                },
                dismissButton = {
                    if (currentApiKey.isNotBlank()) {
                        TextButton(onClick = { showSettings = false }) {
                            Text("Cancel")
                        }
                    }
                },
            )
        }
    }
}
