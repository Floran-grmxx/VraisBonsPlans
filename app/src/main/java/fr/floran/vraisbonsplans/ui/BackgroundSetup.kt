package fr.floran.vraisbonsplans.ui

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

/** Settings that HyperOS / MIUI require for background checks to survive (see SUIVI.md). */
object BackgroundSetup {

    private const val PREFERENCES_NAME = "setup"
    private const val KEY_AUTOSTART_CONFIRMED = "autostart_confirmed"

    fun isBatteryUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)

    @SuppressLint("BatteryLife") // The whole point of the app is a periodic background check.
    fun requestBatteryUnrestricted(context: Context) {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")),
        )
    }

    /** HyperOS hides "Démarrage automatique" in the Security app; falls back to the app settings page. */
    fun openAutostartSettings(context: Context) {
        val miuiAutostart = Intent().setComponent(
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        )
        val appDetails = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
        runCatching { context.startActivity(miuiAutostart) }.onFailure { context.startActivity(appDetails) }
    }

    fun isXiaomi(): Boolean = android.os.Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)

    fun isAutostartConfirmed(context: Context): Boolean =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).getBoolean(KEY_AUTOSTART_CONFIRMED, false)

    fun confirmAutostart(context: Context) {
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AUTOSTART_CONFIRMED, true).apply()
    }
}
