package com.example.freshtrack.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.freshtrack.data.local.dao.CategoryDao
import com.example.freshtrack.data.local.dao.ProductDao
import com.example.freshtrack.data.local.entities.CategoryEntity
import com.example.freshtrack.data.local.entities.DefaultCategories
import com.example.freshtrack.data.local.entities.ProductEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Main Room Database for FreshTrack
 * Manages local data persistence with automatic migration support
 */
@Database(
    entities = [ProductEntity::class, CategoryEntity::class],
    version = 6,
    exportSchema = true
)
abstract class FreshTrackDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: FreshTrackDatabase? = null

        private const val DATABASE_NAME = "freshtrack_database"

        /**
         * Migration from version 1 to 2: adds originalQuantity column
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN originalQuantity INTEGER NOT NULL DEFAULT 1")
                // Set originalQuantity to current quantity for existing products
                db.execSQL("UPDATE products SET originalQuantity = quantity")
            }
        }

        /**
         * Migration from version 2 to 3: adds COLLATE NOCASE to name/category
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Category table migration
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `categories_new` (
                        `name` TEXT NOT NULL COLLATE NOCASE, 
                        `colorHex` TEXT NOT NULL, 
                        `icon` TEXT NOT NULL, 
                        `sortOrder` INTEGER NOT NULL, 
                        PRIMARY KEY(`name`)
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO `categories_new` (`name`, `colorHex`, `icon`, `sortOrder`) SELECT `name`, `colorHex`, `icon`, `sortOrder` FROM `categories`")
                db.execSQL("DROP TABLE `categories`")
                db.execSQL("ALTER TABLE `categories_new` RENAME TO `categories`")

                // Product table migration
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `products_new` (
                        `id` TEXT NOT NULL, 
                        `name` TEXT NOT NULL COLLATE NOCASE, 
                        `barcode` TEXT, 
                        `category` TEXT NOT NULL COLLATE NOCASE, 
                        `expiryDate` INTEGER NOT NULL, 
                        `addedDate` INTEGER NOT NULL, 
                        `quantity` INTEGER NOT NULL, 
                        `originalQuantity` INTEGER NOT NULL, 
                        `notes` TEXT, 
                        `imageUri` TEXT, 
                        `notificationEnabled` INTEGER NOT NULL, 
                        `isConsumed` INTEGER NOT NULL, 
                        `isDiscarded` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("INSERT INTO `products_new` (`id`, `name`, `barcode`, `category`, `expiryDate`, `addedDate`, `quantity`, `originalQuantity`, `notes`, `imageUri`, `notificationEnabled`, `isConsumed`, `isDiscarded`) SELECT `id`, `name`, `barcode`, `category`, `expiryDate`, `addedDate`, `quantity`, `originalQuantity`, `notes`, `imageUri`, `notificationEnabled`, `isConsumed`, `isDiscarded` FROM `products`")
                db.execSQL("DROP TABLE `products`")
                db.execSQL("ALTER TABLE `products_new` RENAME TO `products`")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Remap any products from removed categories to 'Other'
                db.execSQL("UPDATE products SET category = 'Other' WHERE category IN ('Food', 'Medicine', 'Cosmetics')")

                // Remove all old categories and insert food-only set
                db.execSQL("DELETE FROM categories")
                db.execSQL("INSERT INTO categories (name, colorHex, icon, sortOrder) VALUES ('Fresh Produce', '#4CAF50', 'eco', 0)")
                db.execSQL("INSERT INTO categories (name, colorHex, icon, sortOrder) VALUES ('Dairy', '#2196F3', 'water_drop', 1)")
                db.execSQL("INSERT INTO categories (name, colorHex, icon, sortOrder) VALUES ('Bakery', '#FF9800', 'bakery_dining', 2)")
                db.execSQL("INSERT INTO categories (name, colorHex, icon, sortOrder) VALUES ('Beverages', '#00BCD4', 'local_drink', 3)")
                db.execSQL("INSERT INTO categories (name, colorHex, icon, sortOrder) VALUES ('Pantry', '#795548', 'kitchen', 4)")
                db.execSQL("INSERT INTO categories (name, colorHex, icon, sortOrder) VALUES ('Leftovers', '#FF5722', 'takeout_dining', 5)")
                db.execSQL("INSERT INTO categories (name, colorHex, icon, sortOrder) VALUES ('Other', '#9E9E9E', 'category', 6)")
            }
        }

        /**
         * Migration from version 4 to 5: adds resolvedDate, the timestamp of when
         * an item was marked used or discarded. Powers the Impact Dashboard.
         *
         * Rows already resolved before this column existed have no real resolution
         * timestamp available, so they are backfilled to addedDate. That keeps the
         * money-saved and waste totals exact (they are plain counts) and only makes
         * the waste-free day count conservative for pre-migration history.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN resolvedDate INTEGER DEFAULT NULL")
                db.execSQL("UPDATE products SET resolvedDate = addedDate WHERE isConsumed = 1 OR isDiscarded = 1")
            }
        }

        /**
         * Migration from version 5 to 6: adds the columns sync needs.
         *
         * - userId: every existing row predates accounts, so it is backfilled to
         *   'guest'. The claim step on sign-in adopts those rows for the account,
         *   which is the same path a guest-to-signup takes.
         * - updatedAt: seeded from resolvedDate or addedDate so existing rows have
         *   a sane last-write time rather than all colliding on migration time.
         * - isDeleted / deletedAt: tombstones, since a hard DELETE cannot
         *   propagate to another device.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN userId TEXT NOT NULL DEFAULT 'guest'")
                db.execSQL("ALTER TABLE products ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE products ADD COLUMN deletedAt INTEGER DEFAULT NULL")
                db.execSQL("UPDATE products SET updatedAt = COALESCE(resolvedDate, addedDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_userId ON products(userId)")
            }
        }

        fun getInstance(context: Context): FreshTrackDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FreshTrackDatabase::class.java,
                    DATABASE_NAME
                )
                    .addCallback(DatabaseCallback())
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .build()

                INSTANCE = instance
                instance
            }
        }

        /**
         * Callback to populate database with default categories on first creation
         */
        private class DatabaseCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)

                // Populate default categories when database is created
                INSTANCE?.let { database ->
                    CoroutineScope(Dispatchers.IO).launch {
                        populateDefaultCategories(database.categoryDao())
                    }
                }
            }
        }

        /**
         * Insert default categories into the database
         */
        private suspend fun populateDefaultCategories(categoryDao: CategoryDao) {
            val defaultCategories = DefaultCategories.getAll()
            categoryDao.insertCategories(defaultCategories)
        }
    }
}

/**
 * Extension function to provide database instance
 * Makes it easier to access database in dependency injection
 */
fun Context.getFreshTrackDatabase(): FreshTrackDatabase {
    return FreshTrackDatabase.getInstance(this)
}