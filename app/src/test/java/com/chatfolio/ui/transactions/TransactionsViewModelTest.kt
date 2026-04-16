package com.chatfolio.ui.transactions

import app.cash.turbine.test
import com.chatfolio.data.local.entity.TransactionEntity
import com.chatfolio.data.local.entity.TransactionWithTicker
import com.chatfolio.data.repository.PortfolioRepository
import com.google.common.truth.Truth.assertThat
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class TransactionsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val portfolioRepository: PortfolioRepository = mockk(relaxed = true)

    @Test
    fun `uiState maps database flow correctly to presentation model`() =
        runTest {
            val mockData =
                listOf(
                    TransactionWithTicker(
                        transaction =
                            TransactionEntity(
                                holdingId = 1,
                                type = "BUY",
                                date = 1000L,
                                shares = 10.0,
                                pricePerShare = 150.0,
                                totalValue = 1500.0,
                                currency = "USD",
                                source = "CHAT",
                            ),
                        ticker = "AAPL",
                    ),
                )

            coEvery { portfolioRepository.getGlobalLedger() } returns flowOf(mockData)

            val viewModel = TransactionsViewModel(portfolioRepository)

            viewModel.uiState.test {
                val state = awaitItem()

                assertThat(state.isLoading).isFalse()
                assertThat(state.transactions).hasSize(1)
                assertThat(state.transactions[0].ticker).isEqualTo("AAPL")
                assertThat(state.transactions[0].transaction.shares).isEqualTo(10.0)

                cancelAndIgnoreRemainingEvents()
            }
        }
}
