package com.example.freshtrack.domain.capture

import com.example.freshtrack.data.local.entities.DefaultCategories
import com.example.freshtrack.data.local.entities.LocationType

/**
 * A guess at how long something keeps, and what the guess was based on.
 *
 * [basis] exists so the guess can be shown for what it is. "Because it is milk,
 * kept in a fridge" is something a person can disagree with; a bare date with no
 * stated reason is something they can only accept or ignore.
 */
data class ShelfLifeEstimate(
    val days: Int,
    val basis: String,
    val confidence: Float
)

/**
 * Rough shelf life by what a thing is and where it is kept.
 *
 * The figures are the conservative end of the storage ranges published by the
 * USDA FoodKeeper guidance. They are a planning aid and nothing else: this
 * table cannot see the item, does not know whether it has been opened, and has
 * no idea how warm the kitchen is. Everything it produces is written back as
 * [com.example.freshtrack.domain.model.DateKind.ESTIMATED] from
 * [com.example.freshtrack.domain.model.DateSource.RULE] at a low confidence, so
 * a printed or user-confirmed date always outranks it and the UI is obliged to
 * label it.
 *
 * It is never a statement about safety. "Use soon" is the strongest thing the
 * product says, and deciding whether food is still good is the person's job,
 * not this table's.
 *
 * Storage matters as much as the food does — bread on a counter and bread in a
 * freezer differ by weeks — which is why [LocationType] is an input rather than
 * an afterthought.
 */
object ShelfLifeTable {

    /**
     * A named food and how long it tends to keep in each place.
     *
     * [otherwise] covers a storage type with no specific figure, and the case
     * where the item has not been given a location at all.
     */
    private data class Rule(
        val label: String,
        val keywords: List<String>,
        val fridge: Int? = null,
        val freezer: Int? = null,
        val pantry: Int? = null,
        val counter: Int? = null,
        val otherwise: Int
    )

    // Ordered most specific first only where it matters; matching picks the
    // longest keyword hit regardless, so "cream cheese" cannot lose to "cream".
    private val RULES = listOf(
        // Dairy and eggs
        Rule("milk", listOf("MILK"), fridge = 7, freezer = 90, otherwise = 7),
        Rule("yoghurt", listOf("YOGHURT", "YOGURT"), fridge = 14, otherwise = 14),
        Rule("cream cheese", listOf("CREAM CHEESE"), fridge = 14, otherwise = 14),
        Rule("soft cheese", listOf("BRIE", "CAMEMBERT", "RICOTTA", "COTTAGE CHEESE"), fridge = 14, otherwise = 14),
        Rule("hard cheese", listOf("CHEDDAR", "PARMESAN", "GOUDA", "CHEESE"), fridge = 28, freezer = 180, otherwise = 28),
        Rule("butter", listOf("BUTTER"), fridge = 30, freezer = 270, otherwise = 30),
        Rule("cream", listOf("SOUR CREAM", "CREAM"), fridge = 7, otherwise = 7),
        Rule("eggs", listOf("EGG", "EGGS"), fridge = 35, otherwise = 21),

        // Meat and fish
        Rule("raw poultry", listOf("CHICKEN", "TURKEY"), fridge = 2, freezer = 270, otherwise = 2),
        Rule("raw mince", listOf("MINCE", "GROUND BEEF", "GROUND PORK"), fridge = 2, freezer = 120, otherwise = 2),
        Rule("raw meat", listOf("BEEF", "PORK", "LAMB", "STEAK"), fridge = 4, freezer = 180, otherwise = 4),
        Rule("fish", listOf("SALMON", "COD", "TUNA", "HADDOCK", "PRAWN", "SHRIMP", "FISH"), fridge = 2, freezer = 180, otherwise = 2),
        Rule("bacon", listOf("BACON"), fridge = 7, freezer = 30, otherwise = 7),
        Rule("sliced meat", listOf("HAM", "SALAMI", "DELI", "SAUSAGE"), fridge = 5, freezer = 60, otherwise = 5),

        // Chilled prepared food. Short-dated whatever it is made of, which is
        // why these sit above the meat rules: "chicken tikka" is a ready meal
        // first and chicken second, and the longest-keyword rule below picks
        // "chicken tikka" over "chicken".
        Rule("ready meal", listOf("READY MEAL", "LASAGNE", "LASAGNA", "CHICKEN TIKKA", "TIKKA MASALA", "KORMA", "QUICHE", "PIZZA"), fridge = 3, freezer = 90, otherwise = 3),
        Rule("dip", listOf("HUMMUS", "HOUMOUS", "TZATZIKI", "GUACAMOLE", "COLESLAW"), fridge = 4, otherwise = 4),
        Rule("sandwich", listOf("SANDWICH", "SUSHI"), fridge = 1, otherwise = 1),
        Rule("fresh pasta", listOf("TORTELLINI", "RAVIOLI", "GNOCCHI", "FRESH PASTA"), fridge = 4, freezer = 90, otherwise = 4),

        // Bakery
        Rule("bread", listOf("BREAD", "LOAF", "BAGUETTE", "ROLL", "BUN", "BAGEL"), pantry = 5, counter = 5, fridge = 14, freezer = 90, otherwise = 5),
        Rule("cake", listOf("CAKE", "MUFFIN", "PASTRY", "CROISSANT"), pantry = 3, fridge = 7, otherwise = 3),

        // Fresh produce
        Rule("salad leaves", listOf("LETTUCE", "SALAD", "ROCKET", "ARUGULA"), fridge = 7, otherwise = 5),
        Rule("spinach", listOf("SPINACH", "KALE"), fridge = 5, otherwise = 4),
        Rule("berries", listOf("STRAWBERR", "RASPBERR", "BLUEBERR", "BERRY", "BERRIES"), fridge = 3, freezer = 180, otherwise = 3),
        Rule("bananas", listOf("BANANA"), counter = 5, otherwise = 5),
        Rule("apples", listOf("APPLE"), fridge = 30, counter = 7, otherwise = 14),
        Rule("citrus", listOf("LEMON", "LIME", "ORANGE", "GRAPEFRUIT"), fridge = 21, counter = 7, otherwise = 14),
        Rule("grapes", listOf("GRAPE"), fridge = 7, otherwise = 7),
        Rule("tomatoes", listOf("TOMATO"), counter = 5, fridge = 7, otherwise = 5),
        Rule("potatoes", listOf("POTATO"), pantry = 60, otherwise = 21),
        Rule("onions", listOf("ONION"), pantry = 30, otherwise = 21),
        Rule("carrots", listOf("CARROT"), fridge = 21, otherwise = 14),
        Rule("broccoli", listOf("BROCCOLI", "CAULIFLOWER"), fridge = 5, otherwise = 5),
        Rule("mushrooms", listOf("MUSHROOM"), fridge = 7, otherwise = 5),

        // Drinks
        Rule("juice", listOf("JUICE"), fridge = 7, pantry = 180, otherwise = 7),

        // Store cupboard
        Rule("dry goods", listOf("RICE", "PASTA", "FLOUR", "SUGAR", "LENTIL", "NOODLE"), pantry = 365, otherwise = 365),
        Rule("cereal", listOf("CEREAL", "OATS", "MUESLI", "GRANOLA"), pantry = 180, otherwise = 180),
        Rule("cooking oil", listOf("OIL"), pantry = 180, otherwise = 180),
        Rule("tinned food", listOf("TINNED", "CANNED", "TIN OF", "CAN OF"), pantry = 730, otherwise = 730)
    )

