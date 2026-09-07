package com.example.freshtrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * These cover the rules that protect a date once it is good: which sources may
 * overwrite which, and what has to be reviewed before it is allowed to become
 * inventory truth.
 */
class ExpiryDateTest {

    private val today = LocalDate.of(2026, 9, 7)

    private fun at(day: Int) = LocalDate.of(2026, 9, day)

    // ─── Confidence banding ──────────────────────────────────────────────────

    @Test
    fun `user entered dates are high confidence and confirmed`() {
        val date = ExpiryDate.enteredByUser(at(10), DateKind.USE_BY, atMillis = 1_000L)

        assertEquals(ConfidenceBand.HIGH, date.band)
        assertTrue(date.isConfirmedByUser)
        assertFalse(date.requiresConfirmation)
        assertFalse(date.isEstimate)
    }

    @Test
    fun `a medium confidence read still requires confirmation`() {
        val date = ExpiryDate.recognised(at(10), DateKind.BEST_BEFORE, DateSource.PRINTED_OCR, 0.7f)

        assertEquals(ConfidenceBand.MEDIUM, date.band)
        assertTrue(date.requiresConfirmation)
    }

    @Test
    fun `a high confidence read does not require confirmation`() {
        val date = ExpiryDate.recognised(at(10), DateKind.BEST_BEFORE, DateSource.PRINTED_OCR, 0.9f)

        assertEquals(ConfidenceBand.HIGH, date.band)
        assertFalse(date.requiresConfirmation)
    }

    @Test
    fun `estimates are labelled as estimates regardless of confidence`() {
        assertTrue(ExpiryDate.estimated(at(20), confidence = 0.95f).isEstimate)
    }

    // ─── Replacement rules ───────────────────────────────────────────────────

    @Test
    fun `a model cannot overwrite a printed OCR date`() {
        val printed = ExpiryDate.recognised(at(10), DateKind.USE_BY, DateSource.PRINTED_OCR, 0.9f)
        val guess = ExpiryDate.recognised(at(30), DateKind.ESTIMATED, DateSource.AI, 0.99f)

        assertFalse(printed.canBeReplacedBy(guess))
    }

    @Test
    fun `a model cannot overwrite a user confirmed date even at full confidence`() {
        val confirmed = ExpiryDate.enteredByUser(at(10), DateKind.USE_BY, atMillis = 1_000L)
        val guess = ExpiryDate.recognised(at(30), DateKind.ESTIMATED, DateSource.AI, 1f)

        assertFalse(confirmed.canBeReplacedBy(guess))
    }

    @Test
    fun `only the user can replace a user confirmed date`() {
        val confirmed = ExpiryDate.enteredByUser(at(10), DateKind.USE_BY, atMillis = 1_000L)
        val correction = ExpiryDate.enteredByUser(at(12), DateKind.USE_BY, atMillis = 2_000L)
        val betterOcr = ExpiryDate.recognised(at(12), DateKind.USE_BY, DateSource.PRINTED_OCR, 1f)

        assertTrue(confirmed.canBeReplacedBy(correction))
        assertFalse(confirmed.canBeReplacedBy(betterOcr))
    }

    @Test
    fun `a better source may replace a weaker one`() {
        val estimate = ExpiryDate.estimated(at(20))
        val printed = ExpiryDate.recognised(at(10), DateKind.USE_BY, DateSource.PRINTED_OCR, 0.8f)

        assertTrue(estimate.canBeReplacedBy(printed))
    }

    @Test
    fun `an equal source may replace itself, so a re-scan can correct a misread`() {
        val first = ExpiryDate.recognised(at(10), DateKind.BEST_BEFORE, DateSource.PRINTED_OCR, 0.7f)
        val second = ExpiryDate.recognised(at(11), DateKind.BEST_BEFORE, DateSource.PRINTED_OCR, 0.95f)

        assertTrue(first.canBeReplacedBy(second))
    }

    @Test
    fun `confirming keeps the date and pins it against weaker sources`() {
        val ocr = ExpiryDate.recognised(at(10), DateKind.BEST_BEFORE, DateSource.PRINTED_OCR, 0.7f)
        val confirmed = ocr.confirmedAt(5_000L)

        assertEquals(at(10), confirmed.value)
        assertEquals(DateKind.BEST_BEFORE, confirmed.kind)
        assertTrue(confirmed.isConfirmedByUser)
        assertFalse(confirmed.requiresConfirmation)
        assertFalse(
            confirmed.canBeReplacedBy(
                ExpiryDate.recognised(at(28), DateKind.ESTIMATED, DateSource.RULE, 1f)
            )
        )
    }

    // ─── Calendar behaviour ──────────────────────────────────────────────────

    @Test
    fun `days until is counted in calendar days`() {
        assertEquals(3L, ExpiryDate.enteredByUser(at(10), atMillis = 0L).daysUntil(today))
        assertEquals(0L, ExpiryDate.enteredByUser(at(7), atMillis = 0L).daysUntil(today))
        assertEquals(-2L, ExpiryDate.enteredByUser(at(5), atMillis = 0L).daysUntil(today))
    }

    @Test
    fun `an item expiring today is not yet expired`() {
        val date = ExpiryDate.enteredByUser(at(7), atMillis = 0L)

        assertFalse(date.isExpired(today))
        assertEquals(ExpiryUrgency.CRITICAL, date.urgency(today))
    }

    @Test
    fun `urgency bands follow calendar days`() {
        fun urgencyOn(day: Int) = ExpiryDate.enteredByUser(at(day), atMillis = 0L).urgency(today)

        assertEquals(ExpiryUrgency.EXPIRED, urgencyOn(6))
        assertEquals(ExpiryUrgency.CRITICAL, urgencyOn(7))
        assertEquals(ExpiryUrgency.CRITICAL, urgencyOn(9))
        assertEquals(ExpiryUrgency.WARNING, urgencyOn(10))
        assertEquals(ExpiryUrgency.WARNING, urgencyOn(14))
        assertEquals(ExpiryUrgency.SAFE, urgencyOn(15))
    }

    @Test
    fun `printed kinds are distinguished from inferred ones`() {
        assertTrue(DateKind.USE_BY.isPrinted)
        assertTrue(DateKind.BEST_BEFORE.isPrinted)
        assertFalse(DateKind.ESTIMATED.isPrinted)
        assertFalse(DateKind.FROZEN_UNTIL.isPrinted)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `confidence outside zero to one is rejected`() {
        ExpiryDate(at(10), DateKind.BEST_BEFORE, DateSource.AI, confidence = 1.5f)
    }
}
