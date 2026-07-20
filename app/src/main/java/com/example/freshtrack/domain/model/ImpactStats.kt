package com.example.freshtrack.domain.model

import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Aggregated impact figures for the Impact Dashboard.
 *
 * Every field is derived from Room on read — there is no separate counter store
 * to fall out of sync with the products table.
 */
data class ImpactStats(
    val itemsSaved: Int = 0,
    val itemsWasted: Int = 0,
    val wasteFreeDays: Int = 0,
    /** True once the user has resolved at least one item. */
    val hasHistory: Boolean = false
) {
    val totalResolved: Int get() = itemsSaved + itemsWasted

    /**
     * Share of resolved items that were used rather than thrown away, 0f..1f.
     * Zero when nothing has been resolved yet.
     */
    val savedRatio: Float
        get() = if (totalResolved == 0) 0f else itemsSaved.toFloat() / totalResolved
}

/**
 * Whole calendar days between two timestamps, ignoring time of day.
 *
 * Used for the waste-free day count so that discarding something at 11pm and
 * checking the dashboard at 1am does not read as two days.
 */
fun calendarDaysBetween(fromMillis: Long, toMillis: Long): Int {
    val calendar = Calendar.getInstance()

    calendar.timeInMillis = fromMillis
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val fromMidnight = calendar.timeInMillis

    calendar.timeInMillis = toMillis
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val toMidnight = calendar.timeInMillis

    return TimeUnit.MILLISECONDS.toDays(toMidnight - fromMidnight).toInt().coerceAtLeast(0)
}
