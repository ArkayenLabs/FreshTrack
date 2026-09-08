package com.example.freshtrack.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshtrack.data.repository.ItemRepository
import com.example.freshtrack.domain.model.ImpactStats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Holds no state of its own. Every figure is projected from the event ledger,
 * so the dashboard cannot disagree with History and cannot be changed by
 * tidying up the item list.
 */
class ImpactViewModel(
    itemRepository: ItemRepository
) : ViewModel() {

    val stats: StateFlow<ImpactStats> = itemRepository.observeImpact()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ImpactStats()
        )
}
