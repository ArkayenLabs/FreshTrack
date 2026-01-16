package com.example.freshtrack.data.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.freshtrack.domain.model.Product
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

/**
 * Utility class for exporting products to CSV format
 */
object CsvExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * Export products to CSV and return share intent
     */
    fun exportToCSV(context: Context, products: List<Product>): Intent? {
        try {
            // Create CSV content
            val csvContent = buildString {
                // Header row
                appendLine("Name,Category,Barcode,Expiry Date,Quantity,Notes,Added Date,Status")
                
                // Data rows
                products.forEach { product ->
                    val expiryDate = dateFormat.format(Date(product.expiryDate))
                    val addedDate = dateFormat.format(Date(product.addedDate))
                    val status = when {
                        product.isConsumed -> "Consumed"
                        product.isDiscarded -> "Discarded"
                        product.expiryDate < System.currentTimeMillis() -> "Expired"
                        else -> "Active"
                    }
                    
                    // Escape special characters in CSV
                    val name = escapeCsv(product.name)
                    val notes = escapeCsv(product.notes ?: "")
                    val barcode = product.barcode ?: ""
                    
                    appendLine("$name,${product.category},$barcode,$expiryDate,${product.quantity},$notes,$addedDate,$status")
                }
            }

            // Create file in cache directory
            val fileName = "FreshTrack_Export_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"
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
                putExtra(Intent.EXTRA_SUBJECT, "FreshTrack Products Export")
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
