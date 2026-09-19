package com.example.freshtrack.presentation.screen.today

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import java.util.Locale
import java.time.format.DateTimeFormatter
import com.example.freshtrack.domain.model.DateKind
import androidx.compose.material.icons.outlined.AcUnit
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
import com.example.freshtrack.data.preferences.ConsentPreferences
import com.example.freshtrack.data.preferences.OnboardingPreferences
import com.example.freshtrack.util.AnalyticsHelper
import com.example.freshtrack.util.ReviewPrompter
import android.app.Activity
import androidx.activity.compose.LocalActivity
import com.example.freshtrack.presentation.component.NotificationPermissionHandler
import com.example.freshtrack.presentation.component.WaysToAdd
import com.example.freshtrack.domain.model.Item
import com.example.freshtrack.domain.rescue.RescueEntry
import com.example.freshtrack.domain.rescue.RescueList
import com.example.freshtrack.domain.rescue.RescueReason
import com.example.freshtrack.presentation.theme.Arrive
import com.example.freshtrack.presentation.theme.GoodBefore
import com.example.freshtrack.presentation.theme.Motion
import com.example.freshtrack.presentation.viewmodel.TodayViewModel
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import org.koin.compose.koinInject
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.example.freshtrack.presentation.theme.pressable

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
    onNavigateToScanner: () -> Unit = {},
    onNavigateToReceipt: () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    onboardingPreferences: OnboardingPreferences = koinInject(),
    consentPreferences: ConsentPreferences = koinInject(),
    reviewPrompter: ReviewPrompter = koinInject(),
    viewModel: TodayViewModel = koinViewModel()
) {
    val activity = LocalActivity.current
    val rescue by viewModel.rescue.collectAsState()
    val hasAnyItem by viewModel.hasAnyItem.collectAsState()
    val comingUp by viewModel.comingUp.collectAsState()
    val freezerLocationId by viewModel.freezerLocationId.collectAsState()
    val frozenNotice by viewModel.frozenNotice.collectAsState()
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
        if (hasAnyItem == true && !onboardingPreferences.hasAskedAboutReminders()) {
            onboardingPreferences.setAskedAboutReminders()
            askAboutReminders = true
        }
    }
    if (askAboutReminders) {
        NotificationPermissionHandler()
    }

    // The analytics question is put once, after the first thing is used,
    // never at first launch: by then the person has seen what the app does
    // and the request has a subject. Either answer counts as decided.
    var askAboutAnalytics by rememberSaveable { mutableStateOf(false) }
    if (askAboutAnalytics) {
        AnalyticsConsentDialog(
            onDecide = { granted ->
                consentPreferences.setAnalyticsConsent(granted)
                AnalyticsHelper.applyConsent(granted)
                askAboutAnalytics = false
            }
        )
    }

    val frozenTemplate = stringResource(R.string.today_frozen_item)
    LaunchedEffect(frozenNotice) {
        val notice = frozenNotice ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = frozenTemplate.format(notice.itemName, formatShortDate(notice.until)),
            duration = SnackbarDuration.Short
        )
        viewModel.dismissFrozenNotice()
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
        // After a real rescue, one question at most: the analytics ask if it
        // has never been put, otherwise the store's rating sheet if the
        // local gate allows it. Never both in one moment.
        if (prompt.resolvedAs == TodayViewModel.ResolvedAs.USED && result != SnackbarResult.ActionPerformed) {
            when {
                !consentPreferences.hasDecided() -> askAboutAnalytics = true
                activity != null && reviewPrompter.shouldAsk() -> reviewPrompter.ask(activity)
            }
        }
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
            // The first-run screen lays out the ways in itself; a button on
            // top of them would be a second answer to the same question.
            AnimatedVisibility(
                visible = hasAnyItem == true,
                enter = scaleIn(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
                exit = scaleOut(tween(Motion.EXIT_MS)) + fadeOut(tween(Motion.EXIT_MS))
            ) {
                ExtendedFloatingActionButton(
                    onClick = onNavigateToAddItem,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.action_add)) }
                )
            }
        }
    ) { padding ->
        // Nothing yet: Room has not answered. A blank beat is better than the
        // wrong screen arriving with a flourish and being replaced.
        if (hasAnyItem == null) {
            Box(Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        if (hasAnyItem == false) {
            FirstRun(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onScanBarcode = onNavigateToScanner,
                onScanReceipt = onNavigateToReceipt,
                onTypeIn = onNavigateToAddItem
            )
            return@Scaffold
        }
        if (rescue.isEmpty) {
            NothingToRescue(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                comingUp = comingUp,
                onOpenItem = onNavigateToItemDetails,
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
                    modifier = Modifier.animateItem(),
                    entry = entry,
                    onOpen = { onNavigateToItemDetails(entry.item.id) },
                    onUse = { viewModel.use(entry.item.id, entry.item.name) },
                    onDiscard = { viewModel.discard(entry.item.id, entry.item.name) },
                    onSnooze = { viewModel.snoozeUntilTomorrow(entry.item.id) },
                    // Only where there is a freezer to go to and it is not already there.
                    onFreeze = freezerLocationId
                        ?.takeIf { it != entry.item.locationId }
                        ?.let { { viewModel.freeze(entry.item.id, entry.item.name) } }
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
    onSnooze: () -> Unit,
    onFreeze: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val accent = accentFor(entry.primaryReason)
    val press = remember { MutableInteractionSource() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .pressable(press)
            .clickable(interactionSource = press, indication = LocalIndication.current, onClick = onOpen),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                // Sits with the name, not the centre of a block that may
                // have grown a third line.
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
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
                    // A date past is not the same as food gone. Said once,
                    // on the item it applies to, and only for a date kind
                    // that is about quality: never on a use-by.
                    coachLine(entry)?.let { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
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
                // Freeze is the escape route, so it gets a word; Later and
                // Bin keep one tap each but shrink to icons so four actions
                // fit a narrow phone.
                if (onFreeze != null) {
                    TextButton(onClick = onFreeze) {
                        Icon(Icons.Outlined.AcUnit, null, Modifier.size(18.dp))
                        androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.today_action_freeze))
                    }
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                IconButton(onClick = onSnooze) {
                    Icon(Icons.Outlined.Schedule, stringResource(R.string.today_action_later))
                }
                IconButton(onClick = onDiscard) {
                    Icon(Icons.Outlined.DeleteOutline, stringResource(R.string.today_action_bin))
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

/**
 * The one sentence worth saying about a date that has passed, and only
 * when it is true: a best-before (or sell-by) date is about quality. A
 * use-by is treated as what it says, so nothing is added to it. The
 * wording differs by market; `values-en-rGB` carries the UK one.
 */
@Composable
private fun coachLine(entry: RescueEntry): String? {
    if (entry.primaryReason !is RescueReason.Overdue) return null
    return when (entry.item.expiry.kind) {
        DateKind.BEST_BEFORE, DateKind.SELL_BY -> stringResource(R.string.today_coach_best_before)
        else -> null
    }
}

private fun formatShortDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("MMM d", Locale.getDefault()))

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
 * Nobody has added anything yet, and this is the landing tab, so this is
 * the first screen of the app. It says what the app does in one line and
 * offers the three ways in, each going straight into its flow.
 */
@Composable
private fun FirstRun(
    modifier: Modifier = Modifier,
    onScanBarcode: () -> Unit,
    onScanReceipt: () -> Unit,
    onTypeIn: () -> Unit
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Arrive(index = 0) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.today_first_title),
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = stringResource(R.string.today_first_body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        WaysToAdd(
            onScanBarcode = onScanBarcode,
            onScanReceipt = onScanReceipt,
            onTypeIn = onTypeIn,
            firstIndex = 1
        )
    }
}

/**
 * Nothing is close to its date. That is the goal, so the screen credits the
 * user in one line and then shows what is coming up next, because a kitchen
 * with food in it is not "empty" and should not look it.
 */
@Composable
private fun NothingToRescue(
    modifier: Modifier = Modifier,
    comingUp: TodayViewModel.ComingUp,
    onOpenItem: (String) -> Unit,
    onNavigateToKitchen: () -> Unit
) {
    val today = remember { LocalDate.now() }
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Arrive(index = 0) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.padding(top = 4.dp).size(28.dp),
                    tint = GoodBefore.urgency.safe
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.today_empty_title), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        // With one or two items Today has little to say; one
                        // quiet line says why, once, with no counter.
                        stringResource(
                            if (comingUp.total < 3) R.string.today_few_items_body else R.string.today_empty_body
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (comingUp.next.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Arrive(index = 1) {
                    Text(
                        stringResource(R.string.today_coming_up),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                comingUp.next.forEachIndexed { index, item ->
                    Arrive(index = index + 2) { ComingUpRow(item, today) { onOpenItem(item.id) } }
                }
            }
        }
        Arrive(index = comingUp.next.size + 2) {
            TextButton(onClick = onNavigateToKitchen) {
                Text(pluralStringResource(R.plurals.today_see_kitchen_count, comingUp.total, comingUp.total))
            }
        }
    }
}

@Composable
private fun ComingUpRow(item: Item, today: LocalDate, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    val days = item.daysUntilExpiry(today).toInt()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(press)
            .clip(MaterialTheme.shapes.medium)
            .clickable(interactionSource = press, indication = LocalIndication.current, onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(item.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1)
            Text(
                pluralStringResource(R.plurals.today_due_in_days, days, days),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * One plain question, with what is and is not collected in the same
 * breath. No pre-screen, no "maybe later": a dismissal is a no, and the
 * toggle in Settings is the way back.
 */
@Composable
private fun AnalyticsConsentDialog(onDecide: (Boolean) -> Unit) {
    AlertDialog(
        onDismissRequest = { onDecide(false) },
        title = { Text(stringResource(R.string.consent_title)) },
        text = {
            Text(
                stringResource(R.string.consent_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(onClick = { onDecide(true) }, shape = MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.consent_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = { onDecide(false) }, shape = MaterialTheme.shapes.medium) {
                Text(stringResource(R.string.consent_no))
            }
        }
    )
}
