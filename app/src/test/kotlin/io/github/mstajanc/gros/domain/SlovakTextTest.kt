package io.github.mstajanc.gros.domain

import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class SlovakTextTest {

    private val today = LocalDate.of(2026, 11, 15)

    @Test
    fun `plural form follows Slovak rules`() {
        assertEquals(PluralForm.ONE, SlovakText.pluralForm(1))
        assertEquals(listOf(PluralForm.FEW, PluralForm.FEW, PluralForm.FEW), listOf(2, 3, 4).map(SlovakText::pluralForm))
        assertEquals(PluralForm.MANY, SlovakText.pluralForm(0))
        assertEquals(PluralForm.MANY, SlovakText.pluralForm(5))
        assertEquals(PluralForm.MANY, SlovakText.pluralForm(21))
        assertEquals(PluralForm.MANY, SlovakText.pluralForm(104))
    }

    @Test
    fun `preposition before a number is z or zo`() {
        // „3 zo 4 účtov“, „2 z 5 účtov“
        assertEquals("zo", SlovakText.prepositionFrom(4))
        assertEquals("z", SlovakText.prepositionFrom(5))
        assertEquals("zo", SlovakText.prepositionFrom(6))
        assertEquals("zo", SlovakText.prepositionFrom(7))
        assertEquals("z", SlovakText.prepositionFrom(1))
        assertEquals("z", SlovakText.prepositionFrom(2))
        assertEquals("z", SlovakText.prepositionFrom(3))
        assertEquals("z", SlovakText.prepositionFrom(8))
        assertEquals("z", SlovakText.prepositionFrom(10))
        assertEquals("zo", SlovakText.prepositionFrom(14))
        assertEquals("zo", SlovakText.prepositionFrom(16))
        assertEquals("zo", SlovakText.prepositionFrom(17))
        assertEquals("z", SlovakText.prepositionFrom(20))
        assertEquals("zo", SlovakText.prepositionFrom(40))
        assertEquals("zo", SlovakText.prepositionFrom(65))
        assertEquals("zo", SlovakText.prepositionFrom(100))
    }

    @Test
    fun `relative day recognises today and yesterday`() {
        assertEquals(RelativeDay.TODAY, SlovakDates.relativeDay(today, today))
        assertEquals(RelativeDay.YESTERDAY, SlovakDates.relativeDay(today.minusDays(1), today))
        assertEquals(RelativeDay.OTHER, SlovakDates.relativeDay(today.minusDays(2), today))
        assertEquals(RelativeDay.OTHER, SlovakDates.relativeDay(today.plusDays(1), today))
    }

    @Test
    fun `dates are always formatted in Slovak`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("Štvrtok 12. 11.", SlovakDates.weekdayDate(LocalDate.of(2026, 11, 12), today))
            assertEquals("Utorok 10. 11.", SlovakDates.weekdayDate(LocalDate.of(2026, 11, 10), today))
            assertEquals("Streda 31. 12. 2025", SlovakDates.weekdayDate(LocalDate.of(2025, 12, 31), today))
            assertEquals("12. 11.", SlovakDates.shortDate(LocalDate.of(2026, 11, 12), today))
            assertEquals("3. 9. 2025", SlovakDates.shortDate(LocalDate.of(2025, 9, 3), today))
            assertEquals("8. 11. 2026", SlovakDates.fullDate(LocalDate.of(2026, 11, 8)))
            assertEquals("November 2026", SlovakDates.monthYear(YearMonth.of(2026, 11)))
            assertEquals("Február 2027", SlovakDates.monthYear(YearMonth.of(2027, 2)))
            assertEquals("november", SlovakDates.monthName(YearMonth.of(2026, 11)))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `note suggestions start at two characters`() {
        assertNull(NoteSuggestions.pattern(""))
        assertNull(NoteSuggestions.pattern(" a "))
        assertEquals("%al%", NoteSuggestions.pattern("Al"))
        assertEquals("%lidl 50\\%%", NoteSuggestions.pattern(" Lidl 50% "))
        assertEquals(5, NoteSuggestions.LIMIT)
    }
}
