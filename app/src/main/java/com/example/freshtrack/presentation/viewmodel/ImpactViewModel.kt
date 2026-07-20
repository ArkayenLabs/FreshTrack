package com.example.freshtrack.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshtrack.data.repository.ProductRepository
import com.example.freshtrack.domain.model.ImpactStats
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * ViewModel for the Impact Dashboard.
 *
 * Holds no state of its own — every figure is projected from the products table,
 * so the dashboard cannot disagree with History.
 */
class ImpactViewModel(
    productRepository: ProductRepository
) : ViewModel() {

    val stats: StateFlow<ImpactStats> = productRepository.getImpactStats()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ImpactStats()
        )
}
