package com.example.freshtrack.data.notification

import com.example.freshtrack.domain.model.Product

/**
 * Turns a set of expiring products into notification copy.
 *
 * Split out from the Android notification plumbing so the wording rules can be
 * unit tested — the phrasing is the part most likely to be wrong, and the part
 * users actually see every day.
 */
object ExpiryNotificationContent {

    /** Most urgent first, because the notification is titled by the worst case. */
    enum class Urgency { EXPIRED, TODAY, TOMORROW, SOON }

    data class Content(
        val title: String,
        val summary: String,
        val lines: List<String>,
        val urgency: Urgency,
        /** Set only when a single product is involved, enabling a direct action. */
        val singleProductId: String?
    )

    fun urgencyOf(product: Product): Urgency = when (product.daysUntilExpiry()) {
        in Long.MIN_VALUE..-1L -> Urgency.EXPIRED
        0L -> Urgency.TODAY
        1L -> Urgency.TOMORROW
        else -> Urgency.SOON
    }

    /** "today", "tomorrow", "in 3 days", "2 days ago" — never a bare date. */
    fun relativeDay(product: Product): String {
        val days = product.daysUntilExpiry()
        return when {
            days == 0L -> "today"
            days == 1L -> "tomorrow"
            days == -1L -> "yesterday"
            days < -1L -> "${-days} days ago"
            else -> "in $days days"
        }
    }

    fun build(products: List<Product>): Content? {
        if (products.isEmpty()) return null

        val sorted = products.sortedBy { it.daysUntilExpiry() }
        val worst = urgencyOf(sorted.first())

        // A single item is named outright. "1 Product Expiring Soon" makes the
        // user open the app just to find out which one.
        if (sorted.size == 1) {
            val product = sorted.first()
            val title = when (worst) {
                Urgency.EXPIRED -> "${product.name} has expired"
                Urgency.TODAY -> "${product.name} expires today"
                Urgency.TOMORROW -> "${product.name} expires tomorrow"
                Urgency.SOON -> "${product.name} expires ${relativeDay(product)}"
            }
            val summary = when (worst) {
                Urgency.EXPIRED -> "Expired ${relativeDay(product)}. Use it or bin it."
                Urgency.TODAY -> "Use it today to avoid waste."
                else -> "Plan to use it before ${relativeDay(product)}."
            }
            return Content(
                title = title,
                summary = summary,
                lines = emptyList(),
                urgency = worst,
                singleProductId = product.id
            )
        }

        val expiredOrToday = sorted.count { urgencyOf(it) <= Urgency.TODAY }
        val title = when {
            worst == Urgency.EXPIRED && expiredOrToday == 1 -> "1 item needs attention now"
            worst == Urgency.EXPIRED -> "$expiredOrToday items need attention now"
            worst == Urgency.TODAY && expiredOrToday == 1 -> "1 item expires today"
            worst == Urgency.TODAY -> "$expiredOrToday items expire today"
            worst == Urgency.TOMORROW -> "${sorted.size} items expiring, some tomorrow"
            else -> "${sorted.size} items expiring this week"
        }

        // Each line carries its own timing, so the list is scannable without
        // opening the app.
        val lines = sorted.take(MAX_LINES).map { "${it.name} — ${relativeDay(it)}" }
        val remaining = sorted.size - lines.size
        val allLines = if (remaining > 0) lines + "and $remaining more" else lines

        return Content(
            title = title,
            summary = "${sorted.size} items to use up",
            lines = allLines,
            urgency = worst,
            singleProductId = null
        )
    }

    private const val MAX_LINES = 5
}
