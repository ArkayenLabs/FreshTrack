package com.example.freshtrack.domain.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * What text recognition actually hands the receipt parser.
 *
 * [ReceiptParser] requires an item and its price on one line: it looks for a
 * trailing amount and returns nothing without one. Every test of it so far has
 * supplied text typed by hand in exactly that shape, which assumes something
 * about ML Kit that has never been checked.
 *
 * That assumption is no longer made: [ReceiptLines] rebuilds rows from where the
 * text physically sat, so the pipeline is correct whether the recogniser returns
 * whole lines or splits the two columns into separate blocks. These tests check
 * that on a real recogniser rather than on geometry written out by hand, which
 * is what `ReceiptLinesTest` does on the JVM.
 *
 * They render rather than photograph, so they still cannot speak to glare or
 * crumple, and the fixture debt owed for real receipts is unchanged. What they
 * settle is that the assembled text survives an actual ML Kit result.
 */
class ReceiptRecognitionTest {

    private val recogniser =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @After
    fun tearDown() = recogniser.close()

    private val shopping = listOf(
        "SEMI-SKIMMED MILK" to "1.85",
        "SOURDOUGH LOAF" to "2.40",
        "BASMATI RICE 1KG" to "3.99",
        "CHEDDAR MATURE" to "2.75"
    )

    /**
     * A till receipt, printed the way they are: names on the left, prices
     * against the right edge, with whatever gap the paper width leaves.
     */
    private fun receiptImage(width: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, 700, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 34f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            isAntiAlias = true
        }

        canvas.drawText("GREENFIELD MARKET", 30f, 60f, paint)
        shopping.forEachIndexed { index, (name, price) ->
            val y = 160f + index * 70f
            canvas.drawText(name, 30f, y, paint)
            // Right-aligned, which is what opens the gap.
            canvas.drawText(price, width - 30f - paint.measureText(price), y, paint)
        }
        val total = "TOTAL"
        val totalAmount = "11.09"
        canvas.drawText(total, 30f, 500f, paint)
        canvas.drawText(totalAmount, width - 30f - paint.measureText(totalAmount), 500f, paint)

        return bitmap
    }

    /** Recognises, then rebuilds the rows the same way the screen does. */
    private fun recognise(width: Int): String {
        val image = InputImage.fromBitmap(receiptImage(width), 0)
        val result = Tasks.await(recogniser.process(image), 30, TimeUnit.SECONDS)
        val fragments = result.textBlocks
            .flatMap { block -> block.lines }
            .mapNotNull { line ->
                line.boundingBox?.let { box ->
                    TextFragment(line.text, box.top, box.bottom, box.left)
                }
            }
        return if (fragments.isEmpty()) result.text else ReceiptLines.assemble(fragments)
    }

    /**
     * Reports what came back as well as whether it worked.
     *
     * A bare "expected 4, got 0" would say the pipeline is broken without
     * saying how, and how is the entire question here.
     */
    private fun report(label: String, text: String): String {
        val parsed = ReceiptParser.parse(text)
        return buildString {
            appendLine("$label produced ${parsed.items.size} items " +
                "and ${parsed.unresolvedLines.size} unreadable lines.")
            appendLine("--- items ---")
            parsed.items.forEach { appendLine("  ${it.name} | price=${it.priceMinor}") }
            appendLine("--- unreadable ---")
            parsed.unresolvedLines.forEach { appendLine("  $it") }
            appendLine("--- raw recognised text ---")
            appendLine(text)
        }
    }

    @Test
    fun readsAReceiptWithAWideGapBetweenNameAndPrice() {
        // 900px of paper for 34px text puts a lot of white between the columns,
        // which is exactly the receipt people actually photograph.
        val text = recognise(width = 900)
        val parsed = ReceiptParser.parse(text)

        assertTrue(
            report("wide gap", text),
            parsed.items.size >= shopping.size
        )
        assertTrue(
            "wide gap: an item came through without its price\n" + report("wide gap", text),
            parsed.items.all { it.priceMinor != null }
        )
    }

    @Test
    fun readsAReceiptWithTheColumnsCloseTogether() {
        // The control. If this passes and the wide one fails, the gap is the
        // cause and the fix is to reassemble lines from block geometry rather
        // than to trust result.text.
        val text = recognise(width = 520)
        val parsed = ReceiptParser.parse(text)

        assertTrue(
            report("narrow gap", text),
            parsed.items.size >= shopping.size
        )
    }
}
