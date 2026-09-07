package com.example.freshtrack.domain.model

/**
 * Where a price came from. Only [USER] and [RECEIPT] are observed facts; an
 * [ESTIMATED] price is our arithmetic and must never be presented as money the
 * user actually spent.
 */
enum class PriceSource {
    USER,
    RECEIPT,
    ESTIMATED;

    /** Whether this price can back a "you saved X" claim without qualification. */
    val isVerified: Boolean get() = this == USER || this == RECEIPT
}

/**
 * What one unit of an item cost.
 *
 * Held in minor units (paise, cents) as an integer because money in a float is
 * a rounding bug waiting to be summed over a year of groceries.
 *
 * The point of tracking provenance here is the Progress screen: item counts and
 * money are separate claims, and a total is only allowed to be stated as fact
 * when every contributing price was observed rather than inferred.
 */
data class ItemPrice(
    val minorUnits: Long,
    /** ISO 4217, e.g. "INR". */
    val currency: String,
    val source: PriceSource
) {
    init {
        require(minorUnits >= 0) { "price cannot be negative, was $minorUnits" }
        require(currency.length == 3) { "currency must be an ISO 4217 code, was '$currency'" }
    }

    val isVerified: Boolean get() = source.isVerified

    /** Total for [quantity] units. */
    fun times(quantity: Int): Long {
        require(quantity >= 0) { "quantity cannot be negative, was $quantity" }
        return minorUnits * quantity
    }
}
