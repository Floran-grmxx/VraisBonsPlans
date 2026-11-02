package fr.floran.vraisbonsplans.scan

import android.content.Context

data class ScanSettings(
    val alertsEnabled: Boolean,
    /** Minimum real discount, in percent (30, 50, 75 or 90). */
    val minimumDiscountPercent: Int,
    /** Check interval chosen by the user, in minutes. */
    val intervalMinutes: Int,
) {
    /** Price errors disappear within hours: high thresholds are checked every 15 minutes. */
    val effectiveIntervalMinutes: Int
        get() = if (minimumDiscountPercent >= FAST_CHECK_THRESHOLD) MINIMUM_INTERVAL_MINUTES else intervalMinutes

    companion object {
        val DISCOUNT_CHOICES = listOf(30, 50, 75, 90)
        val INTERVAL_CHOICES = listOf(15, 60, 360, 720)
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

    fun read(context: Context): ScanSettings {
        val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        return ScanSettings(
            alertsEnabled = preferences.getBoolean(KEY_ENABLED, true),
            minimumDiscountPercent = preferences.getInt(KEY_DISCOUNT, 30),
            intervalMinutes = preferences.getInt(KEY_INTERVAL, 60),
        )
    }

    fun write(context: Context, settings: ScanSettings) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, settings.alertsEnabled)
            .putInt(KEY_DISCOUNT, settings.minimumDiscountPercent)
            .putInt(KEY_INTERVAL, settings.intervalMinutes)
            .apply()
    }
}
