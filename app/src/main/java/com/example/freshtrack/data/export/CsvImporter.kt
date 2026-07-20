package com.example.freshtrack.data.export

import com.example.freshtrack.domain.model.Product
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID

/**
 * Parses a FreshTrack CSV export back into products.
 *
 * Deliberately forgiving about everything except the three fields a product
 * cannot exist without. A restore is usually attempted when something has
 * already gone wrong, so one malformed row should cost that row — not the whole
 * file.
 */
object CsvImporter {

    /** Matches the exporter. Locale.US because the column is machine-written. */
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    data class RowError(val line: Int, val reason: String)

    data class ParseResult(
        val products: List<Product>,
        val errors: List<RowError>
    )

    fun parse(csv: String): ParseResult {
        val rows = splitRows(csv)
        val products = mutableListOf<Product>()
        val errors = mutableListOf<RowError>()

        rows.forEachIndexed { index, row ->
            val lineNumber = index + 1
            val fields = parseLine(row)

            // Skip the header wherever it appears, so a concatenated file still
            // imports rather than failing on a repeated header.
            if (fields.firstOrNull()?.equals("Name", ignoreCase = true) == true) return@forEachIndexed
            if (fields.all { it.isBlank() }) return@forEachIndexed

            if (fields.size < 5) {
                errors += RowError(lineNumber, "Expected at least 5 columns, found ${fields.size}")
                return@forEachIndexed
            }

            val name = fields[0].trim()
            val category = fields[1].trim()
            val barcode = fields.getOrNull(2)?.trim().orEmpty()
            val expiryRaw = fields[3].trim()
            val quantityRaw = fields[4].trim()
            val notes = fields.getOrNull(5)?.trim().orEmpty()
            val addedRaw = fields.getOrNull(6)?.trim().orEmpty()
            val status = fields.getOrNull(7)?.trim().orEmpty()

            if (name.isEmpty()) {
                errors += RowError(lineNumber, "Name is required")
                return@forEachIndexed
            }

            val expiryDate = parseDate(expiryRaw)
            if (expiryDate == null) {
                errors += RowError(lineNumber, "Could not read expiry date \"$expiryRaw\"")
                return@forEachIndexed
            }

            products += Product(
                id = UUID.randomUUID().toString(),
                name = name,
                barcode = barcode.ifEmpty { null },
                // An unknown category becomes Other rather than failing the row;
                // the item itself is still worth keeping.
                category = category.ifEmpty { "Other" },
                expiryDate = expiryDate,
                addedDate = parseDate(addedRaw) ?: System.currentTimeMillis(),
                quantity = quantityRaw.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                originalQuantity = quantityRaw.toIntOrNull()?.coerceAtLeast(1) ?: 1,
                notes = notes.ifEmpty { null },
                imageUri = null,
                notificationEnabled = true,
                isConsumed = status.equals("Used", ignoreCase = true),
                isDiscarded = status.equals("Discarded", ignoreCase = true)
            )
        }

        return ParseResult(products, errors)
    }

    private fun parseDate(value: String): Long? = try {
        if (value.isBlank()) null else dateFormat.parse(value)?.time
    } catch (e: ParseException) {
        null
    }

    /**
     * Splits into rows on newlines that are not inside a quoted field, so a note
     * containing a line break does not split into two broken rows.
     */
    private fun splitRows(csv: String): List<String> {
        val rows = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < csv.length) {
            val c = csv[i]
            when {
                c == '"' -> {
                    inQuotes = !inQuotes
                    current.append(c)
                }
                (c == '\n' || c == '\r') && !inQuotes -> {
                    if (current.isNotEmpty()) {
                        rows += current.toString()
                        current.clear()
                    }
                    // Consume the second half of a CRLF pair.
                    if (c == '\r' && i + 1 < csv.length && csv[i + 1] == '\n') i++
                }
                else -> current.append(c)
            }
            i++
        }
        if (current.isNotEmpty()) rows += current.toString()
        return rows
    }

    /** Splits one row, honouring quoted fields and doubled escaped quotes. */
    private fun parseLine(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                    current.append('"')
                    i++
                }
                c == '"' -> inQuotes = !inQuotes
                c == ',' && !inQuotes -> {
                    fields += current.toString()
                    current.clear()
                }
                else -> current.append(c)
            }
            i++
        }
        fields += current.toString()
        return fields
    }
}
