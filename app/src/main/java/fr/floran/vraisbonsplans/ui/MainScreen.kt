package fr.floran.vraisbonsplans.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.LaunchedEffect
import fr.floran.vraisbonsplans.filter.DealFilter
import fr.floran.vraisbonsplans.filter.DealSort
import fr.floran.vraisbonsplans.model.DealSource
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import fr.floran.vraisbonsplans.model.Deal
import fr.floran.vraisbonsplans.scan.ScanScheduler
import fr.floran.vraisbonsplans.scan.ScanSettings
import fr.floran.vraisbonsplans.scan.ScanSettingsStore
import fr.floran.vraisbonsplans.scan.ScanStore
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainScreen(isBatteryUnrestricted: Boolean) {
    val context = LocalContext.current
    var settings by remember { mutableStateOf(ScanSettingsStore.read(context)) }
    val result by ScanStore.lastResult(context).collectAsState()
    val manualScans by ScanScheduler.isManualScanRunning(context).collectAsState(initial = emptyList())
    val isWorkActive = manualScans.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
    // Shows the loading state right on tap, before WorkManager reports the new work.
    var isScanRequested by remember { mutableStateOf(false) }
    var hasSeenActiveWork by remember { mutableStateOf(false) }
    LaunchedEffect(isWorkActive) {
        if (isWorkActive) hasSeenActiveWork = true
        else if (hasSeenActiveWork) {
            isScanRequested = false
            hasSeenActiveWork = false
        }
    }
    val isScanning = isScanRequested || isWorkActive
    var filter by remember { mutableStateOf(DealFilter()) }
    var minimumPriceText by remember { mutableStateOf("") }
    var maximumPriceText by remember { mutableStateOf("") }
    var areFiltersOpen by remember { mutableStateOf(false) }
    var autostartConfirmed by remember { mutableStateOf(BackgroundSetup.isAutostartConfirmed(context)) }

    fun update(newSettings: ScanSettings) {
        settings = newSettings
        ScanSettingsStore.write(context, newSettings)
        ScanScheduler.apply(context, newSettings)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Vrais bons plans", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "idealo · Dealabs · recoupé avec Ledenicheur",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        stickyHeader {
            FilterBar(
                filter = filter,
                minimumPriceText = minimumPriceText,
                maximumPriceText = maximumPriceText,
                areFiltersOpen = areFiltersOpen,
                onToggleFilters = { areFiltersOpen = !areFiltersOpen },
                onFilterChange = { filter = it },
                onMinimumPriceChange = {
                    minimumPriceText = it
                    filter = filter.copy(minimumPriceCents = DealFilter.parseEuros(it))
                },
                onMaximumPriceChange = {
                    maximumPriceText = it
                    filter = filter.copy(maximumPriceCents = DealFilter.parseEuros(it))
                },
                onReset = {
                    filter = DealFilter()
                    minimumPriceText = ""
                    maximumPriceText = ""
                },
            )
        }

        val needsAutostart = BackgroundSetup.isXiaomi() && !autostartConfirmed
        if (!isBatteryUnrestricted || needsAutostart) {
            item {
                SetupCard(
                    showBattery = !isBatteryUnrestricted,
                    showAutostart = needsAutostart,
                    onBattery = { BackgroundSetup.requestBatteryUnrestricted(context) },
                    onAutostart = { BackgroundSetup.openAutostartSettings(context) },
                    onAutostartDone = {
                        BackgroundSetup.confirmAutostart(context)
                        autostartConfirmed = true
                    },
                )
            }
        }

        item { SettingsCard(settings, ::update) }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bons plans détectés", fontWeight = FontWeight.SemiBold)
                    Text(
                        result?.let { "Dernière vérification : ${DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.finishedAtMillis))}" }
                            ?: "Aucune vérification pour l'instant",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Button(
                    onClick = {
                        isScanRequested = true
                        ScanScheduler.scanNow(context)
                    },
                    enabled = !isScanning,
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = AppColors.Orange,
                        disabledContentColor = androidx.compose.ui.graphics.Color.White,
                    ),
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = androidx.compose.ui.graphics.Color.White,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Vérification…")
                    } else {
                        Text("Vérifier")
                    }
                }
            }
            if (isScanning) {
                Spacer(Modifier.size(8.dp))
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = AppColors.Orange)
                Text(
                    "Recherche sur idealo, Dealabs et Ledenicheur (environ 1 min)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        result?.errors?.forEach { error ->
            item { Text("⚠ $error", color = AppColors.Red, style = MaterialTheme.typography.bodySmall) }
        }

        val allDeals = result?.deals.orEmpty()
        val deals = filter.apply(allDeals)
        if (allDeals.isNotEmpty() && deals.isEmpty()) {
            item { Text("Aucun bon plan avec ces filtres.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (result != null && allDeals.isEmpty()) {
            item {
                Text(
                    "Aucun vrai bon plan à -${settings.minimumDiscountPercent} % ou plus pour l'instant. Tu seras notifié dès qu'il y en a un.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(deals, key = { it.key }) { deal ->
            DealCard(deal, onIgnore = { ScanStore.ignore(context, deal.key) })
        }

        val dealsToVerify = filter.apply(result?.dealsToVerify.orEmpty())
        if (dealsToVerify.isNotEmpty()) {
            item {
                Spacer(Modifier.size(8.dp))
                Text("À vérifier", fontWeight = FontWeight.SemiBold)
                Text(
                    "Grosse baisse sur idealo, mais historique douteux et aucune 2e source pour confirmer : pas de notification",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(dealsToVerify, key = { "verify-" + it.key }) { deal ->
                DealCard(deal, onIgnore = { ScanStore.ignore(context, deal.key) })
            }
        }

        val fakeDeals = filter.apply(result?.fakeDeals.orEmpty())
        if (fakeDeals.isNotEmpty()) {
            item {
                Spacer(Modifier.size(8.dp))
                Text("Fausses réductions écartées", fontWeight = FontWeight.SemiBold)
                Text(
                    "Promo affichée par la source, mais prix de référence gonflé ou pas un vrai prix plancher",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(fakeDeals, key = { "fake-" + it.key }) { deal -> FakeDealCard(deal) }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FilterBar(
    filter: DealFilter,
    minimumPriceText: String,
    maximumPriceText: String,
    areFiltersOpen: Boolean,
    onToggleFilters: () -> Unit,
    onFilterChange: (DealFilter) -> Unit,
    onMinimumPriceChange: (String) -> Unit,
    onMaximumPriceChange: (String) -> Unit,
    onReset: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = filter.query,
                onValueChange = { onFilterChange(filter.copy(query = it)) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Rechercher un produit") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (filter.query.isNotEmpty()) {
                        IconButton(onClick = { onFilterChange(filter.copy(query = "")) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Effacer la recherche")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
            )
            FilterChip(
                selected = areFiltersOpen || filter.activeFilterCount > 0,
                onClick = onToggleFilters,
                leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp)) },
                label = { Text(if (filter.activeFilterCount > 0) "Filtres (${filter.activeFilterCount})" else "Filtres") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = AppColors.Orange,
                    selectedLeadingIconColor = AppColors.Orange,
                ),
            )
        }
        if (areFiltersOpen) {
            Card {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Prix", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        PriceField("Min", minimumPriceText, onMinimumPriceChange, Modifier.weight(1f))
                        PriceField("Max", maximumPriceText, onMaximumPriceChange, Modifier.weight(1f))
                    }
                    Text("Sources", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DealSource.entries.forEach { source ->
                            val isSelected = source in filter.sources
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    val sources = if (isSelected) filter.sources - source else filter.sources + source
                                    if (sources.isNotEmpty()) onFilterChange(filter.copy(sources = sources))
                                },
                                label = { Text(source.label.substringBefore(" · ")) },
                                colors = selectedOrangeChipColors(),
                            )
                        }
                    }
                    Text("Trier par", fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DealSort.entries.forEach { sort ->
                            FilterChip(
                                selected = filter.sort == sort,
                                onClick = { onFilterChange(filter.copy(sort = sort)) },
                                label = { Text(sort.label) },
                                colors = selectedOrangeChipColors(),
                            )
                        }
                    }
                    Row {
                        TextButton(onClick = onReset) { Text("Réinitialiser") }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onToggleFilters) { Text("Fermer") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PriceField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter { it.isDigit() || it == ',' || it == '.' }) },
        modifier = modifier,
        label = { Text(label) },
        suffix = { Text("€") },
        isError = value.isNotEmpty() && DealFilter.parseEuros(value) == null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

@Composable
private fun selectedOrangeChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
    selectedLabelColor = AppColors.Orange,
)

@Composable
private fun SetupCard(
    showBattery: Boolean,
    showAutostart: Boolean,
    onBattery: () -> Unit,
    onAutostart: () -> Unit,
    onAutostartDone: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Réglages nécessaires pour les alertes", fontWeight = FontWeight.SemiBold)
            Text(
                "Sans ça, le téléphone bloque les vérifications quand l'app est fermée.",
                style = MaterialTheme.typography.bodySmall,
            )
            if (showBattery) {
                OutlinedButton(onClick = onBattery, modifier = Modifier.fillMaxWidth()) { Text("1. Batterie : autoriser sans restriction") }
            }
            if (showAutostart) {
                OutlinedButton(onClick = onAutostart, modifier = Modifier.fillMaxWidth()) {
                    Text("2. Activer « Démarrage automatique »")
                }
                TextButton(onClick = onAutostartDone) { Text("C'est fait") }
            }
        }
    }
}

@Composable
private fun SettingsCard(settings: ScanSettings, onChange: (ScanSettings) -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Alertes automatiques", fontWeight = FontWeight.SemiBold)
                    Text("Notification pour tout vrai bon plan", style = MaterialTheme.typography.bodySmall)
                }
                Switch(
                    checked = settings.alertsEnabled,
                    onCheckedChange = { onChange(settings.copy(alertsEnabled = it)) },
                    colors = SwitchDefaults.colors(checkedTrackColor = AppColors.Orange, checkedThumbColor = androidx.compose.ui.graphics.Color.White),
                )
            }
            Text("Réduction réelle minimum", fontWeight = FontWeight.SemiBold)
            ChoiceChips(
                choices = ScanSettings.DISCOUNT_CHOICES,
                selected = settings.minimumDiscountPercent,
                label = { "-$it %" },
                onSelect = { onChange(settings.copy(minimumDiscountPercent = it)) },
            )
            Text(
                if (settings.minimumDiscountPercent >= 75) "Seuil élevé : chasse aux erreurs de prix, vérification toutes les 15 min."
                else "-75 % et -90 % servent à attraper les erreurs de prix (article à 1 €…).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Vérifier", fontWeight = FontWeight.SemiBold)
            ChoiceChips(
                choices = ScanSettings.INTERVAL_CHOICES,
                selected = settings.effectiveIntervalMinutes,
                label = { if (it < 60) "$it min" else if (it == 60) "Toutes les heures" else "${it / 60} h" },
                enabled = settings.minimumDiscountPercent < 75,
                onSelect = { onChange(settings.copy(intervalMinutes = it)) },
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChoiceChips(
    choices: List<Int>,
    selected: Int,
    label: (Int) -> String,
    onSelect: (Int) -> Unit,
    enabled: Boolean = true,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { choice ->
            FilterChip(
                selected = choice == selected,
                onClick = { onSelect(choice) },
                label = { Text(label(choice)) },
                enabled = enabled,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = AppColors.Orange,
                ),
            )
        }
    }
}

@Composable
private fun DealCard(deal: Deal, onIgnore: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(deal.productUrl)))
        },
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RemoteImage(deal.imageUrl, Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(deal.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    buildString {
                        append(deal.source.label)
                        if (deal.detail.isNotEmpty()) append(" · ").append(deal.detail)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (deal.currentPriceCents > 0) formatEuros(deal.currentPriceCents) else "Prix inconnu",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                deal.referencePriceCents?.let {
                    Text("Prix habituel : ${formatEuros(it)}", style = MaterialTheme.typography.bodySmall)
                }
                deal.crossCheck?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (it.contains("✓")) AppColors.Green else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onIgnore, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                    Text("Ignorer ce produit")
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                DiscountBadge(deal.realDiscountPercent?.let { "-$it %" } ?: "Erreur")
                deal.sourceDiscountPercent?.let {
                    Text(
                        "-$it % ${deal.source.label}",
                        style = MaterialTheme.typography.labelSmall,
                        textDecoration = TextDecoration.LineThrough,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FakeDealCard(deal: Deal) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            RemoteImage(deal.imageUrl, Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
            Column(Modifier.weight(1f)) {
                Text(deal.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${deal.source.label} : -${deal.sourceDiscountPercent ?: "?"} % · réel : -${deal.realDiscountPercent ?: "?"} %",
                    style = MaterialTheme.typography.bodySmall,
                )
                deal.rejectionReason?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DiscountBadge(text: String) {
    Text(
        text,
        color = androidx.compose.ui.graphics.Color.White,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(AppColors.Orange)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .width(androidx.compose.ui.unit.Dp.Unspecified),
    )
}
