package com.example.freshtrack.data.billing

import android.app.Activity
import com.example.freshtrack.data.session.KitchenSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumClaimTest {

    private class FakeBilling(var owned: List<OwnedPurchase> = emptyList()) : PlayBilling {
        var queries = 0
        override suspend fun premiumPlans() = Result.success(emptyList<PremiumPlan>())
        override suspend fun ownedPurchases(): Result<List<OwnedPurchase>> {
            queries++
            return Result.success(owned)
        }
        override fun subscribe(activity: Activity, plan: PremiumPlan) = Result.success(Unit)
        override val purchaseUpdates: Flow<PurchaseUpdate> = emptyFlow()
    }

    /** The server's answer per token; a token it has not been told about is "not premium". */
    private class FakeRemote(private val answers: Map<String, Boolean> = emptyMap()) : EntitlementRemote {
        val verified = mutableListOf<String>()
        override suspend fun verify(purchaseToken: String): Result<Entitlement> {
            verified += purchaseToken
            val premium = answers[purchaseToken] ?: false
            return Result.success(Entitlement(premium, if (premium) "SUBSCRIPTION_STATE_ACTIVE" else "SUBSCRIPTION_STATE_EXPIRED", "monthly", null))
        }
        override suspend fun current(uid: String) = Result.success(null)
    }

    private fun session(signedIn: Boolean) = object : KitchenSession {
        override fun currentUserId() = if (signedIn) "alice" else "guest"
        override fun isSignedIn() = signedIn
        override fun activeKitchenId() = if (signedIn) "personal-alice" else "local"
    }

    private val premiumPurchase = OwnedPurchase("tok-1", listOf(PREMIUM_PRODUCT_ID), isAcknowledged = false)
    private val otherPurchase = OwnedPurchase("tok-other", listOf("something-else"), isAcknowledged = false)

    private var entitledCalls = 0
    private fun claim(billing: PlayBilling, remote: EntitlementRemote, signedIn: Boolean = true) =
        PremiumClaim(billing, remote, session(signedIn)) { entitledCalls++ }

    @Test
    fun `a premium purchase is presented to the server and, once premium, starts the backup`() = runTest {
        val remote = FakeRemote(mapOf("tok-1" to true))
        val result = claim(FakeBilling(), remote).claim(listOf(otherPurchase, premiumPurchase))

        assertTrue(result.getOrThrow().isPremium)
        assertEquals(listOf("tok-1"), remote.verified)
        assertEquals(1, entitledCalls)
    }

    @Test
    fun `a purchase the server does not call premium does not start the backup`() = runTest {
        val remote = FakeRemote(mapOf("tok-1" to false))
        val result = claim(FakeBilling(), remote).claim(listOf(premiumPurchase))

        assertEquals(false, result.getOrThrow().isPremium)
        assertEquals(0, entitledCalls)
    }

    @Test
    fun `signed out, nothing goes to the server`() = runTest {
        val remote = FakeRemote(mapOf("tok-1" to true))
        val result = claim(FakeBilling(), remote, signedIn = false).claim(listOf(premiumPurchase))

        assertTrue(result.exceptionOrNull() is NotSignedInException)
        assertTrue(remote.verified.isEmpty())
        assertEquals(0, entitledCalls)
    }

    @Test
    fun `a purchase of something else is not claimable`() = runTest {
        val remote = FakeRemote()
        val result = claim(FakeBilling(), remote).claim(listOf(otherPurchase))

        assertTrue(result.isFailure)
        assertTrue(remote.verified.isEmpty())
    }

    @Test
    fun `on resume only an unacknowledged premium purchase is presented`() = runTest {
        val acknowledged = OwnedPurchase("tok-old", listOf(PREMIUM_PRODUCT_ID), isAcknowledged = true)
        val billing = FakeBilling(owned = listOf(acknowledged, otherPurchase, premiumPurchase))
        val remote = FakeRemote(mapOf("tok-1" to true))

        val result = claim(billing, remote).claimUnacknowledged()

        assertTrue(result.getOrThrow()!!.isPremium)
        assertEquals(listOf("tok-1"), remote.verified)
        assertEquals(1, entitledCalls)
    }

    @Test
    fun `on resume with everything acknowledged the network is not touched`() = runTest {
        val acknowledged = OwnedPurchase("tok-old", listOf(PREMIUM_PRODUCT_ID), isAcknowledged = true)
        val billing = FakeBilling(owned = listOf(acknowledged))
        val remote = FakeRemote()

        assertNull(claim(billing, remote).claimUnacknowledged().getOrThrow())
        assertEquals(1, billing.queries)
        assertTrue(remote.verified.isEmpty())
    }

    @Test
    fun `on resume signed out Play is not even asked`() = runTest {
        val billing = FakeBilling(owned = listOf(premiumPurchase))
        val remote = FakeRemote(mapOf("tok-1" to true))

        assertNull(claim(billing, remote, signedIn = false).claimUnacknowledged().getOrThrow())
        assertEquals(0, billing.queries)
        assertTrue(remote.verified.isEmpty())
    }

    @Test
    fun `restore presents every premium purchase Play holds and reports the one that is premium`() = runTest {
        val lapsed = OwnedPurchase("tok-lapsed", listOf(PREMIUM_PRODUCT_ID), isAcknowledged = true)
        val live = OwnedPurchase("tok-live", listOf(PREMIUM_PRODUCT_ID), isAcknowledged = true)
        val billing = FakeBilling(owned = listOf(lapsed, otherPurchase, live))
        val remote = FakeRemote(mapOf("tok-lapsed" to false, "tok-live" to true))

        val result = claim(billing, remote).restore()

        assertTrue(result.getOrThrow()!!.isPremium)
        assertEquals(listOf("tok-lapsed", "tok-live"), remote.verified)
        assertEquals(1, entitledCalls)
    }

    @Test
    fun `restore with nothing owned is not an error`() = runTest {
        assertNull(claim(FakeBilling(), FakeRemote()).restore().getOrThrow())
    }
}
