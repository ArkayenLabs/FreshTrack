package com.example.freshtrack.presentation.screen.impact

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.freshtrack.R
import com.example.freshtrack.domain.model.ImpactStats
import com.example.freshtrack.presentation.theme.Arrive
import com.example.freshtrack.presentation.theme.Motion
import com.example.freshtrack.presentation.viewmodel.ImpactViewModel
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImpactScreen(
    onNavigateToSettings: () -> Unit = {},
    onNavigateToToday: () -> Unit = {},
    onNavigateToAddItem: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    viewModel: ImpactViewModel = koinViewModel()
) {
    val stats by viewModel.stats.collectAsState()
    val hasAnyItem by viewModel.hasAnyItem.collectAsState()

    Scaffold(
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.nav_progress),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        val loaded = stats
        if (loaded == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        if (!loaded.hasHistory) {
            NothingCountedYet(
                modifier = Modifier.padding(padding),
                hasAnyItem = hasAnyItem,
                onNavigateToToday = onNavigateToToday,
                onNavigateToAddItem = onNavigateToAddItem
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                WasteFreeCard(days = loaded.wasteFreeDays)

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.CheckCircle,
                        value = loaded.itemsSaved,
                        label = stringResource(R.string.impact_items_used),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        icon = Icons.Outlined.DeleteOutline,
                        value = loaded.itemsWasted,
                        label = stringResource(R.string.impact_items_wasted),
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                SavedRatioCard(stats = loaded)
            }
        }
    }
}

/**
 * Waste-free day count.
 *
 * Deliberately framed as "days since you last threw something away" rather than
 * a streak that breaks. Discarding an item restarts the count at zero, which
 * reads as a fresh start instead of a loss.
 */
@Composable
private fun WasteFreeCard(days: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.LocalFireDepartment,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = if (days == 0) {
                        stringResource(R.string.impact_fresh_start)
                    } else {
                        pluralStringResource(R.plurals.impact_waste_free_days, days, days)
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = stringResource(
                        if (days == 0) {
                            R.string.impact_fresh_start_body
                        } else {
                            R.string.impact_counting_body
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: Int,
    label: String,
    tint: androidx.compose.ui.graphics.Color
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            // The number counts up to where it is: a figure that arrives
            // reads as measured rather than pasted in.
            val shown by animateIntAsState(
                targetValue = value,
                animationSpec = tween(Motion.ENTER_MS * 2, easing = Motion.EmphasizedDecelerate),
                label = "stat"
            )
            Text(
                text = shown.toString(),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun SavedRatioCard(stats: ImpactStats) {
    val percent = (stats.savedRatio * 100).roundToInt()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.impact_used_vs_wasted),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(12.dp))
            val ratio by animateFloatAsState(
                targetValue = stats.savedRatio,
                animationSpec = tween(Motion.ENTER_MS * 2, easing = Motion.EmphasizedDecelerate),
                label = "ratio"
            )
            LinearProgressIndicator(
                progress = { ratio },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = pluralStringResource(
                    R.plurals.impact_ratio_body,
                    stats.totalResolved,
                    percent,
                    stats.totalResolved
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Nothing has been counted yet, so show the shape of what will be: the
 * three figures at zero with what feeds each, and the one thing to do
 * next. Zero is information — it says the count is honest and starts from
 * here — where a picture of a chart says nothing.
 */
@Composable
private fun NothingCountedYet(
    modifier: Modifier = Modifier,
    hasAnyItem: Boolean,
    onNavigateToToday: () -> Unit,
    onNavigateToAddItem: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Arrive(index = 0) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stringResource(R.string.impact_empty_title), style = MaterialTheme.typography.headlineSmall)
                Text(
                    stringResource(R.string.impact_empty_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Arrive(index = 1) {
            // Three tiles, one height: a label that wraps must not leave its
            // neighbours shorter.
            Row(
                modifier = Modifier.height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    icon = Icons.Outlined.CheckCircle,
                    value = 0,
                    label = stringResource(R.string.impact_items_used),
                    tint = MaterialTheme.colorScheme.primary
                )
                StatCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    icon = Icons.Outlined.DeleteOutline,
                    value = 0,
                    label = stringResource(R.string.impact_items_wasted),
                    tint = MaterialTheme.colorScheme.error
                )
                StatCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    icon = Icons.Outlined.LocalFireDepartment,
                    value = 0,
                    label = stringResource(R.string.impact_days_waste_free),
                    tint = MaterialTheme.colorScheme.tertiary
                )
            }
        }
        Arrive(index = 2) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.impact_how_title), style = MaterialTheme.typography.titleMedium)
                    HowRow(Icons.Outlined.CheckCircle, MaterialTheme.colorScheme.primary, stringResource(R.string.impact_how_used))
                    HowRow(Icons.Outlined.DeleteOutline, MaterialTheme.colorScheme.error, stringResource(R.string.impact_how_wasted))
                    HowRow(Icons.Outlined.Lock, MaterialTheme.colorScheme.onSurfaceVariant, stringResource(R.string.impact_how_ledger))
                }
            }
        }
        Arrive(index = 3) {
            if (hasAnyItem) {
                FilledTonalButton(onClick = onNavigateToToday, shape = MaterialTheme.shapes.medium) {
                    Text(stringResource(R.string.impact_go_today))
                }
            } else {
                FilledTonalButton(onClick = onNavigateToAddItem, shape = MaterialTheme.shapes.medium) {
                    Text(stringResource(R.string.impact_go_add))
                }
            }
        }
    }
}

@Composable
private fun HowRow(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp).padding(top = 1.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
