package com.example.freshtrack.presentation.viewmodel

import com.example.freshtrack.data.local.entities.LocationEntity
import com.example.freshtrack.data.local.entities.LocationType
import com.example.freshtrack.data.repository.CategoryRepository
import com.example.freshtrack.data.repository.ItemRepository
import com.example.freshtrack.data.repository.LocationRepository
import com.example.freshtrack.domain.capture.RowDecision
import com.example.freshtrack.domain.model.Category
import com.example.freshtrack.domain.model.DateKind
import com.example.freshtrack.domain.model.ExpiryDate
import com.example.freshtrack.domain.model.Item
import com.example.freshtrack.util.AppClock
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * The sheet between a receipt and the kitchen.
 *
 * The tests that matter are the refusals: a sheet with an unfinished row saves
 * nothing at all, and a row somebody deliberately kept as its own batch is not
 * quietly folded into the one it resembles.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReceiptReviewViewModelTest {

    private val today = LocalDate.of(2026, 9, 9)

    private val itemRepository = mockk<ItemRepository>(relaxed = true)
    private val categoryRepository = mockk<CategoryRepository>(relaxed = true)
    private val locationRepository = mockk<LocationRepository>(relaxed = true)

    private val clock = object : AppClock {
        override fun nowMillis() = 1_757_000_000_000L
        override fun today(): LocalDate = today
    }

    /** Milk and bread are datable; the wafers are not, which is the point. */
    private val receipt = """
        GREENFIELD MARKET
        SEMI-SKIMMED MILK         1.85
        SOURDOUGH LOAF            2.40
        ZORBANI WAFERS            3.10
        TOTAL                    £7.35
    """.trimIndent()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { categoryRepository.getAllCategories() } returns MutableStateFlow(
            listOf(Category("Dairy", "#fff", "cup", 0), Category("Bakery", "#fff", "bread", 1))
        )
        coEvery { locationRepository.observeLocations() } returns MutableStateFlow(
            listOf(
                LocationEntity(id = "loc-fridge", name = "Fridge", type = LocationType.FRIDGE),
                LocationEntity(id = "loc-freezer", name = "Freezer", type = LocationType.FREEZER)
            )
        )
        coEvery { itemRepository.findDuplicate(any(), any()) } returns null
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = ReceiptReviewViewModel(
        itemRepository = itemRepository,
        categoryRepository = categoryRepository,
        locationRepository = locationRepository,
        clock = clock
    )

    private fun existingItem(id: String, name: String, expiry: LocalDate, quantity: Int) = Item(
        id = id,
        name = name,
        category = "Dairy",
        expiry = ExpiryDate.enteredByUser(expiry, DateKind.BEST_BEFORE, clock.nowMillis()),
        quantity = quantity
    )

    // ─── Reading ────────────────────────────────────────────────────────────

    @Test
    fun `a receipt with nothing on it does not open an empty sheet`() = runTest {
        // An empty review sheet looks like a receipt that genuinely had nothing
        // on it. A failed read has to say it failed.
        val vm = viewModel()

        vm.onTextRecognised("   \n  \n ")

        assertEquals(ReceiptPhase.CAPTURE, vm.uiState.value.phase)
        assertEquals(ReceiptError.NOTHING_READABLE, vm.uiState.value.error)
    }

    @Test
    fun `text with no shopping in it is reported as such`() = runTest {
        val vm = viewModel()

        vm.onTextRecognised("GREENFIELD MARKET\nPlease retain for your records\n")

        assertEquals(ReceiptPhase.CAPTURE, vm.uiState.value.phase)
        assertEquals(ReceiptError.NO_SHOPPING_FOUND, vm.uiState.value.error)
    }

    @Test
    fun `a readable receipt becomes a sheet`() = runTest {
        val vm = viewModel()

        vm.onTextRecognised(receipt)

        assertEquals(ReceiptPhase.REVIEW, vm.uiState.value.phase)
        assertEquals(3, vm.uiState.value.rows.size)
        assertEquals("GBP", vm.uiState.value.currency)
    }

    // ─── Refusing to save half a shop ───────────────────────────────────────

    @Test
    fun `a row with no date blocks the whole sheet`() = runTest {
        val vm = viewModel()
        vm.onTextRecognised(receipt)

        assertTrue(vm.uiState.value.hasUnresolvedRows)
        assertEquals(1, vm.uiState.value.unresolvedCount)
        assertFalse(vm.uiState.value.canCommit)

        vm.commit()

        coVerify(exactly = 0) { itemRepository.commitReceipt(any(), any()) }
    }

    @Test
    fun `giving the unfinished row a date releases the sheet`() = runTest {
        val vm = viewModel()
        vm.onTextRecognised(receipt)
        val stuck = vm.uiState.value.rows.first { it.isUnresolved }

        vm.chooseDate(stuck.candidateId, today.plusDays(30))

        assertFalse(vm.uiState.value.hasUnresolvedRows)
        assertTrue(vm.uiState.value.canCommit)
        assertEquals(3, vm.uiState.value.savedCount)
    }

    @Test
    fun `leaving the unfinished row out also releases the sheet`() = runTest {
        val vm = viewModel()
        vm.onTextRecognised(receipt)
        val stuck = vm.uiState.value.rows.first { it.isUnresolved }

        vm.setDecision(stuck.candidateId, RowDecision.SKIP)

        assertTrue(vm.uiState.value.canCommit)
        // Left out means left out: two rows are saved, not three.
        assertEquals(2, vm.uiState.value.savedCount)
    }

    // ─── Duplicates ─────────────────────────────────────────────────────────

    @Test
    fun `something already in the kitchen is surfaced, not acted on`() = runTest {
        coEvery { itemRepository.findDuplicate("Semi-Skimmed Milk", today.plusDays(7)) } returns
            existingItem("item-milk", "Semi-Skimmed Milk", today.plusDays(7), quantity = 2)

        val vm = viewModel()
        vm.onTextRecognised(receipt)
        val milk = vm.uiState.value.rows.first { it.name.contains("Milk") }

        assertEquals("item-milk", milk.duplicate?.itemId)
        assertEquals(2, milk.duplicate?.quantity)
        // Still its own row until somebody says otherwise.
        assertEquals(RowDecision.ADD, milk.decision)
    }

    @Test
    fun `keeping a duplicate separate really does add a second batch`() = runTest {
        // The reason this screen does not reuse the CSV import path: that one
        // decides for itself what is a duplicate and skips it, which would
        // silently throw away the batch the person just chose to keep.
        coEvery { itemRepository.findDuplicate("Semi-Skimmed Milk", today.plusDays(7)) } returns
            existingItem("item-milk", "Semi-Skimmed Milk", today.plusDays(7), quantity = 2)

        val vm = viewModel()
        vm.onTextRecognised(receipt)
        vm.setDecision(
            vm.uiState.value.rows.first { it.isUnresolved }.candidateId,
            RowDecision.SKIP
        )

        val newItems = slot<List<Item>>()
        val additions = slot<Map<String, Int>>()
        vm.commit()

        coVerify { itemRepository.commitReceipt(capture(newItems), capture(additions)) }
        assertTrue(newItems.captured.any { it.name == "Semi-Skimmed Milk" })
        assertTrue(additions.captured.isEmpty())
    }

    @Test
    fun `merging sends units to the existing batch instead of adding a row`() = runTest {
        coEvery { itemRepository.findDuplicate("Semi-Skimmed Milk", today.plusDays(7)) } returns
            existingItem("item-milk", "Semi-Skimmed Milk", today.plusDays(7), quantity = 2)

        val vm = viewModel()
        vm.onTextRecognised(receipt)
        val milk = vm.uiState.value.rows.first { it.name.contains("Milk") }
        vm.updateQuantity(milk.candidateId, 3)
        vm.setDecision(milk.candidateId, RowDecision.MERGE)
        vm.setDecision(
            vm.uiState.value.rows.first { it.isUnresolved }.candidateId,
            RowDecision.SKIP
        )

        val newItems = slot<List<Item>>()
        val additions = slot<Map<String, Int>>()
        vm.commit()

        coVerify { itemRepository.commitReceipt(capture(newItems), capture(additions)) }
        assertFalse(newItems.captured.any { it.name.contains("Milk") })
        assertEquals(mapOf("item-milk" to 3), additions.captured)
    }

    @Test
    fun `two rows folding into the same batch add up`() = runTest {
        // Receipts do print the same item twice. Keying by item id without
        // summing would keep only the last of them.
        val twoLines = """
            SEMI-SKIMMED MILK         1.85
            SEMI-SKIMMED MILK         1.85
            TOTAL                     3.70
        """.trimIndent()
        coEvery { itemRepository.findDuplicate("Semi-Skimmed Milk", today.plusDays(7)) } returns
            existingItem("item-milk", "Semi-Skimmed Milk", today.plusDays(7), quantity = 1)

        val vm = viewModel()
        vm.onTextRecognised(twoLines)
        vm.uiState.value.rows.forEach { vm.setDecision(it.candidateId, RowDecision.MERGE) }

        val additions = slot<Map<String, Int>>()
        vm.commit()

        coVerify { itemRepository.commitReceipt(any(), capture(additions)) }
        assertEquals(mapOf("item-milk" to 2), additions.captured)
    }

    @Test
    fun `renaming away from a duplicate takes the merge decision with it`() = runTest {
        coEvery { itemRepository.findDuplicate("Semi-Skimmed Milk", today.plusDays(7)) } returns
            existingItem("item-milk", "Semi-Skimmed Milk", today.plusDays(7), quantity = 2)

        val vm = viewModel()
        vm.onTextRecognised(receipt)
        val milk = vm.uiState.value.rows.first { it.name.contains("Milk") }
        vm.setDecision(milk.candidateId, RowDecision.MERGE)

        vm.updateName(milk.candidateId, "Oat milk")

        val renamed = vm.uiState.value.rows.first { it.candidateId == milk.candidateId }
        assertEquals(null, renamed.duplicate)
        // Falls back to its own batch rather than sitting on an impossible merge.
        assertEquals(RowDecision.ADD, renamed.decision)
    }

    // ─── Committing ─────────────────────────────────────────────────────────

    @Test
    fun `a successful commit reports what happened to every row`() = runTest {
        val vm = viewModel()
        vm.onTextRecognised(receipt)
        vm.setDecision(
            vm.uiState.value.rows.first { it.isUnresolved }.candidateId,
            RowDecision.SKIP
        )

        vm.commit()

        assertEquals(ReceiptPhase.SAVED, vm.uiState.value.phase)
        val result = vm.uiState.value.result!!
        assertEquals(2, result.added)
        assertEquals(0, result.merged)
        assertEquals(1, result.left)
    }

    @Test
    fun `a failed commit says so and saves nothing`() = runTest {
        coEvery { itemRepository.commitReceipt(any(), any()) } throws RuntimeException("disk full")

        val vm = viewModel()
        vm.onTextRecognised(receipt)
        vm.setDecision(
            vm.uiState.value.rows.first { it.isUnresolved }.candidateId,
            RowDecision.SKIP
        )

        vm.commit()

        assertEquals(ReceiptError.SAVE_FAILED, vm.uiState.value.error)
        assertEquals(ReceiptPhase.REVIEW, vm.uiState.value.phase)
    }

    @Test
    fun `lines that could not be read are kept and shown`() = runTest {
        val vm = viewModel()

        vm.onTextRecognised(receipt + "\nSOMETHING SMUDGED HERE\n")

        assertTrue(
            vm.uiState.value.unreadableLines.any { it.contains("SMUDGED") }
        )
    }
}
