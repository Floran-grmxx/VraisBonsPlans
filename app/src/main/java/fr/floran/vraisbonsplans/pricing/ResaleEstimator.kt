package fr.floran.vraisbonsplans.pricing

import java.text.Normalizer

/** A second-hand listing read on a resale site. */
data class ResaleListing(
    val source: String,
    val title: String,
    val priceCents: Long,
    val url: String,
    val condition: String,
)

enum class ListingKind { MATCH, ACCESSORY, UNRELATED, OTHER_CAPACITY }

/** Resale price estimated from the listings that match the product. */
data class ResaleEstimate(
    val query: String,
    val medianCents: Long?,
    val matchCount: Int,
    val gainCents: Long?,
    /** Sites that blocked the lookup (anti-bot page): the estimate comes from the others only. */
    val unreadSites: List<String> = emptyList(),
) {
    /** Rare items have few listings: the estimate is shown but flagged. */
    val hasFewListings: Boolean get() = matchCount in 1 until ResaleEstimator.FEW_LISTINGS
}

/**
 * Turns an idealo title into the search second-hand sellers would type (brand + model + capacity),
 * then keeps only the listings of that very product to estimate its resale price.
 */
object ResaleEstimator {

    const val FEW_LISTINGS = 3
    private const val MAXIMUM_NAME_WORDS = 6
    private const val MINIMUM_SHARED_WORDS = 0.6
    /** Below this share of the deal price, a listing is an accessory, a box or a broken unit. */
    private const val MINIMUM_PRICE_RATIO = 0.35

    private val STOP_WORDS = setOf("de", "du", "la", "le", "les", "et", "pour", "avec", "en", "a", "the", "with", "to")
    private val ACCESSORY_WORDS = setOf(
        "coque", "housse", "etui", "case", "cover", "cable", "chargeur", "boite", "vide", "protection",
        "verre", "trempe", "film", "support", "bracelet", "compatible", "sticker", "skin", "notice",
        "embout", "embouts", "pochette", "sacoche", "adaptateur", "piece", "pieces", "hs", "panne", "lot",
        "casse", "cassee", "fissure", "bloque", "icloud", "defectueux",
    )
    private val COLOR_WORDS = setOf(
        "noir", "blanc", "gris", "argent", "or", "bleu", "rouge", "vert", "rose", "violet", "lavande",
        "jaune", "orange", "beige", "graphite", "titane", "black", "white", "silver", "gold", "blue",
    )
    /** The first word describing a variant (capacity, color, specs) ends the basic product name. */
    private val VARIANT_WORD = Regex(
        "^(\\d+([.,]\\d+)?(go|gb|to|tb|mo|mb|w|v|mah|hz|l|cm|mm)|go|gb|to|tb|kit|rgb|ddr\\d.*|cl\\d+|\\d+pouces|pouces)$",
    )
    private val CAPACITY = Regex("\\b(\\d+)\\s?(go|gb|to|tb)\\b")
    private val BRACKETS = Regex("\\(.*?\\)")

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")

    /** "64GB", "64 Go" and "64go" give the same word. */
    fun tokens(text: String): Set<String> =
        CAPACITY.replace(normalize(text)) { match ->
            match.groupValues[1] + if (match.groupValues[2] in setOf("to", "tb")) "to" else "go"
        }
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() && it !in STOP_WORDS }
            .toSet()

    /** Storage or memory size such as "128 Go"; Go and GB are the same unit for sellers. */
    fun capacityOf(text: String): String? {
        val match = CAPACITY.find(normalize(text)) ?: return null
        val unit = if (match.groupValues[2] in setOf("to", "tb")) "To" else "Go"
        return "${match.groupValues[1]} $unit"
    }

    /** Manufacturer references such as RH2079WO or B3570N: letters and digits mixed, 6+ characters. */
    private fun isReferenceCode(word: String): Boolean =
        word.length >= 6 && word.any { it.isLetter() } && word.any { it.isDigit() }

    /** Basic product name (brand + model) plus its capacity; color, specs and references are left out. */
    fun searchQuery(title: String): String {
        val words = title.replace(BRACKETS, " ").split(Regex("\\s+")).filter { it.isNotBlank() }
        val basic = mutableListOf<String>()
        for (word in words) {
            val plain = normalize(word).trim(',', ';')
            if (VARIANT_WORD.matches(plain) || plain in COLOR_WORDS) {
                // "128 Go": the number before a separate unit is the capacity, added back below.
                if (basic.isNotEmpty() && basic.last().all { it.isDigit() } && plain in setOf("go", "gb", "to", "tb")) {
                    basic.removeAt(basic.lastIndex)
                }
                break
            }
            basic += word
        }
        // Drop reference codes, unless the name would shrink to the brand alone.
        val withoutCodes = basic.filterNot { isReferenceCode(normalize(it)) }
        val name = (if (withoutCodes.size >= 2) withoutCodes else basic).take(MAXIMUM_NAME_WORDS)
        return (name + listOfNotNull(capacityOf(title.replace(BRACKETS, " ")))).joinToString(" ")
    }

    /** Same search without the brand: sellers rarely type distributor names such as "Asmodée". */
    fun searchQueryWithoutBrand(query: String): String? {
        val rest = query.split(" ").drop(1)
        return if (rest.size >= 2) rest.joinToString(" ") else null
    }

    fun classify(listing: ResaleListing, dealTitle: String, dealPriceCents: Long, query: String): ListingKind {
        val words = tokens(listing.title)
        if (words.any { it in ACCESSORY_WORDS } || listing.priceCents < MINIMUM_PRICE_RATIO * dealPriceCents) {
            return ListingKind.ACCESSORY
        }
        val keyWords = tokens(query)
        if (keyWords.isNotEmpty() && keyWords.count { it in words }.toDouble() / keyWords.size < MINIMUM_SHARED_WORDS) {
            return ListingKind.UNRELATED
        }
        // A listing without a stated capacity is kept; a different one is another product price-wise.
        val dealCapacity = capacityOf(dealTitle)
        val listingCapacity = capacityOf(listing.title)
        if (dealCapacity != null && listingCapacity != null && listingCapacity != dealCapacity) {
            return ListingKind.OTHER_CAPACITY
        }
        return ListingKind.MATCH
    }

    fun estimate(query: String, matchingPricesCents: List<Long>, dealPriceCents: Long): ResaleEstimate {
        val median = median(matchingPricesCents)
        return ResaleEstimate(query, median, matchingPricesCents.size, median?.let { it - dealPriceCents })
    }

    private fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }

    /** "1 916,90 €", "190.00 €" or "200" → cents; null when no amount can be read. */
    fun parsePriceCents(text: String): Long? {
        val cleaned = text.replace(Regex("[^0-9,.]"), "").replace(',', '.')
        val amount = cleaned.toDoubleOrNull() ?: return null
        return Math.round(amount * 100)
    }
}
