package com.example.freshtrack.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.freshtrack.data.local.entities.LocationEntity
import com.example.freshtrack.data.repository.CategoryRepository
import com.example.freshtrack.data.repository.ItemRepository
import com.example.freshtrack.data.repository.LocationRepository
import com.example.freshtrack.domain.capture.DuplicateMatch
import com.example.freshtrack.domain.capture.ReceiptDraft
import com.example.freshtrack.domain.capture.ReceiptParser
import com.example.freshtrack.domain.capture.ReceiptRow
import com.example.freshtrack.domain.capture.RowDecision
import com.example.freshtrack.util.AppClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Reviewing a receipt before any of it becomes inventory.
 *
 * Nothing here writes until [commit], and [commit] refuses while any row is
 * unresolved. That is the whole point of the screen: a receipt is a list of
 * things someone bought, not a list of things with dates on them, and the gap
 * between those two is filled by a person rather than by a guess promoted
 * quietly to a fact.
 */
class ReceiptReviewViewModel(
    private val itemRepository: ItemRepository,
    private val categoryRepository: CategoryRepository,
    private val locationRepository: LocationRepository,
    private val clock: AppClock = AppClock.System
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReceiptReviewUiState())
    val uiState: StateFlow<ReceiptReviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            categoryRepository.getAllCategories().collect { categories ->
                _uiState.update { it.copy(categories = categories.map { c -> c.name }) }
            }
        }
        viewModelScope.launch {
            locationRepository.observeLocations().collect { locations ->
                _uiState.update { it.copy(locations = locations) }
            }
        }
    }

    /** The camera or the picker produced an image; recognition is running. */
    fun onReadingStarted() {
        _uiState.update { it.copy(phase = ReceiptPhase.READING, error = null) }
    }

    /** Recognition failed or produced nothing at all. */
    fun onReadingFailed() {
        _uiState.update {
            it.copy(phase = ReceiptPhase.CAPTURE, error = ReceiptError.NOTHING_READABLE)
        }
    }

    /**
     * Takes the text off a receipt and turns it into a sheet to review.
     *
     * A receipt with no readable shopping on it is reported as a failed read
     * rather than shown as an empty sheet, because an empty sheet looks like a
     * receipt that genuinely had nothing on it.
     */
    fun onTextRecognised(text: String) {
        val candidates = ReceiptParser.parse(text)
        if (candidates.items.isEmpty()) {
            _uiState.update {
                it.copy(
                    phase = ReceiptPhase.CAPTURE,
                    error = if (candidates.unresolvedLines.isEmpty()) {
                        ReceiptError.NOTHING_READABLE
                    } else {
                        ReceiptError.NO_SHOPPING_FOUND
                    }
                )
            }
            return
        }

        val rows = ReceiptDraft.rowsFrom(candidates, clock.today())
        _uiState.update {
            it.copy(
                phase = ReceiptPhase.REVIEW,
                rows = rows,
                unreadableLines = candidates.unresolvedLines,
                currency = candidates.currency,
                error = null
            )
        }
        refreshDuplicates()
    }

    fun updateName(candidateId: String, name: String) {
        editRow(candidateId) { it.copy(name = name).reestimated(clock.today()) }
        refreshDuplicate(candidateId)
    }

    fun updateQuantity(candidateId: String, quantity: Int) {
        editRow(candidateId) { it.copy(quantity = quantity.coerceAtLeast(1)) }
    }

    fun updateCategory(candidateId: String, category: String) {
        editRow(candidateId) { it.copy(category = category).reestimated(clock.today()) }
        refreshDuplicate(candidateId)
    }

    /** Where it is being kept, which is half of how long it is likely to last. */
    fun updateLocation(candidateId: String, locationId: String?) {
        val location = locationId?.let { id -> _uiState.value.locations.find { it.id == id } }
        editRow(candidateId) {
            it.copy(locationId = location?.id, locationType = location?.type)
                .reestimated(clock.today())
        }
        refreshDuplicate(candidateId)
    }

    fun chooseDate(candidateId: String, date: LocalDate) {
        editRow(candidateId) { it.withChosenDate(date) }
        refreshDuplicate(candidateId)
    }

    fun setDecision(candidateId: String, decision: RowDecision) {
        editRow(candidateId) { it.copy(decision = decision) }
    }

    /**
     * Re-checks every row against what is already in the kitchen.
     *
     * The match is on name and date together, so two batches of the same food
     * with different dates are correctly two batches.
     */
    private fun refreshDuplicates() {
        viewModelScope.launch {
            _uiState.value.rows.forEach { row -> applyDuplicate(row) }
        }
    }

    private fun refreshDuplicate(candidateId: String) {
        val row = _uiState.value.rows.find { it.candidateId == candidateId } ?: return
        viewModelScope.launch { applyDuplicate(row) }
    }

    private suspend fun applyDuplicate(row: ReceiptRow) {
        val expiry = row.expiry
        val match = if (expiry == null || row.name.isBlank()) {
            null
        } else {
            itemRepository.findDuplicate(row.name.trim(), expiry)
                ?.let { DuplicateMatch(itemId = it.id, quantity = it.quantity) }
        }
        editRow(row.candidateId) { current ->
            current.copy(
                duplicate = match,
                // A row cannot go on saying it will merge into a batch that is
                // no longer there; it falls back to being its own, which is a
                // decision the person can see and change.
                decision = if (match == null && current.decision == RowDecision.MERGE) {
                    RowDecision.ADD
                } else {
                    current.decision
                }
            )
        }
    }

    private fun editRow(candidateId: String, change: (ReceiptRow) -> ReceiptRow) {
        _uiState.update { state ->
            state.copy(
                rows = state.rows.map { row ->
                    if (row.candidateId == candidateId) change(row) else row
                }
            )
        }
    }

    /**
     * Writes the sheet, all of it at once.
     *
     * Refuses outright while anything is unresolved rather than saving the rows
     * that happen to be complete: a partial import of a shop is worse than none,
     * because the person has no way to tell what did not make it.
     */
    fun commit() {
        val state = _uiState.value
        if (state.isWorking || state.hasUnresolvedRows || state.savedCount == 0) return

        val now = clock.nowMillis()
        val newItems = state.rows
            .filter { it.decision == RowDecision.ADD && !it.isUnresolved }
            .map { it.toItem(state.currency, now) }

        // Grouped rather than mapped one to one: two lines of the same food can
        // both fold into the same batch, and keying by item id would drop all
        // but the last of them.
        val additions = state.rows
            .filter { it.decision == RowDecision.MERGE && it.duplicate != null }
            .groupBy { it.duplicate!!.itemId }
            .mapValues { (_, rows) -> rows.sumOf { it.quantity } }

        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, error = null) }
            runCatching { itemRepository.commitReceipt(newItems, additions) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isWorking = false,
                            phase = ReceiptPhase.SAVED,
                            result = ReceiptCommitResult(
                                added = newItems.size,
                                merged = additions.values.sum(),
                                left = state.rows.count { row -> row.decision == RowDecision.SKIP }
                            )
                        )
                    }
                }
                .onFailure {
                    _uiState.update {
                        it.copy(isWorking = false, error = ReceiptError.SAVE_FAILED)
                    }
                }
        }
    }

    /** Throws the sheet away and goes back to the camera. */
    fun startOver() {
        _uiState.update {
            it.copy(
                phase = ReceiptPhase.CAPTURE,
                rows = emptyList(),
                unreadableLines = emptyList(),
                currency = null,
                error = null,
                result = null
            )
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}

/** Where the person is in the flow. Capture, then review, then it is written. */
enum class ReceiptPhase { CAPTURE, READING, REVIEW, SAVED }

enum class ReceiptError {
    /** Nothing legible came back at all. */
    NOTHING_READABLE,

    /** Text came back, but none of it looked like things someone bought. */
    NO_SHOPPING_FOUND,

    SAVE_FAILED
}

/** What was written, in the three ways a row could end up. */
data class ReceiptCommitResult(
    val added: Int,
    val merged: Int,
    val left: Int
)

data class ReceiptReviewUiState(
    val phase: ReceiptPhase = ReceiptPhase.CAPTURE,
    val rows: List<ReceiptRow> = emptyList(),

    /**
     * Lines that looked like shopping and could not be read.
     *
     * Shown rather than dropped. Someone who bought eleven things and is handed
     * ten has to be told, or the app has decided on their behalf that the
     * eleventh did not happen.
     */
    val unreadableLines: List<String> = emptyList(),

    val currency: String? = null,
    val categories: List<String> = emptyList(),
    val locations: List<LocationEntity> = emptyList(),
    val isWorking: Boolean = false,
    val error: ReceiptError? = null,
    val result: ReceiptCommitResult? = null
) {
    val hasUnresolvedRows: Boolean get() = rows.any { it.isUnresolved }

    val unresolvedCount: Int get() = rows.count { it.isUnresolved }

    /** How many rows would write something if the sheet were committed now. */
    val savedCount: Int get() = rows.count { it.willBeSaved }

    val canCommit: Boolean get() = !isWorking && !hasUnresolvedRows && savedCount > 0
}
