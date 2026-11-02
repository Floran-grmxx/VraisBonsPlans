package fr.floran.vraisbonsplans.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette of the prototype (deal_alert.html): navy for structure, orange for discounts. */
object AppColors {
    val Navy = Color(0xFF0A3761)
    val Orange = Color(0xFFF97100)
    val Green = Color(0xFF1A8A4A)
    val Red = Color(0xFFC0392B)
    val SoftOrange = Color(0xFFFFF4EA)
    val LightBackground = Color(0xFFEEF1F5)
    val DarkBackground = Color(0xFF12171D)
    val DarkSurface = Color(0xFF1D2329)
    val LightNavy = Color(0xFF8DB4DC)
    val DarkSoftOrange = Color(0xFF3A2A1C)
}

@Composable
fun VraisBonsPlansTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(
            primary = AppColors.LightNavy,
            secondary = AppColors.Orange,
            secondaryContainer = AppColors.DarkSoftOrange,
            onSecondaryContainer = Color.White,
            background = AppColors.DarkBackground,
            surface = AppColors.DarkSurface,
        )
    } else {
        lightColorScheme(
            primary = AppColors.Navy,
            secondary = AppColors.Orange,
            secondaryContainer = AppColors.SoftOrange,
            background = AppColors.LightBackground,
        )
    }
    MaterialTheme(colorScheme = colors, content = content)
}
