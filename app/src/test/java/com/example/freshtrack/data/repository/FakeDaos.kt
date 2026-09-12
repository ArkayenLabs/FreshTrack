package com.example.freshtrack.data.repository

import com.example.freshtrack.data.local.dao.ItemDao
import com.example.freshtrack.data.local.dao.ItemEventDao
import com.example.freshtrack.data.local.dao.OutboxDao
import com.example.freshtrack.data.local.entities.ItemEntity
import com.example.freshtrack.data.local.entities.ItemEventEntity
import com.example.freshtrack.data.local.entities.ItemEventType
import com.example.freshtrack.data.local.entities.ItemState
import com.example.freshtrack.data.local.entities.OutboxEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * In-memory stand-ins for the DAOs.
 *
 * Hand-written rather than mocked because these tests are about what ends up
 * stored after a sequence of calls, and a mock would only prove which methods
 * were invoked. The fakes enforce the two constraints that actually matter:
 * name matching is case-insensitive, mirroring COLLATE NOCASE, and appending an
 * event with a duplicate operation id is ignored, mirroring the unique index.
 */
class FakeItemDao : ItemDao {

    val rows = linkedMapOf<String, ItemEntity>()
    private val changes = MutableStateFlow(0)

    private fun touch() { changes.value += 1 }

    private fun visible(kitchenId: String) =
        rows.values.filter { it.kitchenId == kitchenId && !it.isDeleted }

    private fun observe(selector: () -> List<ItemEntity>): Flow<List<ItemEntity>> =
        changes.map { selector() }

    override fun getActiveItems(kitchenId: String): Flow<List<ItemEntity>> = observe {
        visible(kitchenId).filter { it.state == ItemState.ACTIVE }.sortedBy { it.expiryDate }
    }

    override fun getActiveItemsByCategory(
        kitchenId: String,
        category: String
    ): Flow<List<ItemEntity>> = observe {
        visible(kitchenId).filter {
            it.state == ItemState.ACTIVE && it.category.equals(category, ignoreCase = true)
        }.sortedBy { it.expiryDate }
    }

    override fun getActiveItemsByLocation(
        kitchenId: String,
        locationId: String
    ): Flow<List<ItemEntity>> = observe {
        visible(kitchenId).filter {
            it.state == ItemState.ACTIVE && it.locationId == locationId
        }.sortedBy { it.expiryDate }
    }

    override fun getItemById(kitchenId: String, itemId: String): Flow<ItemEntity?> =
        changes.map { visible(kitchenId).firstOrNull { it.id == itemId } }

    override suspend fun getItemByIdOnce(kitchenId: String, itemId: String): ItemEntity? =
        visible(kitchenId).firstOrNull { it.id == itemId }

    override suspend fun getItemByBarcode(kitchenId: String, barcode: String): ItemEntity? =
        visible(kitchenId).firstOrNull {
            it.state == ItemState.ACTIVE && it.barcode == barcode
        }

    override suspend fun getItemsDueThrough(
        kitchenId: String,
        through: LocalDate,
        now: Long
    ): List<ItemEntity> = visible(kitchenId)
        .filter {
            it.state == ItemState.ACTIVE &&
                !it.expiryDate.isAfter(through) &&
                it.notificationEnabled &&
                (it.snoozedUntil == null || it.snoozedUntil <= now)
        }
        .sortedBy { it.expiryDate }

    override fun getExpiredItems(kitchenId: String, today: LocalDate): Flow<List<ItemEntity>> =
        observe {
            visible(kitchenId).filter {
                it.state == ItemState.ACTIVE && it.expiryDate.isBefore(today)
            }
        }

    override fun getItemsInState(kitchenId: String, state: ItemState): Flow<List<ItemEntity>> =
        observe { visible(kitchenId).filter { it.state == state } }

    override fun getActiveItemCount(kitchenId: String): Flow<Int> =
        changes.map { visible(kitchenId).count { it.state == ItemState.ACTIVE } }

    override suspend fun findActiveDuplicate(
        kitchenId: String,
        name: String,
        expiryDate: LocalDate
    ): ItemEntity? = visible(kitchenId).firstOrNull {
        it.state == ItemState.ACTIVE &&
            it.name.equals(name, ignoreCase = true) &&
            it.expiryDate == expiryDate
    }

