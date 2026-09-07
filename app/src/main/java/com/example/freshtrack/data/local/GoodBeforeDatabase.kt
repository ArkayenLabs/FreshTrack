package com.example.freshtrack.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.freshtrack.data.local.dao.CategoryDao
import com.example.freshtrack.data.local.dao.ItemDao
import com.example.freshtrack.data.local.dao.ItemEventDao
import com.example.freshtrack.data.local.dao.LocationDao
import com.example.freshtrack.data.local.dao.OutboxDao
import com.example.freshtrack.data.local.entities.CategoryEntity
import com.example.freshtrack.data.local.entities.DefaultCategories
import com.example.freshtrack.data.local.entities.DefaultLocations
import com.example.freshtrack.data.local.entities.ItemEntity
import com.example.freshtrack.data.local.entities.ItemEventEntity
import com.example.freshtrack.data.local.entities.LocationEntity
import com.example.freshtrack.data.local.entities.OutboxEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * The GoodBefore local store, and the source of truth for everything the app
 * shows. The network is a mirror of this, never a prerequisite for a read.
 *
 * This is a new database rather than a migration of `freshtrack_database`. The
 * shapes are not reconcilable by adding columns — expiry moves from an epoch
 * instant to a calendar date with provenance, the two resolution booleans
 * collapse into one state, storage location becomes a first-class entity, and
 * history moves from being inferred off row state to an append-only ledger. A
 * migration would have had to invent provenance for dates whose origin was
 * never recorded.
 *
 * Starting at version 1 with no migration chain is only safe because there is
 * no installed base to carry forward. That is a fact about today, not a licence
 * to keep doing it: from here on every schema change needs a real migration,
 * and `fallbackToDestructiveMigration` must never appear in this builder.
 */
@Database(
    entities = [
        ItemEntity::class,
        CategoryEntity::class,
        LocationEntity::class,
        ItemEventEntity::class,
        OutboxEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class GoodBeforeDatabase : RoomDatabase() {

    abstract fun itemDao(): ItemDao
    abstract fun categoryDao(): CategoryDao
    abstract fun locationDao(): LocationDao
    abstract fun itemEventDao(): ItemEventDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        const val DATABASE_NAME = "goodbefore_database"

        @Volatile
        private var INSTANCE: GoodBeforeDatabase? = null

        fun getInstance(context: Context): GoodBeforeDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    GoodBeforeDatabase::class.java,
                    DATABASE_NAME
                )
                    .addCallback(SeedCallback())
                    .build()
                    .also { INSTANCE = it }
            }
        }

        /**
         * Seeds the categories and storage locations a new kitchen starts with.
         *
         * Done on first creation rather than lazily on first read, so the add
         * screen always has something to offer and never shows an empty picker.
         */
        private class SeedCallback : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                val instance = INSTANCE ?: return
                CoroutineScope(Dispatchers.IO).launch {
                    instance.categoryDao().insertCategories(DefaultCategories.getAll())
                    instance.locationDao().insertAll(DefaultLocations.getAll())
                }
            }
        }
    }
}
