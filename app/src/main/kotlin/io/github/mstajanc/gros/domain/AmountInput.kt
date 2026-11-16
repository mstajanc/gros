package io.github.mstajanc.gros.domain

/**
 * Stav číselnej klávesnice appky. [text] je presne to, čo používateľ naťukal:
 * číslice s najviac jednou čiarkou a dvoma desatinnými miestami („42,3“).
 * Celá časť má najviac [MAX_WHOLE_DIGITS] číslic, takže suma nikdy neprekročí [Money.MAX_CENTS].
 */
data class AmountInput(val text: String = "") {

    private val wholePart: String get() = text.substringBefore(',')
    private val fraction: String? get() = if (',' in text) text.substringAfter(',') else null

    fun digit(digit: Int): AmountInput {
        require(digit in 0..9)
        val decimals = fraction
        return when {
            decimals != null -> if (decimals.length < 2) AmountInput(text + digit) else this
            text == "0" || text.isEmpty() -> AmountInput(digit.toString())
            wholePart.length >= MAX_WHOLE_DIGITS -> this
            else -> AmountInput(text + digit)
        }
    }

    fun comma(): AmountInput = when {
        fraction != null -> this
        text.isEmpty() -> AmountInput("0,")
        else -> AmountInput("$text,")
    }

    fun backspace(): AmountInput = AmountInput(text.dropLast(1))

    /** Hodnota v centoch; vždy nezáporná. */
    val cents: Long
        get() {
            val whole = wholePart.toLongOrNull() ?: 0L
            val decimals = (fraction ?: "").padEnd(2, '0').toLongOrNull() ?: 0L
            return whole * 100 + decimals
        }

    /** Text na zobrazenie bez znaku meny: tisícky oddelené nedeliteľnou medzerou, prázdny vstup ako „0“. */
    val display: String
        get() {
            val whole = wholePart.ifEmpty { "0" }
            val grouped = buildString {
                whole.forEachIndexed { index, digit ->
                    if (index > 0 && (whole.length - index) % 3 == 0) append(' ')
                    append(digit)
                }
            }
            return if (fraction == null) grouped else "$grouped,$fraction"
        }

    companion object {
        const val MAX_WHOLE_DIGITS = 7

        /** Predvyplnenie pri úprave; znamienko sa neprenáša. */
        fun fromCents(cents: Long): AmountInput {
            val absolute = if (cents < 0) -cents else cents
            val whole = absolute / 100
            val decimals = absolute % 100
            return when {
                absolute == 0L -> AmountInput()
                decimals == 0L -> AmountInput(whole.toString())
                else -> AmountInput("$whole,${decimals.toString().padStart(2, '0')}")
            }
        }
    }
}
