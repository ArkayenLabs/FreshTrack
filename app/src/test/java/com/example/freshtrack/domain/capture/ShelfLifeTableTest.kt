package com.example.freshtrack.domain.capture

import com.example.freshtrack.data.local.entities.LocationType
import com.example.freshtrack.domain.model.ExpiryDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShelfLifeTableTest {

    @Test
    fun `storage changes the answer`() {
        // The whole reason location is an input. Bread on a counter and bread
        // in a freezer are not the same question.
        val counter = ShelfLifeTable.estimate("Sourdough bread", storage = LocationType.COUNTER)!!
        val freezer = ShelfLifeTable.estimate("Sourdough bread", storage = LocationType.FREEZER)!!

        assertEquals(5, counter.days)
        assertEquals(90, freezer.days)
    }

    @Test
    fun `a more specific name wins over a shorter one`() {
        // "Cream cheese" must not be read as "cream".
        val creamCheese = ShelfLifeTable.estimate("Cream cheese", storage = LocationType.FRIDGE)!!
        val cream = ShelfLifeTable.estimate("Double cream", storage = LocationType.FRIDGE)!!

        assertEquals(14, creamCheese.days)
        assertEquals(7, cream.days)
    }

    @Test
    fun `a named food beats its category`() {
        val spinach = ShelfLifeTable.estimate(
            name = "Baby spinach",
            category = "Fresh Produce",
            storage = LocationType.FRIDGE
        )!!

        assertEquals(5, spinach.days)
        assertTrue(spinach.basis.startsWith("spinach"))
    }

    @Test
    fun `an unknown name falls back to the category, and says so`() {
        val estimate = ShelfLifeTable.estimate(
            name = "Zorbani wafers",
            category = "Bakery",
            storage = LocationType.PANTRY
        )!!

        assertEquals(5, estimate.days)
        assertEquals("Bakery kept in a pantry", estimate.basis)
    }

    @Test
    fun `no name and no useful category means no guess`() {
        // Refusing is the honest answer. A number here would look, on screen,
        // exactly like one that came from somewhere.
        assertNull(ShelfLifeTable.estimate("Zorbani wafers"))
        assertNull(ShelfLifeTable.estimate("Zorbani wafers", category = "Other"))
    }

    @Test
    fun `an unhelpful location does not invent precision`() {
        val known = ShelfLifeTable.estimate("Milk", storage = LocationType.FRIDGE)!!
        val vague = ShelfLifeTable.estimate("Milk", storage = LocationType.OTHER)!!

        assertTrue(vague.confidence < known.confidence)
        assertEquals("milk", vague.basis)
    }

    @Test
    fun `a category guess is trusted less than a named one`() {
        val named = ShelfLifeTable.estimate("Milk", category = "Dairy", storage = LocationType.FRIDGE)!!
        val category = ShelfLifeTable.estimate("Zorbani", category = "Dairy", storage = LocationType.FRIDGE)!!

        assertTrue(category.confidence < named.confidence)
    }

    @Test
    fun `every estimate is weak enough to need confirming`() {
        // The contract that matters: nothing this table produces may become
        // inventory truth without a person agreeing to it, and a printed date
        // must always be able to overrule it.
        val purchased = LocalDate.of(2026, 9, 9)
        val samples = listOf(
            ShelfLifeTable.estimate("Milk", storage = LocationType.FRIDGE),
            ShelfLifeTable.estimate("Bread", storage = LocationType.FREEZER),
            ShelfLifeTable.estimate("Zorbani", category = "Pantry")
        )

        samples.forEach { estimate ->
            assertNotNull(estimate)
            val date = ExpiryDate.estimated(
                value = purchased.plusDays(estimate!!.days.toLong()),
                confidence = estimate.confidence
            )
            assertTrue("an estimate must be labelled as one", date.isEstimate)
            assertTrue("an estimate must require confirmation", date.requiresConfirmation)
        }
    }

    @Test
    fun `a printed date outranks anything this table produces`() {
        val estimate = ShelfLifeTable.estimate("Milk", storage = LocationType.FRIDGE)!!
        val guessed = ExpiryDate.estimated(
            value = LocalDate.of(2026, 9, 16),
            confidence = estimate.confidence
        )
        val printed = ExpiryDate.recognised(
            value = LocalDate.of(2026, 9, 20),
            kind = com.example.freshtrack.domain.model.DateKind.USE_BY,
            source = com.example.freshtrack.domain.model.DateSource.PRINTED_OCR,
            confidence = 0.9f
        )

        assertTrue(guessed.canBeReplacedBy(printed))
        assertTrue(!printed.canBeReplacedBy(guessed))
    }

    @Test
    fun `matching ignores case and surrounding words`() {
        assertEquals(
            ShelfLifeTable.estimate("MILK", storage = LocationType.FRIDGE)?.days,
            ShelfLifeTable.estimate("Semi-skimmed milk 2L", storage = LocationType.FRIDGE)?.days
        )
    }
}
