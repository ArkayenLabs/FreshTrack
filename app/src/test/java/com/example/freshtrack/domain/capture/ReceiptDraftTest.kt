package com.example.freshtrack.domain.capture

import com.example.freshtrack.data.local.entities.LocationType
import com.example.freshtrack.domain.model.DateKind
import com.example.freshtrack.domain.model.DateSource
import com.example.freshtrack.domain.model.PriceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The step between a receipt and inventory.
 *
 * Most of what is worth testing here is refusal: the row that gets no date, the
 * quantity that is a weight rather than a count, and the estimate that stays an
 * estimate after somebody has looked at it.
 */
class ReceiptDraftTest {

    private val today = LocalDate.of(2026, 9, 9)
    private val now = 1_757_000_000_000L

    private val receipt = """
        GREENFIELD MARKET
        SEMI-SKIMMED MILK         1.85
        SOURDOUGH LOAF            2.40
        2 x TOMATOES              1.50
        1.5 KG POTATOES           2.20
        ZORBANI WAFERS            3.10
        TOTAL                     11.05
    """.trimIndent()

    private fun rows() = ReceiptDraft.rowsFrom(ReceiptParser.parse(receipt), today)

    private fun row(name: String) = rows().first { it.name.contains(name) }

    // ─── Dates ──────────────────────────────────────────────────────────────

    @Test
    fun `a food the table knows gets a dated estimate with its reason`() {
        val milk = row("MILK")

        assertEquals(today.plusDays(7), milk.expiry)
        assertEquals("milk", milk.estimateBasis)
        assertFalse(milk.dateIsUserChosen)
    }

    @Test
    fun `a food nothing can date gets no date, and stays unresolved`() {
        // No shelf-life rule matches, and the parser guessed no category, so
        // there is nothing to base a guess on. An invented date here would be
        // indistinguishable on screen from one that came from somewhere.
        val unknown = row("ZORBANI")

        assertNull(unknown.expiry)
        assertNull(unknown.estimateBasis)
        assertTrue(unknown.isUnresolved)
        assertFalse(unknown.willBeSaved)
    }

    @Test
    fun `storage changes the estimate`() {
        val bread = row("SOURDOUGH")
        assertEquals(today.plusDays(5), bread.expiry)

        val frozen = bread
            .copy(locationId = "loc-freezer", locationType = LocationType.FREEZER)
            .reestimated(today)

        assertEquals(today.plusDays(90), frozen.expiry)
        assertEquals("bread kept in a freezer", frozen.estimateBasis)
    }

    @Test
    fun `a date the person picked survives an edit to the name`() {
        val chosen = row("MILK").withChosenDate(today.plusDays(30))
        assertTrue(chosen.dateIsUserChosen)

        val renamed = chosen.copy(name = "Whole milk").reestimated(today)

        assertEquals(today.plusDays(30), renamed.expiry)
        assertTrue(renamed.dateIsUserChosen)
    }

    @Test
    fun `losing the basis for a guess takes the date with it`() {
        // A row that had an estimate and is renamed to something unrecognisable
        // must not keep the old date: it was a statement about the old name.
        val renamed = row("MILK").copy(name = "Zorbani wafers", category = null)
            .reestimated(today)

        assertNull(renamed.expiry)
        assertTrue(renamed.isUnresolved)
    }

    // ─── Quantities ─────────────────────────────────────────────────────────

    @Test
    fun `a leading count becomes the quantity`() {
        assertEquals(2, row("TOMATOES").quantity)
        assertNull(row("TOMATOES").measureNote)
    }

    @Test
    fun `a weight is not a count`() {
        // "1.5 KG POTATOES" is one bag. Two-thirds of a potato is not a thing
        // the kitchen can hold, and 1 is the only honest count here.
        val potatoes = row("POTATOES")

        assertEquals(1, potatoes.quantity)
        assertEquals("1.5 KG", potatoes.measureNote)
    }

    // ─── What gets written ──────────────────────────────────────────────────

    @Test
    fun `an accepted estimate is still saved as an estimate`() {
        // Reviewing a sheet means the person looked at a guess, not that they
        // know when the milk goes off. Recording this as user-confirmed would
        // pin it against a printed date read off the carton later.
        val item = row("MILK").toItem(currency = "GBP", now = now)

        assertEquals(DateKind.ESTIMATED, item.expiry.kind)
        assertEquals(DateSource.RULE, item.expiry.source)
        assertNull(item.expiry.confirmedByUserAt)
        assertTrue(item.hasEstimatedDate)
    }

    @Test
    fun `a date the person picked is saved as theirs`() {
        val item = row("MILK").withChosenDate(today.plusDays(4))
            .toItem(currency = "GBP", now = now)

        assertEquals(DateSource.USER, item.expiry.source)
        assertEquals(now, item.expiry.confirmedByUserAt)
    }

    @Test
    fun `the line total becomes a price for one`() {
        // 1.50 for two tomatoes is 75p each, and it is an observed price
        // rather than an estimate because the receipt printed both numbers.
        val item = row("TOMATOES").toItem(currency = "GBP", now = now)

        assertEquals(75L, item.price?.minorUnits)
        assertEquals("GBP", item.price?.currency)
        assertEquals(PriceSource.RECEIPT, item.price?.source)
        assertEquals(150L, item.verifiedValueMinorUnits)
    }

    @Test
    fun `no currency means no price rather than a bare number`() {
        val item = row("TOMATOES").toItem(currency = null, now = now)

        assertNull(item.price)
    }

    @Test
    fun `a weight is carried into the item rather than dropped`() {
        val item = row("POTATOES").toItem(currency = "GBP", now = now)

        assertEquals("1.5 KG", item.notes)
    }

    @Test
    fun `a row with no category is saved as Other rather than blank`() {
        val item = row("MILK").copy(category = null)
            .withChosenDate(today.plusDays(3))
            .toItem(currency = null, now = now)

        assertEquals("Other", item.category)
    }

    // ─── Decisions ──────────────────────────────────────────────────────────

    @Test
    fun `a row left out is not unresolved, whatever is missing from it`() {
        val skipped = row("ZORBANI").copy(decision = RowDecision.SKIP)

        assertFalse(skipped.isUnresolved)
        assertFalse(skipped.willBeSaved)
    }

    @Test
    fun `merging into a batch that is not there is unresolved`() {
        val orphan = row("MILK").copy(decision = RowDecision.MERGE, duplicate = null)

        assertTrue(orphan.isUnresolved)
    }

    @Test
    fun `merging needs no date of its own`() {
        // The batch it joins already has one, and that date stands.
        val merging = row("ZORBANI").copy(
            decision = RowDecision.MERGE,
            duplicate = DuplicateMatch(itemId = "item-1", quantity = 2)
        )

        assertNull(merging.expiry)
        assertFalse(merging.isUnresolved)
        assertTrue(merging.willBeSaved)
    }
}
