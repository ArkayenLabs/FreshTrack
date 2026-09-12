package com.example.freshtrack.domain.capture

import com.example.freshtrack.data.local.entities.DefaultCategories

/**
 * One line of a receipt that looks like food someone bought.
 *
 * There is deliberately no date on this type. A receipt says when something was
 * bought, which is not when it goes off, and the two are only related through a
 * shelf-life estimate that has to be labelled as one. Giving this class a date
 * field would make it far too easy to quietly promote a purchase date into an
 * expiry, so the type refuses to carry one at all.
 */
data class ReceiptCandidate(
    /** Stable within one parse, so a review screen can key rows by it. */
    val candidateId: String,
    val name: String,
    val quantity: Double,
    val unit: String?,
    /** Minor units — paise, cents — to keep money off floating point. */
    val priceMinor: Int?,
    val categoryGuess: String?,
    val confidence: Float,
    val requiresReview: Boolean
)

/**
 * Everything a receipt yielded, including what it did not.
 *
 * [unresolvedLines] is the honest half. A line that looked like an item but
 * could not be read is kept and shown rather than dropped, because silently
 * losing a row is indistinguishable, to the person holding the receipt, from
 * the app deciding they did not buy it.
 */
data class ReceiptCandidates(
    val currency: String?,
    val items: List<ReceiptCandidate>,
    val unresolvedLines: List<String>
)

/**
 * Turns the text of a till receipt into candidate food rows.
 *
 * Deterministic and on-device. A model may later do better at the ambiguous
 * middle, but this is what runs when there is no network, no consent and no
 * budget, and it is the thing a model's output gets checked against.
 *
 * Nothing it produces is inventory. Every row is a candidate for review.
 */
object ReceiptParser {

    /** Contract limits: a receipt longer than this is truncated, not rejected. */
    private const val MAX_ITEMS = 200
    private const val MAX_UNRESOLVED = 200
    private const val MAX_NAME = 160

    /** Above this a row is not singled out for attention in review. */
    private const val CONFIDENT = 0.7f

    /**
     * Lines carrying payment or identity detail.
     *
     * These are dropped before anything else and are never kept, not even as an
     * unresolved line. A card number or a tax id is not something to retain
     * because it happened to be printed near the shopping, and `unresolvedLines`
     * is the only thing here that survives to be shown or stored.
     */
    private val SENSITIVE = listOf(
        "VISA", "MASTERCARD", "MAESTRO", "AMEX", "AMERICAN EXPRESS", "DISCOVER",
        "CREDIT", "DEBIT", "CARD", "CHIP", "CONTACTLESS", "PIN VERIFIED",
        "AID", "AUTH", "APPROVAL", "TERMINAL", "MERCHANT", "MID", "TID",
        "EBT", "SNAP", "ACCOUNT", "IBAN", "SORT CODE",
        "VAT NO", "VAT REG", "TAX ID", "EIN"
    )

    /** A masked card number in any of the usual shapes, plus long digit runs. */
    private val MASKED_PAN = Regex("""[X*•]{3,}\s*\d{3,}|\b\d{12,}\b""")
    private val EMAIL = Regex("""[\w.+-]+@[\w-]+\.[\w.]+""")
    private val PHONE = Regex("""\b(?:\+?\d[\d\s-]{8,})\b""")

    /** Real lines on a receipt that are simply not shopping. */
    private val NOT_AN_ITEM = listOf(
        "SUBTOTAL", "SUB TOTAL", "TOTAL", "GRAND TOTAL", "NET", "GROSS",
        "TAX", "SALES TAX", "VAT", "DISCOUNT", "SAVINGS", "SAVED", "COUPON",
        "ROUND", "AMOUNT DUE", "BALANCE", "CHANGE", "CASH", "TENDER",
        "TENDERED", "PAID", "REFUND", "VOID", "INVOICE", "RECEIPT", "ORDER NO",
        "CASHIER", "CHECKOUT", "TILL", "LANE", "STORE", "BRANCH", "THANK",
        "VISIT", "WELCOME", "CUSTOMER COPY", "WWW", "HTTP",
        "LOYALTY", "CLUBCARD", "NECTAR", "MEMBER", "POINTS", "REWARDS",
        "QTY", "ITEM", "DESCRIPTION", "PRICE", "RATE"
    )

