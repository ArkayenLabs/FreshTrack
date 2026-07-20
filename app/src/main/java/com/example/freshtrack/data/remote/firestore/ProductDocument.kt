package com.example.freshtrack.data.remote.firestore

import com.example.freshtrack.data.local.entities.ProductEntity

/**
 * Mapping between [ProductEntity] and its Firestore document.
 *
 * Two fields are deliberately not synced:
 *
 * - **pantryId** is implied by the document path, so storing it in the document
 *   would be a second copy of the same fact that could disagree with the path.
 *   It is restored from the path on read.
 * - **imageUri** points at a file on one device's storage. Sending it to another
 *   device would produce a path that resolves to nothing, showing a broken image
 *   rather than no image. Photos need real file upload, which is out of scope
 *   here, so each device keeps its own value.
 */
object ProductFields {
    const val NAME = "name"
    const val BARCODE = "barcode"
    const val CATEGORY = "category"
    const val EXPIRY_DATE = "expiryDate"
    const val ADDED_DATE = "addedDate"
    const val QUANTITY = "quantity"
    const val ORIGINAL_QUANTITY = "originalQuantity"
    const val NOTES = "notes"
    const val NOTIFICATION_ENABLED = "notificationEnabled"
    const val IS_CONSUMED = "isConsumed"
    const val IS_DISCARDED = "isDiscarded"
    const val RESOLVED_DATE = "resolvedDate"
    const val USER_ID = "userId"
    const val UPDATED_AT = "updatedAt"
    const val IS_DELETED = "isDeleted"
    const val DELETED_AT = "deletedAt"
}

fun ProductEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    ProductFields.NAME to name,
    ProductFields.BARCODE to barcode,
    ProductFields.CATEGORY to category,
    ProductFields.EXPIRY_DATE to expiryDate,
    ProductFields.ADDED_DATE to addedDate,
    ProductFields.QUANTITY to quantity.toLong(),
    ProductFields.ORIGINAL_QUANTITY to originalQuantity.toLong(),
    ProductFields.NOTES to notes,
    ProductFields.NOTIFICATION_ENABLED to notificationEnabled,
    ProductFields.IS_CONSUMED to isConsumed,
    ProductFields.IS_DISCARDED to isDiscarded,
    ProductFields.RESOLVED_DATE to resolvedDate,
    ProductFields.USER_ID to userId,
    ProductFields.UPDATED_AT to updatedAt,
    ProductFields.IS_DELETED to isDeleted,
    ProductFields.DELETED_AT to deletedAt
)

/**
 * @param existingImageUri the value already held locally, preserved because the
 *        remote document intentionally carries no image path.
 */
fun productFromFirestore(
    id: String,
    pantryId: String,
    data: Map<String, Any?>,
    existingImageUri: String? = null
): ProductEntity? {
    // A document missing these is malformed — skip it rather than materialise a
    // half-built row that would then be pushed back out.
    val name = data[ProductFields.NAME] as? String ?: return null
    val category = data[ProductFields.CATEGORY] as? String ?: return null
    val expiryDate = (data[ProductFields.EXPIRY_DATE] as? Number)?.toLong() ?: return null
    val updatedAt = (data[ProductFields.UPDATED_AT] as? Number)?.toLong() ?: return null

    return ProductEntity(
        id = id,
        pantryId = pantryId,
        userId = data[ProductFields.USER_ID] as? String ?: "",
        name = name,
        barcode = data[ProductFields.BARCODE] as? String,
        category = category,
        expiryDate = expiryDate,
        addedDate = (data[ProductFields.ADDED_DATE] as? Number)?.toLong() ?: updatedAt,
        quantity = (data[ProductFields.QUANTITY] as? Number)?.toInt() ?: 1,
        originalQuantity = (data[ProductFields.ORIGINAL_QUANTITY] as? Number)?.toInt() ?: 1,
        notes = data[ProductFields.NOTES] as? String,
        imageUri = existingImageUri,
        notificationEnabled = data[ProductFields.NOTIFICATION_ENABLED] as? Boolean ?: true,
        isConsumed = data[ProductFields.IS_CONSUMED] as? Boolean ?: false,
        isDiscarded = data[ProductFields.IS_DISCARDED] as? Boolean ?: false,
        resolvedDate = (data[ProductFields.RESOLVED_DATE] as? Number)?.toLong(),
        updatedAt = updatedAt,
        isDeleted = data[ProductFields.IS_DELETED] as? Boolean ?: false,
        deletedAt = (data[ProductFields.DELETED_AT] as? Number)?.toLong()
    )
}
