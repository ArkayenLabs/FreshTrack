package com.example.freshtrack.domain.capture

import com.example.freshtrack.domain.model.DateKind
import java.time.DateTimeException
import java.time.LocalDate
import java.util.Locale

/**
 * Which way round an all-numeric date is written.
 *
 * There is no way to tell 03/04/26 apart by looking at it: it is the 3rd of
 * April to most of the world and the 4th of March in the United States. This
 * says which reading to *prefer*, never which to assume — an ambiguous date
 * still produces both candidates.
 */
enum class DateOrdering {
    DAY_FIRST,
    MONTH_FIRST;

    companion object {
        /**
         * The convention where [locale] is spoken.
         *
         * The United States writes the month first; the United Kingdom, and
         * almost everywhere else, writes the day first. This is derived rather
         * than fixed because a wrong default here is not a cosmetic problem —
         * it silently prefers the wrong one of two real dates, and 03/04 is
         * five weeks out either way.
         */
        fun forLocale(locale: Locale): DateOrdering =
            if (locale.country.equals("US", ignoreCase = true)) MONTH_FIRST else DAY_FIRST
    }
}

/**
 * One reading of a date found in text, with why it was read that way.
 *
 * [confidence] describes the *parse* only: how sure we are that these digits
 * mean this calendar date. It says nothing about whether the camera read the
 * digits correctly, which is the other half and belongs to whatever produced
 * the text. The two multiply — a crisp parse of a misread label is still wrong,
 * so a caller with an OCR confidence must combine them rather than use this one
 * alone.
 */
data class DateCandidate(
    val value: LocalDate,
    val kind: DateKind,
    val confidence: Float,
    val reasonCodes: List<String>
)

/**
 * Reads printed date labels off food packaging, deterministically.
 *
 * This exists before any model does, and stays after one arrives: it is the
 * fallback when a model is unavailable, and the check on one when it is not.
 * It never guesses silently — where a label genuinely has two readings it
 * returns both, at a confidence low enough that the caller must ask.
 */
object PrintedDateParser {

    /** How far either side of today a printed food date can plausibly fall. */
    private const val MAX_YEARS_PAST = 5L
    private const val MAX_YEARS_FUTURE = 15L

    /** Longest gap between a label and the date it introduces. */
    private const val LABEL_REACH = 20

    private const val MAX_CANDIDATES = 5

    private val MONTHS = mapOf(
        "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4, "MAY" to 5, "JUN" to 6,
        "JUL" to 7, "AUG" to 8, "SEP" to 9, "OCT" to 10, "NOV" to 11, "DEC" to 12
    )

    /**
     * What a label says the date *means*.
     *
     * "Use by" is a safety instruction and "best before" is a quality one, so
     * they are kept apart rather than folded into a single notion of expiry.
     * A bare "EXP" is deliberately [DateKind.UNKNOWN]: it is certainly a date
     * about the end of the product's life, but it does not say which of the two
     * it is, and inventing that distinction is exactly the collapse to avoid.
     */
    private enum class Label(val kind: DateKind?, val patterns: List<String>) {
        BEST_BEFORE(
            DateKind.BEST_BEFORE,
            listOf("BEST BEFORE END", "BEST BEFORE", "BEST BY", "BBE", "BB")
        ),
        USE_BY(DateKind.USE_BY, listOf("USE BY", "CONSUME BY", "UB")),
        SELL_BY(DateKind.SELL_BY, listOf("SELL BY", "DISPLAY UNTIL")),
        EXPIRY(DateKind.UNKNOWN, listOf("EXPIRY DATE", "EXPIRES", "EXPIRY", "EXP DATE", "EXP")),

        /**
         * Not an expiry at all. A packed-on date sits beside the expiry on a
         * great deal of packaging, and reading it as one would report food as
         * months out of date the moment it was bought — so dates this label
         * introduces are dropped rather than ranked low.
         */
        MANUFACTURE(
            null,
            listOf("MANUFACTURED ON", "MANUFACTURED", "PACKED ON", "PACKED", "MFD", "MFG", "PKD")
        )
    }

    private data class LabelHit(val label: Label, val endsAt: Int)

    /** ISO, and unambiguous by construction. */
    private val ISO = Regex("""\b(\d{4})-(\d{1,2})-(\d{1,2})\b""")

