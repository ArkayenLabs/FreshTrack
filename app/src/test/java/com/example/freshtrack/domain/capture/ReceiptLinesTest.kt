package com.example.freshtrack.domain.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Putting a receipt's columns back into lines.
 *
 * The failure this exists to prevent is not a bad parse. It is a recogniser
 * that reads a receipt as two blocks — every item name, then every price — and
 * a parser that consequently finds no shopping on a perfectly legible receipt,
 * while every unit test of the parser stays green because they were all written
 * against text typed out one row at a time.
 */
class ReceiptLinesTest {

    /** One run of text on a line of the receipt, [row] counting downwards. */
    private fun fragment(text: String, row: Int, left: Int) = TextFragment(
        text = text,
        top = row * 70,
        bottom = row * 70 + 40,
        left = left
    )

    /**
     * A receipt read as two blocks: the whole name column, then the whole price
     * column. This is the shape that breaks everything downstream.
     */
    private val splitColumns = listOf(
        fragment("SEMI-SKIMMED MILK", row = 1, left = 30),
        fragment("SOURDOUGH LOAF", row = 2, left = 30),
        fragment("BASMATI RICE 1KG", row = 3, left = 30),
        fragment("1.85", row = 1, left = 760),
        fragment("2.40", row = 2, left = 760),
        fragment("3.99", row = 3, left = 760)
    )

    @Test
    fun `puts a name back beside its price`() {
        val text = ReceiptLines.assemble(splitColumns)

        assertEquals(
            listOf(
                "SEMI-SKIMMED MILK  1.85",
                "SOURDOUGH LOAF  2.40",
                "BASMATI RICE 1KG  3.99"
            ),
            text.lines()
        )
    }

    @Test
    fun `the parser finds the shopping once the columns are rejoined`() {
        // The whole point, stated end to end: this same input read as
        // result.text would be three names and three bare numbers, and the
        // parser would find nothing purchasable in any of it.
        val parsed = ReceiptParser.parse(ReceiptLines.assemble(splitColumns))

        assertEquals(3, parsed.items.size)
        assertEquals(185, parsed.items[0].priceMinor)
        assertEquals(240, parsed.items[1].priceMinor)
        assertEquals(399, parsed.items[2].priceMinor)
    }

    @Test
    fun `the same input concatenated by column really would fail`() {
        // Guards the premise rather than the fix. If this ever stops being
        // true, the reassembly above is solving a problem that went away.
        val byColumn = splitColumns.joinToString("\n") { it.text }

        assertTrue(ReceiptParser.parse(byColumn).items.isEmpty())
    }

    @Test
    fun `lines that already arrived whole are left alone`() {
        // The property that makes this safe to apply unconditionally: it is
        // correct whichever way the recogniser behaves.
        val whole = listOf(
            fragment("SEMI-SKIMMED MILK      1.85", row = 1, left = 30),
            fragment("SOURDOUGH LOAF         2.40", row = 2, left = 30)
        )

        assertEquals(
            listOf("SEMI-SKIMMED MILK      1.85", "SOURDOUGH LOAF         2.40"),
            ReceiptLines.assemble(whole).lines()
        )
    }

    @Test
    fun `rows come back in the order they were printed`() {
        val shuffled = splitColumns.reversed()

        assertEquals(ReceiptLines.assemble(splitColumns), ReceiptLines.assemble(shuffled))
    }

    @Test
    fun `a slight skew still reads as one line`() {
        // A receipt photographed by hand is never quite square, so the price
        // sits a few pixels off the name. Requiring exact alignment would break
        // on every real photograph.
        val skewed = listOf(
            TextFragment("SEMI-SKIMMED MILK", top = 100, bottom = 140, left = 30),
            TextFragment("1.85", top = 108, bottom = 148, left = 760)
        )

        assertEquals("SEMI-SKIMMED MILK  1.85", ReceiptLines.assemble(skewed))
    }

    @Test
    fun `text on separate lines stays on separate lines`() {
        val stacked = listOf(
            fragment("SEMI-SKIMMED MILK", row = 1, left = 30),
            fragment("SOURDOUGH LOAF", row = 2, left = 30)
        )

        assertEquals(2, ReceiptLines.assemble(stacked).lines().size)
    }

    @Test
    fun `nothing recognised is an empty string rather than a blank line`() {
        assertEquals("", ReceiptLines.assemble(emptyList()))
        assertEquals("", ReceiptLines.assemble(listOf(fragment("   ", row = 1, left = 30))))
    }
}
