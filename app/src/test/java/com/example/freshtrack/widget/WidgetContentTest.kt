package com.example.freshtrack.widget

import com.example.freshtrack.domain.model.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.concurrent.TimeUnit

class WidgetContentTest {

    private fun daysFromNow(days: Long): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 12)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis + TimeUnit.DAYS.toMillis(days)
    }

    private fun product(name: String, days: Long) = Product(
        id = name,
        name = name,
        barcode = null,
        category = "Dairy",
        expiryDate = daysFromNow(days),
        addedDate = 0,
        quantity = 1,
        notes = null,
        imageUri = null,
        notificationEnabled = true,
        isConsumed = false,
        isDiscarded = false
    )

    @Test
    fun `an empty pantry reads as all clear`() {
        val state = WidgetContent.build(emptyList())
        assertTrue(state.isAllClear)
        assertTrue(state.items.isEmpty())
    }

    @Test
    fun `items beyond the horizon are all clear, not listed`() {
        // Something expiring in a month should not make the widget look busy.
        val state = WidgetContent.build(listOf(product("Rice", 30), product("Dal", 60)))
        assertTrue(state.isAllClear)
        assertEquals("Nothing expiring soon", state.headline)
    }

    @Test
    fun `overdue items lead the headline`() {
        val state = WidgetContent.build(
            listOf(product("Milk", -2), product("Bread", 4))
        )
        assertFalse(state.isAllClear)
        assertEquals("1 item overdue", state.headline)
    }

    @Test
    fun `overdue and due today are reported together`() {
        val state = WidgetContent.build(
            listOf(product("Milk", -1), product("Curd", 0), product("Bread", 3))
        )
        assertEquals("1 overdue, 1 due today", state.headline)
    }

    @Test
    fun `most urgent items come first`() {
        val state = WidgetContent.build(
            listOf(product("Late", 6), product("Overdue", -3), product("Today", 0))
        )
        assertEquals(listOf("Overdue", "Today", "Late"), state.items.map { it.name })
    }

    @Test
    fun `the list is capped so it fits a small widget`() {
        val many = (1..10).map { product("Item$it", it.toLong() % 7) }
        val state = WidgetContent.build(many)
        assertEquals(WidgetContent.MAX_ITEMS, state.items.size)
    }

    @Test
    fun `only overdue items are flagged`() {
        val state = WidgetContent.build(
            listOf(product("Old", -1), product("Fine", 3))
        )
        assertTrue(state.items.first { it.name == "Old" }.isOverdue)
        assertFalse(state.items.first { it.name == "Fine" }.isOverdue)
    }

    @Test
    fun `timing labels stay short enough for a narrow widget`() {
        assertEquals("today", WidgetContent.timingLabel(product("a", 0)))
        assertEquals("tomorrow", WidgetContent.timingLabel(product("a", 1)))
        assertEquals("yesterday", WidgetContent.timingLabel(product("a", -1)))
        assertEquals("3d ago", WidgetContent.timingLabel(product("a", -3)))
        assertEquals("5d", WidgetContent.timingLabel(product("a", 5)))
    }

    @Test
    fun `an item due exactly on the horizon is still shown`() {
        val state = WidgetContent.build(listOf(product("Edge", WidgetContent.HORIZON_DAYS)))
        assertFalse(state.isAllClear)
        assertEquals(1, state.items.size)
    }
}
