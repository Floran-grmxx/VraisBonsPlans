package fr.floran.vraisbonsplans.sources

import android.content.Context
import android.util.Log
import fr.floran.vraisbonsplans.pricing.ListingKind
import fr.floran.vraisbonsplans.pricing.ResaleEstimate
import fr.floran.vraisbonsplans.pricing.ResaleEstimator
import fr.floran.vraisbonsplans.pricing.ResaleListing
import org.json.JSONArray
import java.net.URLEncoder

/** Where the resale price can be checked by hand, as search pages. */
enum class ResaleSite(val label: String, private val searchUrlPrefix: String, private val searchUrlSuffix: String = "") {
    VINTED("Vinted", "https://www.vinted.fr/catalog?search_text="),
    LEBONCOIN("Leboncoin", "https://www.leboncoin.fr/recherche?text="),
    /** eBay shows a captcha to automated browsers: link only, never read. */
    EBAY_SOLD("eBay vendus", "https://www.ebay.fr/sch/i.html?_nkw=", "&LH_Sold=1&LH_Complete=1"),
    ;

    fun searchUrl(query: String): String = searchUrlPrefix + URLEncoder.encode(query, "UTF-8") + searchUrlSuffix
}

/**
 * Reads second-hand listings on Vinted and Leboncoin inside [HiddenBrowser] (both reject plain
 * HTTP clients) and estimates the resale price of a deal.
 */
object ResaleSource {

    private const val TAG = "ResaleSource"
    private const val PAGE_TIMEOUT_MILLIS = 45_000L
    private const val BLOCKED_WAIT_MILLIS = 8_000L
    private const val BLOCKED = "blocked"

    private class BlockedException(site: ResaleSite) : Exception("${site.label} anti-bot page")

    private class Lookup(val matchingPricesCents: List<Long>, val unreadSites: List<String>)

    /** Each script returns `[{title, price, url, condition}]` through `Bridge.onResult`. */
    private val VINTED_SCRIPT = """
        (() => {
          const items = [];
          document.querySelectorAll('a[data-testid${'$'}="--overlay-link"][title]').forEach(link => {
            // Title attribute: "<name>, Marque: X, État: Y, <price> €, <price with buyer fees> €"
            const parts = link.getAttribute('title').split(',').map(part => part.trim());
            const price = parts.find(part => /€${'$'}/.test(part));
            if (!price) return;
            const condition = (parts.find(part => part.startsWith('État')) || '').split(':').slice(1).join(':').trim();
            items.push({ title: parts[0], price: price, url: link.href, condition: condition });
          });
          Bridge.onResult(JSON.stringify(items.slice(0, 30)));
        })()
    """

    private val LEBONCOIN_SCRIPT = """
        (() => {
          const read = () => {
            const data = document.getElementById('__NEXT_DATA__');
            if (!data) return false;
            const ads = (((JSON.parse(data.textContent).props || {}).pageProps || {}).searchData || {}).ads || [];
            Bridge.onResult(JSON.stringify(ads.filter(ad => ad.price && ad.price.length).slice(0, 30).map(ad => {
              const condition = (ad.attributes || []).find(attribute => attribute.key === 'condition');
              return { title: ad.subject, price: String(ad.price[0]), url: ad.url, condition: condition ? condition.value_label : '' };
            })));
            return true;
          };
          if (read()) return;
          // DataDome check page (titled "leboncoin.fr"): it may pass by itself and reload the
          // results, which runs this script again; otherwise report the block.
          setTimeout(() => { if (!read()) Bridge.onResult('"$BLOCKED"'); }, ${BLOCKED_WAIT_MILLIS});
        })()
    """

    private val READ_SITES = listOf(ResaleSite.VINTED to VINTED_SCRIPT, ResaleSite.LEBONCOIN to LEBONCOIN_SCRIPT)

    suspend fun estimate(context: Context, dealTitle: String, dealPriceCents: Long): ResaleEstimate {
        var query = ResaleEstimator.searchQuery(dealTitle)
        var lookup = lookUp(context, query, dealTitle, dealPriceCents)
        // Sellers often skip the brand (distributor names like "Asmodée" are never typed).
        val withoutBrand = ResaleEstimator.searchQueryWithoutBrand(query)
        if (lookup.matchingPricesCents.size < ResaleEstimator.FEW_LISTINGS && withoutBrand != null) {
            val retry = lookUp(context, withoutBrand, dealTitle, dealPriceCents)
            if (retry.matchingPricesCents.size > lookup.matchingPricesCents.size) {
                query = withoutBrand
                lookup = retry
            }
        }
        return ResaleEstimator.estimate(query, lookup.matchingPricesCents, dealPriceCents)
            .copy(unreadSites = lookup.unreadSites)
    }

    private suspend fun lookUp(context: Context, query: String, dealTitle: String, dealPriceCents: Long): Lookup {
        val unreadSites = mutableListOf<String>()
        val listings = READ_SITES.flatMap { (site, script) ->
            try {
                parse(site, HiddenBrowser.runOnPage(context, site.searchUrl(query), script, PAGE_TIMEOUT_MILLIS))
                    .also { Log.i(TAG, "${site.label}: ${it.size} listings for « $query »") }
            } catch (error: Exception) {
                // A blocked site must not stop the other one.
                Log.w(TAG, "${site.label} failed for « $query »: $error")
                unreadSites += site.label
                emptyList()
            }
        }
        val matching = listings.filter { ResaleEstimator.classify(it, dealTitle, dealPriceCents, query) == ListingKind.MATCH }
        Log.i(TAG, "« $query » → ${listings.size} listings, ${matching.size} matching")
        return Lookup(matching.map { it.priceCents }, unreadSites)
    }

    private fun parse(site: ResaleSite, json: String): List<ResaleListing> {
        if (json == "\"$BLOCKED\"") throw BlockedException(site)
        val array = JSONArray(json)
        return (0 until array.length()).mapNotNull { index ->
            val item = array.getJSONObject(index)
            val priceCents = ResaleEstimator.parsePriceCents(item.optString("price")) ?: return@mapNotNull null
            ResaleListing(site.label, item.optString("title"), priceCents, item.optString("url"), item.optString("condition"))
        }
    }
}
