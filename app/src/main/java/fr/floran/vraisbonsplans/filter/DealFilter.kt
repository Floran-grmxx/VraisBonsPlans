package fr.floran.vraisbonsplans.filter

import fr.floran.vraisbonsplans.model.Deal
import fr.floran.vraisbonsplans.model.DealSource
import java.text.Normalizer

enum class DealSort(val label: String) {
    BIGGEST_DISCOUNT("Plus grosse réduction"),
    LOWEST_PRICE("Prix croissant"),
    HIGHEST_PRICE("Prix décroissant"),
}

/** Search and filters typed on the main screen; applied to every list of deals. */
data class DealFilter(
    val query: String = "",
    val minimumPriceCents: Long? = null,
    val maximumPriceCents: Long? = null,
    val sources: Set<DealSource> = DealSource.entries.toSet(),
    val sort: DealSort = DealSort.BIGGEST_DISCOUNT,
) {
    val activeFilterCount: Int
        get() = listOf(
            minimumPriceCents != null,
            maximumPriceCents != null,
            sources.size != DealSource.entries.size,
            sort != DealSort.BIGGEST_DISCOUNT,
        ).count { it }

    fun apply(deals: List<Deal>): List<Deal> {
        // Every typed word must appear in the name or detail, accents and case ignored.
        val queryWords = words(query)
        val filtered = deals.filter { deal ->
            val dealWords = words(deal.name + " " + deal.detail)
            val matchesQuery = queryWords.all { word -> dealWords.any { it.startsWith(word) } }
            val price = deal.currentPriceCents
            val hasKnownPrice = price > 0
            matchesQuery &&
                deal.source in sources &&
                (minimumPriceCents == null || (hasKnownPrice && price >= minimumPriceCents)) &&
                (maximumPriceCents == null || (hasKnownPrice && price <= maximumPriceCents))
        }
        return when (sort) {
            DealSort.BIGGEST_DISCOUNT -> filtered.sortedByDescending { it.realDiscountPercent ?: 0 }
            DealSort.LOWEST_PRICE -> filtered.sortedBy { it.currentPriceCents.takeIf { price -> price > 0 } ?: Long.MAX_VALUE }
            DealSort.HIGHEST_PRICE -> filtered.sortedByDescending { it.currentPriceCents }
        }
    }

    /** Lowercase words without accents; unlike the title matcher, short words like "5" are kept. */
    private fun words(text: String): List<String> =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .split(Regex("[^a-z0-9]+"))
            .filter { it.isNotEmpty() }

    companion object {
        /** "49", "49,90" or "49.9 €" → cents; null when empty or not a number. */
        fun parseEuros(text: String): Long? {
            val cleaned = text.replace("€", "").replace(" ", "").replace(',', '.')
            if (cleaned.isEmpty()) return null
            val euros = cleaned.toDoubleOrNull() ?: return null
            return if (euros < 0) null else Math.round(euros * 100)
        }
    }
}
