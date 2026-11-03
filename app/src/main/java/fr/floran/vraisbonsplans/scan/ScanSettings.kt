package fr.floran.vraisbonsplans.scan

import android.content.Context
import fr.floran.vraisbonsplans.sources.IdealoCategory

data class ScanSettings(
    val alertsEnabled: Boolean,
    /** Minimum real discount, in percent (30, 50, 75 or 90). */
    val minimumDiscountPercent: Int,
    /** Check interval chosen by the user, in minutes. */
    val intervalMinutes: Int,
    /** idealo categories to scan; empty means every category. */
    val categories: Set<IdealoCategory> = emptySet(),
    /** Number of idealo products whose price history is analysed per scan. */
    val productLimit: Int = DEFAULT_PRODUCT_LIMIT,
) {
    /** Price errors disappear within hours: high thresholds are checked every 15 minutes. */
    val effectiveIntervalMinutes: Int
        get() = if (minimumDiscountPercent >= FAST_CHECK_THRESHOLD) MINIMUM_INTERVAL_MINUTES else intervalMinutes

    companion object {
        val DISCOUNT_CHOICES = listOf(30, 50, 75, 90)
        val INTERVAL_CHOICES = listOf(15, 60, 360, 720)
        val PRODUCT_LIMIT_CHOICES = listOf(50, 100, 200)
        const val DEFAULT_PRODUCT_LIMIT = 100
        const val MINIMUM_INTERVAL_MINUTES = 15
        private const val FAST_CHECK_THRESHOLD = 75
    }
}

/** Settings persisted in SharedPreferences (no DataStore, per project conventions). */
object ScanSettingsStore {

    private const val PREFERENCES_NAME = "settings"
    private const val KEY_ENABLED = "alerts_enabled"
    private const val KEY_DISCOUNT = "minimum_discount_percent"
    private const val KEY_INTERVAL = "interval_minutes"
    private const val KEY_CATEGORIES = "categories"
    private const val KEY_PRODUCT_LIMIT = "product_limit"

    fun read(context: Context): ScanSettings {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return ScanSettings(
            alertsEnabled = preferences.getBoolean(KEY_ENABLED, true),
            minimumDiscountPercent = preferences.getInt(KEY_DISCOUNT, 30),
            intervalMinutes = preferences.getInt(KEY_INTERVAL, 60),
            categories = preferences.getStringSet(KEY_CATEGORIES, emptySet()).orEmpty()
                .mapNotNull { name -> IdealoCategory.entries.firstOrNull { it.name == name } }
                .toSet(),
            productLimit = preferences.getInt(KEY_PRODUCT_LIMIT, ScanSettings.DEFAULT_PRODUCT_LIMIT),
        )
    }

    fun write(context: Context, settings: ScanSettings) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, settings.alertsEnabled)
            .putInt(KEY_DISCOUNT, settings.minimumDiscountPercent)
            .putInt(KEY_INTERVAL, settings.intervalMinutes)
            .putStringSet(KEY_CATEGORIES, settings.categories.map { it.name }.toSet())
            .putInt(KEY_PRODUCT_LIMIT, settings.productLimit)
            .apply()
    }
}
