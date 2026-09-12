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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
    // Read here rather than inside the effect: stringResource is a
    // composable read and the effect body is not composition.
    val undoLabel = stringResource(R.string.action_undo)
    val usedTemplate = stringResource(R.string.today_used_item)
    val binnedTemplate = stringResource(R.string.today_binned_item)
    val undoMessage = { prompt: TodayViewModel.UndoPrompt ->
        when (prompt.resolvedAs) {
            TodayViewModel.ResolvedAs.USED -> usedTemplate.format(prompt.itemName)
            TodayViewModel.ResolvedAs.BINNED -> binnedTemplate.format(prompt.itemName)
        }
    }

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
            message = undoMessage(prompt),
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undo() else viewModel.dismissUndo()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.action_settings)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToAddItem,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_add)) }
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
                    Text(stringResource(R.string.today_see_kitchen))
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
@Composable
private fun headlineFor(rescue: RescueList): String {
    val overdue = rescue.overdueCount
    val today = rescue.dueTodayCount
    return when {
        overdue > 0 && today > 0 ->
            stringResource(R.string.today_headline_overdue_and_due, overdue, today)
        overdue > 0 ->
            pluralStringResource(R.plurals.today_headline_overdue, overdue, overdue)
        today > 0 ->
            pluralStringResource(R.plurals.today_headline_due_today, today, today)
        else -> pluralStringResource(
            R.plurals.today_headline_this_week,
            rescue.entries.size,
            rescue.entries.size
        )
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
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = describe(entry),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (entry.item.quantity > 1) {
                    Text(
                        text = stringResource(
                            R.string.today_quantity,
                            entry.item.quantity
                        ),
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
                    Text(
                        stringResource(
                            if (entry.item.quantity > 1) {
                                R.string.today_action_use_one
                            } else {
                                R.string.today_action_use
                            }
                        )
                    )
                }
                TextButton(onClick = onSnooze) {
                    Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp))
                    androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.today_action_later))
                }
                TextButton(onClick = onDiscard) {
                    Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp))
                    androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.today_action_bin))
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
@Composable
private fun describe(entry: RescueEntry): String {
    val timing = when (val reason = entry.primaryReason) {
        is RescueReason.Overdue ->
            if (reason.days == 1L) {
                stringResource(R.string.today_due_yesterday)
            } else {
                pluralStringResource(
                    R.plurals.today_due_days_ago,
                    reason.days.toInt(),
                    reason.days.toInt()
                )
            }
        RescueReason.DueToday -> stringResource(R.string.today_due_today)
        RescueReason.DueTomorrow -> stringResource(R.string.today_due_tomorrow)
        is RescueReason.DueInDays -> pluralStringResource(
            R.plurals.today_due_in_days,
            reason.days.toInt(),
            reason.days.toInt()
        )
    }

    val qualifier = if (entry.supportingReasons.any { it is RescueReason.DateUnconfirmed }) {
        stringResource(R.string.today_estimated_suffix)
    } else {
        ""
    }

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
                text = stringResource(R.string.today_empty_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = stringResource(R.string.today_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onNavigateToKitchen) {
                Text(stringResource(R.string.today_see_kitchen))
            }
        }
    }
}
