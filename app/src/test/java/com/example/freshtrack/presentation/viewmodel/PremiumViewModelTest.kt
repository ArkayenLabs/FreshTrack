package com.example.freshtrack.presentation.viewmodel

import android.app.Activity
import com.example.freshtrack.data.billing.Entitlement
import com.example.freshtrack.data.billing.EntitlementRemote
import com.example.freshtrack.data.billing.PremiumPlan
import com.example.freshtrack.data.billing.PremiumStore
import com.example.freshtrack.data.billing.PurchaseOutcome
import com.example.freshtrack.data.session.KitchenSession
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PremiumViewModelTest {

    private val monthly = PremiumPlan("monthly", "$3.99", "P1M", "\$rc_monthly")
    private val annual = PremiumPlan("annual", "$19.99", "P1Y", "\$rc_annual")

    private class FakeStore(
        var plans: Result<List<PremiumPlan>> = Result.success(emptyList()),
        var purchase: Result<PurchaseOutcome> = Result.success(PurchaseOutcome.Purchased),
        var restored: Result<Boolean> = Result.success(false)
    ) : PremiumStore {
        var plansAsked = 0
        val bought = mutableListOf<PremiumPlan>()
        override suspend fun premiumPlans(): Result<List<PremiumPlan>> { plansAsked++; return plans }
        override suspend fun subscribe(activity: Activity, plan: PremiumPlan): Result<PurchaseOutcome> { bought += plan; return purchase }
        override suspend fun restore(): Result<Boolean> = restored
    }

    /** The server's copy, which the webhook rewrites some time after the store says yes. */
    private class FakeRemote(var entitlement: Entitlement? = null, private val premiumAfterReads: Int = 0) : EntitlementRemote {
        var reads = 0
        override suspend fun current(uid: String): Result<Entitlement?> {
            reads++
            if (premiumAfterReads > 0 && reads >= premiumAfterReads) {
                entitlement = Entitlement(isPremium = true, state = "ACTIVE", basePlanId = "annual", expiryTime = 1L)
            }
            return Result.success(entitlement)
        }
    }

    private fun session(signedIn: Boolean) = object : KitchenSession {
        override fun currentUserId() = if (signedIn) "alice" else "guest"
        override fun isSignedIn() = signedIn
        override fun activeKitchenId() = if (signedIn) "personal-alice" else "local"
    }

    private var entitledCalls = 0
    private fun viewModel(store: PremiumStore, remote: EntitlementRemote, signedIn: Boolean = true) =
        PremiumViewModel(store, remote, session(signedIn), onEntitled = { entitledCalls++ }, confirmAttempts = 3, confirmIntervalMillis = 10)

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a guest sees no plans and the store is not asked`() = runTest {
        val store = FakeStore(Result.success(listOf(monthly, annual)))
        val vm = viewModel(store, FakeRemote(), signedIn = false)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.isSignedIn)
        assertEquals(0, store.plansAsked)
        assertTrue(vm.uiState.value.plans.isEmpty())
    }

    @Test
    fun `signed in, plans load with yearly preselected`() = runTest {
        val vm = viewModel(FakeStore(Result.success(listOf(monthly, annual))), FakeRemote())
        advanceUntilIdle()
        assertEquals(listOf(monthly, annual), vm.uiState.value.plans)
        assertEquals("annual", vm.uiState.value.selectedPlanId)
        assertNull(vm.uiState.value.message)
    }

    @Test
    fun `plans that will not load say so`() = runTest {
        val vm = viewModel(FakeStore(Result.failure(IllegalStateException("offline"))), FakeRemote())
        advanceUntilIdle()
        assertEquals(PremiumMessage.PLANS_UNAVAILABLE, vm.uiState.value.message)
    }

    @Test
    fun `a purchase waits for the server to say premium, then starts the backup`() = runTest {
        val store = FakeStore(Result.success(listOf(monthly, annual)))
        val remote = FakeRemote(premiumAfterReads = 3) // one on load, premium on the second poll
        val vm = viewModel(store, remote)
        advanceUntilIdle()

        vm.subscribe(mockk(relaxed = true))
        advanceUntilIdle()

        assertEquals(listOf(annual), store.bought)
        assertEquals(PremiumMessage.VERIFIED, vm.uiState.value.message)
        assertTrue(vm.uiState.value.entitlement!!.isPremium)
        assertFalse(vm.uiState.value.isBusy)
        assertEquals(1, entitledCalls)
    }

    @Test
    fun `a purchase the server has not caught up with is not called premium`() = runTest {
        val vm = viewModel(FakeStore(Result.success(listOf(annual))), FakeRemote())
        advanceUntilIdle()
        vm.subscribe(mockk(relaxed = true))
        advanceUntilIdle()

        assertEquals(PremiumMessage.VERIFY_FAILED, vm.uiState.value.message)
        assertNull(vm.uiState.value.entitlement)
        assertFalse(vm.uiState.value.isBusy)
        assertEquals(0, entitledCalls)
    }

    @Test
    fun `backing out of the sheet is not an error`() = runTest {
        val store = FakeStore(Result.success(listOf(annual)), purchase = Result.success(PurchaseOutcome.Cancelled))
        val remote = FakeRemote()
        val vm = viewModel(store, remote)
        advanceUntilIdle()
        vm.subscribe(mockk(relaxed = true))
        advanceUntilIdle()

        assertNull(vm.uiState.value.message)
        assertFalse(vm.uiState.value.isBusy)
        assertEquals(1, remote.reads) // the load only; nothing to confirm
    }

    @Test
    fun `a pending payment leaves the screen alone until the store reports again`() = runTest {
        val store = FakeStore(Result.success(listOf(annual)), purchase = Result.success(PurchaseOutcome.Pending))
        val vm = viewModel(store, FakeRemote())
        advanceUntilIdle()
        vm.subscribe(mockk(relaxed = true))
        advanceUntilIdle()
        assertNull(vm.uiState.value.message)
        assertFalse(vm.uiState.value.isBusy)
        assertEquals(0, entitledCalls)
    }

    @Test
    fun `a store failure is reported and the buttons come back`() = runTest {
        val store = FakeStore(Result.success(listOf(annual)), purchase = Result.failure(IllegalStateException("store")))
        val vm = viewModel(store, FakeRemote())
        advanceUntilIdle()
        vm.subscribe(mockk(relaxed = true))
        advanceUntilIdle()
        assertEquals(PremiumMessage.PURCHASE_FAILED, vm.uiState.value.message)
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test
    fun `restore with nothing owned says so`() = runTest {
        val vm = viewModel(FakeStore(restored = Result.success(false)), FakeRemote())
        advanceUntilIdle()
        vm.restore()
        advanceUntilIdle()
        assertEquals(PremiumMessage.NOTHING_TO_RESTORE, vm.uiState.value.message)
        assertEquals(0, entitledCalls)
    }

    @Test
    fun `restore that finds premium confirms with the server like a purchase`() = runTest {
        val vm = viewModel(FakeStore(restored = Result.success(true)), FakeRemote(premiumAfterReads = 2))
        advanceUntilIdle()
        vm.restore()
        advanceUntilIdle()
        assertEquals(PremiumMessage.VERIFIED, vm.uiState.value.message)
        assertEquals(1, entitledCalls)
    }
}
