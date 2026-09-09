package com.example.freshtrack.domain.capture

import com.example.freshtrack.domain.model.DateKind
import com.example.freshtrack.domain.model.DateSource
import com.example.freshtrack.domain.model.ExpiryDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class PrintedDateParserTest {

    private val today = LocalDate.of(2026, 9, 9)

    private fun parse(text: String, ordering: DateOrdering = DateOrdering.DAY_FIRST) =
        PrintedDateParser.parse(text, today, ordering)

    // ─── The formats actually printed on food ───────────────────────────────

    @Test
    fun `reads an ISO date`() {
        val best = parse("BEST BEFORE 2027-03-12").first()
        assertEquals(LocalDate.of(2027, 3, 12), best.value)
        assertEquals(DateKind.BEST_BEFORE, best.kind)
    }

    @Test
    fun `reads a spelled month with a two digit year`() {
        val best = parse("USE BY 04 MAR 27").first()
        assertEquals(LocalDate.of(2027, 3, 4), best.value)
        assertEquals(DateKind.USE_BY, best.kind)
        assertTrue("two_digit_year" in best.reasonCodes)
    }

    @Test
    fun `a month with no day means the end of that month`() {
        // "Best before end 03/2027" is a claim about the whole of March, and
        // reading it as the 1st would expire the food thirty days early.
        assertEquals(LocalDate.of(2027, 3, 31), parse("BEST BEFORE END 03/2027").first().value)
        assertEquals(LocalDate.of(2027, 3, 31), parse("BEST BEFORE MAR 2027").first().value)
    }

    @Test
    fun `reads a date written across a line break`() {
        val best = parse("BEST BEFORE\n25/12/2027").first()
        assertEquals(LocalDate.of(2027, 12, 25), best.value)
    }

    // ─── Ambiguity ──────────────────────────────────────────────────────────

    @Test
    fun `an ambiguous numeric date returns both readings, preference first`() {
        val candidates = parse("BEST BEFORE 03/04/26")

        assertEquals(LocalDate.of(2026, 4, 3), candidates[0].value)
        assertEquals(LocalDate.of(2026, 3, 4), candidates[1].value)
        assertTrue(candidates.all { "ambiguous_day_month" in it.reasonCodes })
    }

    @Test
    fun `ordering flips which reading is preferred, not which exist`() {
        val dayFirst = parse("BEST BEFORE 03/04/26", DateOrdering.DAY_FIRST)
        val monthFirst = parse("BEST BEFORE 03/04/26", DateOrdering.MONTH_FIRST)

        assertEquals(LocalDate.of(2026, 4, 3), dayFirst[0].value)
        assertEquals(LocalDate.of(2026, 3, 4), monthFirst[0].value)
        assertEquals(dayFirst.map { it.value }.toSet(), monthFirst.map { it.value }.toSet())
    }

    @Test
    fun `an ambiguous date is never confident enough to save unasked`() {
        // The whole point of the provenance model: a date this uncertain has to
        // reach a person before it becomes inventory truth.
        parse("BEST BEFORE 03/04/26").forEach { candidate ->
            val date = ExpiryDate.recognised(
                value = candidate.value,
                kind = candidate.kind,
                source = DateSource.PRINTED_OCR,
                confidence = candidate.confidence
            )
            assertTrue(date.requiresConfirmation)
        }
    }

    @Test
    fun `a day past the twelfth resolves the ordering by itself`() {
        val candidates = parse("BEST BEFORE 25/12/2027")
        assertEquals(1, candidates.size)
        assertEquals(LocalDate.of(2027, 12, 25), candidates[0].value)
        assertTrue("day_exceeds_twelve" in candidates[0].reasonCodes)
    }

    // ─── Labels ─────────────────────────────────────────────────────────────

    @Test
    fun `a packing date is not offered as an expiry`() {
        // Plenty of packaging prints a packed-on date beside the expiry.
        // Reading the wrong one reports fresh food as long overdue.
        val candidates = parse("MFD 12/03/2025 EXP 12/03/2027")

        assertTrue(candidates.none { it.value.year == 2025 })
        assertTrue(candidates.any { it.value == LocalDate.of(2027, 3, 12) })
    }

    @Test
    fun `use by and best before stay different kinds`() {
        val useBy = parse("USE BY 2027-03-12").first().kind
        val bestBefore = parse("BEST BEFORE 2027-03-12").first().kind

        assertEquals(DateKind.USE_BY, useBy)
        assertEquals(DateKind.BEST_BEFORE, bestBefore)
        assertNotEquals(useBy, bestBefore)
    }

    @Test
    fun `a bare EXP does not claim to know which kind of date it is`() {
        // It is certainly about the end of the product's life, but it does not
        // say whether that is a safety limit or a quality one, and inventing
        // the distinction is worse than admitting to not having it.
        assertEquals(DateKind.UNKNOWN, parse("EXP 2027-03-12").first().kind)
    }

    @Test
    fun `an unlabelled date is read but trusted less`() {
        val labelled = parse("BEST BEFORE 2027-03-12").first()
        val bare = parse("2027-03-12").first()

        assertEquals(labelled.value, bare.value)
        assertEquals(DateKind.UNKNOWN, bare.kind)
        assertTrue(bare.confidence < labelled.confidence)
    }

    // ─── Refusals ───────────────────────────────────────────────────────────

    @Test
    fun `a date that is not on the calendar is not returned`() {
        assertTrue(parse("BEST BEFORE 31/02/2027").isEmpty())
    }

    @Test
    fun `a date far outside any shelf life is not returned`() {
        assertTrue(parse("BEST BEFORE 12/03/1998").isEmpty())
        assertTrue(parse("BEST BEFORE 2099-01-01").isEmpty())
    }

    @Test
    fun `text with no date in it yields nothing`() {
        assertTrue(parse("KEEP REFRIGERATED BELOW 5 C").isEmpty())
        assertTrue(parse("").isEmpty())
    }

    // ─── Where the reader is ────────────────────────────────────────────────

    @Test
    fun `the preferred reading follows the locale`() {
        // The United States writes the month first and the United Kingdom does
        // not, so the same label means two different dates depending on who is
        // holding it. Defaulting to one of them would quietly be wrong for the
        // other half of the audience.
        assertEquals(DateOrdering.MONTH_FIRST, DateOrdering.forLocale(Locale.US))
        assertEquals(DateOrdering.DAY_FIRST, DateOrdering.forLocale(Locale.UK))
        assertEquals(DateOrdering.DAY_FIRST, DateOrdering.forLocale(Locale("en", "AU")))
        assertEquals(DateOrdering.DAY_FIRST, DateOrdering.forLocale(Locale("en", "IE")))
    }

    @Test
    fun `an American and a British reader see different first choices`() {
        val label = "BEST BEFORE 03/04/27"

        assertEquals(
            LocalDate.of(2027, 3, 4),
            parse(label, DateOrdering.forLocale(Locale.US)).first().value
        )
        assertEquals(
            LocalDate.of(2027, 4, 3),
            parse(label, DateOrdering.forLocale(Locale.UK)).first().value
        )
    }

    @Test
    fun `never returns more than the contract allows`() {
        val candidates = parse("BB 01/02/27 BB 03/04/27 BB 05/06/27 BB 07/08/27")
        assertTrue(candidates.size <= 5)
    }
}
