package com.chatfolio.ui.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chatfolio.data.local.entity.TransactionWithTicker
import com.chatfolio.data.repository.PortfolioRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TransactionsUiState(
    val transactions: List<TransactionWithTicker> = emptyList(),
    val isLoading: Boolean = false,
)

@HiltViewModel
class TransactionsViewModel
    @Inject
    constructor(
        private val portfolioRepository: PortfolioRepository,
    ) : ViewModel() {
        val uiState: StateFlow<TransactionsUiState> =
            portfolioRepository.getGlobalLedger()
                .map { TransactionsUiState(transactions = it, isLoading = false) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5000),
                    initialValue = TransactionsUiState(isLoading = true),
                )

        fun deleteTransaction(transaction: com.chatfolio.data.local.entity.TransactionEntity) {
            viewModelScope.launch {
                portfolioRepository.deleteTransaction(transaction)
            }
        }

        fun updateTransaction(transaction: com.chatfolio.data.local.entity.TransactionEntity) {
            viewModelScope.launch {
                portfolioRepository.updateTransaction(transaction)
            }
        }
    }