    override suspend fun insert(item: ItemEntity) {
        rows[item.id] = item
        touch()
    }

    override suspend fun insertAll(items: List<ItemEntity>) = items.forEach { insert(it) }

    override suspend fun update(item: ItemEntity) {
        rows[item.id] = item
        touch()
    }

    override suspend fun softDelete(
        kitchenId: String,
        itemId: String,
        actorUid: String,
        at: Long
    ) {
        rows[itemId]?.let {
            rows[itemId] = it.copy(
                isDeleted = true, deletedAt = at, updatedAt = at, lastEditedBy = actorUid
            )
        }
        touch()
    }

    override suspend fun setState(
        kitchenId: String,
        itemId: String,
        state: ItemState,
        actorUid: String,
        at: Long
    ) {
        rows[itemId]?.let {
            rows[itemId] = it.copy(
                state = state, resolvedAt = at, updatedAt = at, lastEditedBy = actorUid
            )
        }
        touch()
    }

    override suspend fun setQuantity(
        kitchenId: String,
        itemId: String,
        quantity: Int,
        actorUid: String,
        at: Long
    ) {
        rows[itemId]?.let {
            rows[itemId] = it.copy(
                quantity = quantity, updatedAt = at, lastEditedBy = actorUid
            )
        }
        touch()
    }

    override suspend fun setSnoozedUntil(
        kitchenId: String,
        itemId: String,
        until: Long?,
        actorUid: String,
        at: Long
    ) {
        rows[itemId]?.let {
            rows[itemId] = it.copy(
                snoozedUntil = until, updatedAt = at, lastEditedBy = actorUid
            )
        }
        touch()
    }

    override suspend fun softDeleteResolved(kitchenId: String, at: Long) {
        rows.values.filter { it.kitchenId == kitchenId && it.state != ItemState.ACTIVE }
            .forEach { rows[it.id] = it.copy(isDeleted = true, deletedAt = at, updatedAt = at) }
        touch()
    }

    override suspend fun claimLocalItems(
        localKitchenId: String,
        kitchenId: String,
        uid: String,
        at: Long
    ) {
        rows.values.filter { it.kitchenId == localKitchenId }.forEach {
            rows[it.id] = it.copy(
                kitchenId = kitchenId, createdBy = uid, lastEditedBy = uid, updatedAt = at
            )
        }
        touch()
    }

    override suspend fun countLocalItems(localKitchenId: String): Int =
        rows.values.count { it.kitchenId == localKitchenId && !it.isDeleted }

    override suspend fun deleteAllForKitchen(kitchenId: String) {
        rows.values.filter { it.kitchenId == kitchenId }.forEach { rows.remove(it.id) }
        touch()
    }

    override suspend fun getByIdIncludingDeleted(itemId: String): ItemEntity? = rows[itemId]

    override suspend fun getByIdsIncludingDeleted(itemIds: List<String>): List<ItemEntity> =
        itemIds.mapNotNull { rows[it] }

    override suspend fun upsertFromRemote(items: List<ItemEntity>) = items.forEach { insert(it) }
}

class FakeItemEventDao : ItemEventDao {

    val events = mutableListOf<ItemEventEntity>()
    private val changes = MutableStateFlow(0)

    /** Mirrors the unique index on operationId: a repeat is ignored, not applied. */
    override suspend fun append(event: ItemEventEntity): Long {
        if (events.any { it.operationId == event.operationId }) return -1L
        events += event
        changes.value += 1
        return events.size.toLong()
    }

    override suspend fun appendAll(events: List<ItemEventEntity>): List<Long> =
        events.map { append(it) }

    override fun getEventsForItem(itemId: String): Flow<List<ItemEventEntity>> =
        changes.map { events.filter { e -> e.itemId == itemId }.sortedByDescending { it.occurredAt } }

    override fun getRecentEvents(kitchenId: String, limit: Int): Flow<List<ItemEventEntity>> =
        changes.map {
            events.filter { it.kitchenId == kitchenId }
                .sortedByDescending { it.occurredAt }
                .take(limit)
        }

    override suspend fun findByOperationId(operationId: String): ItemEventEntity? =
        events.firstOrNull { it.operationId == operationId }

