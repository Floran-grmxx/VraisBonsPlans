package fr.floran.vraisbonsplans.pricing

import kotlin.math.roundToInt

/** A price observed from [epochDay] until the next point (price histories only list change days). */
data class PricePoint(val epochDay: Long, val priceCents: Long)

data class DiscountAnalysis(
    val currentPriceCents: Long,
    /** Usual price: time-weighted median of the last 30 days, absurd spikes and dips removed. */
    val referencePriceCents: Long,
    /** Lowest plausible price of the history, the last 2 days excluded. */
    val lowestPriceCents: Long,
    val realDiscountPercent: Int,
    val isNewLow: Boolean,
    /**
     * False when the reference is far above a low price that held for days: the "usual" price may
     * itself be inflated for weeks (e.g. a TV listed 3 400 € for 2 months by marketplace sellers,
     * really sold 600 €). Such a discount must be confirmed by another source.
     */
    val isHistoryConsistent: Boolean,
)

/**
 * Computes a discount that resists the tricks seen on idealo: a price inflated for a few days
 * (e.g. 670 € instead of 119 €) inflates the 3-month average and fakes a "-70 %" deal.
 */
object RealDiscountCalculator {

    private const val REFERENCE_WINDOW_DAYS = 30
    /** The very last days are excluded so that the drop being analysed does not lower its own reference. */
    private const val RECENT_DAYS_EXCLUDED = 2
    /** Prices more than 3 times above the low prices (or below the high ones) are input errors. */
    private const val OUTLIER_RATIO = 3.0
    /** A low price must hold this long to prove what the product really sells for. */
    private const val SUSTAINED_DAYS = 3
    private const val MAXIMUM_REFERENCE_TO_SUSTAINED_LOW_RATIO = 2.5

    fun analyze(history: List<PricePoint>, currentPriceCents: Long, todayEpochDay: Long): DiscountAnalysis? {
        val segments = toSegments(history.sortedBy { it.epochDay }, todayEpochDay)
        if (segments.isEmpty() || currentPriceCents <= 0) return null

        // Outliers are judged against the low / high quarters, not the median: when an absurd price
        // lasts longer than the real one, it becomes the median itself.
        // Applied one after the other: with two long price levels, both filters at once would exclude everything.
        val lowQuarter = weightedPercentile(segments, 0.25) ?: return null
        val withoutSpikes = segments.filter { it.priceCents <= lowQuarter * OUTLIER_RATIO }
        val highQuarter = weightedPercentile(withoutSpikes, 0.75) ?: return null
        val plausible = withoutSpikes.filter { it.priceCents >= highQuarter / OUTLIER_RATIO }
        if (plausible.isEmpty()) return null

        val windowEnd = todayEpochDay - RECENT_DAYS_EXCLUDED
        val windowStart = windowEnd - REFERENCE_WINDOW_DAYS
        val referenceSegments = plausible.mapNotNull { it.clip(windowStart, windowEnd) }
        val reference = weightedPercentile(referenceSegments, 0.5) ?: weightedPercentile(plausible, 0.5) ?: return null

        val olderSegments = plausible.filter { it.startDay < windowEnd }
        val lowest = (olderSegments.ifEmpty { plausible }).minOf { it.priceCents }

        val sustainedLow = segments
            .mapNotNull { it.clip(Long.MIN_VALUE, windowEnd) }
            .filter { it.days >= SUSTAINED_DAYS }
            .minOfOrNull { it.priceCents }

        val discount = ((1.0 - currentPriceCents.toDouble() / reference) * 100).roundToInt().coerceAtLeast(0)
        return DiscountAnalysis(
            currentPriceCents = currentPriceCents,
            referencePriceCents = reference,
            lowestPriceCents = lowest,
            realDiscountPercent = discount,
            isNewLow = currentPriceCents <= lowest,
            isHistoryConsistent = sustainedLow == null || reference <= sustainedLow * MAXIMUM_REFERENCE_TO_SUSTAINED_LOW_RATIO,
        )
    }

    private data class Segment(val startDay: Long, val endDay: Long, val priceCents: Long) {
        val days: Long get() = endDay - startDay

        fun clip(fromDay: Long, toDay: Long): Segment? {
            val start = maxOf(startDay, fromDay)
            val end = minOf(endDay, toDay)
            return if (end > start) Segment(start, end, priceCents) else null
        }
    }

    private fun toSegments(points: List<PricePoint>, todayEpochDay: Long): List<Segment> =
        points.mapIndexedNotNull { index, point ->
            val end = points.getOrNull(index + 1)?.epochDay ?: (todayEpochDay + 1)
            if (end > point.epochDay) Segment(point.epochDay, end, point.priceCents) else null
        }

    /** Price below which [fraction] of the time was spent (0.5 = time-weighted median). */
    private fun weightedPercentile(segments: List<Segment>, fraction: Double): Long? {
        val totalDays = segments.sumOf { it.days }
        if (totalDays <= 0) return null
        var accumulated = 0L
        for (segment in segments.sortedBy { it.priceCents }) {
            accumulated += segment.days
            if (accumulated >= totalDays * fraction) return segment.priceCents
        }
        return null
    }
}
