package com.example.freshtrack.domain.rescue

import com.example.freshtrack.data.local.entities.ItemState
import com.example.freshtrack.domain.model.Item
import java.time.LocalDate

/**
 * Why an item is on the Today list.
 *
 * Ranking without a reason is a score, and a score the user cannot interrogate
 * is indistinguishable from a guess. Every entry carries the reasons that put
 * it where it is, so the UI can say "printed date is tomorrow" rather than
 * showing an unexplained order.
 */
sealed interface RescueReason {

    /** The one that decides the ordering. Exactly one per entry. */
    sealed interface Primary : RescueReason

    data class Overdue(val days: Long) : Primary
    data object DueToday : Primary
    data object DueTomorrow : Primary
    data class DueInDays(val days: Long) : Primary

    /** Several units at stake, so ignoring it wastes more. */
    data class MultipleUnits(val quantity: Int) : RescueReason

    /** Only ever built from an observed price, never an inferred one. */
    data class WorthValue(val minorUnits: Long, val currency: String) : RescueReason

    /** The date itself is an estimate or unconfirmed, so treat the rank as soft. */
    data object DateUnconfirmed : RescueReason
}

/** One ranked item, with the reasoning that produced its position. */
data class RescueEntry(
    val item: Item,
    val score: Int,
    val primaryReason: RescueReason.Primary,
    val supportingReasons: List<RescueReason>
) {
    val reasons: List<RescueReason> get() = listOf(primaryReason) + supportingReasons
}

/** What Today should show: a short honest headline and a few ranked rows. */
data class RescueList(
    val entries: List<RescueEntry>,
    val overdueCount: Int,
    val dueTodayCount: Int
) {
    val isEmpty: Boolean get() = entries.isEmpty()
}

/**
 * Decides what is worth acting on today, deterministically.
 *
 * Deterministic on purpose, and separated from anything that could later
 * generate suggestions. A model may eventually explain or compose actions
 * around this list, but it must not be able to put an item on it — a
 * recommendation to use food you do not have, or to ignore food you do, is the
 * failure mode that destroys trust in the whole feature.
 *
 * Nothing here reads the clock: [today] is passed in, so the same inventory
 * always produces the same list and the tests are not time-dependent.
 */
object RescueRanking {

    /** How far ahead Today looks. Beyond this an item is not yet a decision. */
    const val HORIZON_DAYS = 7L

    /**
     * Past this, an item has most likely already been dealt with or is beyond
     * saving. Still listed so it can be cleared, but never above fresh food.
     */
    const val STALE_AFTER_DAYS = 3L

    /** Today is a short list. More than this is an inventory, not a decision. */
    const val MAX_ENTRIES = 5

    fun build(
        items: List<Item>,
        today: LocalDate,
        nowMillis: Long,
        maxEntries: Int = MAX_ENTRIES
    ): RescueList {
        val eligible = items.filter { isEligible(it, today, nowMillis) }

        val ranked = eligible
            .map { score(it, today) }
            .sortedWith(
                // Score first, then the earlier date, then name. The last two
                // are not cosmetic: without a total order the list can reshuffle
                // between reads for equally urgent items, which reads as a bug.
                compareByDescending<RescueEntry> { it.score }
                    .thenBy { it.item.expiry.value }
                    .thenBy { it.item.name.lowercase() }
            )

        return RescueList(
            entries = ranked.take(maxEntries),
            overdueCount = eligible.count { it.daysUntilExpiry(today) < 0 },
            dueTodayCount = eligible.count { it.daysUntilExpiry(today) == 0L }
        )
    }

    /**
     * Eligibility is a filter, not a judgement: active, not snoozed, and close
     * enough to matter. Anything excluded here can never be recommended.
     */
    fun isEligible(item: Item, today: LocalDate, nowMillis: Long): Boolean {
        if (item.state != ItemState.ACTIVE) return false
        if (item.quantity <= 0) return false
        item.snoozedUntil?.let { if (it > nowMillis) return false }
        return item.daysUntilExpiry(today) <= HORIZON_DAYS
    }

    private fun score(item: Item, today: LocalDate): RescueEntry {
        val days = item.daysUntilExpiry(today)

        val primary: RescueReason.Primary = when {
            days < 0 -> RescueReason.Overdue(-days)
            days == 0L -> RescueReason.DueToday
            days == 1L -> RescueReason.DueTomorrow
            else -> RescueReason.DueInDays(days)
        }

        // Base urgency. Recently overdue outranks due-today because its window
        // is already closing, but food long past its date drops below fresh
        // items: recommending three-week-old milk over tonight's spinach would
        // be worse than not recommending anything.
        val base = when {
            days < 0 && -days <= STALE_AFTER_DAYS -> 1000
            days == 0L -> 900
            days == 1L -> 800
            days in 2..HORIZON_DAYS -> 700 - (days.toInt() * 10)
            else -> 400
        }

        val supporting = mutableListOf<RescueReason>()
        var modifier = 0

        if (item.quantity > 1) {
            supporting += RescueReason.MultipleUnits(item.quantity)
            modifier += minOf(item.quantity, 5) * 5
        }

        // Value only counts when it was observed. An inferred price must never
        // push an item up the list and then be shown as a reason, because the
        // user would read an estimate as a fact about their money.
        item.verifiedValueMinorUnits?.let { value ->
            val currency = item.price?.currency
            if (currency != null && value > 0) {
                supporting += RescueReason.WorthValue(value, currency)
                modifier += minOf((value / 10_000).toInt(), 10) * 5
            }
        }

        // An unconfirmed date is a weaker claim, so it ranks slightly lower and
        // says so, rather than borrowing the confidence of a printed one.
        if (item.needsDateReview || item.hasEstimatedDate) {
            supporting += RescueReason.DateUnconfirmed
            modifier -= 25
        }

        return RescueEntry(
            item = item,
            score = base + modifier,
            primaryReason = primary,
            supportingReasons = supporting
        )
    }
}