    /** Nets reversals out, mirroring the subtraction the SQL does. */
    override fun sumQuantityForType(kitchenId: String, type: ItemEventType): Flow<Int> =
        changes.map {
            val resolved = events
                .filter { it.kitchenId == kitchenId && it.type == type }
                .sumOf { it.quantity ?: 0 }
            val reversed = events
                .filter {
                    it.kitchenId == kitchenId &&
                        it.type == ItemEventType.ITEM_RESTORED &&
                        it.reversesEventType == type
                }
                .sumOf { it.quantity ?: 0 }
            resolved - reversed
        }

    override fun getLastDiscardAt(kitchenId: String): Flow<Long?> = changes.map {
        events.filter {
            it.kitchenId == kitchenId &&
                it.type == ItemEventType.QUANTITY_DISCARDED &&
                !isReversed(it.id)
        }.maxOfOrNull { it.occurredAt }
    }

    /** Insertion order is the tiebreak, mirroring the query's ORDER BY rowid. */
    override suspend fun findLatestUnreversedResolution(itemId: String): ItemEventEntity? =
        events.withIndex()
            .filter { (_, e) ->
                e.itemId == itemId &&
                    (e.type == ItemEventType.QUANTITY_USED ||
                        e.type == ItemEventType.QUANTITY_DISCARDED) &&
                    !isReversed(e.id)
            }
            .maxWithOrNull(compareBy({ it.value.occurredAt }, { it.index }))
            ?.value

    private fun isReversed(eventId: String) = events.any { it.reversesEventId == eventId }

    override fun getFirstEventAt(kitchenId: String): Flow<Long?> = changes.map {
        events.filter { it.kitchenId == kitchenId }.minOfOrNull { it.occurredAt }
    }

    override fun sumVerifiedValueForType(kitchenId: String, type: ItemEventType): Flow<Long> =
        changes.map { 0L }

    override suspend fun claimLocalEvents(localKitchenId: String, kitchenId: String, uid: String) {
        events.replaceAll {
            if (it.kitchenId == localKitchenId) it.copy(kitchenId = kitchenId, actorUid = uid) else it
        }
        changes.value += 1
    }

    override suspend fun deleteAllForKitchen(kitchenId: String) {
        events.removeAll { it.kitchenId == kitchenId }
        changes.value += 1
    }
}

class FakeOutboxDao : OutboxDao {

    val operations = mutableListOf<OutboxEntity>()
    private val changes = MutableStateFlow(0)

    override suspend fun enqueue(operation: OutboxEntity) {
        if (operations.any { it.operationId == operation.operationId }) return
        operations += operation
        changes.value += 1
    }

    override suspend fun enqueueAll(operations: List<OutboxEntity>) =
        operations.forEach { enqueue(it) }

    override suspend fun peek(kitchenId: String, limit: Int): List<OutboxEntity> =
        operations.filter { it.kitchenId == kitchenId }
            .sortedBy { it.clientSequence }
            .take(limit)

    override fun getPendingCount(kitchenId: String): Flow<Int> =
        changes.map { operations.count { it.kitchenId == kitchenId } }

    override suspend fun getHighestSequence(): Long? =
        operations.maxOfOrNull { it.clientSequence }

    override suspend fun acknowledge(operationIds: List<String>) {
        operations.removeAll { it.operationId in operationIds }
        changes.value += 1
    }

    override suspend fun recordFailure(operationId: String, at: Long, error: String?) {
        val index = operations.indexOfFirst { it.operationId == operationId }
        if (index >= 0) {
            operations[index] = operations[index].copy(
                attemptCount = operations[index].attemptCount + 1,
                lastAttemptAt = at,
                lastError = error
            )
        }
    }

    override suspend fun getStuck(kitchenId: String, threshold: Int): List<OutboxEntity> =
        operations.filter { it.kitchenId == kitchenId && it.attemptCount >= threshold }

    override suspend fun claimLocalOperations(localKitchenId: String, kitchenId: String, uid: String) {
        operations.replaceAll {
            if (it.kitchenId == localKitchenId) it.copy(kitchenId = kitchenId, actorUid = uid) else it
        }
        changes.value += 1
    }

    override suspend fun deleteAllForKitchen(kitchenId: String) {
        operations.removeAll { it.kitchenId == kitchenId }
        changes.value += 1
    }

    override suspend fun clear() {
        operations.clear()
        changes.value += 1
    }
}
