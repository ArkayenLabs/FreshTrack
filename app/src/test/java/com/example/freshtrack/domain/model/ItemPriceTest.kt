package com.example.freshtrack.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemPriceTest {

    @Test
    fun `observed prices are verified, inferred ones are not`() {
        assertTrue(ItemPrice(4900, "INR", PriceSource.USER).isVerified)
        assertTrue(ItemPrice(4900, "INR", PriceSource.RECEIPT).isVerified)
        assertFalse(ItemPrice(4900, "INR", PriceSource.ESTIMATED).isVerified)
    }

    @Test
    fun `totals stay exact in minor units`() {
        assertEquals(14_700L, ItemPrice(4900, "INR", PriceSource.RECEIPT).times(3))
        assertEquals(0L, ItemPrice(4900, "INR", PriceSource.RECEIPT).times(0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a negative price is rejected`() {
        ItemPrice(-1, "INR", PriceSource.USER)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a currency that is not an ISO code is rejected`() {
        ItemPrice(100, "Rupees", PriceSource.USER)
    }
}
