package fr.floran.vraisbonsplans.scan

import android.content.Context
import android.util.Log
import fr.floran.vraisbonsplans.model.Deal
import fr.floran.vraisbonsplans.model.DealSource
import fr.floran.vraisbonsplans.pricing.DiscountAnalysis
import fr.floran.vraisbonsplans.pricing.RealDiscountCalculator
import fr.floran.vraisbonsplans.sources.DealabsItem
import fr.floran.vraisbonsplans.sources.DealabsSource
import fr.floran.vraisbonsplans.sources.IdealoProduct
import fr.floran.vraisbonsplans.sources.IdealoSource
import fr.floran.vraisbonsplans.sources.LedenicheurHistory
import fr.floran.vraisbonsplans.sources.LedenicheurSource
import fr.floran.vraisbonsplans.ui.formatEuros
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Reads every source, keeps the genuine deals and explains why the others are fake. */
object DealScanner {

    private const val TAG = "DealScanner"
    /** Dealabs price errors older than this are usually fixed or sold out. */
    private const val DEALABS_MAXIMUM_AGE_SECONDS = 48 * 3_600L
    /** From this real discount on, a second source must confirm the deal before any notification. */
    private const val CONFIRMATION_REQUIRED_FROM_PERCENT = 50
    private const val IDEALO_STEP_END = 0.55f
    private const val DEALABS_STEP_END = 0.6f
    private const val LEDENICHEUR_STEP_END = 0.97f

    /**
     * [onProgress] receives the fraction the progress bar may reach during the step that starts,
     * so the bar keeps moving forward while a slow page loads.
     */
    suspend fun scan(context: Context, settings: ScanSettings, onProgress: suspend (Float) -> Unit = {}): ScanResult {
        val threshold = settings.minimumDiscountPercent
        val today = LocalDate.now().toEpochDay()
        val errors = mutableListOf<String>()

        // 1. idealo: genuine deals vs fake promotions.
        val idealoCandidates = mutableListOf<Deal>()
        val consistentHistoryKeys = mutableSetOf<String>()
        val fakeDeals = mutableListOf<Deal>()
        onProgress(IDEALO_STEP_END)
        runSource("idealo", errors) {
            IdealoSource.fetchDeals(context, settings.categories, settings.productLimit).forEach { product ->
                val analysis = RealDiscountCalculator.analyze(product.history, product.currentPriceCents, today)
                when {
                    analysis != null && analysis.realDiscountPercent >= threshold && analysis.isNewLow ->
                        idealoDeal(product, analysis, rejectionReason = null).also { deal ->
                            idealoCandidates += deal
                            if (analysis.isHistoryConsistent) consistentHistoryKeys += deal.key
                        }
                    product.idealoDiscountPercent >= threshold ->
                        fakeDeals += idealoDeal(product, analysis, rejectionReason = fakeReason(analysis))
                }
            }
        }

        // 2. Dealabs: recent price errors reported by the community.
        val dealabsItems = mutableListOf<DealabsItem>()
        onProgress(DEALABS_STEP_END)
        runSource("Dealabs", errors) {
            val now = System.currentTimeMillis() / 1_000
            dealabsItems += withContext(Dispatchers.IO) { DealabsSource.fetchPriceErrors() }
                .filter { now - it.publishedAtEpochSecond <= DEALABS_MAXIMUM_AGE_SECONDS }
        }

        // 3. Ledenicheur: one page for every lookup of this scan.
        val titles = idealoCandidates.map { it.name } + dealabsItems.filter { it.priceCents != null }.map { it.title }
        var histories = emptyMap<String, LedenicheurHistory?>()
        onProgress(LEDENICHEUR_STEP_END)
        runSource("Ledenicheur", errors) { histories = LedenicheurSource.findHistories(context, titles) }

        val deals = mutableListOf<Deal>()
        val dealsToVerify = mutableListOf<Deal>()
        idealoCandidates.forEach { candidate ->
            val hasConsistentHistory = candidate.key in consistentHistoryKeys
            when (val verdict = checkWithLedenicheur(candidate, histories[candidate.name], hasConsistentHistory, threshold, today)) {
                is Verdict.Genuine -> deals += verdict.deal
                is Verdict.Fake -> fakeDeals += verdict.deal
                is Verdict.ToVerify -> dealsToVerify += verdict.deal
            }
        }

        dealabsItems.forEach { item ->
            val deal = dealabsDeal(item, histories[item.title], today)
            val discount = deal.realDiscountPercent
            // A reported price error stays an alert when its usual price is unknown.
            if (discount == null || discount >= threshold) deals += deal.copy(rejectionReason = null) else fakeDeals += deal
        }

        return ScanResult(
            finishedAtMillis = System.currentTimeMillis(),
            deals = deals.sortedByDescending { it.realDiscountPercent ?: 100 },
            dealsToVerify = dealsToVerify.sortedByDescending { it.realDiscountPercent ?: 0 },
            fakeDeals = fakeDeals.sortedByDescending { it.sourceDiscountPercent ?: 0 },
            errors = errors,
        )
    }

