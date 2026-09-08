package com.example.freshtrack.data.notification

import com.example.freshtrack.domain.model.DateKind
import com.example.freshtrack.domain.model.ExpiryDate
import com.example.freshtrack.domain.model.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExpiryNotificationContentTest {

    /**
     * A fixed date, so these assertions do not depend on what time of day the
     * suite runs.
     */
    private val now: LocalDate = LocalDate.of(2026, 9, 7)

    private fun product(name: String, days: Long, id: String = name) = Item(
        id = id,
        name = name,
        category = "Dairy",
        expiry = ExpiryDate.enteredByUser(
            now.plusDays(days), DateKind.BEST_BEFORE, atMillis = 0L
        ),
        quantity = 1
    )

    @Test
    fun `no products produces no notification`() {
        assertNull(ExpiryNotificationContent.build(emptyList(), now))
    }

    @Test
    fun `a single product is named rather than counted`() {
        val content = ExpiryNotificationContent.build(listOf(product("Milk", 0)), now)!!

        assertEquals("Milk expires today", content.title)
        assertTrue("a single item needs no list", content.lines.isEmpty())
    }

    @Test
    fun `a single product offers a direct action`() {
        val content = ExpiryNotificationContent.build(listOf(product("Milk", 0, id = "milk-1")), now)!!
        assertEquals("milk-1", content.singleItemId)
    }

    @Test
    fun `several products offer no single action`() {
        val content = ExpiryNotificationContent.build(
            listOf(product("Milk", 0), product("Bread", 2)),
            now
        )!!
        assertNull(content.singleItemId)
    }

    @Test
    fun `expired reads differently from expiring`() {
        val expired = ExpiryNotificationContent.build(listOf(product("Milk", -2)), now)!!
        val dueToday = ExpiryNotificationContent.build(listOf(product("Milk", 0)), now)!!

        assertEquals("Milk has expired", expired.title)
        assertEquals("Milk expires today", dueToday.title)
        assertTrue(
            "expired copy should say when",
            expired.summary.contains("2 days ago")
        )
    }

    @Test
    fun `tomorrow is not phrased as in 1 days`() {
        val content = ExpiryNotificationContent.build(listOf(product("Milk", 1)), now)!!
        assertEquals("Milk expires tomorrow", content.title)
    }

    @Test
    fun `title reflects the most urgent item, not the count`() {
        val content = ExpiryNotificationContent.build(
            listOf(product("Milk", -1), product("Bread", 5), product("Eggs", 6)),
            now
        )!!
        assertEquals(ExpiryNotificationContent.Urgency.EXPIRED, content.urgency)
        assertTrue(content.title.contains("need") || content.title.contains("needs"))
    }

    @Test
    fun `each line carries its own timing`() {
        val content = ExpiryNotificationContent.build(
            listOf(product("Milk", 0), product("Bread", 3)),
            now
        )!!

        assertTrue(content.lines.any { it == "Milk — today" })
        assertTrue(content.lines.any { it == "Bread — in 3 days" })
    }

    @Test
    fun `lines are capped and the remainder is summarised`() {
        val many = (1..9).map { product("Item$it", it.toLong()) }
        val content = ExpiryNotificationContent.build(many, now)!!

        assertEquals(6, content.lines.size) // 5 items plus the overflow line
        assertEquals("and 4 more", content.lines.last())
    }

    @Test
    fun `most urgent item is listed first`() {
        val content = ExpiryNotificationContent.build(
            listOf(product("Late", 5), product("Urgent", -1), product("Middle", 2)),
            now
        )!!
        assertTrue(content.lines.first().startsWith("Urgent"))
    }

    @Test
    fun `copy contains no emoji`() {
        val content = ExpiryNotificationContent.build(
            listOf(product("Milk", 0), product("Bread", 3)),
            now
        )!!
        val all = (content.lines + content.title + content.summary).joinToString()
        assertTrue(
            "notification copy must stay emoji free",
            all.none { it.code in 0x1F300..0x1FAFF || it.code in 0x2600..0x27BF }
        )
    }
}