    /** 12 MAR 2027, 12 MARCH 27, 12-MAR-2027. */
    private val DAY_MONTH_NAME_YEAR =
        Regex("""\b(\d{1,2})\s*[-/ ]?\s*([A-Z]{3,9})\.?\s*[-/ ]?\s*(\d{2,4})\b""")

    /** 12/03/2027, 12.03.27, 12-3-2027. */
    private val NUMERIC = Regex("""\b(\d{1,2})[./\-](\d{1,2})[./\-](\d{2,4})\b""")

    /** MAR 2027 — a month with no day means the end of it. */
    private val MONTH_NAME_YEAR = Regex("""\b([A-Z]{3,9})\.?\s*[-/ ]?\s*(\d{4}|\d{2})\b""")

    /** 03/2027 — likewise end of month. */
    private val MONTH_YEAR = Regex("""\b(\d{1,2})[./\-](\d{4})\b""")

    /**
     * Every date reading found in [text], best first.
     *
     * [today] anchors both the plausibility window and the century a two-digit
     * year expands into, and is passed in rather than read from the clock so
     * the same input always parses the same way in a test.
     */
    fun parse(
        text: String,
        today: LocalDate,
        ordering: DateOrdering = DateOrdering.forLocale(Locale.getDefault())
    ): List<DateCandidate> {
        val normalised = text.uppercase().replace(Regex("""\s+"""), " ")
        val labels = findLabels(normalised)
        val consumed = mutableListOf<IntRange>()
        val candidates = mutableListOf<DateCandidate>()

        fun claim(range: IntRange): Boolean {
            if (consumed.any { it.first <= range.last && range.first <= it.last }) return false
            consumed += range
            return true
        }

        // Ordered most specific first, so that "12 MAR 2027" is not first eaten
        // by a rule that only wanted "MAR 2027".
        for (match in ISO.findAll(normalised)) {
            if (!claim(match.range)) continue
            val (y, m, d) = match.destructured
            val label = labelFor(labels, match.range.first) ?: continue
            addCandidate(
                candidates, dateOf(y.toInt(), m.toInt(), d.toInt()), label, today,
                base = 0.95f, reasons = listOf("iso_8601")
            )
        }

        for (match in DAY_MONTH_NAME_YEAR.findAll(normalised)) {
            val month = MONTHS[match.groupValues[2].take(3)] ?: continue
            if (!claim(match.range)) continue
            val label = labelFor(labels, match.range.first) ?: continue
            val year = expandYear(match.groupValues[3], today)
            addCandidate(
                candidates, dateOf(year, month, match.groupValues[1].toInt()), label, today,
                base = 0.90f, reasons = listOf("month_name") + yearReason(match.groupValues[3])
            )
        }

        for (match in NUMERIC.findAll(normalised)) {
            if (!claim(match.range)) continue
            val label = labelFor(labels, match.range.first) ?: continue
            val a = match.groupValues[1].toInt()
            val b = match.groupValues[2].toInt()
            val year = expandYear(match.groupValues[3], today)
            val yearReasons = yearReason(match.groupValues[3])

            when {
                // Only one reading survives the calendar, so there is nothing
                // ambiguous about it however it was written.
                a > 12 && b <= 12 -> addCandidate(
                    candidates, dateOf(year, b, a), label, today,
                    base = 0.80f, reasons = listOf("day_exceeds_twelve") + yearReasons
                )
                b > 12 && a <= 12 -> addCandidate(
                    candidates, dateOf(year, a, b), label, today,
                    base = 0.80f, reasons = listOf("day_exceeds_twelve") + yearReasons
                )
                a <= 12 && b <= 12 && a != b -> {
                    // Genuinely two dates. Both are offered, preference first,
                    // and both sit below the bar that would let either be saved
                    // without being shown to someone.
                    val dayFirst = ordering == DateOrdering.DAY_FIRST
                    val reasons = listOf("ambiguous_day_month") + yearReasons
                    addCandidate(
                        candidates, dateOf(year, if (dayFirst) b else a, if (dayFirst) a else b),
                        label, today, base = 0.55f, reasons = reasons
                    )
                    addCandidate(
                        candidates, dateOf(year, if (dayFirst) a else b, if (dayFirst) b else a),
                        label, today, base = 0.35f, reasons = reasons
                    )
                }
                // a == b, so both readings agree and the ambiguity is moot.
                a <= 12 && b <= 12 -> addCandidate(
                    candidates, dateOf(year, a, b), label, today,
                    base = 0.80f, reasons = listOf("day_equals_month") + yearReasons
                )
            }
        }

        for (match in MONTH_NAME_YEAR.findAll(normalised)) {
            val month = MONTHS[match.groupValues[1].take(3)] ?: continue
            if (!claim(match.range)) continue
            val label = labelFor(labels, match.range.first) ?: continue
            val year = expandYear(match.groupValues[2], today)
            addCandidate(
                candidates, endOfMonth(year, month), label, today,
                base = 0.85f, reasons = listOf("month_name", "end_of_month")
            )
        }

        for (match in MONTH_YEAR.findAll(normalised)) {
            if (!claim(match.range)) continue
            val label = labelFor(labels, match.range.first) ?: continue
            val month = match.groupValues[1].toInt()
            if (month !in 1..12) continue
            addCandidate(
                candidates, endOfMonth(match.groupValues[2].toInt(), month), label, today,
                base = 0.75f, reasons = listOf("end_of_month")
            )
        }

        return candidates
            .distinctBy { it.value to it.kind }
            .sortedByDescending { it.confidence }
            .take(MAX_CANDIDATES)
    }

