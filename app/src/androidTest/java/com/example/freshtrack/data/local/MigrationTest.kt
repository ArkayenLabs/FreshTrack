package com.example.freshtrack.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Migration tests for the live database.
 *
 * FreshTrack is published, so a broken migration corrupts real users' data. These
 * run against real SQLite files rather than an in-memory schema.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FreshTrackDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    /**
     * 4 → 5 adds resolvedDate and backfills already-resolved rows to addedDate.
     * An active row must keep a null resolvedDate.
     */
    @Test
    fun migrate4To5_backfillsResolvedDateForResolvedRowsOnly() {
        helper.createDatabase(dbName, 4).apply {
            execSQL(
                """
                INSERT INTO products
                (id, name, barcode, category, expiryDate, addedDate, quantity,
                 originalQuantity, notes, imageUri, notificationEnabled, isConsumed, isDiscarded)
                VALUES ('used', 'Milk', NULL, 'Dairy', 200, 100, 1, 1, NULL, NULL, 1, 1, 0)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO products
                (id, name, barcode, category, expiryDate, addedDate, quantity,
                 originalQuantity, notes, imageUri, notificationEnabled, isConsumed, isDiscarded)
                VALUES ('active', 'Bread', NULL, 'Bakery', 200, 150, 1, 1, NULL, NULL, 1, 0, 0)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 5, true, *FreshTrackDatabase.ALL_MIGRATIONS)

        db.query("SELECT resolvedDate FROM products WHERE id = 'used'").use {
            assertTrue(it.moveToFirst())
            assertEquals(100L, it.getLong(0))
        }
        db.query("SELECT resolvedDate FROM products WHERE id = 'active'").use {
            assertTrue(it.moveToFirst())
            assertTrue("active row should have no resolvedDate", it.isNull(0))
        }
    }

    /**
     * 5 → 6 adds the sync columns. Every pre-existing row must land on the guest
     * owner, because that is what the startup claim step looks for. If this
     * backfill is wrong, users open the app to an empty inventory.
     */
    @Test
    fun migrate5To6_backfillsGuestOwnerAndUpdatedAt() {
        helper.createDatabase(dbName, 5).apply {
            execSQL(
                """
                INSERT INTO products
                (id, name, barcode, category, expiryDate, addedDate, quantity,
                 originalQuantity, notes, imageUri, notificationEnabled, isConsumed,
                 isDiscarded, resolvedDate)
                VALUES ('resolved', 'Milk', NULL, 'Dairy', 200, 100, 1, 1, NULL, NULL, 1, 1, 0, 180)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO products
                (id, name, barcode, category, expiryDate, addedDate, quantity,
                 originalQuantity, notes, imageUri, notificationEnabled, isConsumed,
                 isDiscarded, resolvedDate)
                VALUES ('active', 'Bread', NULL, 'Bakery', 200, 150, 1, 1, NULL, NULL, 1, 0, 0, NULL)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 6, true, *FreshTrackDatabase.ALL_MIGRATIONS)

        db.query("SELECT userId, isDeleted, deletedAt FROM products").use {
            assertTrue(it.moveToFirst())
            do {
                assertEquals("guest", it.getString(0))
                assertEquals(0, it.getInt(1))
                assertTrue("deletedAt should start null", it.isNull(2))
            } while (it.moveToNext())
        }

        // updatedAt seeds from resolvedDate when present, else addedDate.
        db.query("SELECT updatedAt FROM products WHERE id = 'resolved'").use {
            assertTrue(it.moveToFirst())
            assertEquals(180L, it.getLong(0))
        }
        db.query("SELECT updatedAt FROM products WHERE id = 'active'").use {
            assertTrue(it.moveToFirst())
            assertEquals(150L, it.getLong(0))
        }
    }

    /**
     * 6 → 7 adds pantryId. Guest rows go to the local pantry; rows already
     * claimed by an account go to that account's personal pantry. Getting this
     * wrong hides a signed-in user's entire inventory, because pantryId is what
     * every user-facing query filters on.
     */
    @Test
    fun migrate6To7_backfillsPantryFromExistingOwner() {
        helper.createDatabase(dbName, 6).apply {
            execSQL(
                """
                INSERT INTO products
                (id, name, barcode, category, expiryDate, addedDate, quantity,
                 originalQuantity, notes, imageUri, notificationEnabled, isConsumed,
                 isDiscarded, resolvedDate, userId, updatedAt, isDeleted, deletedAt)
                VALUES ('guest-row', 'Milk', NULL, 'Dairy', 200, 100, 1, 1, NULL, NULL,
                        1, 0, 0, NULL, 'guest', 100, 0, NULL)
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO products
                (id, name, barcode, category, expiryDate, addedDate, quantity,
                 originalQuantity, notes, imageUri, notificationEnabled, isConsumed,
                 isDiscarded, resolvedDate, userId, updatedAt, isDeleted, deletedAt)
                VALUES ('owned-row', 'Eggs', NULL, 'Dairy', 200, 100, 1, 1, NULL, NULL,
                        1, 0, 0, NULL, 'abc123', 100, 0, NULL)
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 7, true, *FreshTrackDatabase.ALL_MIGRATIONS)

        db.query("SELECT pantryId FROM products WHERE id = 'guest-row'").use {
            assertTrue(it.moveToFirst())
            assertEquals("local", it.getString(0))
        }
        db.query("SELECT pantryId FROM products WHERE id = 'owned-row'").use {
            assertTrue(it.moveToFirst())
            assertEquals("personal-abc123", it.getString(0))
        }
    }

    /** The whole chain, as an upgrading user from the earliest shipped version. */
    @Test
    fun migrateAll_1To7() {
        helper.createDatabase(dbName, 1).close()
        helper.runMigrationsAndValidate(dbName, 7, true, *FreshTrackDatabase.ALL_MIGRATIONS)
    }
}
