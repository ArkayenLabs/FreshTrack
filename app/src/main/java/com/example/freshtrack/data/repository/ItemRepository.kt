package com.example.freshtrack.data.repository

import com.example.freshtrack.data.local.TransactionRunner
import com.example.freshtrack.data.local.dao.ItemDao
import com.example.freshtrack.data.local.dao.ItemEventDao
import com.example.freshtrack.data.local.dao.OutboxDao
import com.example.freshtrack.data.local.entities.ItemEntity
import com.example.freshtrack.data.local.entities.ItemEventEntity
import com.example.freshtrack.data.local.entities.ItemEventType
import com.example.freshtrack.data.local.entities.ItemState
import com.example.freshtrack.data.local.entities.LOCAL_KITCHEN_ID
import com.example.freshtrack.data.local.entities.OutboxEntity
import com.example.freshtrack.data.local.entities.OutboxOperationType
import com.example.freshtrack.data.session.KitchenSession
import com.example.freshtrack.domain.model.ExpiryDate
import com.example.freshtrack.domain.model.ImpactStats
import com.example.freshtrack.domain.model.ImportSummary
import com.example.freshtrack.domain.model.Item
import com.example.freshtrack.domain.model.calendarDaysBetween
import com.example.freshtrack.domain.model.toDomain
import com.example.freshtrack.domain.model.toEntity
import com.example.freshtrack.util.AppClock
import com.example.freshtrack.util.IdGenerator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * Everything the app does to food.
 *
 * Reads come straight from the local store and never wait on the network.
 * Writes go through [ItemRepositoryImpl.record], which is the single place that
 * updates a row, appends its event and queues it for sync — so no screen can
 * change something without it being recorded and no history can be lost by a
 * call site forgetting a step.
 */
interface ItemRepository {

    fun observeActiveItems(): Flow<List<Item>>
    fun observeItemsByCategory(category: String): Flow<List<Item>>
    fun observeItemsByLocation(locationId: String): Flow<List<Item>>
    fun observeItem(itemId: String): Flow<Item?>
    fun observeExpiredItems(): Flow<List<Item>>
    fun observeActiveCount(): Flow<Int>
    fun observeUsedItems(): Flow<List<Item>>
    fun observeDiscardedItems(): Flow<List<Item>>
    fun observeImpact(): Flow<ImpactStats>
    fun observePendingSyncCount(): Flow<Int>

    suspend fun getItem(itemId: String): Item?
    suspend fun getItemByBarcode(barcode: String): Item?

    /** Items due on or before [through], for reminders. Excludes snoozed ones. */
    suspend fun getItemsDueThrough(through: LocalDate): List<Item>

    suspend fun add(item: Item): String
    suspend fun update(item: Item)

    /** Uses [amount] units. Resolves the item when that is all of them. */
    suspend fun use(itemId: String, amount: Int = 1)

    /** Discards [amount] units. Resolves the item when that is all of them. */
    suspend fun discard(itemId: String, amount: Int = 1)

    /** Moves the item and revises its date, both confirmed by the user. */
    suspend fun freeze(itemId: String, newExpiry: LocalDate, freezerLocationId: String?)

    suspend fun move(itemId: String, locationId: String?)
    suspend fun snooze(itemId: String, until: Long)
    suspend fun delete(itemId: String)

    /** Accepts a recognised date as correct, pinning it against weaker sources. */
    suspend fun confirmDate(itemId: String)

    /**
     * Reverses the most recent use or discard of this item.
     *
     * Returns false when there is nothing left to undo. Reverses one step at a
     * time, so calling it twice undoes two separate resolutions rather than
     * crediting the same one back twice.
     */
    suspend fun undoLastResolution(itemId: String): Boolean

    suspend fun findDuplicate(name: String, expiry: LocalDate): Item?
    suspend fun import(items: List<Item>): ImportSummary

