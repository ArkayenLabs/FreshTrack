package com.example.freshtrack.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Something that happened to an item.
 *
 * Named in the past tense because these are facts, not commands: they are
 * appended, never edited or deleted.
 */
enum class ItemEventType {
    ITEM_CREATED,
    ITEM_EDITED,
    QUANTITY_USED,
    QUANTITY_DISCARDED,
    ITEM_FROZEN,
    ITEM_MOVED,
    ITEM_SNOOZED,
    ITEM_DELETED,
    ITEM_RESTORED,
    DATE_CONFIRMED,
    PRICE_CONFIRMED;

    /** Events that represent food leaving the kitchen one way or the other. */
    val isResolution: Boolean
        get() = this == QUANTITY_USED || this == QUANTITY_DISCARDED
}

/**
 * An immutable record of one change to one item.
 *
 * The previous design derived impact figures by counting rows in their current
 * state. That works until a row is edited or deleted, at which point the
 * history it represented silently changes too — and an earlier version of this
 * app lost data exactly that way, by keeping a separate counter that drifted.
 *
 * An append-only ledger fixes both: what happened is recorded once, at the time
 * it happened, and nothing later can rewrite it. It is also what makes undo and
 * cross-device sync tractable, since an event is replayable and a mutated row
 * is not.
 */
@Entity(
    tableName = "item_events",
    indices = [
        Index(value = ["itemId"]),
        Index(value = ["kitchenId", "occurredAt"]),
        // Idempotency. Replaying a sync operation must not double-count a use.
        Index(value = ["operationId"], unique = true),
        Index(value = ["reversesEventId"])
    ]
)
data class ItemEventEntity(
    @PrimaryKey
    val id: String,

    val itemId: String,

    val kitchenId: String,

    val type: ItemEventType,

    /** Firebase uid, or the guest sentinel. Who did it, for household activity. */
    val actorUid: String,

    /** Units involved, for the quantity events. Null where it means nothing. */
    val quantity: Int? = null,

    /** When it happened, epoch millis. Not when it was recorded or synced. */
    val occurredAt: Long,

    /**
     * Stable id for the operation that produced this event.
     *
     * Uniquely indexed so that applying the same operation twice — a retried
     * push, a replayed pull, a notification action tapped twice — inserts once.
     */
    val operationId: String,

    /**
     * The event this one reverses, for an [ItemEventType.ITEM_RESTORED].
     *
     * A reversal is recorded as a new event rather than by deleting the
     * original, because the original did happen — the user really did tap
     * "used", and then corrected it. Deleting it would make the ledger disagree
     * with its own history, which is the property the ledger exists to have.
     *
     * The id lets an undone resolution be excluded exactly once. The type is
     * carried alongside so the impact sums can net used and discarded totals
     * separately without joining the table to itself.
     */
    val reversesEventId: String? = null,
    val reversesEventType: ItemEventType? = null,

    /** Small JSON blob for event-specific detail. Never user content. */
    val metadata: String? = null,

    val schemaVersion: Int = CURRENT_ITEM_SCHEMA_VERSION
)
