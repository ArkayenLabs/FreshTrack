package com.example.freshtrack.data.local.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.freshtrack.domain.model.DateKind
import com.example.freshtrack.domain.model.DateSource
import com.example.freshtrack.domain.model.PriceSource
import java.time.LocalDate
import java.util.UUID

/**
 * How a batch of food ended up: still in the kitchen, eaten, or thrown away.
 *
 * Replaces the previous pair of `isConsumed` / `isDiscarded` booleans, which
 * allowed the meaningless fourth combination where both were true. Being a
 * single column, the impossible state can no longer be written.
 */
enum class ItemState {
    ACTIVE,
    USED,
    DISCARDED;

    val isResolved: Boolean get() = this != ACTIVE
}

/**
 * One batch of one food: the thing that expires.
 *
 * A batch, not a product. Two litres of milk bought a week apart are two rows,
 * because they go off on different days — which is the entire point of the app.
 * Shared product facts (name, barcode, category) are denormalised onto the row
 * rather than joined, because every read is "what is in my kitchen right now"
 * and none of them care about the product in the abstract.
 */
@Entity(
    tableName = "items",
    indices = [
        Index(value = ["kitchenId"]),
        Index(value = ["kitchenId", "state"]),
        Index(value = ["locationId"]),
        Index(value = ["barcode"])
    ]
)
data class ItemEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    /**
     * Which kitchen this belongs to, and the key every user-facing query
     * filters on.
     *
     * Access is decided here rather than by [createdBy] because a shared
     * household kitchen is visible to several people. Keying on the viewer's
     * uid would make another member's item either invisible or look like the
     * viewer's own, depending which uid had been stamped.
     */
    val kitchenId: String = LOCAL_KITCHEN_ID,

    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val name: String,

    @ColumnInfo(collate = ColumnInfo.NOCASE)
    val category: String,

    /**
     * Where it is physically stored. Null means unassigned.
     *
     * Deliberately separate from [category]: "Dairy" and "Fridge" answer
     * different questions, and the old model could only express one of them.
     */
    val locationId: String? = null,

    val barcode: String? = null,

    val quantity: Int = 1,
    val originalQuantity: Int = quantity,

    // ─── Expiry, with provenance ─────────────────────────────────────────────
    // A calendar date, not an instant: "expires on the 14th" must not shift to
    // the 13th because the device moved timezone.

    val expiryDate: LocalDate,
    val dateKind: DateKind,
    val dateSource: DateSource,
    val dateConfidence: Float,
    /** Epoch millis of explicit user confirmation; null while unconfirmed. */
    val dateConfirmedByUserAt: Long? = null,

    // ─── Price, with provenance ──────────────────────────────────────────────
    // All three are null together. Money is only ever claimed as fact when the
    // source says it was observed rather than inferred.

    val priceMinorUnits: Long? = null,
    val priceCurrency: String? = null,
    val priceSource: PriceSource? = null,

    val notes: String? = null,
    val imageUri: String? = null,

    val state: ItemState = ItemState.ACTIVE,
    val addedAt: Long = System.currentTimeMillis(),
    /** When it was used or discarded. Null while [state] is ACTIVE. */
    val resolvedAt: Long? = null,

    /**
     * Kept from the previous schema. There is no UI to turn this off, but the
     * expiry query has always honoured it and dropping it would silently change
     * that behaviour.
     */
    val notificationEnabled: Boolean = true,

    /** Reminders suppressed until this instant. Set by the snooze action. */
    val snoozedUntil: Long? = null,

    // ─── Attribution and sync ────────────────────────────────────────────────

    /** Who created the row. Attribution only; access is [kitchenId]. */
    val createdBy: String = GUEST_USER_ID,
    val lastEditedBy: String = GUEST_USER_ID,

    /** Last local write, epoch millis. */
    val updatedAt: Long = System.currentTimeMillis(),

    /**
     * Server-assigned revision, 0 until this row has been accepted by the
     * backend. Ordering across devices is settled by this rather than by
     * comparing two devices' clocks, which are not comparable.
     */
    val revision: Long = 0L,

    val schemaVersion: Int = CURRENT_ITEM_SCHEMA_VERSION,

    /**
     * Tombstone. A hard DELETE cannot propagate to another device, so deletes
     * are soft and every user-facing query filters these out.
     */
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null
) {
    init {
        require(quantity >= 0) { "quantity cannot be negative, was $quantity" }
        require(dateConfidence in 0f..1f) {
            "dateConfidence must be in 0..1, was $dateConfidence"
        }
    }
}

/** Wire-format version for an item, carried so older clients can be rejected. */
const val CURRENT_ITEM_SCHEMA_VERSION = 1

// GUEST_USER_ID is still declared alongside the outgoing ProductEntity. It moves
// here when that file is removed.

/** Kitchen id for a device that has never been attached to an account. */
const val LOCAL_KITCHEN_ID = "local"

/**
 * A personal kitchen id is derived from the uid rather than allocated, so the
 * client can address it without a round trip and creating it twice is harmless.
 */
fun personalKitchenId(uid: String) = "personal-$uid"