    // Deliberately short. "$" is read as USD, which is right far more often
    // than not for this product's markets but is a real assumption: a Canadian
    // or Australian receipt prints the same symbol. Currency is only ever used
    // for display, never for arithmetic across currencies.
    private val CURRENCIES = mapOf(
        "$" to "USD", "USD" to "USD",
        "£" to "GBP", "GBP" to "GBP",
        "€" to "EUR", "EUR" to "EUR"
    )

    /**
     * A trailing amount, with or without a symbol in front of it.
     *
     * The symbol class is every currency sign rather than the handful this
     * product targets, so that an unexpected one is stripped off the price
     * instead of being left stuck to the end of the item's name.
     */
    private val TRAILING_PRICE =
        Regex("""\p{Sc}?\s*(\d{1,3}(?:,\d{3})*|\d+)(?:[.](\d{1,2}))?\s*$""")

    /** "2 x ", "3 @ ", "2 * " at the start of a line. */
    private val LEADING_COUNT = Regex("""^(\d{1,4})\s*[xX*@]\s*""")

    /** "1.5 KG ", "500 ML " at the start of a line. */
    private val LEADING_MEASURE =
        Regex("""^(\d+(?:\.\d+)?)\s*(KG|G|GM|GMS|L|ML|PC|PCS|PKT|DOZ)\b\s*""")

    /**
     * Keyword to category, using only the categories the product actually has.
     *
     * A guess, and marked as one. It exists to save typing in review, not to be
     * trusted — which is why a row with no guess is not treated as worse than a
     * row with one, only as needing a moment more attention.
     */
    private val CATEGORY_WORDS = listOf(
        // First match wins, so the chilled aisle goes first: "chicken salad
        // sandwich" is a sandwich before it is chicken or salad.
        DefaultCategories.READY_MEALS.name to listOf(
            "READY MEAL", "LASAGNE", "LASAGNA", "TIKKA", "KORMA", "MASALA",
            "QUICHE", "PIZZA", "HUMMUS", "HOUMOUS", "TZATZIKI", "GUACAMOLE",
            "COLESLAW", "SANDWICH", "SUSHI", "TORTELLINI", "RAVIOLI", "GNOCCHI"
        ),
        DefaultCategories.DAIRY.name to listOf(
            "MILK", "CHEESE", "CHEDDAR", "BRIE", "BUTTER", "YOGHURT", "YOGURT",
            "CREAM", "EGG", "EGGS"
        ),
        // No "HAM" or "COD": three letters is too few for a substring match
        // on a till line, and champagne is not a meat.
        DefaultCategories.MEAT_FISH.name to listOf(
            "CHICKEN", "TURKEY", "BEEF", "PORK", "LAMB", "STEAK", "MINCE",
            "SAUSAGE", "BACON", "SALAMI", "SALMON", "TUNA", "HADDOCK",
            "PRAWN", "SHRIMP", "FISH"
        ),
        DefaultCategories.BAKERY.name to listOf(
            "BREAD", "LOAF", "BAGUETTE", "BUN", "ROLL", "BAGEL", "CROISSANT",
            "CAKE", "MUFFIN", "SCONE", "PASTRY", "BISCUIT", "COOKIE"
        ),
        DefaultCategories.BEVERAGES.name to listOf(
            "JUICE", "WATER", "COLA", "SODA", "LEMONADE", "SQUASH", "TEA",
            "COFFEE", "BEER", "WINE", "CIDER", "DRINK"
        ),
        DefaultCategories.FRESH_PRODUCE.name to listOf(
            "TOMATO", "ONION", "POTATO", "APPLE", "BANANA", "SPINACH", "KALE",
            "CARROT", "CUCUMBER", "LEMON", "LIME", "ORANGE", "GRAPE", "BERRY",
            "BERRIES", "STRAWBERR", "LETTUCE", "SALAD", "BROCCOLI", "PEPPER",
            "COURGETTE", "ZUCCHINI", "AUBERGINE", "EGGPLANT", "PARSNIP",
            "CELERY", "MUSHROOM", "AVOCADO", "CORIANDER", "CILANTRO"
        ),
        DefaultCategories.STORE_CUPBOARD.name to listOf(
            "RICE", "PASTA", "NOODLE", "FLOUR", "SUGAR", "SALT", "OIL",
            "CEREAL", "OATS", "GRANOLA", "HONEY", "LENTIL", "BEANS", "TINNED",
            "CANNED"
        )
    )

