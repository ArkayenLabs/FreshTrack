package com.example.freshtrack.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshtrack.data.repository.ItemRepository
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
    private val clock: AppClock = AppClock.System
) : ViewModel() {

    val rescue: StateFlow<RescueList> = itemRepository.observeActiveItems()
        .map { items -> RescueRanking.build(items, clock.today(), clock.nowMillis()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RescueList(emptyList(), 0, 0)
        )

    private val _undoPrompt = MutableStateFlow<UndoPrompt?>(null)

    /** The last resolution, offered back until it is used or dismissed. */
    val undoPrompt: StateFlow<UndoPrompt?> = _undoPrompt.asStateFlow()

    fun use(itemId: String, itemName: String, amount: Int = 1) {
        viewModelScope.launch {
            itemRepository.use(itemId, amount)
            _undoPrompt.value = UndoPrompt(itemId, "Used $itemName")
        }
    }

    fun discard(itemId: String, itemName: String, amount: Int = 1) {
        viewModelScope.launch {
            itemRepository.discard(itemId, amount)
            _undoPrompt.value = UndoPrompt(itemId, "Binned $itemName")
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

    data class UndoPrompt(val itemId: String, val message: String)

    /** Hides the item from Today until tomorrow without resolving it. */
    fun snoozeUntilTomorrow(itemId: String) {
        viewModelScope.launch {
            itemRepository.snooze(itemId, clock.nowMillis() + TimeUnit.DAYS.toMillis(1))
            _undoPrompt.value = null
        }
    }
}
