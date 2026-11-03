package fr.floran.vraisbonsplans.model

import org.json.JSONArray
import org.json.JSONObject

enum class DealSource(val label: String) {
    IDEALO("idealo"),
    DEALABS("Dealabs · erreur de prix"),
}

/** A product seen on a source, after the real discount has been computed. */
/** Product page to open; falls back to the idealo id when the stored link is not a web address. */
val Deal.openableUrl: String
    get() = when {
        productUrl.startsWith("http") -> productUrl
        key.startsWith(IDEALO_KEY_PREFIX) -> "https://www.idealo.fr/prix/${key.removePrefix(IDEALO_KEY_PREFIX)}.html"
        else -> "https://www.idealo.fr/prechcat.html?q=" + java.net.URLEncoder.encode(name, "UTF-8")
    }

private const val IDEALO_KEY_PREFIX = "idealo:"

data class Deal(
    /** Stable key used to remember notified and ignored products, e.g. "idealo:201646095". */
    val key: String,
    val source: DealSource,
    val name: String,
    val detail: String,
    val imageUrl: String?,
    val productUrl: String,
    val currentPriceCents: Long,
    /** Usual price; null when unknown (Dealabs price error without a matching history). */
    val referencePriceCents: Long?,
    /** Discount claimed by the source (idealo's "-70 %"); null when the source gives none. */
    val sourceDiscountPercent: Int?,
    /** Discount computed by [fr.floran.vraisbonsplans.pricing.RealDiscountCalculator]; null when unknown. */
    val realDiscountPercent: Int?,
    val isNewLow: Boolean,
    /** Result of the Ledenicheur cross-check, shown as is. */
    val crossCheck: String?,
    /** Why the deal was rejected as a fake promotion; null for genuine deals. */
    val rejectionReason: String?,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("key", key)
        .put("source", source.name)
        .put("name", name)
        .put("detail", detail)
        .put("imageUrl", imageUrl)
        .put("productUrl", productUrl)
        .put("currentPriceCents", currentPriceCents)
        .put("referencePriceCents", referencePriceCents)
        .put("sourceDiscountPercent", sourceDiscountPercent)
        .put("realDiscountPercent", realDiscountPercent)
        .put("isNewLow", isNewLow)
        .put("crossCheck", crossCheck)
        .put("rejectionReason", rejectionReason)

    companion object {
        fun fromJson(json: JSONObject) = Deal(
            key = json.getString("key"),
            source = DealSource.valueOf(json.getString("source")),
            name = json.getString("name"),
            detail = json.optString("detail"),
            imageUrl = json.optStringOrNull("imageUrl"),
            productUrl = json.getString("productUrl"),
            currentPriceCents = json.getLong("currentPriceCents"),
            referencePriceCents = json.optLongOrNull("referencePriceCents"),
            sourceDiscountPercent = json.optLongOrNull("sourceDiscountPercent")?.toInt(),
            realDiscountPercent = json.optLongOrNull("realDiscountPercent")?.toInt(),
            isNewLow = json.optBoolean("isNewLow"),
            crossCheck = json.optStringOrNull("crossCheck"),
            rejectionReason = json.optStringOrNull("rejectionReason"),
        )

        fun listToJson(deals: List<Deal>): String = JSONArray(deals.map { it.toJson() }).toString()

        fun listFromJson(text: String?): List<Deal> {
            if (text.isNullOrEmpty()) return emptyList()
            val array = JSONArray(text)
            return (0 until array.length()).map { fromJson(array.getJSONObject(it)) }
        }

        private fun JSONObject.optStringOrNull(name: String): String? =
            if (isNull(name) || !has(name)) null else getString(name)

        private fun JSONObject.optLongOrNull(name: String): Long? =
            if (isNull(name) || !has(name)) null else getLong(name)
    }
}