    fun parse(text: String): ReceiptCandidates {
        val lines = text.lines()
            .map { it.replace(Regex("""\p{Cntrl}"""), " ").trim() }
            .filter { it.isNotBlank() }

        val currency = detectCurrency(lines)
        val items = mutableListOf<ReceiptCandidate>()
        val unresolved = mutableListOf<String>()

        lines.forEachIndexed { index, line ->
            val upper = line.uppercase()

            // Order matters: sensitive first, so a card line that happens to
            // carry a trailing amount can never be read as a purchase.
            if (isSensitive(upper)) return@forEachIndexed
            if (isNotAnItem(upper)) return@forEachIndexed

            val candidate = readItem(line, "line-$index")
            if (candidate != null) {
                if (items.size < MAX_ITEMS) items += candidate
            } else if (looksLikeShopping(line) && unresolved.size < MAX_UNRESOLVED) {
                unresolved += line.take(240)
            }
        }

        return ReceiptCandidates(currency, items, unresolved)
    }

    private fun isSensitive(upper: String): Boolean =
        SENSITIVE.any { upper.contains(it) } ||
            MASKED_PAN.containsMatchIn(upper) ||
            EMAIL.containsMatchIn(upper) ||
            PHONE.containsMatchIn(upper)

    private fun isNotAnItem(upper: String): Boolean =
        NOT_AN_ITEM.any { upper.contains(it) }

    /**
     * A line with words in it, which is the only reason to keep something that
     * could not be parsed. A row of separators or a bare number is noise.
     */
    private fun looksLikeShopping(line: String): Boolean =
        line.count { it.isLetter() } >= 3

    private fun readItem(line: String, id: String): ReceiptCandidate? {
        val priceMatch = TRAILING_PRICE.find(line) ?: return null
        val priceMinor = toMinorUnits(priceMatch.groupValues[1], priceMatch.groupValues[2])

        var rest = line.removeRange(priceMatch.range).trim().trimEnd('-', '.', ':')
        if (rest.count { it.isLetter() } < 2) return null

        var quantity = 1.0
        var unit: String? = null

        LEADING_MEASURE.find(rest)?.let { measure ->
            quantity = measure.groupValues[1].toDouble()
            unit = measure.groupValues[2].uppercase()
            rest = rest.removeRange(measure.range).trim()
        } ?: LEADING_COUNT.find(rest)?.let { count ->
            quantity = count.groupValues[1].toDouble()
            rest = rest.removeRange(count.range).trim()
        }

        val name = rest.take(MAX_NAME).trim()
        if (name.isEmpty() || quantity <= 0.0) return null

        val category = guessCategory(name.uppercase())
        val confidence = when {
            priceMinor != null && category != null -> 0.7f
            priceMinor != null -> 0.55f
            else -> 0.4f
        }

        return ReceiptCandidate(
            candidateId = id,
            name = name,
            quantity = quantity,
            unit = unit,
            priceMinor = priceMinor,
            categoryGuess = category,
            confidence = confidence,
            // The whole sheet is reviewed before anything is saved. This marks
            // the rows worth looking hardest at, not the ones that need looking
            // at at all — that is every one of them.
            requiresReview = confidence < CONFIDENT
        )
    }

    private fun toMinorUnits(whole: String, fraction: String): Int? {
        val units = whole.replace(",", "").toLongOrNull() ?: return null
        val minor = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 10
            else -> fraction.toLong()
        }
        val total = units * 100 + minor
        return if (total in 0..Int.MAX_VALUE) total.toInt() else null
    }

    private fun guessCategory(upperName: String): String? =
        CATEGORY_WORDS.firstOrNull { (_, words) ->
            words.any { upperName.contains(it) }
        }?.first

    private fun detectCurrency(lines: List<String>): String? {
        val joined = lines.joinToString(" ").uppercase()
        return CURRENCIES.entries.firstOrNull { joined.contains(it.key) }?.value
    }
}
