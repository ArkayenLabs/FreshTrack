package com.example.freshtrack.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshtrack.data.repository.ItemRepository
import com.example.freshtrack.domain.model.Item
import com.example.freshtrack.util.AnalyticsHelper
import com.example.freshtrack.util.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * One item in detail, and the actions available on it.
 *
 * The resolve actions take an amount because using part of a batch is the
 * common case. Only a resolution that empties the batch leaves the screen; a
 * partial use keeps the remainder in front of the user.
 */
class ItemDetailsViewModel(
    private val itemRepository: ItemRepository,
    private val clock: AppClock = AppClock.System
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItemDetailsUiState())
    val uiState: StateFlow<ItemDetailsUiState> = _uiState.asStateFlow()

    fun loadItem(itemId: String) {
        viewModelScope.launch {
            itemRepository.observeItem(itemId).collect { item ->
                _uiState.update { it.copy(item = item, isLoading = false) }
            }
        }
    }

    fun deleteItem(onSuccess: () -> Unit) {
        val itemId = _uiState.value.item?.id ?: return
        viewModelScope.launch {
            itemRepository.delete(itemId)
            onSuccess()
        }
    }

    fun use(amount: Int, onResolved: () -> Unit) {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            itemRepository.use(item.id, amount)
            AnalyticsHelper.logItemConsumed(item.category, item.isExpired(clock.today()))
            if (amount >= item.quantity) onResolved()
        }
    }

    fun discard(amount: Int, onResolved: () -> Unit) {
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            itemRepository.discard(item.id, amount)
            AnalyticsHelper.logItemDiscarded(item.category)
            if (amount >= item.quantity) onResolved()
        }
    }

    /** Accepts a recognised date as correct, so nothing weaker can replace it. */
    fun confirmDate() {
        val itemId = _uiState.value.item?.id ?: return
        viewModelScope.launch { itemRepository.confirmDate(itemId) }
    }

    fun move(locationId: String?) {
        val itemId = _uiState.value.item?.id ?: return
        viewModelScope.launch { itemRepository.move(itemId, locationId) }
    }
}

data class ItemDetailsUiState(
    val item: Item? = null,
    val isLoading: Boolean = true
)

/**
 * Settings screen state. Preferences are read and written by the screen itself
 * through their own stores; this only holds transient display state.
 */
class SettingsViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun updateNotificationDays(days: Int) {
        _uiState.update { it.copy(notificationDaysInAdvance = days) }
    }

    fun toggleDailyReminder(enabled: Boolean) {
        _uiState.update { it.copy(dailyReminderEnabled = enabled) }
    }
}

data class SettingsUiState(
    val notificationDaysInAdvance: Int = 3,
    val dailyReminderEnabled: Boolean = true,
    val darkModeEnabled: Boolean = false
)
