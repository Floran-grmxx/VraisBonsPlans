package fr.floran.vraisbonsplans.sources

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DealabsSourceTest {

    // Shape of a real item of https://www.dealabs.com/rss/groupe/erreur-de-prix (29/10/2026).
    private val feed = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss xmlns:media="http://search.yahoo.com/mrss/" xmlns:pepper="http://www.pepper.com/rss" version="2.0"><channel>
        <item>
          <category><![CDATA[Maison & Habitat]]></category>
          <pepper:merchant name="Fnac" price="278,38€"/>
          <media:content medium="image" url="https://static-pepper.dealabs.com/3436986_1.jpg" width="100" height="100"/>
          <title><![CDATA[Aspirateur laveur Dyson V12s Detect Slim Submarine - 545 W Gris (Vendeur Darty)]]></title>
          <link>https://www.dealabs.com/bons-plans/aspirateur-laveur-dyson-v12s-3436986</link>
          <pubDate>Thu, 22 Oct 2026 22:37:47 +0200</pubDate>
        </item>
        <item>
          <pepper:merchant name="Rokr" price="Gratuit"/>
          <title><![CDATA[Pistolet en bois Rokr]]></title>
          <link>https://www.dealabs.com/bons-plans/pistolet-rokr</link>
          <pubDate>Wed, 12 Aug 2026 10:00:00 +0200</pubDate>
        </item>
        </channel></rss>
    """.trimIndent()

    @Test
    fun `parses merchant, price, image and date`() {
        val item = DealabsSource.parse(feed).first()
        assertEquals("Fnac", item.merchant)
        assertEquals(27_838L, item.priceCents)
        assertEquals("https://static-pepper.dealabs.com/3436986_1.jpg", item.imageUrl)
        assertEquals(1_792_701_467L, item.publishedAtEpochSecond)
    }

    @Test
    fun `free item has no price`() {
        assertNull(DealabsSource.parse(feed)[1].priceCents)
    }

    @Test
    fun `parses prices with thousands separator`() {
        assertEquals(170_430L, DealabsSource.parseEuroCents("1 704,30€"))
        assertEquals(1L, DealabsSource.parseEuroCents("0,01€"))
    }
}
