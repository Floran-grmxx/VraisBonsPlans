package fr.floran.vraisbonsplans.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fr.floran.vraisbonsplans.pricing.ResaleEstimate
import fr.floran.vraisbonsplans.sources.ResaleSite
import kotlin.math.abs

/** Resale lookup of one deal, kept by the screen so it survives scrolling. */
sealed interface ResaleState {
    data object Loading : ResaleState
    data class Done(val estimate: ResaleEstimate) : ResaleState
    data object Failed : ResaleState
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResaleSection(state: ResaleState?, onRequest: () -> Unit) {
    val context = LocalContext.current
    when (state) {
        null -> TextButton(onClick = onRequest) { Text("Prix de revente", color = AppColors.Orange) }
        ResaleState.Loading -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(vertical = 8.dp),
        ) {
            CircularProgressIndicator(Modifier.size(16.dp), color = AppColors.Orange, strokeWidth = 2.dp)
            Text("Recherche sur Vinted et Leboncoin…", style = MaterialTheme.typography.bodySmall)
        }
        ResaleState.Failed -> TextButton(onClick = onRequest) {
            Text("Revente : sites injoignables, réessayer", color = AppColors.Red)
        }
        is ResaleState.Done -> Column(Modifier.padding(top = 6.dp)) {
            val estimate = state.estimate
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val median = estimate.medianCents
                val gain = estimate.gainCents
                if (median == null || gain == null) {
                    Pill("Aucune annonce trouvée", MaterialTheme.colorScheme.onSurfaceVariant)
                    estimate.unreadSites.forEach { site -> Pill("$site non lu", AppColors.Red) }
                } else {
                    Pill("Revente ≈ ${formatWholeEuros(median)}", MaterialTheme.colorScheme.onSurface)
                    Pill(
                        (if (gain >= 0) "+" else "−") + formatWholeEuros(abs(gain)),
                        if (gain >= 0) AppColors.Green else AppColors.Red,
                        isBold = true,
                    )
                    if (estimate.hasFewListings) {
                        Pill("peu d'annonces (${estimate.matchCount})", MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    estimate.unreadSites.forEach { site -> Pill("$site non lu", MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                ResaleSite.entries.forEach { site ->
                    TextButton(onClick = { openUrl(context, site.searchUrl(estimate.query)) }) {
                        Text(site.label, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun Pill(text: String, color: Color, isBold: Boolean = false) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Opens the site's app when installed (Vinted and Leboncoin handle their web links), else the browser. */
private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (error: android.content.ActivityNotFoundException) {
        android.widget.Toast.makeText(context, "Impossible d'ouvrir ce lien", android.widget.Toast.LENGTH_SHORT).show()
    }
}
