package com.example.freshtrack.widget

import com.example.freshtrack.domain.model.DateKind
import com.example.freshtrack.domain.model.ExpiryDate
import com.example.freshtrack.domain.model.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WidgetContentTest {

    /**
     * A fixed date rather than the real one. These assertions are about day
     * counts, so running them against the system clock makes them depend on
     * what time of day the suite happens to run.
     */
    private val today: LocalDate = LocalDate.of(2026, 9, 7)

    private fun product(name: String, days: Long) = Item(
        id = name,
        name = name,
        category = "Dairy",
        expiry = ExpiryDate.enteredByUser(
            today.plusDays(days),
            DateKind.BEST_BEFORE,
            atMillis = 0L
        ),
        quantity = 1
    )

    @Test
    fun `an empty kitchen reads as all clear, and knows it is empty`() {
        val state = WidgetContent.build(emptyList(), today)
        assertTrue(state.isAllClear)
        assertTrue(state.isEmptyKitchen)
        assertTrue(state.items.isEmpty())
        assertEquals(null, state.nextUp)
    }

    @Test
    fun `items beyond the horizon are all clear, not listed, but the soonest is named as next up`() {
        // Something expiring in a month should not make the widget look busy.
        val state = WidgetContent.build(listOf(product("Dal", 60), product("Rice", 30)), today)
        assertTrue(state.isAllClear)
        assertFalse(state.isEmptyKitchen)
        assertEquals("Nothing expiring soon", state.headline)
        assertEquals(2, state.total)
        assertEquals("Rice", state.nextUp?.name)
        assertEquals("30d", state.nextUp?.timing)
    }

    @Test
    fun `urgency grades a row by how close it is`() {
        val state = WidgetContent.build(
            listOf(product("Old", -2), product("Now", 0), product("Soon", 2), product("Later", 6)),
            today
        )
        val byName = state.items.associate { it.name to it.urgency }
        assertEquals(WidgetContent.Urgency.OVERDUE, byName["Old"])
        assertEquals(WidgetContent.Urgency.TODAY, byName["Now"])
        assertEquals(WidgetContent.Urgency.SOON, byName["Soon"])
        assertEquals(WidgetContent.Urgency.LATER, byName["Later"])
        assertEquals(4, state.urgentCount)
    }

    @Test
    fun `overdue items lead the headline`() {
        val state = WidgetContent.build(listOf(product("Milk", -2), product("Bread", 4)), today)
        assertFalse(state.isAllClear)
        assertEquals("1 item overdue", state.headline)
    }

    @Test
    fun `overdue and due today are reported together`() {
        val state = WidgetContent.build(
            listOf(product("Milk", -1), product("Curd", 0), product("Bread", 3)),
            today
        )
        assertEquals("1 overdue, 1 due today", state.headline)
    }

    @Test
    fun `most urgent items come first`() {
        val state = WidgetContent.build(
            listOf(product("Late", 6), product("Overdue", -3), product("Today", 0)),
            today
        )
        assertEquals(listOf("Overdue", "Today", "Late"), state.items.map { it.name })
    }

    @Test
    fun `the list is capped so it fits a small widget`() {
        val many = (1..10).map { product("Item$it", it.toLong() % 7) }
        val state = WidgetContent.build(many, today)
        assertEquals(WidgetContent.MAX_ITEMS, state.items.size)
    }

    @Test
    fun `only overdue items are flagged`() {
        val state = WidgetContent.build(listOf(product("Old", -1), product("Fine", 3)), today)
        assertTrue(state.items.first { it.name == "Old" }.isOverdue)
        assertFalse(state.items.first { it.name == "Fine" }.isOverdue)
    }

    @Test
    fun `timing labels stay short enough for a narrow widget`() {
        assertEquals("today", WidgetContent.timingLabel(product("a", 0), today))
        assertEquals("tomorrow", WidgetContent.timingLabel(product("a", 1), today))
        assertEquals("yesterday", WidgetContent.timingLabel(product("a", -1), today))
        assertEquals("3d ago", WidgetContent.timingLabel(product("a", -3), today))
        assertEquals("5d", WidgetContent.timingLabel(product("a", 5), today))
    }

    @Test
    fun `an item due exactly on the horizon is still shown`() {
        val state = WidgetContent.build(
            listOf(product("Edge", WidgetContent.HORIZON_DAYS)),
            today
        )
        assertFalse(state.isAllClear)
        assertEquals(1, state.items.size)
    }
}
