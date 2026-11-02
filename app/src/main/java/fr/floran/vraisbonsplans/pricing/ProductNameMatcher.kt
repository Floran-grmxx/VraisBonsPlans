package fr.floran.vraisbonsplans.pricing

import java.text.Normalizer

/** Decides whether two product titles from different sites describe the same product. */
object ProductNameMatcher {

    private const val MINIMUM_SIMILARITY = 0.6
    private val IGNORED_WORDS = setOf("de", "du", "la", "le", "les", "et", "avec", "pour", "en", "the", "and", "for", "with")

    fun tokens(title: String): Set<String> =
        Normalizer.normalize(title.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 && it !in IGNORED_WORDS }
            .toSet()

    /** Share of the reference title words found in the candidate title (0.0 to 1.0). */
    fun similarity(reference: String, candidate: String): Double {
        val referenceTokens = tokens(reference)
        if (referenceTokens.isEmpty()) return 0.0
        val candidateTokens = tokens(candidate)
        return referenceTokens.count { it in candidateTokens }.toDouble() / referenceTokens.size
    }

    fun isSameProduct(reference: String, candidate: String): Boolean =
        similarity(reference, candidate) >= MINIMUM_SIMILARITY

    /** Search query built from a title: brackets and seller notes removed, 6 words max. */
    fun searchQuery(title: String): String =
        title.replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
            .split(Regex("\\s+-\\s+|,"))
            .first()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(6)
            .joinToString(" ")
}
