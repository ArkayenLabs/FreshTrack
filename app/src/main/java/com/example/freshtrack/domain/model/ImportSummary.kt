package com.example.freshtrack.domain.model

/**
 * Outcome of a CSV import, reported back to the user.
 *
 * Skipped duplicates are counted rather than hidden — someone importing a file
 * needs to know why they got fewer items than the file contained, or they will
 * assume the import broke.
 */
data class ImportSummary(
    val imported: Int = 0,
    val skippedDuplicates: Int = 0,
    val failedRows: Int = 0
) {
    val total: Int get() = imported + skippedDuplicates + failedRows
}
