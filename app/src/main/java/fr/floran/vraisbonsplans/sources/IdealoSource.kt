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
                    // On mobile, href is sometimes an encrypted token: /prix/{id}.html redirects to the product page.
                    id: item.id, name: item.title, imageUrl: item.imageUrl,
                    url: /^https?:\/\//.test(item.href || '') ? item.href : 'https://www.idealo.fr/prix/' + item.id + '.html',
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

    /** One product shared from the idealo app: [url] may be a /prix/ link or a short link the page resolves. */
    suspend fun fetchProduct(context: Context, url: String): IdealoProduct {
        val json = HiddenBrowser.runOnPage(context, url, PRODUCT_SCRIPT, PRODUCT_TIMEOUT_MILLIS)
        Log.i(TAG, "shared product read: ${json.take(300)}")
        return parse(json).single()
    }

    private const val PRODUCT_TIMEOUT_MILLIS = 60_000L

    /**
     * Reads the schema.org Product data that product pages carry for search engines, with the
     * og: meta tags as fallback; the current price falls back to the last point of the history.
     */
    private val PRODUCT_SCRIPT = """
        (async () => {
          const canonical = (document.querySelector('link[rel="canonical"]') || {}).href || '';
          const idMatch = (location.href + ' ' + canonical).match(/\/prix\/(\d+)/);
          if (!idMatch) throw new Error('no product id in ' + location.href);
          const id = idMatch[1];
          let product = null;
          for (const tag of document.querySelectorAll('script[type="application/ld+json"]')) {
            try {
              const data = JSON.parse(tag.textContent);
              const nodes = [].concat(data, data['@graph'] || []);
              product = nodes.find(node => node && [].concat(node['@type']).includes('Product')) || product;
            } catch (error) { /* another JSON-LD block may be the product */ }
          }
          const meta = name => (document.querySelector('meta[property="' + name + '"]') || {}).content || '';
          const offers = product && [].concat(product.offers || [])[0];
          const offerPrice = offers && parseFloat(offers.lowPrice || offers.price);
          const image = product && [].concat(product.image || [])[0];
          const historyResponse = await fetch('/price-chart/sites/4/products/' + id + '/history?period=P3M');
          const historyJson = historyResponse.ok ? await historyResponse.json().catch(() => null) : null;
          const history = ((historyJson && historyJson.data) || []).map(point => [point.x, point.y]);
          const lastPrice = history.length ? history[history.length - 1][1] : 0;
          const titleName = document.title.includes(' au meilleur prix') ? document.title.split(' au meilleur prix')[0].trim() : '';
          const entry = {
            // Only the tab title holds the full name ("Garmin Instinct 2X Solar Moss au meilleur prix | …"):
            // the heading and schema.org both cut the variant ("Garmin Instinct 2").
            id, name: titleName || (product && product.name) || meta('og:title') || document.title,
            imageUrl: (typeof image === 'string' ? image : (image && image.url)) || meta('og:image'),
            url: canonical || location.href,
            // History prices are already in cents, like the ones parse() reads.
            currentPrice: offerPrice ? Math.round(offerPrice * 100) : Math.round(lastPrice),
            discountRate: 0, history,
          };
          Bridge.onResult(JSON.stringify({ title: document.title, products: [entry] }));
        })().catch(error => Bridge.onResult(JSON.stringify({ error: String(error), title: document.title })));
    """.trimIndent()

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
