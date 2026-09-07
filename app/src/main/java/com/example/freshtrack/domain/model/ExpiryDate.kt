package com.example.freshtrack.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * What a date on food actually claims.
 *
 * These are deliberately not interchangeable. "Use by" is a safety instruction
 * from the manufacturer; "best before" is a quality one and food is routinely
 * fine after it. Collapsing them into a single "expiry" is how a tracker ends
 * up telling someone to throw away good food, so the distinction is carried in
 * the data rather than reconstructed in the UI.
 */
enum class DateKind {
    BEST_BEFORE,
    USE_BY,
    SELL_BY,

    /** Counts from when the package was opened, not from anything printed. */
    OPENED_UNTIL,

    /** Revised horizon after freezing. Always the result of a user action. */
    FROZEN_UNTIL,

    /** Derived from a shelf-life rule, never read off the item. */
    ESTIMATED,

    UNKNOWN;

    /**
     * Whether this kind was read off the product rather than inferred. Printed
     * facts outrank anything we calculated.
     */
    val isPrinted: Boolean
        get() = this == BEST_BEFORE || this == USE_BY || this == SELL_BY
}

/**
 * Where a date came from, ordered by how much it should be trusted.
 *
 * [precedence] is what stops a later, cheaper guess from quietly replacing a
 * better answer — see [ExpiryDate.canBeReplacedBy].
 */
enum class DateSource(val precedence: Int) {
    /** A model's interpretation. Lowest trust: always reviewable, never final. */
    AI(0),

    /** A deterministic shelf-life table. Better than a guess, still an estimate. */
    RULE(1),

    /** Decoded from a GS1 2D barcode that actually carries a date. */
    BARCODE_2D(2),

    /** Read from a receipt. A purchase date is not an expiry date. */
    RECEIPT(3),

    /** OCR of the date printed on the item itself. */
    PRINTED_OCR(4),

    /** The person holding the food said so. Nothing outranks this. */
    USER(5)
}

/** Coarse buckets for UI. The raw score is kept; this is what gets shown. */
enum class ConfidenceBand { LOW, MEDIUM, HIGH }

/**
 * An expiry date together with everything needed to say how much to trust it.
 *
 * Stored as a [LocalDate] rather than an instant on purpose. "Expires on the
 * 14th" is a calendar fact: it does not become the 13th because the phone
 * crossed a timezone, which is exactly what happens when a date is pinned to
 * UTC midnight and rendered elsewhere.
 */
data class ExpiryDate(
    val value: LocalDate,
    val kind: DateKind,
    val source: DateSource,
    /** 0..1. Always 1.0 for something the user typed. */
    val confidence: Float,
    /** Epoch millis of explicit user confirmation, or null if never confirmed. */
    val confirmedByUserAt: Long? = null
) {
    init {
        require(confidence in 0f..1f) { "confidence must be in 0..1, was $confidence" }
    }

    val band: ConfidenceBand
        get() = when {
            confidence >= HIGH_THRESHOLD -> ConfidenceBand.HIGH
            confidence >= MEDIUM_THRESHOLD -> ConfidenceBand.MEDIUM
            else -> ConfidenceBand.LOW
        }

    val isConfirmedByUser: Boolean get() = confirmedByUserAt != null

    /**
     * Whether this must be put in front of the user before it is allowed to
     * become inventory truth.
     *
     * Anything the user has already confirmed is settled. Anything else has to
     * clear the high-confidence bar; a medium-confidence OCR read is precisely
     * the case that looks right and silently is not.
     */
    val requiresConfirmation: Boolean
        get() = !isConfirmedByUser && band != ConfidenceBand.HIGH

    /** True when this date was inferred rather than observed. Label it as such. */
    val isEstimate: Boolean
        get() = kind == DateKind.ESTIMATED || source == DateSource.RULE || source == DateSource.AI

    /**
     * Whether [candidate] is allowed to overwrite this date.
     *
     * The rule that matters: a user-confirmed value can only be changed by the
     * user, and a recognised value cannot be downgraded by something less
     * trustworthy. Without this, a background re-scan or a model pass could
     * quietly replace a date someone read off the packet with one it made up.
     */
    fun canBeReplacedBy(candidate: ExpiryDate): Boolean {
        if (isConfirmedByUser) return candidate.source == DateSource.USER
        return candidate.source.precedence >= source.precedence
    }

    /** Calendar days from [today] to this date. Negative once it has passed. */
    fun daysUntil(today: LocalDate): Long = ChronoUnit.DAYS.between(today, value)

    fun isExpired(today: LocalDate): Boolean = value.isBefore(today)

    /**
     * How urgent this is, by calendar day.
     *
     * Same thresholds the app has always used, but computed on dates instead of
     * millisecond arithmetic, so an item does not change urgency because of the
     * time of day or a timezone change.
     */
    fun urgency(today: LocalDate): ExpiryUrgency = when (val days = daysUntil(today)) {
        in Long.MIN_VALUE..-1L -> ExpiryUrgency.EXPIRED
        in 0L..2L -> ExpiryUrgency.CRITICAL
        in 3L..7L -> ExpiryUrgency.WARNING
        else -> {
            check(days > 7L)
            ExpiryUrgency.SAFE
        }
    }

    /** Marks this date as confirmed, without changing what it says. */
    fun confirmedAt(millis: Long): ExpiryDate =
        copy(confidence = 1f, confirmedByUserAt = millis)

    companion object {
        const val HIGH_THRESHOLD = 0.85f
        const val MEDIUM_THRESHOLD = 0.60f

        /** A date the user typed or picked. Confirmed by definition. */
        fun enteredByUser(
            value: LocalDate,
            kind: DateKind = DateKind.BEST_BEFORE,
            atMillis: Long
        ): ExpiryDate = ExpiryDate(
            value = value,
            kind = kind,
            source = DateSource.USER,
            confidence = 1f,
            confirmedByUserAt = atMillis
        )

        /** A date something recognised. Unconfirmed until the user says so. */
        fun recognised(
            value: LocalDate,
            kind: DateKind,
            source: DateSource,
            confidence: Float
        ): ExpiryDate = ExpiryDate(
            value = value,
            kind = kind,
            source = source,
            confidence = confidence,
            confirmedByUserAt = null
        )

        /** A shelf-life estimate. Always low trust and always labelled. */
        fun estimated(value: LocalDate, confidence: Float = 0.4f): ExpiryDate = ExpiryDate(
            value = value,
            kind = DateKind.ESTIMATED,
            source = DateSource.RULE,
            confidence = confidence,
            confirmedByUserAt = null
        )
    }
}
