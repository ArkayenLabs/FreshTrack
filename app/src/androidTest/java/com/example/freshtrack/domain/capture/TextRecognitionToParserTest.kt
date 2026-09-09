package com.example.freshtrack.domain.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.example.freshtrack.domain.model.DateKind
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.android.gms.tasks.Tasks
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Text recognition and the parser, joined.
 *
 * Rendered text is not a photograph of a packet — no glare, no curve, no dot
 * matrix — so this proves the pipeline is connected, not that recognition is
 * accurate in a kitchen. That needs real label photographs and is deliberately
 * still owed. What it does catch is the whole class of failure where the two
 * halves each work and nothing carries between them: the shape of the text ML
 * Kit hands back, multi-line labels arriving as one block, and a parser that
 * quietly returns nothing for all of it.
 */
class TextRecognitionToParserTest {

    private val recogniser =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    private val today = LocalDate.of(2026, 9, 9)

    @After
    fun tearDown() = recogniser.close()

    /** A white card with [lines] printed on it, the way a label reads. */
    private fun labelImage(vararg lines: String): Bitmap {
        val bitmap = Bitmap.createBitmap(900, 400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint().apply {
            color = Color.BLACK
            textSize = 72f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            isAntiAlias = true
        }
        lines.forEachIndexed { index, line ->
            canvas.drawText(line, 40f, 120f + index * 90f, paint)
        }
        return bitmap
    }

    private fun read(vararg lines: String): List<DateCandidate> {
        val image = InputImage.fromBitmap(labelImage(*lines), 0)
        val result = Tasks.await(recogniser.process(image), 30, TimeUnit.SECONDS)
        return PrintedDateParser.parse(result.text, today)
    }

    @Test
    fun readsADateOffARenderedLabel() {
        val candidates = read("BEST BEFORE", "12/03/2027")

        assertTrue("nothing recognised from the label", candidates.isNotEmpty())
        assertEquals(LocalDate.of(2027, 3, 12), candidates.first().value)
        assertEquals(DateKind.BEST_BEFORE, candidates.first().kind)
    }

    @Test
    fun keepsTheLabelKindAcrossTheLineBreak() {
        // The label and the date land on separate lines constantly, and the
        // association between them is what tells a use-by from a best-before.
        assertEquals(DateKind.USE_BY, read("USE BY", "2027-03-12").first().kind)
    }

    @Test
    fun stillRefusesThePackingDateWhenBothAreOnTheLabel() {
        val candidates = read("MFD 12/03/2025", "EXP 12/03/2027")

        assertTrue(candidates.isNotEmpty())
        assertTrue(
            "a packing date reached the candidates",
            candidates.none { it.value.year == 2025 }
        )
    }

    @Test
    fun findsNoDateInALabelThatHasNone() {
        assertTrue(read("KEEP REFRIGERATED", "BELOW 5 C").isEmpty())
    }
}
