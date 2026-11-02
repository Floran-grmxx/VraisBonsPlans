package fr.floran.vraisbonsplans.sources

import org.w3c.dom.Element
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** A price error reported by the Dealabs community. */
data class DealabsItem(
    val title: String,
    val merchant: String,
    val priceCents: Long?,
    val url: String,
    val imageUrl: String?,
    val publishedAtEpochSecond: Long,
)

/** Reads the Dealabs "erreur de prix" RSS feed, which accepts plain HTTP clients. */
object DealabsSource {

    private const val FEED_URL = "https://www.dealabs.com/rss/groupe/erreur-de-prix"
    private val RSS_DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss Z", Locale.ENGLISH)

    fun fetchPriceErrors(): List<DealabsItem> = parse(HttpClient.get(FEED_URL))

    fun parse(xml: String): List<DealabsItem> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(xml.byteInputStream(Charsets.UTF_8))
        val items = document.getElementsByTagName("item")
        return (0 until items.length).mapNotNull { index ->
            val item = items.item(index) as Element
            val title = item.childText("title") ?: return@mapNotNull null
            val url = item.childText("link") ?: return@mapNotNull null
            val merchant = item.firstChild("pepper:merchant")
            DealabsItem(
                title = title.trim(),
                merchant = merchant?.getAttribute("name").orEmpty(),
                priceCents = merchant?.getAttribute("price")?.let(::parseEuroCents),
                url = url.trim(),
                imageUrl = item.firstChild("media:content")?.getAttribute("url")?.ifEmpty { null },
                publishedAtEpochSecond = item.childText("pubDate")
                    ?.let { runCatching { ZonedDateTime.parse(it.trim(), RSS_DATE_FORMAT).toEpochSecond() }.getOrNull() }
                    ?: 0L,
            )
        }
    }

    /** "1 704,30€" → 170430; "Gratuit" or an unreadable price → null. */
    fun parseEuroCents(text: String): Long? {
        val digits = text.replace(Regex("[^0-9,.]"), "").replace(".", "").replace(",", ".")
        val euros = digits.toDoubleOrNull() ?: return null
        return Math.round(euros * 100)
    }

    private fun Element.firstChild(tagName: String): Element? =
        getElementsByTagName(tagName).item(0) as? Element

    private fun Element.childText(tagName: String): String? = firstChild(tagName)?.textContent
}