    /**
     * Writes a whole reviewed receipt, or none of it.
     *
     * Separate from [import] for two reasons, both of which would be bugs if
     * this reused it. [import] decides for itself what is a duplicate and skips
     * it, which would silently discard a row the person had just looked at and
     * explicitly chosen to keep as its own batch; here every duplicate has
     * already been resolved by a human, so nothing is re-judged. And [import]
     * commits row by row, so a receipt interrupted halfway through leaves half
     * a shop in the kitchen and no way to tell which half.
     *
     * [additions] maps an existing item id to the units being folded into it.
     */
    suspend fun commitReceipt(newItems: List<Item>, additions: Map<String, Int>)

    suspend fun clearHistory()

    /** Adopts items created before sign-in. Returns how many. No-op if signed out. */
    suspend fun claimLocalData(): Int
}

class ItemRepositoryImpl(
    private val itemDao: ItemDao,
    private val eventDao: ItemEventDao,
    private val outboxDao: OutboxDao,
    private val session: KitchenSession,
    private val transactions: TransactionRunner,
    private val clock: AppClock,
    private val ids: IdGenerator,
    private val clientId: String,
    private val serialise: (ItemEntity) -> String
) : ItemRepository {

    /** Resolved per call, so a sign-in or sign-out takes effect immediately. */
    private fun kitchen(): String = session.activeKitchenId()

    private fun uid(): String = session.currentUserId()

    // ─── Reads ───────────────────────────────────────────────────────────────

    override fun observeActiveItems(): Flow<List<Item>> =
        itemDao.getActiveItems(kitchen()).map { rows -> rows.map(ItemEntity::toDomain) }

    override fun observeItemsByCategory(category: String): Flow<List<Item>> =
        itemDao.getActiveItemsByCategory(kitchen(), category)
            .map { rows -> rows.map(ItemEntity::toDomain) }

    override fun observeItemsByLocation(locationId: String): Flow<List<Item>> =
        itemDao.getActiveItemsByLocation(kitchen(), locationId)
            .map { rows -> rows.map(ItemEntity::toDomain) }

    override fun observeItem(itemId: String): Flow<Item?> =
        itemDao.getItemById(kitchen(), itemId).map { it?.toDomain() }

    override fun observeExpiredItems(): Flow<List<Item>> =
        itemDao.getExpiredItems(kitchen(), clock.today())
            .map { rows -> rows.map(ItemEntity::toDomain) }

    override fun observeActiveCount(): Flow<Int> = itemDao.getActiveItemCount(kitchen())

    override fun observeUsedItems(): Flow<List<Item>> =
        itemDao.getItemsInState(kitchen(), ItemState.USED)
            .map { rows -> rows.map(ItemEntity::toDomain) }

    override fun observeDiscardedItems(): Flow<List<Item>> =
        itemDao.getItemsInState(kitchen(), ItemState.DISCARDED)
            .map { rows -> rows.map(ItemEntity::toDomain) }

    override fun observePendingSyncCount(): Flow<Int> = outboxDao.getPendingCount(kitchen())

    /**
     * Impact, read from the event ledger rather than from current row state.
     *
     * This is the difference that matters: deleting an item you threw away no
     * longer erases the fact that you threw it away. The previous version
     * counted rows, so tidying up history quietly rewrote it.
     */
    override fun observeImpact(): Flow<ImpactStats> {
        val kitchenId = kitchen()
        return combine(
            eventDao.sumQuantityForType(kitchenId, ItemEventType.QUANTITY_USED),
            eventDao.sumQuantityForType(kitchenId, ItemEventType.QUANTITY_DISCARDED),
            eventDao.getLastDiscardAt(kitchenId),
            eventDao.getFirstEventAt(kitchenId)
        ) { used, discarded, lastDiscardAt, firstEventAt ->
            val now = clock.nowMillis()
            // Days since the last discard. With no discards on record we count
            // from first activity, so someone who has never wasted anything
            // still sees a number that grows rather than a permanent zero.
            val streakOrigin = lastDiscardAt ?: firstEventAt
            ImpactStats(
                itemsSaved = used,
                itemsWasted = discarded,
                wasteFreeDays = streakOrigin?.let { calendarDaysBetween(it, now) } ?: 0,
                hasHistory = used + discarded > 0
            )
        }
    }

    override suspend fun getItem(itemId: String): Item? =
        itemDao.getItemByIdOnce(kitchen(), itemId)?.toDomain()

    override suspend fun getItemByBarcode(barcode: String): Item? =
        itemDao.getItemByBarcode(kitchen(), barcode)?.toDomain()

    override suspend fun getItemsDueThrough(through: LocalDate): List<Item> =
        itemDao.getItemsDueThrough(kitchen(), through, clock.nowMillis())
            .map(ItemEntity::toDomain)

    override suspend fun findDuplicate(name: String, expiry: LocalDate): Item? =
        itemDao.findActiveDuplicate(kitchen(), name.trim(), expiry)?.toDomain()

    // ─── Writes ──────────────────────────────────────────────────────────────

    override suspend fun add(item: Item): String {
        val now = clock.nowMillis()
        val row = item.toEntity().copy(
            // Stamped here rather than at the call site, so no screen can create
            // a row that belongs to nobody.
            kitchenId = kitchen(),
            createdBy = uid(),
            lastEditedBy = uid(),
            name = item.name.trim(),
            category = item.category.trim(),
            updatedAt = now
        )
        record(
            row = row,
            eventType = ItemEventType.ITEM_CREATED,
            quantity = row.quantity,
            operationType = OutboxOperationType.CREATE,
            at = now
        ) { itemDao.insert(row) }
        return row.id
    }

    override suspend fun update(item: Item) {
        val existing = itemDao.getItemByIdOnce(kitchen(), item.id) ?: return
        val now = clock.nowMillis()
        val row = existing.copy(
            name = item.name.trim(),
            category = item.category.trim(),
            locationId = item.locationId,
            barcode = item.barcode,
            quantity = item.quantity,
            expiryDate = item.expiry.value,
            dateKind = item.expiry.kind,
            dateSource = item.expiry.source,
            dateConfidence = item.expiry.confidence,
            dateConfirmedByUserAt = item.expiry.confirmedByUserAt,
            priceMinorUnits = item.price?.minorUnits,
            priceCurrency = item.price?.currency,
            priceSource = item.price?.source,
            notes = item.notes,
            imageUri = item.imageUri,
            lastEditedBy = uid(),
            updatedAt = now
        )
        record(row, ItemEventType.ITEM_EDITED, null, OutboxOperationType.UPDATE, now) {
            itemDao.update(row)
        }
    }

    override suspend fun use(itemId: String, amount: Int) =
        resolve(itemId, amount, ItemEventType.QUANTITY_USED, ItemState.USED)

    override suspend fun discard(itemId: String, amount: Int) =
        resolve(itemId, amount, ItemEventType.QUANTITY_DISCARDED, ItemState.DISCARDED)

    /**
     * Records that [amount] units left the kitchen.
     *
     * Using part of a batch decrements the quantity and leaves the item active;
     * using all of it resolves the item. Either way the amount is recorded as
     * one event, which is what makes partial use count towards impact.
     *
     * The previous implementation had to split a partial use into a second,
     * synthetic resolved row, because impact was counted from row states and a
     * decrement recorded nothing. With a ledger the event is the record, so the
     * duplicate row is unnecessary — and with it goes the phantom item that
     * used to appear in history.
     */
    private suspend fun resolve(
        itemId: String,
        amount: Int,
        eventType: ItemEventType,
        resolvedState: ItemState
    ) {
        require(amount > 0) { "amount must be positive, was $amount" }
        val existing = itemDao.getItemByIdOnce(kitchen(), itemId) ?: return
        val now = clock.nowMillis()
        val taken = amount.coerceAtMost(existing.quantity)
        if (taken == 0) return

        val resolvesItem = taken >= existing.quantity
        val row = if (resolvesItem) {
            existing.copy(
                state = resolvedState,
                resolvedAt = now,
                lastEditedBy = uid(),
                updatedAt = now
            )
        } else {
            existing.copy(
                quantity = existing.quantity - taken,
                lastEditedBy = uid(),
                updatedAt = now
            )
        }

        record(row, eventType, taken, OutboxOperationType.UPDATE, now) { itemDao.update(row) }
    }

    override suspend fun freeze(
        itemId: String,
        newExpiry: LocalDate,
        freezerLocationId: String?
    ) {
        val existing = itemDao.getItemByIdOnce(kitchen(), itemId) ?: return
        val now = clock.nowMillis()
        // Freezing is a user action, so the revised date is user-sourced and
        // confirmed. It is not an estimate we are allowed to overwrite later.
        val row = existing.copy(
            expiryDate = newExpiry,
            dateKind = com.example.freshtrack.domain.model.DateKind.FROZEN_UNTIL,
            dateSource = com.example.freshtrack.domain.model.DateSource.USER,
            dateConfidence = 1f,
            dateConfirmedByUserAt = now,
            locationId = freezerLocationId ?: existing.locationId,
            lastEditedBy = uid(),
            updatedAt = now
        )
        record(row, ItemEventType.ITEM_FROZEN, null, OutboxOperationType.UPDATE, now) {
            itemDao.update(row)
        }
    }

    override suspend fun move(itemId: String, locationId: String?) {
        val existing = itemDao.getItemByIdOnce(kitchen(), itemId) ?: return
        val now = clock.nowMillis()
        val row = existing.copy(locationId = locationId, lastEditedBy = uid(), updatedAt = now)
        record(row, ItemEventType.ITEM_MOVED, null, OutboxOperationType.UPDATE, now) {
            itemDao.update(row)
        }
    }

    override suspend fun snooze(itemId: String, until: Long) {
        val existing = itemDao.getItemByIdOnce(kitchen(), itemId) ?: return
        val now = clock.nowMillis()
        val row = existing.copy(snoozedUntil = until, lastEditedBy = uid(), updatedAt = now)
        record(row, ItemEventType.ITEM_SNOOZED, null, OutboxOperationType.UPDATE, now) {
            itemDao.update(row)
        }
    }

    override suspend fun delete(itemId: String) {
        val existing = itemDao.getItemByIdOnce(kitchen(), itemId) ?: return
        val now = clock.nowMillis()
        val row = existing.copy(
            isDeleted = true,
            deletedAt = now,
            lastEditedBy = uid(),
            updatedAt = now
        )
        record(row, ItemEventType.ITEM_DELETED, null, OutboxOperationType.TOMBSTONE, now) {
            itemDao.softDelete(kitchen(), itemId, uid(), now)
        }
    }

    override suspend fun confirmDate(itemId: String) {
        val existing = itemDao.getItemByIdOnce(kitchen(), itemId) ?: return
        val now = clock.nowMillis()
        val row = existing.copy(
            dateConfidence = 1f,
            dateConfirmedByUserAt = now,
            lastEditedBy = uid(),
            updatedAt = now
        )
        record(row, ItemEventType.DATE_CONFIRMED, null, OutboxOperationType.UPDATE, now) {
            itemDao.update(row)
        }
    }

    /**
     * Undoes a resolution by putting the units back and recording that we did.
     *
     * The reversal is a new event, not a deletion of the original. The original
     * happened — the user really did tap "used" and then correct it — and
     * removing it would leave a ledger that disagrees with its own history. The
     * impact sums net the two, so the figures match what the user actually did
     * without anything being erased.
     */
    override suspend fun undoLastResolution(itemId: String): Boolean {
        val existing = itemDao.getItemByIdOnce(kitchen(), itemId) ?: return false
        val resolution = eventDao.findLatestUnreversedResolution(itemId) ?: return false
        val restored = resolution.quantity ?: return false
        val now = clock.nowMillis()

        // Two shapes to reverse. Resolving the whole batch changed the state and
        // left the quantity alone; a partial use decremented it.
        val row = if (existing.state != ItemState.ACTIVE) {
            existing.copy(
                state = ItemState.ACTIVE,
                resolvedAt = null,
                lastEditedBy = uid(),
                updatedAt = now
            )
        } else {
            existing.copy(
                quantity = existing.quantity + restored,
                lastEditedBy = uid(),
                updatedAt = now
            )
        }

        record(
            row = row,
            eventType = ItemEventType.ITEM_RESTORED,
            quantity = restored,
            operationType = OutboxOperationType.RESTORE,
            at = now,
            reversesEventId = resolution.id,
            reversesEventType = resolution.type
        ) { itemDao.update(row) }

        return true
    }

    override suspend fun import(items: List<Item>): ImportSummary {
        var imported = 0
        var skipped = 0

        items.forEach { candidate ->
            val name = candidate.name.trim()
            if (name.isEmpty()) return@forEach
            // Checked one at a time rather than in bulk, so duplicates *within*
            // the file are caught too, not only ones already stored.
            if (findDuplicate(name, candidate.expiry.value) != null) {
                skipped++
                return@forEach
            }
            add(candidate.copy(name = name))
            imported++
        }

        return ImportSummary(imported = imported, skippedDuplicates = skipped)
    }

    /**
     * One transaction for the whole sheet.
     *
     * [record] opens a transaction of its own for each write; Room joins a
     * nested one to the outer, so every row, event and outbox entry from this
     * receipt lands together or not at all.
     */
    override suspend fun commitReceipt(newItems: List<Item>, additions: Map<String, Int>) {
        if (newItems.isEmpty() && additions.isEmpty()) return
        transactions.run {
            additions.forEach { (itemId, amount) ->
                if (amount <= 0) return@forEach
                val existing = itemDao.getItemByIdOnce(kitchen(), itemId) ?: return@forEach
                val now = clock.nowMillis()
                // Both quantities move: the batch is bigger, and it always was
                // bigger than the history screen would otherwise report once it
                // is used up.
                val row = existing.copy(
                    quantity = existing.quantity + amount,
                    originalQuantity = existing.originalQuantity + amount,
                    lastEditedBy = uid(),
                    updatedAt = now
                )
                record(row, ItemEventType.ITEM_EDITED, null, OutboxOperationType.UPDATE, now) {
                    itemDao.update(row)
                }
            }
            newItems.forEach { add(it) }
        }
    }

    override suspend fun clearHistory() {
        // Only the rows are removed. The ledger is not touched, so the impact
        // figures stay honest — clearing a list is not the same as claiming the
        // waste never happened.
        transactions.run { itemDao.softDeleteResolved(kitchen(), clock.nowMillis()) }
    }

    override suspend fun claimLocalData(): Int {
        if (!session.isSignedIn()) return 0
        val pending = itemDao.countLocalItems(LOCAL_KITCHEN_ID)
        if (pending == 0) return 0
        val kitchenId = kitchen()
        transactions.run {
            itemDao.claimLocalItems(LOCAL_KITCHEN_ID, kitchenId, uid(), clock.nowMillis())
            eventDao.claimLocalEvents(LOCAL_KITCHEN_ID, kitchenId)
            outboxDao.claimLocalOperations(LOCAL_KITCHEN_ID, kitchenId)
        }
        return pending
    }

    // ─── The one write path ──────────────────────────────────────────────────

    /**
     * Applies a change, records what happened, and queues it for sync — as one
     * transaction.
     *
     * All three or none. A row updated without its event loses history that
     * cannot be reconstructed; an event without its outbox entry never leaves
     * the device.
     */
    private suspend fun record(
        row: ItemEntity,
        eventType: ItemEventType,
        quantity: Int?,
        operationType: OutboxOperationType,
        at: Long,
        reversesEventId: String? = null,
        reversesEventType: ItemEventType? = null,
        applyChange: suspend () -> Unit
    ) {
        val operationId = ids.newId()
        transactions.run {
            applyChange()
            eventDao.append(
                ItemEventEntity(
                    id = ids.newId(),
                    itemId = row.id,
                    kitchenId = row.kitchenId,
                    type = eventType,
                    actorUid = uid(),
                    quantity = quantity,
                    occurredAt = at,
                    operationId = operationId,
                    reversesEventId = reversesEventId,
                    reversesEventType = reversesEventType
                )
            )
            outboxDao.enqueue(
                OutboxEntity(
                    operationId = operationId,
                    entityId = row.id,
                    kitchenId = row.kitchenId,
                    actorUid = uid(),
                    clientId = clientId,
                    clientSequence = (outboxDao.getHighestSequence() ?: 0L) + 1L,
                    baseRevision = row.revision.takeIf { it > 0L },
                    operationType = operationType,
                    occurredAtClient = at,
                    payload = serialise(row)
                )
            )
        }
    }
}
