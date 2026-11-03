package fr.floran.vraisbonsplans.sources

import org.junit.Assert.assertEquals
import org.junit.Test

class DealsQueryPlannerTest {

    @Test
    fun everyCategoryUpTo99ProductsIsOneGlobalRequest() {
        assertEquals(listOf(DealsQuery(null, 50)), DealsQueryPlanner.plan(emptySet(), 50))
    }

    @Test
    fun everyCategoryBeyond99ProductsAsksEachTopLevelCategory() {
        val queries = DealsQueryPlanner.plan(emptySet(), 200)
        assertEquals(IdealoCategory.entries.size, queries.size)
        assertEquals(20, queries.first().count)
        assertEquals(IdealoCategory.ELECTRONICS.id, queries.first().categoryId)
    }

    @Test
    fun chosenCategoriesShareTheLimitWithinTheApiMaximum() {
        assertEquals(
            listOf(DealsQuery(IdealoCategory.ELECTRONICS.id, 50), DealsQuery(IdealoCategory.FASHION.id, 50)),
            DealsQueryPlanner.plan(setOf(IdealoCategory.FASHION, IdealoCategory.ELECTRONICS), 100),
        )
        assertEquals(listOf(DealsQuery(IdealoCategory.PETS.id, 99)), DealsQueryPlanner.plan(setOf(IdealoCategory.PETS), 200))
    }
}