    /**
     * When nothing in [RULES] matches, fall back to the category.
     *
     * Much weaker, and scored as such. "Fresh Produce" covers both spinach and
     * potatoes, which keep for three days and two months respectively, so a
     * category-level number is barely better than a shrug.
     */
    private val BY_CATEGORY = mapOf(
        DefaultCategories.DAIRY.name to Rule("Dairy & Eggs", emptyList(), fridge = 10, otherwise = 7),
        // Poultry and fish keep two days, red meat four: three is the honest
        // middle for a cut the table could not name.
        DefaultCategories.MEAT_FISH.name to Rule("Meat & Fish", emptyList(), fridge = 3, freezer = 180, otherwise = 3),
        DefaultCategories.READY_MEALS.name to Rule("Ready Meals", emptyList(), fridge = 3, freezer = 90, otherwise = 3),
        DefaultCategories.BAKERY.name to Rule("Bakery", emptyList(), pantry = 5, counter = 5, fridge = 14, freezer = 90, otherwise = 5),
        DefaultCategories.FRESH_PRODUCE.name to Rule("Fresh Produce", emptyList(), fridge = 7, counter = 5, pantry = 14, otherwise = 5),
        DefaultCategories.BEVERAGES.name to Rule("Beverages", emptyList(), fridge = 7, pantry = 180, otherwise = 7),
        DefaultCategories.STORE_CUPBOARD.name to Rule("Store Cupboard", emptyList(), pantry = 365, fridge = 90, otherwise = 180),
        DefaultCategories.LEFTOVERS.name to Rule("Leftovers", emptyList(), fridge = 4, freezer = 90, otherwise = 4)
        // "Other" is deliberately absent. A category that means "we do not know
        // what this is" cannot support a guess about how long it keeps.
    )

    private const val NAMED_WITH_STORAGE = 0.5f
    private const val NAMED_ONLY = 0.4f
    private const val CATEGORY_ONLY = 0.3f

    /**
     * How long [name] is likely to keep, or null when there is no basis to say.
     *
     * Returning null is a real answer and the correct one for an unrecognised
     * item in an unhelpful category. An invented number would be indistinguish-
     * able, on screen, from one that came from somewhere.
     */
    fun estimate(
        name: String,
        category: String? = null,
        storage: LocationType? = null
    ): ShelfLifeEstimate? {
        val upper = name.uppercase()

        val named = RULES
            .flatMap { rule -> rule.keywords.map { rule to it } }
            .filter { (_, keyword) -> upper.contains(keyword) }
            .maxByOrNull { (_, keyword) -> keyword.length }
            ?.first

        if (named != null) {
            val forStorage = named.daysIn(storage)
            return ShelfLifeEstimate(
                days = forStorage ?: named.otherwise,
                basis = basisFor(named.label, storage, forStorage != null),
                confidence = if (forStorage != null) NAMED_WITH_STORAGE else NAMED_ONLY
            )
        }

        val byCategory = category?.let { BY_CATEGORY[it] } ?: return null
        val forStorage = byCategory.daysIn(storage)
        return ShelfLifeEstimate(
            days = forStorage ?: byCategory.otherwise,
            basis = basisFor(byCategory.label, storage, forStorage != null),
            confidence = CATEGORY_ONLY
        )
    }

    private fun Rule.daysIn(storage: LocationType?): Int? = when (storage) {
        LocationType.FRIDGE -> fridge
        LocationType.FREEZER -> freezer
        LocationType.PANTRY -> pantry
        LocationType.COUNTER -> counter
        LocationType.OTHER, null -> null
    }

    private fun basisFor(label: String, storage: LocationType?, storageUsed: Boolean): String =
        if (storageUsed && storage != null) {
            "$label kept in ${storage.name.lowercase().let { if (it == "counter") "the open" else "a $it" }}"
        } else {
            label
        }
}
