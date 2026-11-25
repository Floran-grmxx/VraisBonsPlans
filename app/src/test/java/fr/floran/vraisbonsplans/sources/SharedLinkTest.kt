package fr.floran.vraisbonsplans.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedLinkTest {

    @Test
    fun linkIsFoundInsideSharedSentence() {
        val text = "Regarde ce que j'ai trouvé sur idealo : https://www.idealo.fr/prix/202650790/garmin-instinct.html."
        assertEquals("https://www.idealo.fr/prix/202650790/garmin-instinct.html", SharedLink.idealoUrl(text))
    }

    @Test
    fun otherSitesAreIgnored() {
        assertNull(SharedLink.idealoUrl("https://www.amazon.fr/dp/B0 et https://notidealo.fr/prix/1.html"))
        assertEquals("https://idealo.fr/x", SharedLink.idealoUrl("https://www.amazon.fr/dp/B0 puis https://idealo.fr/x"))
    }

    @Test
    fun productIdComesFromPrixPath() {
        assertEquals("202650790", SharedLink.productId("https://www.idealo.fr/prix/202650790/garmin.html"))
        assertEquals("201646095", SharedLink.productId("https://www.idealo.fr/prix/201646095.html"))
        assertNull(SharedLink.productId("https://www.idealo.fr/r/abc"))
    }
}
