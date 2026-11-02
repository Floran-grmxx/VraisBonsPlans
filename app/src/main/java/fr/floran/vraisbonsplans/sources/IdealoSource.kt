package fr.floran.vraisbonsplans.sources

import android.content.Context
import android.util.Log
import fr.floran.vraisbonsplans.pricing.PricePoint
import org.json.JSONObject
import java.time.LocalDate

/** A product of the idealo deals page with its price history (prices in cents). */
data class IdealoProduct(
    val id: String,
    val name: String,
    val productUrl: String,
    val imageUrl: String,
    val currentPriceCents: Long,
    val idealoDiscountPercent: Int,
    val history: List<PricePoint>,
)

/**
 * Reads idealo through [HiddenBrowser]: idealo's anti-bot (Akamai) rejects plain HTTP clients with a 403.
 * Once the deals page is loaded, the JSON endpoints used by the idealo website are called with fetch().
 */
object IdealoSource {

    private const val TAG = "IdealoSource"
    /** Deals sorted by biggest saving, so that price errors come first. */
    private const val DEALS_URL = "https://www.idealo.fr/bons-plans?sortKey=biggestSaving"
    private const val TIMEOUT_MILLIS = 120_000L

    suspend fun fetchDeals(context: Context, productLimit: Int): List<IdealoProduct> =
        parse(HiddenBrowser.runOnPage(context, DEALS_URL, extractionScript(productLimit), TIMEOUT_MILLIS))

    private fun extractionScript(productLimit: Int) = """
        (async () => {
          const byId = {};
          document.querySelectorAll('a[href*="/prix/"]').forEach(link => {
            const match = link.href.match(/\/prix\/(\d+)\/([^.?]+)/);
            if (!match) return;
            const entry = byId[match[1]] || (byId[match[1]] = { id: match[1], slug: match[2], url: link.href.split('?')[0] });
            const text = link.innerText.trim();
            if (text && !entry.name) entry.name = text.split('\n')[0];
          });
          // Some products answer with an empty body: each one is read on its own and skipped on failure.
          const readJson = async url => {
            const response = await fetch(url);
            const text = response.ok ? await response.text() : '';
            try { return text ? JSON.parse(text) : null; } catch (error) { return null; }
          };
          const products = [];
          let skipped = 0;
          for (const entry of Object.values(byId).slice(0, $productLimit)) {
            const bargain = await readJson('/price-chart/sites/4/products/' + entry.id + '/bargain');
            if (!bargain || !bargain.currentPrice) { skipped++; continue; }
            const history = await readJson('/price-chart/sites/4/products/' + entry.id + '/history?period=P3M');
            products.push({
              id: entry.id, slug: entry.slug, url: entry.url, name: entry.name || entry.slug,
              currentPrice: bargain.currentPrice, discountRate: bargain.discountRate || 0,
              history: ((history && history.data) || []).map(point => [point.x, point.y]),
            });
          }
          Bridge.onResult(JSON.stringify({ title: document.title, products, skipped }));
        })().catch(error => Bridge.onResult(JSON.stringify({ error: String(error), title: document.title })));
    """.trimIndent()

    private fun parse(json: String): List<IdealoProduct> {
        val root = JSONObject(json)
        if (root.has("error")) throw IllegalStateException("idealo: ${root.getString("error")} (page « ${root.optString("title")} »)")
        val products = root.getJSONArray("products")
        Log.i(TAG, "${products.length()} products read, ${root.optInt("skipped")} skipped")
        return (0 until products.length()).map { index ->
            val product = products.getJSONObject(index)
            val id = product.getString("id")
            val history = product.getJSONArray("history")
            IdealoProduct(
                id = id,
                name = product.getString("name"),
                productUrl = product.getString("url"),
                imageUrl = imageUrl(id, product.getString("slug")),
                currentPriceCents = product.getLong("currentPrice"),
                idealoDiscountPercent = product.optInt("discountRate"),
                history = (0 until history.length()).map { pointIndex ->
                    val point = history.getJSONArray(pointIndex)
                    PricePoint(LocalDate.parse(point.getString(0)).toEpochDay(), point.getLong(1))
                },
            )
        }
    }

    /** idealo CDN pattern: folder = id without its last 3 digits, then the 3rd-last digit. */
    private fun imageUrl(id: String, slug: String): String {
        val folder = id.dropLast(3).ifEmpty { "0" }
        val digit = id.getOrNull(id.length - 3) ?: '0'
        return "https://cdn.idealo.com/folder/Product/$folder/$digit/$id/s4_produktbild_mittelgross/$slug.jpg"
    }
}
