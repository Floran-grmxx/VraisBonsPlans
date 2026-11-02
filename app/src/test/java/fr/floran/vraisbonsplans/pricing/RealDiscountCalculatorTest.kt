package fr.floran.vraisbonsplans.pricing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RealDiscountCalculatorTest {

    private val today = 1_000L

    /** Builds a history where each entry is (days before today, price in euros). */
    private fun history(vararg changes: Pair<Int, Int>) =
        changes.map { (daysAgo, euros) -> PricePoint(today - daysAgo, euros * 100L) }

    @Test
    fun `short spike does not fake a deal`() {
        // Milwaukee case: ~125 €, two days at 670 €, then 119 € for a month. idealo said -70 %.
        val analysis = RealDiscountCalculator.analyze(
            history(90 to 125, 60 to 670, 58 to 125, 30 to 119),
            currentPriceCents = 11_900,
            todayEpochDay = today,
        )!!
        assertEquals(11_900L, analysis.referencePriceCents)
        assertEquals(0, analysis.realDiscountPercent)
    }

    @Test
    fun `return to the usual price after an expensive season is not a deal`() {
        // De'Longhi case: 320 € all summer, then stable 135 € for 5 weeks. idealo said -60 %.
        val analysis = RealDiscountCalculator.analyze(
            history(90 to 320, 35 to 135),
            currentPriceCents = 13_500,
            todayEpochDay = today,
        )!!
        assertEquals(0, analysis.realDiscountPercent)
    }

    @Test
    fun `genuine drop to a new low is detected`() {
        // SSD case: around 455 € for months, drops to 279 € yesterday.
        val analysis = RealDiscountCalculator.analyze(
            history(90 to 470, 40 to 455, 1 to 279),
            currentPriceCents = 27_900,
            todayEpochDay = today,
        )!!
        assertEquals(45_500L, analysis.referencePriceCents)
        assertEquals(39, analysis.realDiscountPercent)
        assertTrue(analysis.isNewLow)
    }

    @Test
    fun `price error at 1 euro is detected as almost free`() {
        val analysis = RealDiscountCalculator.analyze(
            history(90 to 135, 0 to 1),
            currentPriceCents = 100,
            todayEpochDay = today,
        )!!
        assertEquals(99, analysis.realDiscountPercent)
        assertTrue(analysis.isNewLow)
    }

    @Test
    fun `an old price error does not block future new lows`() {
        // A 1 € glitch 50 days ago must not stay the "lowest price" forever.
        val analysis = RealDiscountCalculator.analyze(
            history(90 to 200, 50 to 1, 49 to 200, 1 to 150),
            currentPriceCents = 15_000,
            todayEpochDay = today,
        )!!
        assertEquals(20_000L, analysis.lowestPriceCents)
        assertTrue(analysis.isNewLow)
        assertEquals(25, analysis.realDiscountPercent)
    }

    @Test
    fun `price above the lowest is not a new low`() {
        // Pokémon GO Plus+ case: 105 € now while it was 77 € a few weeks ago.
        val analysis = RealDiscountCalculator.analyze(
            history(90 to 199, 20 to 77, 15 to 199, 1 to 105),
            currentPriceCents = 10_500,
            todayEpochDay = today,
        )!!
        assertFalse(analysis.isNewLow)
    }

    @Test
    fun `absurd price lasting longer than the real one is still removed`() {
        // EDM radiator case: 1 000 € for 2 months, then 76-84 € for 3 weeks. Not a -92 % deal.
        val analysis = RealDiscountCalculator.analyze(
            history(92 to 1_000, 25 to 76, 6 to 84),
            currentPriceCents = 8_250,
            todayEpochDay = today,
        )!!
        assertTrue(analysis.realDiscountPercent < 10)
        assertFalse(analysis.isNewLow)
    }

    @Test
    fun `long inflated price is flagged as inconsistent history`() {
        // LG TV case: 3 800 € in August, 890 € for 8 days, then 3 400 € for 2 months, now 599 €.
        val analysis = RealDiscountCalculator.analyze(
            history(92 to 3_800, 74 to 890, 66 to 3_400, 6 to 599),
            currentPriceCents = 59_900,
            todayEpochDay = today,
        )!!
        assertFalse(analysis.isHistoryConsistent)
    }

    @Test
    fun `stable history is consistent`() {
        val analysis = RealDiscountCalculator.analyze(
            history(90 to 470, 40 to 455, 1 to 279),
            currentPriceCents = 27_900,
            todayEpochDay = today,
        )!!
        assertTrue(analysis.isHistoryConsistent)
    }

    @Test
    fun `empty history gives no analysis`() {
        assertNull(RealDiscountCalculator.analyze(emptyList(), currentPriceCents = 1_000, todayEpochDay = today))
    }
}
