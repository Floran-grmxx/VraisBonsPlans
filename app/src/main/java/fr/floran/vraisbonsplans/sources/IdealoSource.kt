package fr.floran.vraisbonsplans.sources

import android.content.Context
import android.util.Log
import fr.floran.vraisbonsplans.pricing.PricePoint
import org.json.JSONObject
import java.time.LocalDate

/** A product of the idealo deals with its price history (prices in cents). */
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
    private const val TIMEOUT_MILLIS = 180_000L
    /** Price histories fetched at the same time; more makes idealo answer with errors. */
    private const val PARALLEL_HISTORY_REQUESTS = 10

    suspend fun fetchDeals(context: Context, categories: Set<IdealoCategory>, productLimit: Int): List<IdealoProduct> {
        val queries = DealsQueryPlanner.plan(categories, productLimit)
        return parse(HiddenBrowser.runOnPage(context, DEALS_URL, extractionScript(queries, productLimit), TIMEOUT_MILLIS))
    }

    /**
     * Uses the JSON API behind the deals page (/csr/api/v2/modules/dealsResult), which already gives
     * price, idealo discount and image; only the price history needs one more request per product.
     */
    private fun extractionScript(queries: List<DealsQuery>, productLimit: Int): String {
        val urls = queries.joinToString(",") { query ->
            val category = query.categoryId?.let { "&categoryId=$it" }.orEmpty()
            "'/csr/api/v2/modules/dealsResult?locale=fr_FR&sort=DISCOUNT_DESC&itemStates=BARGAIN&itemsPerPage=${query.count}$category'"
        }
        return """
            (async () => {
              // Some products answer with an empty body: each one is read on its own and skipped on failure.
              const readJson = async url => {
                const response = await fetch(url);
                const text = response.ok ? await response.text() : '';
                try { return text ? JSON.parse(text) : null; } catch (error) { return null; }
              };
              const byId = {};
              let failedQueries = 0;
              for (const url of [$urls]) {
                const result = await readJson(url);
                if (!result || !result.items) { failedQueries++; continue; }
                for (const item of result.items) {
                  if (item.type !== 'PRODUCT' || !item.rawPrice || !item.bargainInfo || byId[item.id]) continue;
                  byId[item.id] = {
                    id: item.id, name: item.title, url: item.href, imageUrl: item.imageUrl,
                    currentPrice: Math.round(item.rawPrice * 100), discountRate: item.bargainInfo.saving || 0,
                  };
                }
              }
              const entries = Object.values(byId).sort((a, b) => b.discountRate - a.discountRate).slice(0, $productLimit);
              if (entries.length === 0) throw new Error('no deal returned (' + failedQueries + ' failed requests)');
              for (let start = 0; start < entries.length; start += $PARALLEL_HISTORY_REQUESTS) {
                await Promise.all(entries.slice(start, start + $PARALLEL_HISTORY_REQUESTS).map(async entry => {
                  const history = await readJson('/price-chart/sites/4/products/' + entry.id + '/history?period=P3M');
                  entry.history = ((history && history.data) || []).map(point => [point.x, point.y]);
                }));
              }
              Bridge.onResult(JSON.stringify({ title: document.title, products: entries, failedQueries }));
            })().catch(error => Bridge.onResult(JSON.stringify({ error: String(error), title: document.title })));
        """.trimIndent()
    }

    private fun parse(json: String): List<IdealoProduct> {
        val root = JSONObject(json)
        if (root.has("error")) throw IllegalStateException("idealo: ${root.getString("error")} (page « ${root.optString("title")} »)")
        val products = root.getJSONArray("products")
        Log.i(TAG, "${products.length()} products read, ${root.optInt("failedQueries")} failed deal requests")
        return (0 until products.length()).map { index ->
            val product = products.getJSONObject(index)
            val id = product.getString("id")
            val history = product.getJSONArray("history")
            IdealoProduct(
                id = id,
                name = product.getString("name"),
                productUrl = product.getString("url"),
                imageUrl = product.getString("imageUrl"),
                currentPriceCents = product.getLong("currentPrice"),
                idealoDiscountPercent = product.optInt("discountRate"),
                history = (0 until history.length()).map { pointIndex ->
                    val point = history.getJSONArray(pointIndex)
                    PricePoint(LocalDate.parse(point.getString(0)).toEpochDay(), point.getLong(1))
                },
            )
        }
    }
}
