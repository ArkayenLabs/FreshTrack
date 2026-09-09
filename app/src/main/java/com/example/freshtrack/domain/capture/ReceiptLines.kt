package com.example.freshtrack.domain.capture

/**
 * One run of recognised text, and where it sat on the paper.
 *
 * Deliberately not an ML Kit type. The geometry is the whole point and it is
 * four integers, so the reassembly below can be tested on the JVM against
 * layouts written out by hand rather than only against a photograph on a device.
 */
data class TextFragment(
    val text: String,
    val top: Int,
    val bottom: Int,
    val left: Int
)

/**
 * Puts a receipt's columns back into lines.
 *
 * [ReceiptParser] needs an item and its price on one line — it looks for a
 * trailing amount and returns nothing without one. A recogniser is under no
 * obligation to provide that. A till receipt is two columns with a wide gap,
 * and reading it as two blocks — every name, then every price — is a perfectly
 * reasonable thing for it to do. Concatenating a result in that order gives a
 * list of names with no prices followed by a list of prices with no names, and
 * the parser correctly finds no shopping in it at all.
 *
 * Rather than depend on which way the recogniser happens to go, this puts the
 * rows back together from where the text physically was. Two runs belong on the
 * same line when either one's vertical centre falls within the other's span,
 * which is what "printed side by side" means and survives the slight skew of a
 * photograph taken by hand.
 *
 * The case where the recogniser already returned whole lines is not special:
 * each line is one fragment, forms a row on its own, and comes back unchanged.
 * That is the property that matters — this is correct whichever way ML Kit
 * behaves, which is why it replaces the question rather than answering it.
 */
object ReceiptLines {

    /** Wide enough to read as a gap between columns rather than a space. */
    private const val COLUMN_GAP = "  "

    fun assemble(fragments: List<TextFragment>): String {
        val usable = fragments.filter { it.text.isNotBlank() }
        if (usable.isEmpty()) return ""

        val rows = mutableListOf<MutableList<TextFragment>>()
        // Top down, so a row is opened by its leftmost-highest run and later
        // fragments join the row already holding something beside them.
        usable.sortedBy { it.top }.forEach { fragment ->
            val existing = rows.firstOrNull { row -> row.any { sharesLine(it, fragment) } }
            if (existing != null) existing += fragment else rows += mutableListOf(fragment)
        }

        return rows
            .sortedBy { row -> row.minOf { it.top } }
            .joinToString("\n") { row ->
                row.sortedBy { it.left }.joinToString(COLUMN_GAP) { it.text.trim() }
            }
    }

    /**
     * Whether two runs were printed beside each other.
     *
     * Centre-within-span rather than any overlap at all: a tall run and the
     * line beneath it can clip each other by a pixel or two without being on
     * the same line, and a centre has to be properly inside to count.
     */
    private fun sharesLine(a: TextFragment, b: TextFragment): Boolean {
        val aCentre = (a.top + a.bottom) / 2
        val bCentre = (b.top + b.bottom) / 2
        return aCentre in b.top..b.bottom || bCentre in a.top..a.bottom
    }
}
