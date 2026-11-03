package fr.floran.vraisbonsplans.model

import org.junit.Assert.assertEquals
import org.junit.Test

class DealUrlTest {

    private fun deal(key: String, productUrl: String) = Deal(
        key = key, source = DealSource.IDEALO, name = "Garmin Instinct", detail = "", imageUrl = null, productUrl = productUrl,
        currentPriceCents = 100, referencePriceCents = null, sourceDiscountPercent = null,
        realDiscountPercent = null, isNewLow = true, crossCheck = null, rejectionReason = null,
    )

    @Test
    fun webLinkIsKeptAsIs() {
        val url = "https://www.idealo.fr/prix/202650790/garmin-instinct-2x-solar-moss.html"
        assertEquals(url, deal("idealo:202650790", url).openableUrl)
    }

    @Test
    fun encryptedTokenFallsBackToTheIdealoProductId() {
        assertEquals(
            "https://www.idealo.fr/prix/202650790.html",
            deal("idealo:202650790", "c9fbae92816132c6fe1b144233c29e2c2a478932").openableUrl,
        )
    }

    @Test
    fun unknownProductFallsBackToAnIdealoSearch() {
        assertEquals("https://www.idealo.fr/prechcat.html?q=Garmin+Instinct", deal("dealabs:1", "").openableUrl)
    }
}
