package fr.floran.vraisbonsplans

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.floran.vraisbonsplans.scan.ScanScheduler
import fr.floran.vraisbonsplans.scan.ScanSettingsStore
import fr.floran.vraisbonsplans.ui.BackgroundSetup
import fr.floran.vraisbonsplans.ui.MainScreen
import fr.floran.vraisbonsplans.ui.VraisBonsPlansTheme

class MainActivity : ComponentActivity() {

    /** Refreshed in onResume: the user comes back from the battery settings screen. */
    private var isBatteryUnrestricted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        ScanScheduler.apply(this, ScanSettingsStore.read(this))
        // "autorun" extra lets a scan be started through adb without touching the screen.
        if (intent.getBooleanExtra(EXTRA_AUTORUN, false)) ScanScheduler.scanNow(this)
        setContent {
            VraisBonsPlansTheme {
                // Surface gives every Text the theme's content color (black text on dark background otherwise).
                Surface(color = MaterialTheme.colorScheme.background) { MainScreen(isBatteryUnrestricted) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isBatteryUnrestricted = BackgroundSetup.isBatteryUnrestricted(this)
    }

    companion object {
        const val EXTRA_AUTORUN = "autorun"
    }
}
