package com.example.freshtrack.presentation.viewmodel

import com.example.freshtrack.data.repository.CategoryRepository
import com.example.freshtrack.data.repository.ItemRepository
import com.example.freshtrack.data.repository.LocationRepository
import com.example.freshtrack.domain.model.DateKind
import com.example.freshtrack.domain.model.DateSource
import com.example.freshtrack.domain.model.ExpiryDate
import com.example.freshtrack.domain.model.Item
import com.example.freshtrack.domain.repository.ProductLookupRepository
import com.example.freshtrack.util.AppClock
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * What happens to a scanned date's provenance between the review sheet and the
 * saved row.
 *
 * This is the join that makes the provenance model mean anything. The parser
 * can be perfect and `canBeReplacedBy` can be correct, and it all still comes to
 * nothing if the save path flattens every date into "the user typed this" —
 * which is exactly what it would do if the scanned value were only pushed into
 * the date picker.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddEditItemViewModelScanTest {

    private val itemRepository = mockk<ItemRepository>(relaxed = true)
    private val categoryRepository = mockk<CategoryRepository>()
    private val locationRepository = mockk<LocationRepository>()
    private val productLookupRepository = mockk<ProductLookupRepository>(relaxed = true)

    private val now = 1_757_000_000_000L
    private val clock = object : AppClock {
        override fun nowMillis() = now
        override fun today(): LocalDate = LocalDate.of(2026, 9, 9)
    }

    private val scanned = ExpiryDate.recognised(
        value = LocalDate.of(2027, 3, 12),
        kind = DateKind.BEST_BEFORE,
        source = DateSource.PRINTED_OCR,
        confidence = 0.95f
    ).confirmedAt(now)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { categoryRepository.getAllCategories() } returns flowOf(emptyList())
        every { locationRepository.observeLocations() } returns flowOf(emptyList())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AddEditItemViewModel(
        itemRepository = itemRepository,
        categoryRepository = categoryRepository,
        locationRepository = locationRepository,
        productLookupRepository = productLookupRepository,
        clock = clock
    )

    private fun savedItem(block: AddEditItemViewModel.() -> Unit): Item {
        val captured = slot<Item>()
        coEvery { itemRepository.add(capture(captured)) } returns "saved-id"
        viewModel().apply {
            updateName("Milk")
            updateQuantity("1")
            block()
            saveItem(onSuccess = {})
        }
        return captured.captured
    }

    @Test
    fun `a scanned date is saved as read from the packet, not as typed`() {
        val item = savedItem { applyScannedExpiry(scanned) }

        assertEquals(LocalDate.of(2027, 3, 12), item.expiry.value)
        assertEquals(DateSource.PRINTED_OCR, item.expiry.source)
        assertEquals(DateKind.BEST_BEFORE, item.expiry.kind)
        assertNotNull(item.expiry.confirmedByUserAt)
    }

    @Test
    fun `a scan carries its label kind, not the default`() {
        val useBy = ExpiryDate.recognised(
            value = LocalDate.of(2027, 3, 12),
            kind = DateKind.USE_BY,
            source = DateSource.PRINTED_OCR,
            confidence = 0.95f
        ).confirmedAt(now)

        assertEquals(DateKind.USE_BY, savedItem { applyScannedExpiry(useBy) }.expiry.kind)
    }

    @Test
    fun `changing the date after a scan makes it the user's own`() {
        // The camera read one thing and the person disagreed. Keeping the OCR
        // provenance here would attribute their correction to the packet.
        val item = savedItem {
            applyScannedExpiry(scanned)
            updateExpiryDate(LocalDate.of(2027, 4, 1))
        }

        assertEquals(LocalDate.of(2027, 4, 1), item.expiry.value)
        assertEquals(DateSource.USER, item.expiry.source)
    }

    @Test
    fun `a hand-picked date is still the user's own`() {
        val item = savedItem { updateExpiryDate(LocalDate.of(2027, 4, 1)) }

        assertEquals(DateSource.USER, item.expiry.source)
        assertNotNull(item.expiry.confirmedByUserAt)
    }

    @Test
    fun `a saved scan cannot be overwritten by anything but the user`() {
        // The end of the chain: because the saved date is confirmed, a later
        // rule or model pass cannot quietly move it.
        val saved = savedItem { applyScannedExpiry(scanned) }.expiry
        val guess = ExpiryDate.estimated(LocalDate.of(2027, 1, 1))

        assertEquals(false, saved.canBeReplacedBy(guess))
    }

    @Test
    fun `saving without a date is refused rather than invented`() {
        val vm = viewModel()
        vm.updateName("Milk")
        vm.updateQuantity("1")
        vm.saveItem(onSuccess = {})

        assertNull(vm.uiState.value.expiryDate)
        assertEquals(AddEditError.EXPIRY_REQUIRED, vm.uiState.value.error)
    }
}
