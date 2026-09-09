package com.example.freshtrack.presentation.screen.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.freshtrack.R
import com.example.freshtrack.data.preferences.OnboardingPreferences
import com.example.freshtrack.presentation.component.NotificationPermissionHandler
import com.example.freshtrack.domain.rescue.RescueEntry
import com.example.freshtrack.domain.rescue.RescueList
import com.example.freshtrack.domain.rescue.RescueReason
import com.example.freshtrack.presentation.theme.GoodBefore
import com.example.freshtrack.presentation.viewmodel.TodayViewModel
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

/**
 * The decision screen: a short ranked list of what is worth using now.
 *
 * Not a dashboard. There are no counters above the food, because the question
 * this screen answers is "what do I do", and a grid of totals delays that
 * answer rather than giving it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onNavigateToAddItem: () -> Unit,
    onNavigateToItemDetails: (String) -> Unit,
    onNavigateToKitchen: () -> Unit,
    onNavigateToSettings: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    onboardingPreferences: OnboardingPreferences = koinInject(),
    viewModel: TodayViewModel = koinViewModel()
) {
    val rescue by viewModel.rescue.collectAsState()
    val hasAnyItem by viewModel.hasAnyItem.collectAsState()
    val undoPrompt by viewModel.undoPrompt.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Reminders are worth asking about once there is food to be reminded about,
    // and not before: at first launch the question has no subject, and the
    // honest answer to "why do you want this?" is "we have not shown you yet".
    //
    // The preference is written when the question is put, not when it is
    // answered, so a dismissal, a rotation or a process death cannot bring it
    // back round again.
    var askAboutReminders by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(hasAnyItem) {
        if (hasAnyItem && !onboardingPreferences.hasAskedAboutReminders()) {
            onboardingPreferences.setAskedAboutReminders()
            askAboutReminders = true
        }
    }
    if (askAboutReminders) {
        NotificationPermissionHandler()
    }

    // Shown as a snackbar rather than an inline control: undo is a correction,
    // not a step in the flow, and it should disappear once the moment passes.
    LaunchedEffect(undoPrompt) {
        val prompt = undoPrompt ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = prompt.message,
            actionLabel = "Undo",
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undo() else viewModel.dismissUndo()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToAddItem,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add") }
            )
        }
    ) { padding ->
        if (rescue.isEmpty) {
            NothingToRescue(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onNavigateToKitchen = onNavigateToKitchen
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = headlineFor(rescue),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            items(rescue.entries, key = { it.item.id }) { entry ->
                RescueRow(
                    entry = entry,
                    onOpen = { onNavigateToItemDetails(entry.item.id) },
                    onUse = { viewModel.use(entry.item.id, entry.item.name) },
                    onDiscard = { viewModel.discard(entry.item.id, entry.item.name) },
                    onSnooze = { viewModel.snoozeUntilTomorrow(entry.item.id) }
                )
            }

            item {
                TextButton(
                    onClick = onNavigateToKitchen,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("See everything in the kitchen")
                }
            }
        }
    }
}

/**
 * A sentence about the actual state, not a slogan.
 *
 * Counts describe everything eligible rather than the few rows shown, so the
 * headline does not quietly under-report when the list is capped.
 */
private fun headlineFor(rescue: RescueList): String {
    val overdue = rescue.overdueCount
    val today = rescue.dueTodayCount
    return when {
        overdue > 0 && today > 0 ->
            "$overdue past its date, $today due today"
        overdue == 1 -> "One food is past its date"
        overdue > 1 -> "$overdue foods are past their date"
        today == 1 -> "One food is worth using today"
        today > 1 -> "$today foods are worth using today"
        rescue.entries.size == 1 -> "One food to use this week"
        else -> "${rescue.entries.size} foods to use this week"
    }
}

/**
 * One item and its single most useful action.
 *
 * Use is the primary action because it is the outcome the app exists to
 * produce; discard and snooze are available but not competing for the same
 * visual weight.
 */
@Composable
private fun RescueRow(
    entry: RescueEntry,
    onOpen: () -> Unit,
    onUse: () -> Unit,
    onDiscard: () -> Unit,
    onSnooze: () -> Unit
) {
    val accent = accentFor(entry.primaryReason)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onOpen),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.item.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = describe(entry),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (entry.item.quantity > 1) {
                    Text(
                        text = "×${entry.item.quantity}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onUse) {
                    Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp))
                    androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                    Text(if (entry.item.quantity > 1) "Use one" else "Use")
                }
                TextButton(onClick = onSnooze) {
                    Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp))
                    androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                    Text("Later")
                }
                TextButton(onClick = onDiscard) {
                    Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp))
                    androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                    Text("Bin")
                }
            }
        }
    }
}

/**
 * Turns the reasons into one line of plain language.
 *
 * The timing always leads, because that is what makes the item urgent. An
 * unconfirmed date is always disclosed — presenting a guess in the same voice
 * as a printed date is how a tracker loses the user's trust the first time it
 * is wrong.
 */
private fun describe(entry: RescueEntry): String {
    val timing = when (val reason = entry.primaryReason) {
        is RescueReason.Overdue ->
            if (reason.days == 1L) "Was due yesterday" else "Was due ${reason.days} days ago"
        RescueReason.DueToday -> "Due today"
        RescueReason.DueTomorrow -> "Due tomorrow"
        is RescueReason.DueInDays -> "Due in ${reason.days} days"
    }

    val qualifier = entry.supportingReasons
        .firstOrNull { it is RescueReason.DateUnconfirmed }
        ?.let { " · estimated date" }
        .orEmpty()

    return timing + qualifier
}

@Composable
private fun accentFor(reason: RescueReason.Primary): Color {
    val urgency = GoodBefore.urgency
    return when (reason) {
        is RescueReason.Overdue -> urgency.expired
        RescueReason.DueToday -> urgency.critical
        RescueReason.DueTomorrow -> urgency.critical
        is RescueReason.DueInDays ->
            if (reason.days <= 3) urgency.warning else urgency.safe
    }
}

/**
 * The empty state credits the user rather than selling them something. Nothing
 * expiring is the goal, not a gap to fill.
 */
@Composable
private fun NothingToRescue(
    modifier: Modifier = Modifier,
    onNavigateToKitchen: () -> Unit
) {
    Box(modifier = modifier.padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = GoodBefore.urgency.safe
            )
            Text(
                text = "Nothing needs using yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Everything in your kitchen has more than a week left.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onNavigateToKitchen) {
                Text("See everything in the kitchen")
            }
        }
    }
}
