package com.example.freshtrack.data.sync

import com.example.freshtrack.data.local.entities.GUEST_USER_ID
import com.example.freshtrack.data.local.entities.ItemEntity
import com.example.freshtrack.data.local.entities.ItemEventEntity
import com.example.freshtrack.data.local.entities.ItemEventType
import com.example.freshtrack.data.local.entities.LOCAL_KITCHEN_ID
import com.example.freshtrack.domain.model.DateKind
import com.example.freshtrack.domain.model.DateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * What goes on the wire. Each test holds one of the rules' shape checks, or
 * one of the claim's promises, to account.
 */
class WireFormatTest {

    private fun snapshot(
        kitchenId: String = LOCAL_KITCHEN_ID,
        createdBy: String = GUEST_USER_ID,
        lastEditedBy: String = GUEST_USER_ID
    ) = ItemEntity(
        id = "item-1",
        kitchenId = kitchenId,
        name = "Milk",
        category = "Dairy & Eggs",
        expiryDate = LocalDate.of(2026, 9, 20),
        dateKind = DateKind.USE_BY,
        dateSource = DateSource.USER,
        dateConfidence = 1f,
        createdBy = createdBy,
        lastEditedBy = lastEditedBy,
        revision = 7L
    )

    @Test
    fun `expiry travels as an ISO date string, never a number`() {
        val fields = WireFormat.item(snapshot(), "op-1", "alice", "device-a")

        assertEquals("2026-09-20", fields["expiryDate"])
    }

    @Test
    fun `enums travel by name`() {
        val fields = WireFormat.item(snapshot(), "op-1", "alice", "device-a")

        assertEquals("USE_BY", fields["dateKind"])
        assertEquals("USER", fields["dateSource"])
        assertEquals("ACTIVE", fields["state"])
        assertNull(fields["priceSource"])
    }

    @Test
    fun `the item names the operation that produced it`() {
        assertEquals("op-1", WireFormat.item(snapshot(), "op-1", "alice", "device-a")["lastOperationId"])
    }

    @Test
    fun `a pre-sign-in snapshot does not put the local kitchen on the wire`() {
        // The payload was serialised before sign-in and still says "local".
        // The path comes from the outbox row, which the claim rewrote, so the
        // kitchen must not be a field at all.
        val fields = WireFormat.item(snapshot(kitchenId = LOCAL_KITCHEN_ID), "op-1", "alice", "device-a")

        assertFalse(fields.containsKey("kitchenId"))
    }

    @Test
    fun `guest attribution becomes the actor who claimed it`() {
        val fields = WireFormat.item(snapshot(), "op-1", "alice", "device-a")

        assertEquals("alice", fields["createdBy"])
        assertEquals("alice", fields["lastEditedBy"])
    }

    @Test
    fun `attribution to another member is kept`() {
        // Bob created it in the shared kitchen; Alice edited it. Bob's uid is
        // real attribution and must not be overwritten by the pusher's.
        val fields = WireFormat.item(snapshot(createdBy = "bob", lastEditedBy = "alice"), "op-1", "alice", "device-a")

        assertEquals("bob", fields["createdBy"])
        assertEquals("alice", fields["lastEditedBy"])
    }

    @Test
    fun `local bookkeeping stays local`() {
        val fields = WireFormat.item(snapshot(), "op-1", "alice", "device-a")

        assertFalse(fields.containsKey("revision"))
        assertFalse(fields.containsKey("id"))
        assertFalse("the store stamps the server clock, not the mapper", fields.containsKey("serverUpdatedAt"))
    }

    @Test
    fun `the item names the installation that wrote it`() {
        assertEquals("device-a", WireFormat.item(snapshot(), "op-1", "alice", "device-a")["lastClientId"])
    }

    @Test
    fun `an item survives the round trip, with the server's revision`() {
        // Firestore hands numbers back as Long or Double whatever went in.
        val original = snapshot(createdBy = "alice", lastEditedBy = "alice")
        val fields = WireFormat.item(original, "op-1", "alice", "device-a")
            .mapValues { (_, v) -> if (v is Int) v.toLong() else if (v is Float) v.toDouble() else v }

        val back = WireFormat.itemFrom(fields, "item-1", "personal-alice", revision = 4_200L)

        assertEquals(original.copy(kitchenId = "personal-alice", revision = 4_200L), back)
    }

    @Test
    fun `an event survives the round trip under its operation id`() {
        val original = ItemEventEntity(
            id = "op-9", itemId = "item-1", kitchenId = "personal-alice",
            type = ItemEventType.QUANTITY_DISCARDED, actorUid = "bob", quantity = 1,
            occurredAt = 300L, operationId = "op-9"
        )
        val fields = WireFormat.event(original).mapValues { (_, v) -> if (v is Int) v.toLong() else v }

        assertEquals(original, WireFormat.eventFrom(fields, "op-9", "personal-alice"))
    }

    @Test
    fun `an event carries what the ledger needs and nothing about the kitchen`() {
        val event = ItemEventEntity(
            id = "evt-1",
            itemId = "item-1",
            kitchenId = "personal-alice",
            type = ItemEventType.QUANTITY_USED,
            actorUid = "alice",
            quantity = 2,
            occurredAt = 100L,
            operationId = "op-1",
            reversesEventId = null,
            reversesEventType = null
        )

        val fields = WireFormat.event(event)

        assertEquals("QUANTITY_USED", fields["type"])
        assertEquals("alice", fields["actorUid"])
        assertEquals(2, fields["quantity"])
        assertEquals(100L, fields["occurredAt"])
        assertEquals("op-1", fields["operationId"])
        assertTrue(fields.containsKey("reversesEventId"))
        assertFalse(fields.containsKey("kitchenId"))
    }

    @Test
    fun `a reversal names what it reverses, by name`() {
        val event = ItemEventEntity(
            id = "evt-2",
            itemId = "item-1",
            kitchenId = "personal-alice",
            type = ItemEventType.ITEM_RESTORED,
            actorUid = "alice",
            occurredAt = 200L,
            operationId = "op-2",
            reversesEventId = "evt-1",
            reversesEventType = ItemEventType.QUANTITY_USED
        )

        val fields = WireFormat.event(event)

        assertEquals("evt-1", fields["reversesEventId"])
        assertEquals("QUANTITY_USED", fields["reversesEventType"])
    }
}
