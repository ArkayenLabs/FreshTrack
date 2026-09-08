package com.example.freshtrack.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshtrack.data.repository.ItemRepository
import com.example.freshtrack.domain.model.Item
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val itemRepository: ItemRepository
) : ViewModel() {

    val used: StateFlow<List<Item>> = itemRepository.observeUsedItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val discarded: StateFlow<List<Item>> = itemRepository.observeDiscardedItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Clears the visible list only. The underlying events stay, so the impact
     * figures continue to reflect what actually happened.
     */
    fun clearHistory() {
        viewModelScope.launch { itemRepository.clearHistory() }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch { itemRepository.delete(itemId) }
    }
}
