package com.example.freshtrack.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshtrack.data.repository.ItemRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.flowOf
import com.example.freshtrack.data.local.entities.LocationType
import com.example.freshtrack.data.repository.LocationRepository
import com.example.freshtrack.domain.model.Item
import com.example.freshtrack.domain.rescue.RescueList
import com.example.freshtrack.domain.rescue.RescueRanking
import com.example.freshtrack.util.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * What to do about food today.
 *
 * The ranking itself lives in the domain and is pure; this only supplies the
 * current date and turns actions into repository calls.
 *
 * A resolution can be undone. The reversal is recorded rather than erased, and
 * the impact sums net the two, so correcting a mis-tap does not leave the
 * figures counting food the user said they had not eaten.
 */
class TodayViewModel(
    private val itemRepository: ItemRepository,
    private val clock: AppClock = AppClock.System,
    private val locationRepository: LocationRepository? = null
) : ViewModel() {

    /** The kitchen's freezer, if it has one; null hides the Freeze action. */
    val freezerLocationId: StateFlow<String?> = (locationRepository?.observeLocations() ?: flowOf(emptyList()))
        .map { locations -> locations.firstOrNull { it.type == LocationType.FREEZER }?.id }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    /** Something just went in the freezer; the screen says so once. */
    private val _frozenNotice = MutableStateFlow<FrozenNotice?>(null)
    val frozenNotice: StateFlow<FrozenNotice?> = _frozenNotice.asStateFlow()

    data class FrozenNotice(val itemName: String, val until: LocalDate)

    /**
     * Freezing is the escape route a warning on its own does not give.
     * The item moves to the freezer and gets a new date a fixed distance
     * out, marked as frozen-until so it reads as a guide to quality, never
     * a safety claim; the printed date is kept in the ledger, not
     * overwritten in the person's memory of the item.
     */
    fun freeze(itemId: String, itemName: String) {
        val freezer = freezerLocationId.value ?: return
        viewModelScope.launch {
            val until = clock.today().plusMonths(FREEZER_MONTHS)
            itemRepository.freeze(itemId, until, freezer)
            _undoPrompt.value = null
            _frozenNotice.value = FrozenNotice(itemName, until)
        }
    }

    fun dismissFrozenNotice() {
        _frozenNotice.value = null
    }

    // Shared so that the two things derived from it are one database query
    // rather than two identical ones.
    private val activeItems = itemRepository.observeActiveItems()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    val rescue: StateFlow<RescueList> = activeItems
        .map { items -> RescueRanking.build(items, clock.today(), clock.nowMillis()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RescueList(emptyList(), 0, 0)
        )

    /**
     * Whether there is anything in the kitchen at all.
     *
     * Not the same question as the rescue list, which only holds what is close
     * to its date. Asking about reminders is only meaningful once the person has
     * food being tracked, and that is true well before anything is urgent.
     */
    val hasAnyItem: StateFlow<Boolean?> = activeItems
        .map { it.isNotEmpty() }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            // Null until Room answers: "empty" is a screen of its own, and it
            // must not flash before the real answer arrives.
            initialValue = null
        )

    /**
     * What the all-clear screen shows instead of a tick: the kitchen's size
     * and the few things that will come up first. Everything past the rescue
     * horizon, soonest first, so the screen says "here is what is next"
     * rather than "nothing".
     */
    val comingUp: StateFlow<ComingUp> = activeItems
        .map { items ->
            val today = clock.today()
            ComingUp(
                total = items.size,
                next = items
                    .filter { it.daysUntilExpiry(today) > RescueRanking.HORIZON_DAYS }
                    .sortedBy { it.expiry.value }
                    .take(COMING_UP_COUNT)
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ComingUp(0, emptyList())
        )

    data class ComingUp(val total: Int, val next: List<Item>)

    private val _undoPrompt = MutableStateFlow<UndoPrompt?>(null)

    /** The last resolution, offered back until it is used or dismissed. */
    val undoPrompt: StateFlow<UndoPrompt?> = _undoPrompt.asStateFlow()

    fun use(itemId: String, itemName: String, amount: Int = 1) {
        viewModelScope.launch {
            itemRepository.use(itemId, amount)
            _undoPrompt.value = UndoPrompt(itemId, ResolvedAs.USED, itemName)
        }
    }

    fun discard(itemId: String, itemName: String, amount: Int = 1) {
        viewModelScope.launch {
            itemRepository.discard(itemId, amount)
            _undoPrompt.value = UndoPrompt(itemId, ResolvedAs.BINNED, itemName)
        }
    }

    fun undo() {
        val prompt = _undoPrompt.value ?: return
        viewModelScope.launch {
            itemRepository.undoLastResolution(prompt.itemId)
            _undoPrompt.value = null
        }
    }

    fun dismissUndo() {
        _undoPrompt.value = null
    }

    /** What was done, so the screen can say it in its own language. */
    enum class ResolvedAs { USED, BINNED }

    /**
     * An offer to undo the last resolution.
     *
     * Carries the fact rather than the sentence. A ViewModel has no Context and
     * no business holding English: building the message here would have made it
     * the one piece of user-facing text no translator could reach.
     */
    data class UndoPrompt(
        val itemId: String,
        val resolvedAs: ResolvedAs,
        val itemName: String
    )

    /** Hides the item from Today until tomorrow without resolving it. */
    fun snoozeUntilTomorrow(itemId: String) {
        viewModelScope.launch {
            itemRepository.snooze(itemId, clock.nowMillis() + TimeUnit.DAYS.toMillis(1))
            _undoPrompt.value = null
        }
    }

    companion object {
        const val COMING_UP_COUNT = 3
        /** One window for everything: a guide to quality that the person can edit, not a per-food safety rule. */
        const val FREEZER_MONTHS = 3L
    }
}
