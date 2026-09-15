package com.example.freshtrack.presentation.screen.premium

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.freshtrack.R
import com.example.freshtrack.data.billing.Entitlement
import com.example.freshtrack.data.billing.PremiumPlan
import com.example.freshtrack.presentation.theme.pressable
import com.example.freshtrack.presentation.viewmodel.PremiumMessage
import com.example.freshtrack.presentation.viewmodel.PremiumViewModel
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * The paywall, kept honest: one paid feature, described as what it is, with
 * Play's own prices. Guests are sent to sign in first because the
 * entitlement is bound to an account, not a device.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumScreen(
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: PremiumViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val activity = LocalActivity.current

    val messageText = state.message?.let {
        stringResource(
            when (it) {
                PremiumMessage.PLANS_UNAVAILABLE -> R.string.premium_plans_unavailable
                PremiumMessage.PURCHASE_FAILED -> R.string.premium_purchase_failed
                PremiumMessage.VERIFIED -> R.string.premium_verified
                PremiumMessage.VERIFY_FAILED -> R.string.premium_verify_failed
                PremiumMessage.NOTHING_TO_RESTORE -> R.string.premium_nothing_to_restore
            }
        )
    }
    LaunchedEffect(messageText) {
        if (messageText != null) {
            snackbarHostState.showSnackbar(messageText)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.premium_title), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Outlined.CloudDone,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(stringResource(R.string.premium_headline), style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.premium_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            when {
                !state.isSignedIn -> {
                    Text(
                        stringResource(R.string.premium_sign_in_needed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = onNavigateToLogin,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) { Text(stringResource(R.string.settings_sign_in)) }
                }

                state.entitlement?.isPremium == true -> {
                    EntitlementCard(state.entitlement!!)
                }

                state.isLoading -> {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }

                else -> {
                    if (state.entitlement != null) {
                        Text(
                            stringResource(R.string.premium_lapsed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    state.plans.forEach { plan ->
                        PlanCard(
                            plan = plan,
                            selected = plan.basePlanId == state.selectedPlanId,
                            enabled = !state.isBusy,
                            onClick = { viewModel.select(plan) }
                        )
                    }
                    Button(
                        onClick = { activity?.let { viewModel.subscribe(it) } },
                        enabled = state.selectedPlan != null && !state.isBusy && activity != null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        if (state.isBusy) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Text(stringResource(R.string.premium_subscribe))
                        }
                    }
                    Text(
                        stringResource(R.string.premium_terms),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (state.isSignedIn) {
                TextButton(
                    onClick = { viewModel.restore() },
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) { Text(stringResource(R.string.premium_restore)) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PlanCard(plan: PremiumPlan, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    val isYearly = plan.billingPeriod == "P1Y"
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pressable(press)
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = enabled, interactionSource = press, indication = LocalIndication.current) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (isYearly) R.string.premium_plan_annual else R.string.premium_plan_monthly),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    stringResource(if (isYearly) R.string.premium_per_year else R.string.premium_per_month, plan.formattedPrice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun EntitlementCard(entitlement: Entitlement) {
    val ends = entitlement.expiryTime?.let {
        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.premium_active),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            if (ends != null) {
                Text(
                    stringResource(if (entitlement.isCancelled) R.string.premium_ends else R.string.premium_renews, ends),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
