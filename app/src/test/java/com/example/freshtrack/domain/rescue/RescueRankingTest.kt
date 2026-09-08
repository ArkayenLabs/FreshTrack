package com.example.freshtrack.domain.rescue

import com.example.freshtrack.data.local.entities.ItemState
import com.example.freshtrack.domain.model.DateKind
import com.example.freshtrack.domain.model.DateSource
import com.example.freshtrack.domain.model.ExpiryDate
import com.example.freshtrack.domain.model.Item
import com.example.freshtrack.domain.model.ItemPrice
import com.example.freshtrack.domain.model.PriceSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RescueRankingTest {

    private val today = LocalDate.of(2026, 9, 7)
    private val now = 1_000_000L

    private fun item(
        name: String,
        days: Long,
        quantity: Int = 1,
        state: ItemState = ItemState.ACTIVE,
        price: ItemPrice? = null,
        snoozedUntil: Long? = null,
        expiry: ExpiryDate? = null
    ) = Item(
        id = name,
        name = name,
        category = "Dairy",
        expiry = expiry ?: ExpiryDate.enteredByUser(
            today.plusDays(days), DateKind.USE_BY, atMillis = 0L
        ),
        quantity = quantity,
        state = state,
        price = price,
        snoozedUntil = snoozedUntil
    )

    private fun build(vararg items: Item) =
        RescueRanking.build(items.toList(), today, now)

    private fun names(vararg items: Item) = build(*items).entries.map { it.item.name }

    // ─── Eligibility ─────────────────────────────────────────────────────────

    @Test
    fun `items beyond the horizon are not decisions yet`() {
        assertTrue(build(item("Rice", 30)).isEmpty)
    }

    @Test
    fun `an item exactly on the horizon is included`() {
        assertEquals(listOf("Edge"), names(item("Edge", RescueRanking.HORIZON_DAYS)))
    }

    @Test
    fun `resolved items are never suggested`() {
        assertTrue(build(item("Eaten", 1, state = ItemState.USED)).isEmpty)
        assertTrue(build(item("Binned", 1, state = ItemState.DISCARDED)).isEmpty)
    }

    @Test
    fun `a snoozed item is hidden until its snooze expires`() {
        assertTrue(build(item("Later", 0, snoozedUntil = now + 1)).isEmpty)
        assertEquals(listOf("Later"), names(item("Later", 0, snoozedUntil = now - 1)))
    }

    @Test
    fun `an item with nothing left is not suggested`() {
        assertTrue(build(item("Empty", 0, quantity = 0)).isEmpty)
    }

    // ─── Ordering ────────────────────────────────────────────────────────────

    @Test
    fun `recently overdue outranks due today`() {
        // Its window is already closing, so it is the more urgent decision.
        assertEquals(
            listOf("Overdue", "Today"),
            names(item("Today", 0), item("Overdue", -1))
        )
    }

    @Test
    fun `long overdue food drops below fresh food`() {
        // Recommending three-week-old milk ahead of tonight's spinach would be
        // worse than recommending nothing at all.
        assertEquals(
            listOf("Fresh", "Ancient"),
            names(item("Ancient", -21), item("Fresh", 2))
        )
    }

    @Test
    fun `sooner beats later within the week`() {
        assertEquals(
            listOf("Soon", "Mid", "Late"),
            names(item("Late", 6), item("Soon", 2), item("Mid", 4))
        )
    }

    @Test
    fun `quantity breaks a tie, because more is at stake`() {
        assertEquals(
            listOf("Many", "One"),
            names(item("One", 1, quantity = 1), item("Many", 1, quantity = 4))
        )
    }

    @Test
    fun `an observed price breaks a tie`() {
        assertEquals(
            listOf("Expensive", "Cheap"),
            names(
                item("Cheap", 1),
                item("Expensive", 1, price = ItemPrice(50_000, "INR", PriceSource.RECEIPT))
            )
        )
    }

    @Test
    fun `an inferred price does not push an item up`() {
        // An estimate must not buy rank and then be shown to the user as a fact
        // about their money.
        val estimated = item("Estimated", 1, price = ItemPrice(50_000, "INR", PriceSource.ESTIMATED))
        val plain = item("Plain", 1)

        val entries = build(estimated, plain).entries
        assertEquals(entries[0].score, entries[1].score)
        assertTrue(entries.none { e -> e.reasons.any { it is RescueReason.WorthValue } })
    }

    @Test
    fun `an unconfirmed date ranks below a confirmed one on the same day`() {
        val guessed = item(
            "Guessed", 1,
            expiry = ExpiryDate.estimated(today.plusDays(1))
        )
        assertEquals(listOf("Printed", "Guessed"), names(guessed, item("Printed", 1)))
    }

    @Test
    fun `ordering is total, so equal items do not reshuffle between reads`() {
        val a = item("Apple", 1)
        val b = item("Banana", 1)

        assertEquals(names(a, b), names(b, a))
    }

    // ─── Reasons ─────────────────────────────────────────────────────────────

    @Test
    fun `every entry states why it is there`() {
        val entry = build(item("Milk", 0)).entries.single()
        assertEquals(RescueReason.DueToday, entry.primaryReason)
    }

    @Test
    fun `the primary reason names the actual timing`() {
        assertEquals(
            RescueReason.Overdue(2),
            build(item("Old", -2)).entries.single().primaryReason
        )
        assertEquals(
            RescueReason.DueTomorrow,
            build(item("Soon", 1)).entries.single().primaryReason
        )
        assertEquals(
            RescueReason.DueInDays(4),
            build(item("Later", 4)).entries.single().primaryReason
        )
    }

    @Test
    fun `an uncertain date is disclosed as a reason, not hidden`() {
        val entry = build(
            item("Guessed", 1, expiry = ExpiryDate.estimated(today.plusDays(1)))
        ).entries.single()

        assertTrue(entry.supportingReasons.contains(RescueReason.DateUnconfirmed))
    }

    @Test
    fun `a verified price is disclosed with its currency`() {
        val entry = build(
            item("Cheese", 1, price = ItemPrice(24_000, "INR", PriceSource.RECEIPT))
        ).entries.single()

        assertTrue(
            entry.supportingReasons.contains(RescueReason.WorthValue(24_000, "INR"))
        )
    }

    @Test
    fun `a single unit is not reported as multiple`() {
        val entry = build(item("One", 1)).entries.single()
        assertFalse(entry.supportingReasons.any { it is RescueReason.MultipleUnits })
    }

    // ─── Shape of the list ───────────────────────────────────────────────────

    @Test
    fun `the list stays short enough to be a decision`() {
        val many = (1..20).map { item("Item$it", (it % 7).toLong()) }
        assertEquals(RescueRanking.MAX_ENTRIES, RescueRanking.build(many, today, now).entries.size)
    }

    @Test
    fun `counts describe everything eligible, not just what is shown`() {
        val many = (1..8).map { item("Overdue$it", -1) } + (1..3).map { item("Today$it", 0) }

        val list = RescueRanking.build(many, today, now)

        assertEquals(RescueRanking.MAX_ENTRIES, list.entries.size)
        assertEquals(8, list.overdueCount)
        assertEquals(3, list.dueTodayCount)
    }

    @Test
    fun `an empty kitchen produces an empty list, not a fabricated one`() {
        assertTrue(RescueRanking.build(emptyList(), today, now).isEmpty)
    }

    @Test
    fun `a printed date and an estimate for the same day are ordered predictably`() {
        val printed = item(
            "Printed", 2,
            expiry = ExpiryDate.recognised(
                today.plusDays(2), DateKind.USE_BY, DateSource.PRINTED_OCR, 0.95f
            )
        )
        val estimated = item("Estimated", 2, expiry = ExpiryDate.estimated(today.plusDays(2)))

        assertEquals(listOf("Printed", "Estimated"), names(estimated, printed))
    }
}
