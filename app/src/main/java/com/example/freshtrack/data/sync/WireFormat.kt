package com.example.freshtrack.data.sync

import com.example.freshtrack.data.local.entities.GUEST_USER_ID
import com.example.freshtrack.data.local.entities.ItemEntity
import com.example.freshtrack.data.local.entities.ItemEventEntity

/**
 * The shape an item and an event take on the wire.
 *
 * Pure: local rows in, field maps out, nothing about Firestore. That keeps
 * the mapping testable on the JVM and keeps the rules' shape checks — an ISO
 * date string, enums by name, an operation id on every item — in one place
 * that a test can hold to account.
 *
 * Two things are deliberately *not* copied from the item snapshot:
 *
 * - `kitchenId`. It is the document path, and the path comes from the outbox
 *   row, which the sign-in claim rewrites. The snapshot inside the payload was
 *   taken before sign-in and may still say `local`; putting it on the wire
 *   would let a stale snapshot mislabel a document.
 * - Guest attribution. `createdBy` and `lastEditedBy` that still say `guest`
 *   are replaced with the outbox row's actor, for the same reason. A row that
 *   was genuinely created by someone else in the household keeps their uid.
 *
 * `revision` is local bookkeeping and stays local; `id` is the document id.
 */
object WireFormat {

    fun item(snapshot: ItemEntity, operationId: String, actorUid: String): Map<String, Any?> = mapOf(
        "name" to snapshot.name,
        "category" to snapshot.category,
        "locationId" to snapshot.locationId,
        "barcode" to snapshot.barcode,
        "quantity" to snapshot.quantity,
        "originalQuantity" to snapshot.originalQuantity,
        // ISO calendar date, never a number. The rules refuse a numeric
        // expiryDate because a device reading it as an instant would shift
        // every date by a day in some timezone.
        "expiryDate" to snapshot.expiryDate.toString(),
        "dateKind" to snapshot.dateKind.name,
        "dateSource" to snapshot.dateSource.name,
        "dateConfidence" to snapshot.dateConfidence,
        "dateConfirmedByUserAt" to snapshot.dateConfirmedByUserAt,
        "priceMinorUnits" to snapshot.priceMinorUnits,
        "priceCurrency" to snapshot.priceCurrency,
        "priceSource" to snapshot.priceSource?.name,
        "notes" to snapshot.notes,
        "imageUri" to snapshot.imageUri,
        "state" to snapshot.state.name,
        "addedAt" to snapshot.addedAt,
        "resolvedAt" to snapshot.resolvedAt,
        "notificationEnabled" to snapshot.notificationEnabled,
        "snoozedUntil" to snapshot.snoozedUntil,
        "createdBy" to claimed(snapshot.createdBy, actorUid),
        "lastEditedBy" to claimed(snapshot.lastEditedBy, actorUid),
        "updatedAt" to snapshot.updatedAt,
        "schemaVersion" to snapshot.schemaVersion,
        "isDeleted" to snapshot.isDeleted,
        "deletedAt" to snapshot.deletedAt,
        "lastOperationId" to operationId
    )

    fun event(event: ItemEventEntity): Map<String, Any?> = mapOf(
        "itemId" to event.itemId,
        "type" to event.type.name,
        "actorUid" to event.actorUid,
        "quantity" to event.quantity,
        "occurredAt" to event.occurredAt,
        "operationId" to event.operationId,
        "reversesEventId" to event.reversesEventId,
        "reversesEventType" to event.reversesEventType?.name,
        "metadata" to event.metadata,
        "schemaVersion" to event.schemaVersion
    )

    private fun claimed(uid: String, actorUid: String): String =
        if (uid == GUEST_USER_ID) actorUid else uid
}
