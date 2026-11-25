package fr.floran.vraisbonsplans.pricing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResaleEstimatorTest {

    private fun listing(title: String, euros: Long) = ResaleListing("Vinted", title, euros * 100, "", "")

    @Test
    fun `search keeps the basic name and the capacity, without color, specs or references`() {
        assertEquals("Samsung Galaxy Z Flip 3 128 Go", ResaleEstimator.searchQuery("Samsung Galaxy Z Flip 3 128 Go lavande"))
        assertEquals("G.Skill Ripjaws S5 96 Go", ResaleEstimator.searchQuery("G.Skill Ripjaws S5 96 Go Kit DDR5-6000 CL40 (F5-5600J4040D48GX2-RS5K)"))
        assertEquals("Kingston FURY Renegade 64 Go", ResaleEstimator.searchQuery("Kingston FURY Renegade RGB 64GB Kit DDR5-6400 CL32 (KF564C32RWAK2-64)"))
        assertEquals("Apple iPhone 15 Pro 256 Go", ResaleEstimator.searchQuery("Apple iPhone 15 Pro 256GB Titane naturel"))
        assertEquals("Bissell Crosswave C6", ResaleEstimator.searchQuery("Bissell Crosswave C6 B3570N"))
        assertEquals("Rowenta X Force Flex 9.60", ResaleEstimator.searchQuery("Rowenta X Force Flex 9.60 kit animal RH2079WO"))
    }

    @Test
    fun `a reference is kept when it is the only model name`() {
        assertEquals("TCL 55MQLED75K", ResaleEstimator.searchQuery("TCL 55MQLED75K"))
    }

    @Test
    fun `search without brand needs at least two words left`() {
        assertEquals("Pokémon Coffret Dresseur", ResaleEstimator.searchQueryWithoutBrand("Asmodée Pokémon Coffret Dresseur"))
        assertNull(ResaleEstimator.searchQueryWithoutBrand("TCL 55MQLED75K"))
    }

    @Test
    fun `capacity spellings are the same word`() {
        assertTrue("64go" in ResaleEstimator.tokens("Kingston Fury 64GB DDR5"))
        assertTrue("64go" in ResaleEstimator.tokens("Kingston FURY Renegade 64 Go"))
        assertTrue("2to" in ResaleEstimator.tokens("SSD 2 To"))
    }

    @Test
    fun `accessories, broken units and very low prices are excluded`() {
        val query = "Samsung Galaxy Z Flip 3 128 Go"
        val title = "Samsung Galaxy Z Flip 3 128 Go lavande"
        assertEquals(ListingKind.ACCESSORY, ResaleEstimator.classify(listing("Coque Samsung Galaxy Z Flip 3", 150), title, 34_999, query))
        assertEquals(ListingKind.ACCESSORY, ResaleEstimator.classify(listing("Galaxy Z Flip 3 128 Go écran HS", 150), title, 34_999, query))
        assertEquals(ListingKind.ACCESSORY, ResaleEstimator.classify(listing("Samsung Galaxy Z Flip 3 128 Go", 40), title, 34_999, query))
    }

    @Test
    fun `another capacity is excluded but a missing capacity or another color is kept`() {
        val query = "Samsung Galaxy Z Flip 3 128 Go"
        val title = "Samsung Galaxy Z Flip 3 128 Go lavande"
        assertEquals(ListingKind.OTHER_CAPACITY, ResaleEstimator.classify(listing("Galaxy Z Flip 3 256Go noir", 200), title, 34_999, query))
        assertEquals(ListingKind.MATCH, ResaleEstimator.classify(listing("Samsung Galaxy Z Flip 3 bon état", 200), title, 34_999, query))
        assertEquals(ListingKind.MATCH, ResaleEstimator.classify(listing("Samsung Galaxy Z Flip 3 128 go noir", 200), title, 34_999, query))
    }

    @Test
    fun `another product is unrelated`() {
        val query = "Kingston FURY Renegade 64 Go"
        val title = "Kingston FURY Renegade RGB 64GB Kit DDR5-6400 CL32"
        assertEquals(ListingKind.UNRELATED, ResaleEstimator.classify(listing("PC Gamer i7 RTX 4070 32 Go DDR5", 2_599), title, 64_718, query))
        assertEquals(ListingKind.MATCH, ResaleEstimator.classify(listing("RAM DDR5 Kingston FURY Renegade 64Go", 900), title, 64_718, query))
    }

    @Test
    fun `estimate gives the median, the gain and flags rare items`() {
        val estimate = ResaleEstimator.estimate("q", listOf(19_900L, 16_000L, 25_000L), 5_599)
        assertEquals(19_900L, estimate.medianCents)
        assertEquals(14_301L, estimate.gainCents)
        assertFalse(estimate.hasFewListings)
        assertTrue(ResaleEstimator.estimate("q", listOf(90_000L), 64_718).hasFewListings)
        assertNull(ResaleEstimator.estimate("q", emptyList(), 1_000).medianCents)
    }

    @Test
    fun `prices are read from every site format`() {
        assertEquals(191_690L, ResaleEstimator.parsePriceCents("1 916,90 €"))
        assertEquals(19_000L, ResaleEstimator.parsePriceCents("190.00 €"))
        assertEquals(20_000L, ResaleEstimator.parsePriceCents("200"))
        assertNull(ResaleEstimator.parsePriceCents("Prix inconnu"))
    }
}
