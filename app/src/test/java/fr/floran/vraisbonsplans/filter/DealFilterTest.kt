package fr.floran.vraisbonsplans.filter

import fr.floran.vraisbonsplans.model.Deal
import fr.floran.vraisbonsplans.model.DealSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DealFilterTest {

    private fun deal(name: String, priceCents: Long, discount: Int?, source: DealSource = DealSource.IDEALO) = Deal(
        key = name, source = source, name = name, detail = "", imageUrl = null, productUrl = "",
        currentPriceCents = priceCents, referencePriceCents = null, sourceDiscountPercent = null,
        realDiscountPercent = discount, isNewLow = true, crossCheck = null, rejectionReason = null,
    )

    private val lego = deal("LEGO Batman Batmobile", 1_028, 89)
    private val ryzen = deal("AMD Ryzen 5 7600X", 8_281, 49)
    private val tele = deal("Télévision LG OLED 55", 59_900, 82)
    private val dealabs = deal("Casque Sony à 1 €", 100, null, DealSource.DEALABS)
    private val all = listOf(lego, ryzen, tele, dealabs)

    @Test
    fun searchIgnoresCaseAccentsAndAcceptsPrefixes() {
        assertEquals(listOf(tele), DealFilter(query = "television lg").apply(all))
        assertEquals(listOf(ryzen), DealFilter(query = "ryz 76").apply(all))
        assertEquals(listOf(ryzen), DealFilter(query = "ryzen 5").apply(all))
    }

    @Test
    fun priceRangeKeepsOnlyDealsInside() {
        val filter = DealFilter(minimumPriceCents = 1_000, maximumPriceCents = 10_000, sort = DealSort.LOWEST_PRICE)
        assertEquals(listOf(lego, ryzen), filter.apply(all))
    }

    @Test
    fun sourceFilterKeepsOnlySelectedSources() {
        assertEquals(listOf(dealabs), DealFilter(sources = setOf(DealSource.DEALABS)).apply(all))
    }

    @Test
    fun defaultSortPutsBiggestDiscountFirst() {
        assertEquals(listOf(lego, tele, ryzen, dealabs), DealFilter().apply(all))
        assertEquals(0, DealFilter().activeFilterCount)
    }

    @Test
    fun parseEurosAcceptsFrenchFormats() {
        assertEquals(4_990L, DealFilter.parseEuros("49,90"))
        assertEquals(4_900L, DealFilter.parseEuros("49 €"))
        assertEquals(4_950L, DealFilter.parseEuros("49.5"))
        assertNull(DealFilter.parseEuros(""))
        assertNull(DealFilter.parseEuros("abc"))
    }
}
