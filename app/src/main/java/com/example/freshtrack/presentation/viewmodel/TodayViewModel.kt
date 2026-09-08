package com.example.freshtrack.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshtrack.data.repository.ItemRepository
import com.example.freshtrack.domain.rescue.RescueList
import com.example.freshtrack.domain.rescue.RescueRanking
import com.example.freshtrack.util.AppClock
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
 * There is no undo yet, deliberately. Reversing a "used" has to net that use
 * back out of the event ledger, otherwise the impact figures keep counting food
 * the user told us they had not eaten after all — and impact silently
 * disagreeing with history is the exact failure the ledger was built to end.
 * Doing it properly needs the reversal recorded on the event, which is a schema
 * change. Snooze covers "not now" in the meantime, and is non-destructive.
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

    fun use(itemId: String, amount: Int = 1) {
        viewModelScope.launch { itemRepository.use(itemId, amount) }
    }

    fun discard(itemId: String, amount: Int = 1) {
        viewModelScope.launch { itemRepository.discard(itemId, amount) }
    }

    /** Hides the item from Today until tomorrow without resolving it. */
    fun snoozeUntilTomorrow(itemId: String) {
        viewModelScope.launch {
            itemRepository.snooze(itemId, clock.nowMillis() + TimeUnit.DAYS.toMillis(1))
        }
    }
}
