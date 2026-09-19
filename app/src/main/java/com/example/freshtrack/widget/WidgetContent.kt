package com.example.freshtrack.widget

import com.example.freshtrack.domain.model.Item as InventoryItem
import java.time.LocalDate

/**
 * What the home-screen widget should show, decided independently of Glance so
 * the selection and wording rules can be unit tested.
 *
 * A widget is glanceable: it earns its place by answering "is anything about to
 * go off?" without being opened. So it shows the few most urgent items rather
 * than a scrollable inventory — and when nothing is urgent it says what is
 * coming up next, because a kitchen with food in it is not empty and should
 * not look it.
 */
object WidgetContent {

    /** How loudly a row should read. Only the first two get colour. */
    enum class Urgency { OVERDUE, TODAY, SOON, LATER }

    data class Item(
        val id: String,
        val name: String,
        val timing: String,
        val urgency: Urgency
    ) {
        val isOverdue: Boolean get() = urgency == Urgency.OVERDUE
    }

    data class State(
        val headline: String,
        val items: List<Item>,
        /** True when there is genuinely nothing to act on, not merely no data. */
        val isAllClear: Boolean,
        /** How much food is being tracked at all; zero is the first-run case. */
        val total: Int = 0,
        /** The soonest thing past the horizon, so the all-clear state has something to say. */
        val nextUp: Item? = null,
        /** How many are close to their date, counting past what is listed. */
        val urgentCount: Int = 0,
        /** Past the horizon, soonest first: what a widget with room to spare shows after the urgent rows. */
        val upcoming: List<Item> = emptyList()
    ) {
        val isEmptyKitchen: Boolean get() = total == 0
    }

    /** The tallest widget shows this many; smaller sizes take fewer from the front. */
    const val MAX_ITEMS = 5

    /** Items expiring within this many days are worth surfacing. */
    const val HORIZON_DAYS = 7L

    fun build(products: List<InventoryItem>, today: LocalDate): State {
        val relevant = products
            .filter { it.daysUntilExpiry(today) <= HORIZON_DAYS }
            .sortedBy { it.daysUntilExpiry(today) }

        val upcoming = products
            .filter { it.daysUntilExpiry(today) > HORIZON_DAYS }
            .sortedBy { it.daysUntilExpiry(today) }
            .take(MAX_ITEMS)
            .map { toItem(it, today) }

        if (relevant.isEmpty()) {
            return State(
                headline = "Nothing expiring soon",
                items = emptyList(),
                isAllClear = true,
                total = products.size,
                nextUp = upcoming.firstOrNull(),
                upcoming = upcoming
            )
        }

        val overdue = relevant.count { it.daysUntilExpiry(today) < 0 }
        val dueToday = relevant.count { it.daysUntilExpiry(today) == 0L }

        val headline = when {
            overdue > 0 && dueToday > 0 -> "$overdue overdue, $dueToday due today"
            overdue == 1 -> "1 item overdue"
            overdue > 1 -> "$overdue items overdue"
            dueToday == 1 -> "1 item due today"
            dueToday > 1 -> "$dueToday items due today"
            else -> "${relevant.size} expiring this week"
        }

        return State(
            headline = headline,
            items = relevant.take(MAX_ITEMS).map { toItem(it, today) },
            isAllClear = false,
            total = products.size,
            urgentCount = relevant.size,
            upcoming = upcoming
        )
    }

    private fun toItem(product: InventoryItem, today: LocalDate): Item {
        val days = product.daysUntilExpiry(today)
        return Item(
            id = product.id,
            name = product.name,
            timing = timingLabel(product, today),
            urgency = when {
                days < 0 -> Urgency.OVERDUE
                days == 0L -> Urgency.TODAY
                days <= 3 -> Urgency.SOON
                else -> Urgency.LATER
            }
        )
    }

    /** Short enough to survive a narrow widget without wrapping. */
    fun timingLabel(product: InventoryItem, today: LocalDate): String {
        val days = product.daysUntilExpiry(today)
        return when {
            days < -1 -> "${-days}d ago"
            days == -1L -> "yesterday"
            days == 0L -> "today"
            days == 1L -> "tomorrow"
            else -> "${days}d"
        }
    }
}
