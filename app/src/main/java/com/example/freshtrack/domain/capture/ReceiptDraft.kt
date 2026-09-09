package com.example.freshtrack.domain.capture

import com.example.freshtrack.data.local.entities.LocationType
import com.example.freshtrack.domain.model.ExpiryDate
import com.example.freshtrack.domain.model.Item
import com.example.freshtrack.domain.model.ItemPrice
import com.example.freshtrack.domain.model.PriceSource
import java.time.LocalDate
import java.util.UUID

/**
 * What to do with one row of a receipt.
 *
 * There is no "decide later". A row is going in, folding into something already
 * here, or being left out, and the person says which before anything is written.
 */
enum class RowDecision {
    /** Add it as a new batch. */
    ADD,

    /** Add its units to a batch already in the kitchen. */
    MERGE,

    /** Leave it out entirely. */
    SKIP
}

/** A batch already in the kitchen that this row appears to be more of. */
data class DuplicateMatch(
    val itemId: String,
    val quantity: Int
)

/**
 * One reviewable row, between the receipt and the kitchen.
 *
 * Deliberately not an [Item]. An Item has an expiry date, and a receipt does
 * not carry one — the date here is either a shelf-life guess or something the
 * person picked, and [isUnresolved] is what stops a row that has neither from
 * being written as though it did.
 */
data class ReceiptRow(
    val candidateId: String,
    val name: String,
    val quantity: Int,
    val category: String?,
    val locationId: String?,
    val locationType: LocationType?,
    val expiry: LocalDate?,

    /**
     * Why the date says what it says, or null once the person picked one.
     *
     * Doubling as the flag for "this is the user's date now" keeps the two
     * facts from being able to disagree: a row cannot claim a hand-picked date
     * while still showing the reason a rule gave it.
     */
    val estimateBasis: String?,
    val estimateConfidence: Float?,

    /** "1.5 KG" — kept beside the row rather than folded into the quantity. */
    val measureNote: String?,

    /** What the whole line cost, in minor units. Not the price of one. */
    val lineTotalMinor: Int?,

    val needsAttention: Boolean,
    val decision: RowDecision,
    val duplicate: DuplicateMatch?
) {
    /** True when the person picked this date rather than a rule producing it. */
    val dateIsUserChosen: Boolean get() = expiry != null && estimateBasis == null

    /**
     * Whether this row still needs a decision or a fact before it can be saved.
     *
     * The commonest case by far is a date: a receipt never prints one, and the
     * shelf-life table returns nothing for a food it does not recognise in a
     * category that cannot support a guess. Such a row stays visibly unfinished
     * rather than being given an invented date or quietly dropped.
     */
    val isUnresolved: Boolean
        get() = when (decision) {
            RowDecision.SKIP -> false
            RowDecision.MERGE -> duplicate == null
            RowDecision.ADD -> name.isBlank() || quantity < 1 || expiry == null
        }

    /** Whether this row will write something if the sheet is committed now. */
    val willBeSaved: Boolean get() = decision != RowDecision.SKIP && !isUnresolved

    /**
     * Recomputes the shelf-life guess after an edit that could change it.
     *
     * Leaves a hand-picked date alone. Someone who typed a date does not expect
     * correcting the spelling of the name to throw it away.
     */
    fun reestimated(today: LocalDate): ReceiptRow {
        if (dateIsUserChosen) return this
        val estimate = ShelfLifeTable.estimate(name, category, locationType)
            ?: return copy(expiry = null, estimateBasis = null, estimateConfidence = null)
        return copy(
            expiry = today.plusDays(estimate.days.toLong()),
            estimateBasis = estimate.basis,
            estimateConfidence = estimate.confidence
        )
    }

    /** Takes a date the person chose, which outranks anything a rule produced. */
    fun withChosenDate(date: LocalDate): ReceiptRow =
        copy(expiry = date, estimateBasis = null, estimateConfidence = null)

    /**
     * The item this row becomes.
     *
     * The date keeps whatever provenance it actually has. Reviewing a sheet is
     * not the same as knowing when the food goes off, so an accepted estimate
     * stays an estimate — labelled as one in the kitchen, and outrankable by a
     * printed date later. Only a date the person picked here is theirs.
     */
    fun toItem(currency: String?, now: Long): Item {
        val date = requireNotNull(expiry) { "row $candidateId has no date and cannot be saved" }
        return Item(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            category = category?.takeIf { it.isNotBlank() } ?: "Other",
            expiry = if (dateIsUserChosen) {
                ExpiryDate.enteredByUser(date, atMillis = now)
            } else {
                ExpiryDate.estimated(date, confidence = estimateConfidence ?: 0.3f)
            },
            quantity = quantity,
            originalQuantity = quantity,
            locationId = locationId,
            price = unitPrice(currency),
            notes = measureNote,
            addedAt = now
        )
    }

    /**
     * What one unit cost, from what the line cost.
     *
     * The receipt observed a line total against a count, so the division is
     * still an observed fact rather than an estimate — but it is rounded to the
     * nearest minor unit, so three items at a third of a penny each will not
     * multiply back to exactly the printed total. Money is reported to the
     * nearest cent everywhere in this app; that is the error being accepted.
     */
    private fun unitPrice(currency: String?): ItemPrice? {
        val total = lineTotalMinor ?: return null
        val code = currency ?: return null
        if (quantity < 1) return null
        val perUnit = (total + quantity / 2) / quantity
        return ItemPrice(
            minorUnits = perUnit.toLong(),
            currency = code,
            source = PriceSource.RECEIPT
        )
    }
}

/**
 * Turns what a receipt said into what the review sheet shows.
 *
 * Pure, and separate from the screen, because every rule worth arguing about
 * lives here: which quantities are counts, what happens to a food nothing can
 * date, and the fact that a guess is written down as a guess.
 */
object ReceiptDraft {

    /**
     * Units that measure rather than count.
     *
     * "1.5 KG POTATOES" is one bag, not one and a half potatoes, so the number
     * cannot become the quantity. It is kept beside the row instead of being
     * discarded, because weight is most of what tells two similar lines apart.
     */
    private val MEASURES = setOf("KG", "G", "GM", "GMS", "L", "ML")

    fun rowsFrom(candidates: ReceiptCandidates, today: LocalDate): List<ReceiptRow> =
        candidates.items.map { candidate -> rowFrom(candidate, today) }

    private fun rowFrom(candidate: ReceiptCandidate, today: LocalDate): ReceiptRow {
        val measured = candidate.unit?.uppercase() in MEASURES
        val quantity = if (measured) 1 else candidate.quantity.toInt().coerceAtLeast(1)
        val measureNote = if (measured) {
            "${trimNumber(candidate.quantity)} ${candidate.unit?.uppercase()}"
        } else {
            null
        }

        return ReceiptRow(
            candidateId = candidate.candidateId,
            name = candidate.name,
            quantity = quantity,
            category = candidate.categoryGuess,
            // No location is assumed. Where food is kept changes how long it
            // lasts by weeks, and guessing it would put an invented storage
            // decision behind every date on the sheet.
            locationId = null,
            locationType = null,
            expiry = null,
            estimateBasis = null,
            estimateConfidence = null,
            measureNote = measureNote,
            lineTotalMinor = candidate.priceMinor,
            needsAttention = candidate.requiresReview,
            decision = RowDecision.ADD,
            duplicate = null
        ).reestimated(today)
    }

    /** "1.5" stays as it is, and "2.0" becomes "2". */
    private fun trimNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
}