    /** A failing source is reported on screen but never stops the other sources. */
    private suspend fun runSource(name: String, errors: MutableList<String>, block: suspend () -> Unit) {
        try {
            block()
        } catch (error: Exception) {
            if (error is CancellationException && error !is TimeoutCancellationException) throw error
            Log.e(TAG, "$name failed", error)
            errors += "$name : ${error.javaClass.simpleName} ${error.message.orEmpty()}".trim()
        }
    }

    private fun idealoDeal(product: IdealoProduct, analysis: DiscountAnalysis?, rejectionReason: String?) = Deal(
        key = "idealo:${product.id}",
        source = DealSource.IDEALO,
        name = product.name,
        detail = "",
        imageUrl = product.imageUrl,
        productUrl = product.productUrl,
        currentPriceCents = product.currentPriceCents,
        referencePriceCents = analysis?.referencePriceCents,
        sourceDiscountPercent = product.idealoDiscountPercent,
        realDiscountPercent = analysis?.realDiscountPercent,
        isNewLow = analysis?.isNewLow ?: false,
        crossCheck = null,
        rejectionReason = rejectionReason,
    )

    private fun fakeReason(analysis: DiscountAnalysis?): String = when {
        analysis == null -> "Historique de prix indisponible"
        analysis.realDiscountPercent < 5 ->
            "Prix habituel ces 30 derniers jours : ${formatEuros(analysis.referencePriceCents)} (référence idealo gonflée)"
        !analysis.isNewLow -> "Déjà vu moins cher : ${formatEuros(analysis.lowestPriceCents)} ces 3 derniers mois"
        else -> "Réduction réelle de -${analysis.realDiscountPercent} % seulement"
    }

    private sealed interface Verdict {
        val deal: Deal
        data class Genuine(override val deal: Deal) : Verdict
        data class Fake(override val deal: Deal) : Verdict
        /** Big discount that no second source could confirm: shown, never notified. */
        data class ToVerify(override val deal: Deal) : Verdict
    }

    /**
     * Ledenicheur as second opinion: when it knows the product and disagrees, the deal is fake.
     * Without it, only moderate discounts on a stable idealo history are trusted: idealo and
     * marketplace sellers often keep absurd prices for weeks, faking -60 to -90 % deals.
     */
    private fun checkWithLedenicheur(
        candidate: Deal,
        history: LedenicheurHistory?,
        hasConsistentHistory: Boolean,
        threshold: Int,
        today: Long,
    ): Verdict {
        val analysis = history?.let { RealDiscountCalculator.analyze(it.history, candidate.currentPriceCents, today) }
        if (analysis != null && analysis.isHistoryConsistent) {
            val usual = formatEuros(analysis.referencePriceCents)
            return if (analysis.realDiscountPercent >= threshold && analysis.isNewLow) {
                Verdict.Genuine(
                    candidate.copy(
                        realDiscountPercent = minOf(candidate.realDiscountPercent ?: 0, analysis.realDiscountPercent),
                        crossCheck = "Confirmé par Ledenicheur : habituellement $usual ✓",
                    ),
                )
            } else {
                val reason = if (!analysis.isNewLow) "déjà vu à ${formatEuros(analysis.lowestPriceCents)}"
                else "-${analysis.realDiscountPercent} % seulement"
                Verdict.Fake(candidate.copy(rejectionReason = "Ledenicheur : habituellement $usual, $reason"))
            }
        }
        val isModerate = (candidate.realDiscountPercent ?: 0) < CONFIRMATION_REQUIRED_FROM_PERCENT
        return if (hasConsistentHistory && isModerate) {
            Verdict.Genuine(candidate.copy(crossCheck = "Non recoupé"))
        } else {
            Verdict.ToVerify(
                candidate.copy(
                    crossCheck = if (hasConsistentHistory) "Non confirmé" else "Historique douteux",
                ),
            )
        }
    }

    /** Dealabs gives no old price: the usual price comes from Ledenicheur when the product is found. */
    private fun dealabsDeal(item: DealabsItem, history: LedenicheurHistory?, today: Long): Deal {
        val analysis = if (history != null && item.priceCents != null) {
            RealDiscountCalculator.analyze(history.history, item.priceCents, today)
        } else {
            null
        }
        return Deal(
            key = "dealabs:${item.url}",
            source = DealSource.DEALABS,
            name = item.title,
            detail = "Chez ${item.merchant}",
            imageUrl = item.imageUrl,
            productUrl = item.url,
            currentPriceCents = item.priceCents ?: 0L,
            referencePriceCents = analysis?.referencePriceCents,
            sourceDiscountPercent = null,
            realDiscountPercent = analysis?.realDiscountPercent,
            isNewLow = analysis?.isNewLow ?: true,
            crossCheck = if (analysis != null) "Ledenicheur : habituellement ${formatEuros(analysis.referencePriceCents)}"
            else "Prix habituel inconnu (produit absent de Ledenicheur)",
            rejectionReason = analysis?.let { "Ledenicheur : seulement -${it.realDiscountPercent} % par rapport au prix habituel" },
        )
    }
}
