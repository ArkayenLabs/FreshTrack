package com.example.freshtrack.domain.model

import com.example.freshtrack.data.local.entities.ItemEntity
import com.example.freshtrack.data.local.entities.ItemState
import java.time.LocalDate

/**
 * One batch of food, as the rest of the app sees it.
 *
 * The expiry fields are grouped into [ExpiryDate] rather than left flat, so a
 * date can never be passed around without the provenance that says how much to
 * trust it — which is what makes it possible to show "printed" and "estimated"
 * differently instead of presenting every date as equally certain.
 */
data class Item(
    val id: String,
    val name: String,
    val category: String,
    val expiry: ExpiryDate,
    val quantity: Int,
    val originalQuantity: Int = quantity,
    val locationId: String? = null,
    val barcode: String? = null,
    val price: ItemPrice? = null,
    val notes: String? = null,
    val imageUri: String? = null,
    val state: ItemState = ItemState.ACTIVE,
    val addedAt: Long = System.currentTimeMillis(),
    val resolvedAt: Long? = null,
    val snoozedUntil: Long? = null,
    val createdBy: String? = null
) {
    fun daysUntilExpiry(today: LocalDate): Long = expiry.daysUntil(today)

    fun isExpired(today: LocalDate): Boolean = expiry.isExpired(today)

    fun urgency(today: LocalDate): ExpiryUrgency = expiry.urgency(today)

    /** Whether the date should be shown with an "estimated" qualifier. */
    val hasEstimatedDate: Boolean get() = expiry.isEstimate

    /** Whether the user still needs to confirm this date before it is trusted. */
    val needsDateReview: Boolean get() = expiry.requiresConfirmation

    /** Total value of what is left, when the price was actually observed. */
    val verifiedValueMinorUnits: Long?
        get() = price?.takeIf { it.isVerified }?.times(quantity)
}

/** Maps a stored row to the domain model. */
fun ItemEntity.toDomain(): Item = Item(
    id = id,
    name = name,
    category = category,
    expiry = ExpiryDate(
        value = expiryDate,
        kind = dateKind,
        source = dateSource,
        confidence = dateConfidence,
        confirmedByUserAt = dateConfirmedByUserAt
    ),
    quantity = quantity,
    originalQuantity = originalQuantity,
    locationId = locationId,
    barcode = barcode,
    price = priceMinorUnits?.let { minor ->
        val currency = priceCurrency
        val source = priceSource
        // All three price columns are written together; if they ever disagree,
        // drop the price rather than invent a currency or a provenance.
        if (currency != null && source != null) ItemPrice(minor, currency, source) else null
    },
    notes = notes,
    imageUri = imageUri,
    state = state,
    addedAt = addedAt,
    resolvedAt = resolvedAt,
    snoozedUntil = snoozedUntil,
    createdBy = createdBy
)

/**
 * Maps the domain model back to a row.
 *
 * Ownership, edit attribution and [ItemEntity.updatedAt] are deliberately not
 * set here — the repository stamps them, so no caller can create a row that
 * belongs to nobody or that lies about when it changed.
 */
fun Item.toEntity(): ItemEntity = ItemEntity(
    id = id,
    name = name,
    category = category,
    locationId = locationId,
    barcode = barcode,
    quantity = quantity,
    originalQuantity = originalQuantity,
    expiryDate = expiry.value,
    dateKind = expiry.kind,
    dateSource = expiry.source,
    dateConfidence = expiry.confidence,
    dateConfirmedByUserAt = expiry.confirmedByUserAt,
    priceMinorUnits = price?.minorUnits,
    priceCurrency = price?.currency,
    priceSource = price?.source,
    notes = notes,
    imageUri = imageUri,
    state = state,
    addedAt = addedAt,
    resolvedAt = resolvedAt,
    snoozedUntil = snoozedUntil
)
