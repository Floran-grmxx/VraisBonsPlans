package fr.floran.vraisbonsplans.pricing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductNameMatcherTest {

    @Test
    fun `same product with different wording matches`() {
        assertTrue(
            ProductNameMatcher.isSameProduct(
                "Dyson V12 Detect Slim Absolute (2026)",
                "Dyson V12 Detect Slim Absolute Sans Fil",
            ),
        )
    }

    @Test
    fun `accessory does not match the product`() {
        assertFalse(ProductNameMatcher.isSameProduct("De'Longhi Dragon 4 TRD41025", "Thermostat WK-03 radiateur"))
    }

    @Test
    fun `accents and case are ignored`() {
        assertEquals(1.0, ProductNameMatcher.similarity("Radiateur Électrique", "radiateur electrique noir"), 0.0)
    }

    @Test
    fun `search query drops seller notes and keeps 6 words`() {
        assertEquals(
            "Aspirateur laveur Dyson V12s Detect Slim",
            ProductNameMatcher.searchQuery("Aspirateur laveur Dyson V12s Detect Slim Submarine - 545 W Gris (Vendeur Darty)"),
        )
    }
}
