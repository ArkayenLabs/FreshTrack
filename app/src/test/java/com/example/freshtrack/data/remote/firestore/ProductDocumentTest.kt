package com.example.freshtrack.data.remote.firestore

import com.example.freshtrack.data.local.entities.ProductEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Round-tripping products through their Firestore representation.
 *
 * Firestore hands numbers back as Long regardless of what was written, so the
 * Int fields are the ones most likely to break quietly.
 */
class ProductDocumentTest {

    private val entity = ProductEntity(
        id = "milk-1",
        pantryId = "personal-alice",
        userId = "alice",
        name = "Milk",
        barcode = "12345",
        category = "Dairy",
        expiryDate = 1_700_000_000_000,
        addedDate = 1_600_000_000_000,
        quantity = 3,
        originalQuantity = 5,
        notes = "semi-skimmed",
        imageUri = "file:///local/photo.jpg",
        notificationEnabled = false,
        isConsumed = true,
        isDiscarded = false,
        resolvedDate = 1_650_000_000_000,
        updatedAt = 1_690_000_000_000,
        isDeleted = false,
        deletedAt = null
    )

    @Test
    fun `round trip preserves every synced field`() {
        val restored = productFromFirestore(
            id = entity.id,
            pantryId = entity.pantryId,
            data = entity.toFirestoreMap()
        )!!

        // imageUri is intentionally not synced, so compare everything else.
        assertEquals(entity.copy(imageUri = null), restored)
    }

    @Test
    fun `pantryId comes from the path, not the document`() {
        val map = entity.toFirestoreMap()
        assertNull("pantryId must not be duplicated into the document", map["pantryId"])

        val restored = productFromFirestore("milk-1", "some-other-pantry", map)!!
        assertEquals("some-other-pantry", restored.pantryId)
    }

    @Test
    fun `imageUri is never sent and the local value is kept`() {
        val map = entity.toFirestoreMap()
        assertNull("imageUri must not be sent", map["imageUri"])

        val restored = productFromFirestore(
            id = entity.id,
            pantryId = entity.pantryId,
            data = map,
            existingImageUri = "file:///this/device.jpg"
        )!!
        assertEquals("file:///this/device.jpg", restored.imageUri)
    }

    @Test
    fun `integer fields survive Firestore returning them as Long`() {
        // Firestore always reads numbers back as Long; quantity is an Int.
        val fromServer = entity.toFirestoreMap().toMutableMap().apply {
            this[ProductFields.QUANTITY] = 7L
            this[ProductFields.ORIGINAL_QUANTITY] = 9L
        }

        val restored = productFromFirestore("milk-1", "personal-alice", fromServer)!!

        assertEquals(7, restored.quantity)
        assertEquals(9, restored.originalQuantity)
    }

    @Test
    fun `a tombstone round trips`() {
        val deleted = entity.copy(isDeleted = true, deletedAt = 1_695_000_000_000)
        val restored = productFromFirestore(deleted.id, deleted.pantryId, deleted.toFirestoreMap())!!

        assertEquals(true, restored.isDeleted)
        assertEquals(1_695_000_000_000, restored.deletedAt)
    }

    @Test
    fun `a malformed document is skipped rather than half built`() {
        val missingName = entity.toFirestoreMap().toMutableMap().apply { remove(ProductFields.NAME) }
        assertNull(productFromFirestore("x", "p", missingName))

        val missingUpdatedAt = entity.toFirestoreMap().toMutableMap()
            .apply { remove(ProductFields.UPDATED_AT) }
        assertNull(productFromFirestore("x", "p", missingUpdatedAt))
    }
}
