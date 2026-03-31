package com.chatfolio.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PortfolioCalculatorTest {
    private val epsilon = 0.001

    @Test
    fun `single buy calculates correctly`() {
        val trades =
            listOf(
                TradeRecord("BUY", 10.0, 150.0, 1000L),
            )
        val result = PortfolioCalculator.calculate(trades)

        assertEquals(10.0, result.totalShares, epsilon)
        assertEquals(1500.0, result.costBase, epsilon)
        assertEquals(0.0, result.realizedProfit, epsilon)
    }

    @Test
    fun `multiple buys average correctly`() {
        val trades =
            listOf(
                TradeRecord("BUY", 10.0, 100.0, 1000L),
                TradeRecord("BUY", 10.0, 200.0, 2000L),
            )
        val result = PortfolioCalculator.calculate(trades)

        assertEquals(20.0, result.totalShares, epsilon)
        assertEquals(3000.0, result.costBase, epsilon)
        assertEquals(0.0, result.realizedProfit, epsilon)
    }

    @Test
    fun `sell handles average cost and realized profit`() {
        val trades =
            listOf(
                TradeRecord("BUY", 10.0, 100.0, 1000L),
                TradeRecord("BUY", 10.0, 200.0, 2000L),
                TradeRecord("SELL", 5.0, 300.0, 3000L),
            )
        // Cost basis removed for 5 shares at avg 150 = 750
        // Realized profit: proceeds (1500) - cost basis removed (750) = 750
        // Remaining shares: 15. Remaining cost base: 3000 - 750 = 2250.

        val result = PortfolioCalculator.calculate(trades)

        assertEquals(15.0, result.totalShares, epsilon)
        assertEquals(2250.0, result.costBase, epsilon)
        assertEquals(750.0, result.realizedProfit, epsilon)
    }

    @Test
    fun `full liquidation resets cost base to zero`() {
        val trades =
            listOf(
                TradeRecord("BUY", 10.0, 100.0, 1000L),
                TradeRecord("SELL", 10.0, 150.0, 2000L),
            )

        val result = PortfolioCalculator.calculate(trades)

        assertEquals(0.0, result.totalShares, epsilon)
        assertEquals(0.0, result.costBase, epsilon)
        assertEquals(500.0, result.realizedProfit, epsilon)
    }

    @Test
    fun `short selling throws exception`() {
        val trades =
            listOf(
                TradeRecord("BUY", 10.0, 100.0, 1000L),
                TradeRecord("SELL", 15.0, 150.0, 2000L),
            )

        val exception =
            assertThrows(IllegalArgumentException::class.java) {
                PortfolioCalculator.calculate(trades)
            }

        assertEquals(true, exception.message?.contains("Attempted to sell"))
    }
}
