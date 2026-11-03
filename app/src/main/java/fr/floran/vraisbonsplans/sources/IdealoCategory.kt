package fr.floran.vraisbonsplans.sources

import kotlin.math.ceil

/** Top-level idealo.fr categories, ids from https://cdn.idealo.com/storage/assets/navigation/navigation_fr_FR.json */
enum class IdealoCategory(val id: Int, val label: String) {
    ELECTRONICS(30311, "Électronique"),
    SPORT(3626, "Sport"),
    BABY(4033, "Puériculture"),
    HOME(3686, "Maison & jardin"),
    GROCERY(12913, "Épicerie"),
    GAMES_AND_TOYS(3326, "Jeux & jouets"),
    BEAUTY(3932, "Beauté & santé"),
    VEHICLES(2400, "Auto & moto"),
    FASHION(9908, "Mode"),
    PETS(7032, "Animalerie"),
}

/** One request to idealo's deals API: [categoryId] null means every category. */
data class DealsQuery(val categoryId: Int?, val count: Int)

object DealsQueryPlanner {

    /** idealo's deals API rejects 100 items per page or more. */
    const val MAXIMUM_ITEMS_PER_REQUEST = 99

    /**
     * Splits [productLimit] over the chosen categories. Without a choice, one global request is enough
     * up to 99 products; beyond that every top-level category is asked, which also widens the variety.
     */
    fun plan(categories: Set<IdealoCategory>, productLimit: Int): List<DealsQuery> {
        if (categories.isEmpty() && productLimit <= MAXIMUM_ITEMS_PER_REQUEST) return listOf(DealsQuery(null, productLimit))
        val chosen = categories.ifEmpty { IdealoCategory.entries.toSet() }.sortedBy { it.ordinal }
        val countPerCategory = ceil(productLimit.toDouble() / chosen.size).toInt().coerceIn(1, MAXIMUM_ITEMS_PER_REQUEST)
        return chosen.map { DealsQuery(it.id, countPerCategory) }
    }
}
