package fr.floran.vraisbonsplans.sources

import android.content.Context
import android.util.Log
import fr.floran.vraisbonsplans.pricing.PricePoint
import fr.floran.vraisbonsplans.pricing.ProductNameMatcher
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

/** Lowest offer over time for a product found on Ledenicheur. */
data class LedenicheurHistory(val productId: String, val productName: String, val history: List<PricePoint>)

/**
 * Ledenicheur (PriceRunner) answers plain clients from a PC but returns 403 to Android's HTTP client,
 * so every lookup of a scan runs inside one [HiddenBrowser] page: search page + JSON price history.
 */
object LedenicheurSource {

    private const val TAG = "LedenicheurSource"
    private const val HOME_URL = "https://ledenicheur.fr/"
    private const val CANDIDATES_CHECKED = 2
    private const val TIMEOUT_MILLIS = 120_000L
    private val PARIS = ZoneId.of("Europe/Paris")

    /** For each title, the matching product history, or null when no reliable match was found. */
    suspend fun findHistories(context: Context, titles: List<String>): Map<String, LedenicheurHistory?> {
        if (titles.isEmpty()) return emptyMap()
        val queries = titles.associateWith { ProductNameMatcher.searchQuery(it) }
        val result = JSONObject(
            HiddenBrowser.runOnPage(context, HOME_URL, lookupScript(queries.values.distinct()), TIMEOUT_MILLIS),
        )
        if (result.has("error")) throw IllegalStateException("Ledenicheur : ${result.getString("error")}")
        val candidatesByQuery = result.getJSONObject("results")
        candidatesByQuery.keys().forEach { query ->
            val candidates = candidatesByQuery.getJSONArray(query)
            val names = (0 until candidates.length()).map {
                candidates.getJSONObject(it).getJSONArray("offers").optJSONObject(0)?.optString("name")
            }
            Log.i(TAG, "« $query » → ${candidates.length()} candidates $names")
        }
        return queries.mapValues { (_, query) ->
            candidatesByQuery.optJSONArray(query)?.let { bestMatch(query, it) }
        }
    }

    private fun lookupScript(queries: List<String>) = """
        (async () => {
          const queries = ${JSONArray(queries)};
          const results = {};
          for (const query of queries) {
            const candidates = [];
            try {
              const html = await (await fetch('/search?query=' + encodeURIComponent(query))).text();
              const ids = [...new Set([...html.matchAll(/product\.php\?p=(\d+)/g)].map(match => match[1]))].slice(0, $CANDIDATES_CHECKED);
              for (const productId of ids) {
                const response = await fetch('/api/price-history', {
                  method: 'POST', headers: { 'content-type': 'application/json' },
                  body: JSON.stringify({ productId, timeRange: 'THREE_MONTHS', shopIds: [], variantIds: [] }),
                });
                if (!response.ok) continue;
                const items = ((await response.json()).priceHistory || {}).historyItems || [];
                candidates.push({ productId, offers: items.map(item => ({
                  shopId: item.shopId, date: item.date, price: item.price, active: item.active, name: item.name,
                })) });
              }
            } catch (error) { console.error('[ledenicheur] ' + query + ': ' + error); }
            results[query] = candidates;
          }
          Bridge.onResult(JSON.stringify({ results }));
        })().catch(error => Bridge.onResult(JSON.stringify({ error: String(error) })));
    """.trimIndent()

    private fun bestMatch(query: String, candidates: JSONArray): LedenicheurHistory? {
        for (index in 0 until candidates.length()) {
            val candidate = candidates.getJSONObject(index)
            val offersArray = candidate.getJSONArray("offers")
            val offers = (0 until offersArray.length()).map { offersArray.getJSONObject(it) }
            val productName = offers.firstOrNull()?.optString("name").orEmpty()
            if (offers.isEmpty() || !ProductNameMatcher.isSameProduct(query, productName)) continue
            return LedenicheurHistory(candidate.getString("productId"), productName, lowestOfferOverTime(offers))
        }
        return null
    }

    /**
     * Each history item is a price change of one shop. Replays them in order and records, at each
     * change date, the lowest price among the shops still offering the product.
     */
    private fun lowestOfferOverTime(offers: List<JSONObject>): List<PricePoint> {
        val currentPriceByShop = mutableMapOf<Long, Long>()
        val lowestByDay = sortedMapOf<Long, Long>()
        offers.sortedBy { it.getString("date") }.forEach { offer ->
            val shopId = offer.getLong("shopId")
            if (offer.optBoolean("active", true)) currentPriceByShop[shopId] = offer.getLong("price")
            else currentPriceByShop.remove(shopId)
            val day = Instant.parse(offer.getString("date")).atZone(PARIS).toLocalDate().toEpochDay()
            currentPriceByShop.values.minOrNull()?.let { lowestByDay[day] = it }
        }
        return lowestByDay.map { (day, price) -> PricePoint(day, price) }
    }
}
