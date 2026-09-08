package com.example.freshtrack.domain.model

import com.example.freshtrack.data.local.entities.CategoryEntity

/**
 * How close something is to being a problem.
 *
 * Bands rather than a raw day count, because the UI decision — what colour,
 * what wording, what to put at the top — is the same for everything in a band.
 */
enum class ExpiryUrgency {
    /** More than a week away. */
    SAFE,

    /** Three to seven days. */
    WARNING,

    /** Due today, tomorrow or the day after. */
    CRITICAL,

    /** Already past its date. */
    EXPIRED
}

/** Filters offered on the inventory list. */
enum class ProductFilter {
    ALL,
    EXPIRING_SOON,
    EXPIRED,
    BY_CATEGORY
}

/** Sort orders offered on the inventory list. */
enum class ProductSort {
    EXPIRY_DATE_ASC,
    EXPIRY_DATE_DESC,
    NAME_ASC,
    NAME_DESC,
    ADDED_DATE_DESC
}

data class Category(
    val name: String,
    val colorHex: String,
    val icon: String,
    val sortOrder: Int
)

fun CategoryEntity.toDomain(): Category = Category(
    name = name,
    colorHex = colorHex,
    icon = icon,
    sortOrder = sortOrder
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    name = name,
    colorHex = colorHex,
    icon = icon,
    sortOrder = sortOrder
)
