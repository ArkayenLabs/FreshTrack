package com.example.freshtrack.data.sync

import com.example.freshtrack.data.local.entities.ItemEntity
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializer
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializer
import java.time.LocalDate

/**
 * Serialises the state of an item at the moment it changed, for the outbox.
 *
 * A snapshot rather than a pointer to the current row on purpose. Pushing
 * whatever the row happens to say at send time would collapse several offline
 * edits into whichever one happened last, and lose the intermediate states that
 * another device needs in order to resolve a conflict sensibly.
 *
 * This is a local queue format, not the wire format. The mapping to the shared
 * cross-platform DTO happens at push time, so changing what the backend accepts
 * does not require rewriting queued entries.
 */
object OutboxPayload {

    private val gson = GsonBuilder()
        // Without this a LocalDate is reflected into {"year":..,"month":..},
        // which is both fragile and unreadable in a diagnostic dump.
        .registerTypeAdapter(
            LocalDate::class.java,
            JsonSerializer<LocalDate> { src, _, _ -> JsonPrimitive(src.toString()) }
        )
        .registerTypeAdapter(
            LocalDate::class.java,
            JsonDeserializer { json, _, _ -> LocalDate.parse(json.asString) }
        )
        .create()

    fun serialise(item: ItemEntity): String = gson.toJson(item)

    /** The snapshot back, for the push engine. Exactly what [serialise] wrote. */
    fun deserialise(json: String): ItemEntity = gson.fromJson(json, ItemEntity::class.java)
}
