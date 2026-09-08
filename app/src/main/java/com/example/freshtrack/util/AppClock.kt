package com.example.freshtrack.util

import java.time.LocalDate
import java.time.ZoneId

/**
 * The current time, behind an interface.
 *
 * Expiry logic is almost entirely "how does this date compare to today", which
 * is untestable against a hardcoded call to the system clock — you end up
 * writing tests whose expected values drift, or that only pass before midnight.
 *
 * [today] is a local calendar date rather than a derived instant, because that
 * is the question being asked: whether milk is out of date depends on the date
 * where the person is standing, not on UTC.
 */
interface AppClock {
    fun nowMillis(): Long
    fun today(): LocalDate

    companion object {
        val System: AppClock = SystemAppClock()
    }
}

private class SystemAppClock : AppClock {
    override fun nowMillis(): Long = java.lang.System.currentTimeMillis()
    override fun today(): LocalDate = LocalDate.now(ZoneId.systemDefault())
}
