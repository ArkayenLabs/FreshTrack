package com.example.freshtrack.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.freshtrack.data.local.entities.ItemState
import com.example.freshtrack.domain.model.Item
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import com.example.freshtrack.R

/**
 * Utility class for exporting products to CSV format
 */
object CsvExporter {

    /**
     * Locale.US on purpose. This column is machine-read on import, and
     * Locale.getDefault() can emit non-Latin digits on some device locales,
     * producing a file the importer cannot parse.
     */
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** ISO, and identical to the stored form, so a date cannot shift on export. */
    private val expiryFormat: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    const val HEADER = "Name,Category,Barcode,Expiry Date,Quantity,Notes,Added Date,Status"

    /**
     * Export products to CSV and return share intent
     */
    /**
     * Builds the CSV text.
     *
     * Separate from file and Intent handling so the exact format can be
     * round-tripped against CsvImporter in a unit test — this is the seam where
     * export and import would otherwise silently drift apart.
     */
    fun buildCsv(products: List<Item>, today: LocalDate): String = buildString {
        appendLine(HEADER)

        products.forEach { product ->
            val expiryDate = product.expiry.value.format(expiryFormat)
            val addedDate = dateFormat.format(Date(product.addedAt))
            val status = when {
                product.state == ItemState.USED -> "Used"
                product.state == ItemState.DISCARDED -> "Discarded"
                product.isExpired(today) -> "Expired"
                else -> "Active"
            }

            // Every free-text field is escaped. Category was previously written
            // raw, which would corrupt the file the moment a custom category
            // contained a comma.
            val name = escapeCsv(product.name)
            val category = escapeCsv(product.category)
            val notes = escapeCsv(product.notes ?: "")
            val barcode = escapeCsv(product.barcode ?: "")

            appendLine("$name,$category,$barcode,$expiryDate,${product.quantity},$notes,$addedDate,$status")
        }
    }

    /**
     * Export products to CSV and return share intent
     */
    fun exportToCSV(
        context: Context,
        products: List<Item>,
        today: LocalDate = LocalDate.now()
    ): Intent? {
        try {
            val csvContent = buildCsv(products, today)

            // Create file in cache directory
            val prefix = context.getString(R.string.export_file_prefix)
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(Date())
            val fileName = "${prefix}_$stamp.csv"
            val file = File(context.cacheDir, fileName)
            FileWriter(file).use { writer ->
                writer.write(csvContent)
            }

            // Create share intent using FileProvider
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            return Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_subject))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    /**
     * Escape special characters for CSV format
     */
    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
