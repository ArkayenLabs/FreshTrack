package com.example.freshtrack.presentation.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshtrack.data.billing.Entitlement
import com.example.freshtrack.data.billing.EntitlementRemote
import com.example.freshtrack.data.billing.PremiumPlan
import com.example.freshtrack.data.billing.PremiumStore
import com.example.freshtrack.data.billing.PurchaseOutcome
import com.example.freshtrack.data.session.KitchenSession
import com.example.freshtrack.util.UsageMetrics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PremiumUiState(
    val isSignedIn: Boolean = false,
    val plans: List<PremiumPlan> = emptyList(),
    val selectedPlanId: String? = null,
    /** Null until read, and null when this account has never bought. */
    val entitlement: Entitlement? = null,
    val isLoading: Boolean = true,
    /** A purchase or restore is in flight; the buttons wait. */
    val isBusy: Boolean = false,
    val message: PremiumMessage? = null
) {
    val selectedPlan: PremiumPlan? get() = plans.firstOrNull { it.basePlanId == selectedPlanId }
}

enum class PremiumMessage { PLANS_UNAVAILABLE, PURCHASE_FAILED, VERIFIED, VERIFY_FAILED, NOTHING_TO_RESTORE }

/**
 * The store sells; the server, told by RevenueCat's webhook, writes the
 * entitlement; this reads it back. After a purchase or restore the store
 * already knows, but the kitchen flag lags by however long the webhook
 * takes, so [confirm] polls the server for a few seconds before saying
 * "Premium is on" and starting the backup that was waiting on it.
 */
class PremiumViewModel(
    private val store: PremiumStore,
    private val remote: EntitlementRemote,
    private val session: KitchenSession,
    private val onEntitled: () -> Unit,
    private val metrics: UsageMetrics = UsageMetrics.NoOp,
    private val confirmAttempts: Int = 8,
    private val confirmIntervalMillis: Long = 1_500
) : ViewModel() {

    private val _uiState = MutableStateFlow(PremiumUiState(isSignedIn = session.isSignedIn()))
    val uiState: StateFlow<PremiumUiState> = _uiState

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val signedIn = session.isSignedIn()
            _uiState.update { it.copy(isLoading = true, isSignedIn = signedIn) }
            // A guest is asked to sign in before seeing plans, so the store is
            // not asked on their behalf.
            if (!signedIn) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            val plans = store.premiumPlans()
            val entitlement = remote.current(session.currentUserId())
            if (plans.isSuccess && entitlement.getOrNull()?.isPremium != true) metrics.paywallShown()
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    plans = plans.getOrDefault(emptyList()),
                    // Yearly is the default because it is the better deal; the
                    // person can still pick monthly with one tap.
                    selectedPlanId = state.selectedPlanId
                        ?: plans.getOrNull()?.firstOrNull { it.basePlanId == "annual" }?.basePlanId
                        ?: plans.getOrNull()?.firstOrNull()?.basePlanId,
                    entitlement = entitlement.getOrNull(),
                    message = if (plans.isFailure) PremiumMessage.PLANS_UNAVAILABLE else state.message
                )
            }
        }
    }

    fun select(plan: PremiumPlan) {
        _uiState.update { it.copy(selectedPlanId = plan.basePlanId) }
    }

    fun subscribe(activity: Activity) {
        val plan = _uiState.value.selectedPlan ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            store.subscribe(activity, plan)
                .onSuccess { outcome ->
                    when (outcome) {
                        PurchaseOutcome.Purchased -> confirm()
                        // The store reports again when the payment clears and
                        // the webhook lands then; nothing to wait for here.
                        PurchaseOutcome.Pending, PurchaseOutcome.Cancelled -> _uiState.update { it.copy(isBusy = false) }
                    }
                }
                .onFailure { _uiState.update { it.copy(isBusy = false, message = PremiumMessage.PURCHASE_FAILED) } }
        }
    }

    fun restore() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            store.restore()
                .onSuccess { active ->
                    if (active) confirm()
                    else _uiState.update { it.copy(isBusy = false, message = PremiumMessage.NOTHING_TO_RESTORE) }
                }
                .onFailure { _uiState.update { it.copy(isBusy = false, message = PremiumMessage.PURCHASE_FAILED) } }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /** Wait for the server's copy to say premium; the backup starts the moment it does. */
    private suspend fun confirm() {
        repeat(confirmAttempts) { attempt ->
            val entitlement = remote.current(session.currentUserId()).getOrNull()
            if (entitlement?.isPremium == true) {
                _uiState.update { it.copy(isBusy = false, entitlement = entitlement, message = PremiumMessage.VERIFIED) }
                metrics.purchaseConfirmed(entitlement.basePlanId)
                onEntitled()
                return
            }
            if (attempt < confirmAttempts - 1) delay(confirmIntervalMillis)
        }
        _uiState.update { it.copy(isBusy = false, message = PremiumMessage.VERIFY_FAILED) }
    }
}