    private fun findLabels(text: String): List<LabelHit> {
        val hits = mutableListOf<LabelHit>()
        val taken = mutableListOf<IntRange>()
        // Longest patterns first, so "BEST BEFORE" is not matched as "BB", and
        // "EXPIRY DATE" not left half-consumed by "EXP".
        val ordered = Label.entries
            .flatMap { label -> label.patterns.map { label to it } }
            .sortedByDescending { it.second.length }

        for ((label, pattern) in ordered) {
            Regex("""\b${Regex.escape(pattern)}\b""").findAll(text).forEach { match ->
                if (taken.none { it.first <= match.range.last && match.range.first <= it.last }) {
                    taken += match.range
                    hits += LabelHit(label, match.range.last)
                }
            }
        }
        return hits
    }

    /**
     * The label governing a date at [start], or null if a manufacture label does.
     *
     * Null means "drop this date", which is why the caller `continue`s on it.
     * An unlabelled date is not null — it is [DateKind.UNKNOWN], because plenty
     * of packaging prints the date on its own line.
     */
    private fun labelFor(labels: List<LabelHit>, start: Int): DateKind? {
        val nearest = labels
            .filter { it.endsAt < start && start - it.endsAt <= LABEL_REACH }
            .maxByOrNull { it.endsAt }
            ?: return DateKind.UNKNOWN
        return nearest.label.kind
    }

    private fun addCandidate(
        into: MutableList<DateCandidate>,
        value: LocalDate?,
        kind: DateKind,
        today: LocalDate,
        base: Float,
        reasons: List<String>
    ) {
        if (value == null) return
        if (value.isBefore(today.minusYears(MAX_YEARS_PAST))) return
        if (value.isAfter(today.plusYears(MAX_YEARS_FUTURE))) return

        // A nearby label is evidence the digits are a date at all, not a batch
        // number that happens to look like one.
        val labelled = kind != DateKind.UNKNOWN
        val confidence = (if (labelled) base + 0.05f else base).coerceIn(0f, 1f)
        into += DateCandidate(
            value = value,
            kind = kind,
            confidence = confidence,
            reasonCodes = (reasons + if (labelled) "labelled" else "unlabelled").take(10)
        )
    }

    /** Null for a date that does not exist, such as the 31st of February. */
    private fun dateOf(year: Int, month: Int, day: Int): LocalDate? =
        try {
            LocalDate.of(year, month, day)
        } catch (e: DateTimeException) {
            null
        }

    private fun endOfMonth(year: Int, month: Int): LocalDate? =
        dateOf(year, month, 1)?.let { it.withDayOfMonth(it.lengthOfMonth()) }

    /**
     * Two digits into a year near [today] rather than near 2000.
     *
     * A printed food date is within a few years of now in both directions, so
     * "27" is 2027 and not 1927. The century is taken from today and nudged
     * only when that would land absurdly far in the past.
     */
    private fun expandYear(raw: String, today: LocalDate): Int {
        if (raw.length == 4) return raw.toInt()
        val century = today.year / 100 * 100
        val candidate = century + raw.toInt()
        return if (candidate < today.year - MAX_YEARS_PAST) candidate + 100 else candidate
    }

    private fun yearReason(raw: String): List<String> =
        if (raw.length < 4) listOf("two_digit_year") else emptyList()
}
