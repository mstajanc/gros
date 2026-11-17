package io.github.mstajanc.gros.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** Tvar podstatného mena po čísle: 1 transakcia, 2–4 transakcie, 0 a 5+ transakcií. */
enum class PluralForm { ONE, FEW, MANY }

object SlovakText {

    fun pluralForm(count: Int): PluralForm = when (count) {
        1 -> PluralForm.ONE
        in 2..4 -> PluralForm.FEW
        else -> PluralForm.MANY
    }

    /**
     * Predložka pred číslom zapísaným číslicami: „3 zo 4 účtov“, „2 z 5 účtov“.
     * „zo“ sa píše pred číslovkou začínajúcou na s, z, š alebo ž (štyri, šesť, sedem, sto…).
     */
    fun prepositionFrom(number: Int): String {
        val n = if (number < 0) -number else number
        val startsWithSibilant = when {
            n in 100..199 -> true // sto…
            n >= 100 -> prefixNeedsZo(n / 100) // štyristo, šesťsto, sedemsto
            n in 11..19 -> prefixNeedsZo(n - 10) // štrnásť, šestnásť, sedemnásť
            n >= 20 -> prefixNeedsZo(n / 10) // štyridsať, šesťdesiat, sedemdesiat
            else -> prefixNeedsZo(n)
        }
        return if (startsWithSibilant) "zo" else "z"
    }

    private fun prefixNeedsZo(digit: Int): Boolean = digit == 4 || digit == 6 || digit == 7
}

enum class RelativeDay { TODAY, YESTERDAY, OTHER }

/** Dátumy sa formátujú vždy po slovensky, nezávisle od jazyka zariadenia. */
object SlovakDates {

    private val weekdays = mapOf(
        DayOfWeek.MONDAY to "Pondelok",
        DayOfWeek.TUESDAY to "Utorok",
        DayOfWeek.WEDNESDAY to "Streda",
        DayOfWeek.THURSDAY to "Štvrtok",
        DayOfWeek.FRIDAY to "Piatok",
        DayOfWeek.SATURDAY to "Sobota",
        DayOfWeek.SUNDAY to "Nedeľa",
    )

    private val months = listOf(
        "január", "február", "marec", "apríl", "máj", "jún",
        "júl", "august", "september", "október", "november", "december",
    )

    fun relativeDay(date: LocalDate, today: LocalDate): RelativeDay = when (date) {
        today -> RelativeDay.TODAY
        today.minusDays(1) -> RelativeDay.YESTERDAY
        else -> RelativeDay.OTHER
    }

    /** „12. 11.“; rok sa pridá, len ak je iný než aktuálny. */
    fun shortDate(date: LocalDate, today: LocalDate): String =
        if (date.year == today.year) "${date.dayOfMonth}. ${date.monthValue}." else fullDate(date)

    /** „8. 11. 2026“ */
    fun fullDate(date: LocalDate): String = "${date.dayOfMonth}. ${date.monthValue}. ${date.year}"

    /** „Štvrtok 12. 11.“ – hlavička dňa v zozname transakcií. */
    fun weekdayDate(date: LocalDate, today: LocalDate): String =
        "${weekdays.getValue(date.dayOfWeek)} ${shortDate(date, today)}"

    /** „pondelok“ – deň v týždni malým písmenom. */
    fun weekdayName(date: LocalDate): String = weekdays.getValue(date.dayOfWeek).lowercase()

    /** „november“ */
    fun monthName(month: YearMonth): String = months[month.monthValue - 1]

    /** „November 2026“ */
    fun monthYear(month: YearMonth): String =
        "${monthName(month).replaceFirstChar { it.uppercaseChar() }} ${month.year}"
}
