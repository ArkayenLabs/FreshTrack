package com.example.freshtrack.presentation.viewmodel

import com.example.freshtrack.data.repository.ItemRepository
import com.example.freshtrack.domain.model.DateKind
import com.example.freshtrack.domain.model.ExpiryDate
import com.example.freshtrack.domain.model.Item
import com.example.freshtrack.util.AppClock
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/**
 * `hasAnyItem`, which decides whether asking about reminders has a subject yet.
 *
 * It is deliberately not the same question as the rescue list. A jar bought
 * today with a date a year out belongs in neither "use this soon" nor "there is
 * nothing here", and asking about reminders is reasonable the moment it exists.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    private val today = LocalDate.of(2026, 9, 9)
    private val items = MutableStateFlow<List<Item>>(emptyList())
    private val itemRepository = mockk<ItemRepository>(relaxed = true)

    private val clock = object : AppClock {
        override fun nowMillis() = 1_757_000_000_000L
        override fun today(): LocalDate = today
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { itemRepository.observeActiveItems() } returns items
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun item(daysOut: Long) = Item(
        id = "id-$daysOut",
        name = "Thing",
        category = "Pantry",
        expiry = ExpiryDate.enteredByUser(
            value = today.plusDays(daysOut),
            kind = DateKind.BEST_BEFORE,
            atMillis = clock.nowMillis()
        ),
        quantity = 1,
        addedAt = clock.nowMillis()
    )

    /**
     * Both flows are shared with WhileSubscribed, so they sit at their initial
     * value until something collects them. Reading `.value` off an uncollected
     * one measures the default rather than the repository, which is a way to
     * write a test that passes regardless of the code under it.
     */
    private fun TestScope.started(): TodayViewModel {
        val vm = TodayViewModel(itemRepository, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.hasAnyItem.collect {}
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            vm.rescue.collect {}
        }
        return vm
    }

    @Test
    fun `an empty kitchen has nothing to be reminded about`() = runTest {
        assertFalse(started().hasAnyItem.value)
    }

    @Test
    fun `one item is enough, even with nothing urgent`() = runTest {
        // A year out is not on the rescue list and still worth a reminder.
        items.value = listOf(item(daysOut = 365))
        val vm = started()

        assertTrue(vm.hasAnyItem.value)
        assertTrue("a distant date should not be urgent", vm.rescue.value.isEmpty)
    }

    @Test
    fun `something due soon counts for both`() = runTest {
        items.value = listOf(item(daysOut = 1))
        val vm = started()

        assertTrue(vm.hasAnyItem.value)
        assertFalse(vm.rescue.value.isEmpty)
    }
}
