package com.example.freshtrack.data.sync

import com.example.freshtrack.data.local.entities.GUEST_USER_ID
import com.example.freshtrack.data.local.entities.ItemEntity
import com.example.freshtrack.data.local.entities.ItemEventEntity
import com.example.freshtrack.data.local.entities.ItemEventType
import com.example.freshtrack.data.local.entities.ItemState
import com.example.freshtrack.domain.model.DateKind
import com.example.freshtrack.domain.model.DateSource
import com.example.freshtrack.domain.model.PriceSource
import java.time.LocalDate

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
 *
 * `lastClientId` names the installation that wrote the document, so that on
 * pull a device can tell its own write coming back from a change made
 * elsewhere without keeping a list of what it has pushed.
 */
object WireFormat {

    fun item(
        snapshot: ItemEntity,
        operationId: String,
        actorUid: String,
        clientId: String
    ): Map<String, Any?> = mapOf(
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
        "lastOperationId" to operationId,
        "lastClientId" to clientId
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

    /**
     * A document back into a row. [revision] is the server's ordering value for
     * this state, and [kitchenId] is the path it was read from.
     *
     * Numbers come back from Firestore as Long or Double regardless of how
     * they went in, hence the [Number] conversions.
     */
    fun itemFrom(fields: Map<String, Any?>, id: String, kitchenId: String, revision: Long): ItemEntity =
        ItemEntity(
            id = id,
            kitchenId = kitchenId,
            name = fields["name"] as String,
            category = fields["category"] as String,
            locationId = fields["locationId"] as String?,
            barcode = fields["barcode"] as String?,
            quantity = (fields["quantity"] as Number).toInt(),
            originalQuantity = (fields["originalQuantity"] as Number).toInt(),
            expiryDate = LocalDate.parse(fields["expiryDate"] as String),
            dateKind = DateKind.valueOf(fields["dateKind"] as String),
            dateSource = DateSource.valueOf(fields["dateSource"] as String),
            dateConfidence = (fields["dateConfidence"] as Number).toFloat(),
            dateConfirmedByUserAt = (fields["dateConfirmedByUserAt"] as Number?)?.toLong(),
            priceMinorUnits = (fields["priceMinorUnits"] as Number?)?.toLong(),
            priceCurrency = fields["priceCurrency"] as String?,
            priceSource = (fields["priceSource"] as String?)?.let(PriceSource::valueOf),
            notes = fields["notes"] as String?,
            imageUri = fields["imageUri"] as String?,
            state = ItemState.valueOf(fields["state"] as String),
            addedAt = (fields["addedAt"] as Number).toLong(),
            resolvedAt = (fields["resolvedAt"] as Number?)?.toLong(),
            notificationEnabled = fields["notificationEnabled"] as? Boolean ?: true,
            snoozedUntil = (fields["snoozedUntil"] as Number?)?.toLong(),
            createdBy = fields["createdBy"] as String,
            lastEditedBy = fields["lastEditedBy"] as String,
            updatedAt = (fields["updatedAt"] as Number).toLong(),
            revision = revision,
            schemaVersion = (fields["schemaVersion"] as Number?)?.toInt() ?: 1,
            isDeleted = fields["isDeleted"] as? Boolean ?: false,
            deletedAt = (fields["deletedAt"] as Number?)?.toLong()
        )

    /**
     * An event document back into a ledger row. The local id is the operation
     * id: the document had no other identity, and the unique index on
     * `operationId` is what makes a replay insert nothing.
     */
    fun eventFrom(fields: Map<String, Any?>, operationId: String, kitchenId: String): ItemEventEntity =
        ItemEventEntity(
            id = operationId,
            itemId = fields["itemId"] as String,
            kitchenId = kitchenId,
            type = ItemEventType.valueOf(fields["type"] as String),
            actorUid = fields["actorUid"] as String,
            quantity = (fields["quantity"] as Number?)?.toInt(),
            occurredAt = (fields["occurredAt"] as Number).toLong(),
            operationId = operationId,
            reversesEventId = fields["reversesEventId"] as String?,
            reversesEventType = (fields["reversesEventType"] as String?)?.let(ItemEventType::valueOf),
            metadata = fields["metadata"] as String?,
            schemaVersion = (fields["schemaVersion"] as Number?)?.toInt() ?: 1
        )

    private fun claimed(uid: String, actorUid: String): String =
        if (uid == GUEST_USER_ID) actorUid else uid
}
