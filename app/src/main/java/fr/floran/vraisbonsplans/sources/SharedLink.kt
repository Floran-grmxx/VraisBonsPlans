package fr.floran.vraisbonsplans.sources

/** Reads the idealo link out of the text the idealo app shares ("Regarde ce produit… https://…"). */
object SharedLink {

    private val URL_PATTERN = Regex("""https?://[^\s"'<>]+""")
    private val PRODUCT_ID_PATTERN = Regex("""/prix/(\d+)""")

    /** First idealo address of [text], trailing punctuation removed; null when there is none. */
    fun idealoUrl(text: String): String? =
        URL_PATTERN.findAll(text)
            .map { it.value.trimEnd('.', ',', ';', ')', '!', '?') }
            .firstOrNull { url -> hostOf(url)?.let { it == "idealo.fr" || it.endsWith(".idealo.fr") } == true }

    /** idealo product id of a /prix/{id} link; null for short or tracking links, resolved by the page itself. */
    fun productId(url: String): String? = PRODUCT_ID_PATTERN.find(url)?.groupValues?.get(1)

    private fun hostOf(url: String): String? =
        url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':').lowercase().ifEmpty { null }
}
