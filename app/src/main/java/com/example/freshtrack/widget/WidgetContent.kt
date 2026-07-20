package com.example.freshtrack.widget

import com.example.freshtrack.domain.model.Product

/**
 * What the home-screen widget should show, decided independently of Glance so
 * the selection and wording rules can be unit tested.
 *
 * A widget is glanceable: it earns its place by answering "is anything about to
 * go off?" without being opened. So it shows the few most urgent items rather
 * than a scrollable inventory.
 */
object WidgetContent {

    data class Item(
        val id: String,
        val name: String,
        val timing: String,
        val isOverdue: Boolean
    )

    data class State(
        val headline: String,
        val items: List<Item>,
        /** True when there is genuinely nothing to act on, not merely no data. */
        val isAllClear: Boolean
    )

    const val MAX_ITEMS = 4

    /** Items expiring within this many days are worth surfacing. */
    const val HORIZON_DAYS = 7L

    fun build(products: List<Product>): State {
        val relevant = products
            .filter { it.daysUntilExpiry() <= HORIZON_DAYS }
            .sortedBy { it.daysUntilExpiry() }

        if (relevant.isEmpty()) {
            return State(
                headline = "Nothing expiring soon",
                items = emptyList(),
                isAllClear = true
            )
        }

        val overdue = relevant.count { it.daysUntilExpiry() < 0 }
        val today = relevant.count { it.daysUntilExpiry() == 0L }

        val headline = when {
            overdue > 0 && today > 0 -> "$overdue overdue, $today due today"
            overdue == 1 -> "1 item overdue"
            overdue > 1 -> "$overdue items overdue"
            today == 1 -> "1 item due today"
            today > 1 -> "$today items due today"
            else -> "${relevant.size} expiring this week"
        }

        return State(
            headline = headline,
            items = relevant.take(MAX_ITEMS).map { product ->
                Item(
                    id = product.id,
                    name = product.name,
                    timing = timingLabel(product),
                    isOverdue = product.daysUntilExpiry() < 0
                )
            },
            isAllClear = false
        )
    }

    /** Short enough to survive a narrow widget without wrapping. */
    fun timingLabel(product: Product): String {
        val days = product.daysUntilExpiry()
        return when {
            days < -1 -> "${-days}d ago"
            days == -1L -> "yesterday"
            days == 0L -> "today"
            days == 1L -> "tomorrow"
            else -> "${days}d"
        }
    }
}
