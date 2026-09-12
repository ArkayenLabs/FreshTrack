package com.example.freshtrack.data.sync

/**
 * Where sync has got to, per kitchen, on this device.
 *
 * An interface so the engine can be tested on the JVM with a map. The real
 * one is `SyncPreferences`, which persists it.
 *
 * Bootstrap progress is a count rather than a flag because a first backup of
 * a long-standing kitchen is several batches, and a device that dies between
 * two of them must carry on from where it was — it cannot re-send events the
 * server already has, since the rules refuse an event write twice.
 */
interface SyncState {
    fun isBootstrapped(kitchenId: String): Boolean
    fun markBootstrapped(kitchenId: String)

    /** How many ledger events, in `ItemEventDao.getAllForKitchen` order, are on the server. */
    fun bootstrapEventsUploaded(kitchenId: String): Int
    fun setBootstrapEventsUploaded(kitchenId: String, count: Int)

    /**
     * The highest server timestamp applied so far, in the unit
     * `RemoteDocument.serverUpdatedAt` uses. Items and events are paged
     * separately, so each has its own; one shared cursor could skip whatever
     * the shorter page had not reached.
     */
    fun itemsCursor(kitchenId: String): Long
    fun setItemsCursor(kitchenId: String, cursor: Long)
    fun eventsCursor(kitchenId: String): Long
    fun setEventsCursor(kitchenId: String, cursor: Long)
}
