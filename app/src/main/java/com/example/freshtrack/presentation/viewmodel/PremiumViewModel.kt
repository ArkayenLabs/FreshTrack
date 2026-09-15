package com.example.freshtrack.presentation.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshtrack.data.billing.Entitlement
import com.example.freshtrack.data.billing.EntitlementRemote
import com.example.freshtrack.data.billing.PlayBilling
import com.example.freshtrack.data.billing.PremiumClaim
import com.example.freshtrack.data.billing.PremiumPlan
import com.example.freshtrack.data.billing.PurchaseUpdate
import com.example.freshtrack.data.session.KitchenSession
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

class PremiumViewModel(
    private val billing: PlayBilling,
    private val remote: EntitlementRemote,
    private val claim: PremiumClaim,
    private val session: KitchenSession
) : ViewModel() {

    private val _uiState = MutableStateFlow(PremiumUiState(isSignedIn = session.isSignedIn()))
    val uiState: StateFlow<PremiumUiState> = _uiState

    init {
        load()
        viewModelScope.launch { billing.purchaseUpdates.collect(::onPurchaseUpdate) }
    }

    fun load() {
        viewModelScope.launch {
            val signedIn = session.isSignedIn()
            _uiState.update { it.copy(isLoading = true, isSignedIn = signedIn) }
            // A guest is asked to sign in before seeing plans, so Play is
            // not asked on their behalf.
            if (!signedIn) {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            val plans = billing.premiumPlans()
            val entitlement = remote.current(session.currentUserId())
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
        billing.subscribe(activity, plan)
            .onSuccess { _uiState.update { it.copy(isBusy = true) } }
            .onFailure { _uiState.update { it.copy(message = PremiumMessage.PURCHASE_FAILED) } }
    }

    fun restore() {
        viewModelScope.launch {
            _uiState.update { it.copy(isBusy = true) }
            claim.restore()
                .onSuccess { entitlement ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            entitlement = entitlement ?: it.entitlement,
                            message = when {
                                entitlement == null -> PremiumMessage.NOTHING_TO_RESTORE
                                entitlement.isPremium -> PremiumMessage.VERIFIED
                                else -> null
                            }
                        )
                    }
                }
                .onFailure { _uiState.update { it.copy(isBusy = false, message = PremiumMessage.VERIFY_FAILED) } }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private suspend fun onPurchaseUpdate(update: PurchaseUpdate) {
        when (update) {
            is PurchaseUpdate.Purchased -> {
                if (update.purchases.none { it.isPremium }) {
                    // Nothing completed yet — a pending payment. Play reports
                    // again when it clears, and resume picks it up.
                    _uiState.update { it.copy(isBusy = false) }
                    return
                }
                claim.claim(update.purchases)
                    .onSuccess { entitlement ->
                        _uiState.update {
                            it.copy(
                                isBusy = false,
                                entitlement = entitlement,
                                message = if (entitlement.isPremium) PremiumMessage.VERIFIED else null
                            )
                        }
                    }
                    .onFailure { _uiState.update { it.copy(isBusy = false, message = PremiumMessage.VERIFY_FAILED) } }
            }
            PurchaseUpdate.Cancelled -> _uiState.update { it.copy(isBusy = false) }
            PurchaseUpdate.AlreadyOwned -> restore()
            is PurchaseUpdate.Failed -> _uiState.update { it.copy(isBusy = false, message = PremiumMessage.PURCHASE_FAILED) }
        }
    }
}
