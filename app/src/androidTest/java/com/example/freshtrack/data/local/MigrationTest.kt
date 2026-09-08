package com.example.freshtrack.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs each migration against a database actually built at the previous
 * version, on a device.
 *
 * The point is the data, not the DDL. A migration that adds a column always
 * "works"; what has to be proved is that the rows already there survive it and
 * still mean the same thing afterwards.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    private companion object {
        const val TEST_DB = "migration-test"
    }

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GoodBeforeDatabase::class.java
    )

    @Test
    fun migrate1To2_addsReversalColumnsAndKeepsExistingEvents() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO items (
                    id, kitchenId, name, category, locationId, barcode,
                    quantity, originalQuantity, expiryDate, dateKind, dateSource,
                    dateConfidence, dateConfirmedByUserAt, priceMinorUnits,
                    priceCurrency, priceSource, notes, imageUri, state, addedAt,
                    resolvedAt, notificationEnabled, snoozedUntil, createdBy,
                    lastEditedBy, updatedAt, revision, schemaVersion, isDeleted, deletedAt
                ) VALUES (
                    'item-1', 'local', 'Milk', 'Dairy', NULL, NULL,
                    3, 3, '2026-09-11', 'USE_BY', 'USER',
                    1.0, 100, NULL,
                    NULL, NULL, NULL, NULL, 'ACTIVE', 100,
                    NULL, 1, NULL, 'guest',
                    'guest', 100, 0, 1, 0, NULL
                )
                """.trimIndent()
            )
            execSQL(
                """
                INSERT INTO item_events (
                    id, itemId, kitchenId, type, actorUid, quantity,
                    occurredAt, operationId, metadata, schemaVersion
                ) VALUES (
                    'evt-1', 'item-1', 'local', 'QUANTITY_USED', 'guest', 2,
                    200, 'op-1', NULL, 1
                )
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 2, true, GoodBeforeDatabase.MIGRATION_1_2
        )

        db.query("SELECT quantity, reversesEventId, reversesEventType FROM item_events").use {
            assertTrue("the existing event should survive the migration", it.moveToFirst())
            assertEquals(2, it.getInt(0))
            // An event written before undo existed reverses nothing, which is
            // exactly what null means here.
            assertTrue(it.isNull(1))
            assertTrue(it.isNull(2))
        }

        db.query("SELECT name, expiryDate, quantity FROM items").use {
            assertTrue(it.moveToFirst())
            assertEquals("Milk", it.getString(0))
            // The calendar date must come through as written. A migration that
            // reinterpreted it would shift every expiry in the database.
            assertEquals("2026-09-11", it.getString(1))
            assertEquals(3, it.getInt(2))
        }
    }

    @Test
    fun migrate1To2_leavesTheLedgerQueryableForUndo() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO item_events (
                    id, itemId, kitchenId, type, actorUid, quantity,
                    occurredAt, operationId, metadata, schemaVersion
                ) VALUES (
                    'evt-1', 'item-1', 'local', 'QUANTITY_DISCARDED', 'guest', 1,
                    200, 'op-1', NULL, 1
                )
                """.trimIndent()
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 2, true, GoodBeforeDatabase.MIGRATION_1_2
        )

        // The query undo relies on must work against pre-migration rows, not
        // just newly written ones.
        db.query(
            """
            SELECT e.id FROM item_events e
            WHERE e.type IN ('QUANTITY_USED', 'QUANTITY_DISCARDED')
            AND NOT EXISTS (SELECT 1 FROM item_events r WHERE r.reversesEventId = e.id)
            ORDER BY e.occurredAt DESC, e.rowid DESC
            LIMIT 1
            """.trimIndent()
        ).use {
            assertTrue(it.moveToFirst())
            assertEquals("evt-1", it.getString(0))
        }
    }

    @Test
    fun migrate1To2_indexExistsSoImpactReadsDoNotScanTheLedger() {
        helper.createDatabase(TEST_DB, 1).close()

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 2, true, GoodBeforeDatabase.MIGRATION_1_2
        )

        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' " +
                "AND name='index_item_events_reversesEventId'"
        ).use {
            assertTrue("the reversal index should exist after migrating", it.moveToFirst())
            assertNull(null)
        }
    }
}
