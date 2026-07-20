package com.example.freshtrack.data.export

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class CsvImporterTest {

    private val header = CsvExporter.HEADER

    private fun csv(vararg rows: String) = (listOf(header) + rows).joinToString("\n")

    @Test
    fun `a plain row imports`() {
        val result = CsvImporter.parse(csv("Milk,Dairy,12345,2026-08-01,2,fresh,2026-07-01,Active"))

        assertTrue(result.errors.isEmpty())
        val product = result.products.single()
        assertEquals("Milk", product.name)
        assertEquals("Dairy", product.category)
        assertEquals("12345", product.barcode)
        assertEquals(2, product.quantity)
        assertEquals("fresh", product.notes)
    }

    @Test
    fun `quoted fields containing commas stay intact`() {
        val result = CsvImporter.parse(
            csv(""""Milk, full fat",Dairy,,2026-08-01,1,"buy again, maybe",2026-07-01,Active""")
        )

        val product = result.products.single()
        assertEquals("Milk, full fat", product.name)
        assertEquals("buy again, maybe", product.notes)
    }

    @Test
    fun `escaped quotes are unescaped`() {
        // Written with escapes rather than a raw string: the CSV contains a
        // doubled quote followed by a closing quote, which would terminate a
        // Kotlin raw string early.
        val row = "\"He said \"\"fresh\"\"\",Dairy,,2026-08-01,1,,2026-07-01,Active"

        val result = CsvImporter.parse(csv(row))

        assertEquals("He said \"fresh\"", result.products.single().name)
    }

    @Test
    fun `a note containing a newline does not split the row`() {
        val result = CsvImporter.parse(
            csv(""""Milk",Dairy,,2026-08-01,1,"line one
line two",2026-07-01,Active""")
        )

        assertEquals(1, result.products.size)
        assertTrue(result.products.single().notes!!.contains("line two"))
    }

    @Test
    fun `status round trips to the right flags`() {
        val result = CsvImporter.parse(
            csv(
                "Used,Dairy,,2026-08-01,1,,2026-07-01,Used",
                "Binned,Dairy,,2026-08-01,1,,2026-07-01,Discarded",
                "Live,Dairy,,2026-08-01,1,,2026-07-01,Active"
            )
        )

        val byName = result.products.associateBy { it.name }
        assertTrue(byName["Used"]!!.isConsumed)
        assertTrue(byName["Binned"]!!.isDiscarded)
        assertFalse(byName["Live"]!!.isConsumed)
        assertFalse(byName["Live"]!!.isDiscarded)
    }

    @Test
    fun `one bad row does not abandon the rest of the file`() {
        val result = CsvImporter.parse(
            csv(
                "Good,Dairy,,2026-08-01,1,,2026-07-01,Active",
                "Broken,Dairy,,not-a-date,1,,2026-07-01,Active",
                "AlsoGood,Bakery,,2026-08-02,1,,2026-07-01,Active"
            )
        )

        assertEquals(listOf("Good", "AlsoGood"), result.products.map { it.name })
        assertEquals(1, result.errors.size)
        assertTrue(result.errors.single().reason.contains("expiry date"))
    }

    @Test
    fun `a row without a name is rejected`() {
        val result = CsvImporter.parse(csv(",Dairy,,2026-08-01,1,,2026-07-01,Active"))
        assertTrue(result.products.isEmpty())
        assertEquals(1, result.errors.size)
    }

    @Test
    fun `a missing category falls back rather than failing the row`() {
        val result = CsvImporter.parse(csv("Mystery,,,2026-08-01,1,,2026-07-01,Active"))
        assertEquals("Other", result.products.single().category)
    }

    @Test
    fun `a bad quantity falls back to one`() {
        val result = CsvImporter.parse(csv("Milk,Dairy,,2026-08-01,abc,,2026-07-01,Active"))
        assertEquals(1, result.products.single().quantity)
    }

    @Test
    fun `blank lines and a repeated header are ignored`() {
        val text = buildString {
            appendLine(header)
            appendLine("Milk,Dairy,,2026-08-01,1,,2026-07-01,Active")
            appendLine("")
            appendLine(header)
            appendLine("Bread,Bakery,,2026-08-02,1,,2026-07-01,Active")
        }

        val result = CsvImporter.parse(text)
        assertEquals(listOf("Milk", "Bread"), result.products.map { it.name })
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `carriage returns from a windows editor are handled`() {
        val text = header + "\r\n" + "Milk,Dairy,,2026-08-01,1,,2026-07-01,Active\r\n"
        val result = CsvImporter.parse(text)
        assertEquals("Milk", result.products.single().name)
    }

    @Test
    fun `an empty file yields nothing rather than throwing`() {
        val result = CsvImporter.parse("")
        assertTrue(result.products.isEmpty())
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun `dates are read in the fixed export format`() {
        val result = CsvImporter.parse(csv("Milk,Dairy,,2026-08-01,1,,2026-07-01,Active"))
        val expected = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2026-08-01")!!.time
        assertEquals(expected, result.products.single().expiryDate)
    }

    @Test
    fun `an empty barcode becomes null rather than an empty string`() {
        val result = CsvImporter.parse(csv("Milk,Dairy,,2026-08-01,1,,2026-07-01,Active"))
        assertNull(result.products.single().barcode)
    }
}

/**
 * Export and import must agree on the exact format. These go through the real
 * exporter rather than a hand-written CSV, so a change to either side that
 * breaks the round trip fails here.
 */
class CsvRoundTripTest {

    private fun product(
        name: String,
        category: String = "Dairy",
        notes: String? = null,
        barcode: String? = null,
        quantity: Int = 1
    ) = com.example.freshtrack.domain.model.Product(
        id = name,
        name = name,
        barcode = barcode,
        category = category,
        expiryDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2026-08-01")!!.time,
        addedDate = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse("2026-07-01")!!.time,
        quantity = quantity,
        notes = notes,
        imageUri = null,
        notificationEnabled = true,
        isConsumed = false,
        isDiscarded = false
    )

    @Test
    fun `ordinary products survive a round trip`() {
        val original = listOf(
            product("Milk", quantity = 2, barcode = "12345"),
            product("Bread", category = "Bakery", notes = "sourdough")
        )

        val restored = CsvImporter.parse(CsvExporter.buildCsv(original)).products

        assertEquals(original.map { it.name }, restored.map { it.name })
        assertEquals(original.map { it.category }, restored.map { it.category })
        assertEquals(original.map { it.quantity }, restored.map { it.quantity })
        assertEquals(original.map { it.barcode }, restored.map { it.barcode })
        assertEquals(original.map { it.notes }, restored.map { it.notes })
        assertEquals(original.map { it.expiryDate }, restored.map { it.expiryDate })
    }

    @Test
    fun `awkward text survives a round trip`() {
        // Commas, quotes and newlines are exactly what naive CSV handling breaks
        // on, and a user's notes are free text.
        val original = listOf(
            product("Milk, full fat", notes = "he said \"fresh\""),
            product("Rice", category = "Pantry", notes = "line one\nline two"),
            product("Dal, toor", category = "Pantry", notes = "buy 2, maybe 3")
        )

        val restored = CsvImporter.parse(CsvExporter.buildCsv(original)).products

        assertEquals(3, restored.size)
        assertEquals(original.map { it.name }, restored.map { it.name })
        assertEquals(original.map { it.notes }, restored.map { it.notes })
    }

    @Test
    fun `exporting nothing produces a file that imports as nothing`() {
        val restored = CsvImporter.parse(CsvExporter.buildCsv(emptyList()))
        assertTrue(restored.products.isEmpty())
        assertTrue(restored.errors.isEmpty())
    }
}
