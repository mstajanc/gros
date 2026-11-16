package io.github.mstajanc.gros.domain

/** Sumy sú vždy celé centy v `Long`. */
object Money {
    /** Najväčšia suma, ktorá sa dá zadať: 9 999 999,99 €. */
    const val MAX_CENTS: Long = 999_999_999L
}

enum class SignDisplay {
    /** Znamienko len pri záporných sumách. */
    NEGATIVE_ONLY,

    /** „+“ pri kladných, „−“ pri záporných, nula bez znamienka. */
    ALWAYS,

    /** Absolútna hodnota bez znamienka (napr. prevod). */
    NEVER,
}

/**
 * Jediné miesto, kde sa sumy menia na text: `1 234,56 €`.
 *
 * Píše sa ručne, aby výsledok nezávisel od `NumberFormat` ani od jazyka zariadenia:
 * nedeliteľná medzera medzi tisíckami a pred €, desatinná čiarka, znamienko mínus U+2212.
 */
object MoneyFormatter {

    private const val NBSP = ' '
    private const val MINUS = '−'

    fun format(cents: Long, sign: SignDisplay = SignDisplay.NEGATIVE_ONLY): String {
        // Cez ULong, aby absolútna hodnota nepretiekla ani pri Long.MIN_VALUE.
        val absolute = (if (cents < 0) -cents else cents).toULong()
        val euros = (absolute / 100u).toString()
        val fraction = (absolute % 100u).toString().padStart(2, '0')

        return buildString(euros.length + euros.length / 3 + 8) {
            when {
                sign == SignDisplay.NEVER || cents == 0L -> Unit
                cents < 0 -> append(MINUS)
                sign == SignDisplay.ALWAYS -> append('+')
            }
            euros.forEachIndexed { index, digit ->
                if (index > 0 && (euros.length - index) % 3 == 0) append(NBSP)
                append(digit)
            }
            append(',')
            append(fraction)
            append(NBSP)
            append('€')
        }
    }
}
