package io.github.mstajanc.gros.domain

import io.github.mstajanc.gros.domain.model.Frequency
import io.github.mstajanc.gros.domain.model.RecurringRule
import io.github.mstajanc.gros.domain.model.TransactionType
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

private fun d(year: Int, month: Int, day: Int): LocalDate = LocalDate.of(year, month, day)

class RecurrenceTest {

    private fun series(anchor: LocalDate, frequency: Frequency, interval: Int, count: Int): List<LocalDate> {
        val schedule = Schedule(anchor, frequency, interval)
        return generateSequence(anchor) { schedule.nextAfter(it) }.take(count).toList()
    }

    @Test
    fun `monthly payment from the 31st falls on the last day of shorter months and returns to the 31st`() {
        assertEquals(
            listOf(d(2026, 1, 31), d(2026, 2, 28), d(2026, 3, 31), d(2026, 4, 30), d(2026, 5, 31)),
            series(d(2026, 1, 31), Frequency.MONTHLY, 1, 5),
        )
    }

    @Test
    fun `yearly payment from the 29th of February uses the 28th in non-leap years`() {
        assertEquals(
            listOf(d(2028, 2, 29), d(2029, 2, 28), d(2030, 2, 28), d(2031, 2, 28), d(2032, 2, 29)),
            series(d(2028, 2, 29), Frequency.YEARLY, 1, 5),
        )
    }

    @Test
    fun `interval multiplies the period`() {
        assertEquals(
            listOf(d(2026, 11, 30), d(2027, 2, 28), d(2027, 5, 30), d(2027, 8, 30)),
            series(d(2026, 11, 30), Frequency.MONTHLY, 3, 4),
        )
        assertEquals(
            listOf(d(2026, 11, 13), d(2026, 11, 27), d(2026, 12, 11)),
            series(d(2026, 11, 13), Frequency.WEEKLY, 2, 3),
        )
        assertEquals(listOf(d(2026, 3, 3), d(2028, 3, 3)), series(d(2026, 3, 3), Frequency.YEARLY, 2, 2))
    }

    @Test
    fun `next occurrence is computed from the anchor not from a shifted date`() {
        val schedule = Schedule(d(2026, 1, 31), Frequency.MONTHLY, 1)
        // Aj keď je aktuálny výskyt posunutý na 28. 2., ďalší je znova 31.
        assertEquals(d(2026, 3, 31), schedule.nextAfter(d(2026, 2, 28)))
        // Dátum medzi výskytmi vráti nasledujúci výskyt radu.
        assertEquals(d(2026, 3, 31), schedule.nextAfter(d(2026, 3, 5)))
        // Pred kotvou je prvým výskytom samotná kotva.
        assertEquals(d(2026, 1, 31), schedule.nextAfter(d(2025, 12, 1)))
    }

    @Test
    fun `first occurrence on or after a date`() {
        val schedule = Schedule(d(2026, 1, 31), Frequency.MONTHLY, 1)
        assertEquals(d(2026, 6, 30), schedule.firstOnOrAfter(d(2026, 6, 30)))
        assertEquals(d(2026, 7, 31), schedule.firstOnOrAfter(d(2026, 7, 1)))
        assertEquals(d(2026, 1, 31), schedule.firstOnOrAfter(d(2020, 1, 1)))
    }

    @Test
    fun `far dates are reached without walking the whole series`() {
        val schedule = Schedule(d(2000, 1, 3), Frequency.WEEKLY, 1)
        assertEquals(d(2099, 12, 28), schedule.firstOnOrAfter(d(2099, 12, 25)))
    }

    @Test
    fun `occurrences due up to today respect the inclusive end date`() {
        val schedule = Schedule(d(2026, 8, 15), Frequency.MONTHLY, 1)
        assertEquals(
            listOf(d(2026, 8, 15), d(2026, 9, 15), d(2026, 10, 15), d(2026, 11, 15)),
            schedule.dueDates(from = d(2026, 8, 15), today = d(2026, 11, 16), endDate = null),
        )
        assertEquals(
            listOf(d(2026, 8, 15), d(2026, 9, 15), d(2026, 10, 15)),
            schedule.dueDates(from = d(2026, 8, 15), today = d(2026, 11, 16), endDate = d(2026, 10, 15)),
        )
        assertTrue(schedule.dueDates(from = d(2026, 12, 15), today = d(2026, 11, 16), endDate = null).isEmpty())
    }
}

class RuleStateTest {

    private fun rule(nextDate: LocalDate, endDate: LocalDate? = null, paused: Boolean = false, interval: Int = 1) =
        RecurringRule(
            id = 1,
            name = "Nájom",
            type = TransactionType.EXPENSE,
            amountCents = 52_000,
            accountId = 1,
            categoryId = 1,
            frequency = Frequency.MONTHLY,
            interval = interval,
            anchorDate = d(2026, 1, 1),
            nextDate = nextDate,
            endDate = endDate,
            paused = paused,
        )

    @Test
    fun `status is active paused or finished`() {
        assertEquals(RuleStatus.ACTIVE, RuleState.status(rule(d(2026, 12, 1))))
        assertEquals(RuleStatus.ACTIVE, RuleState.status(rule(d(2026, 12, 1), endDate = d(2026, 12, 1))))
        assertEquals(RuleStatus.PAUSED, RuleState.status(rule(d(2026, 12, 1), paused = true)))
        assertEquals(RuleStatus.FINISHED, RuleState.status(rule(d(2027, 1, 1), endDate = d(2026, 12, 31))))
        // Ukončené má prednosť pred pozastaveným.
        assertEquals(RuleStatus.FINISHED, RuleState.status(rule(d(2027, 1, 1), endDate = d(2026, 12, 31), paused = true)))
    }

    @Test
    fun `resume skips missed occurrences`() {
        val today = d(2026, 11, 17)
        val resumed = RuleState.resume(rule(d(2026, 8, 1), paused = true), today)
        assertFalse(resumed.paused)
        assertEquals(d(2026, 12, 1), resumed.nextDate)
        // Výskyt presne dnes sa nevynechá.
        assertEquals(d(2026, 11, 17), RuleState.resume(rule(d(2026, 6, 17), paused = true).copy(anchorDate = d(2026, 1, 17)), today).nextDate)
        // Budúci dátum ostáva.
        assertEquals(d(2027, 1, 1), RuleState.resume(rule(d(2027, 1, 1), paused = true), today).nextDate)
    }

    @Test
    fun `monthly summary matches mockup 06`() {
        fun r(type: TransactionType, cents: Long, frequency: Frequency, interval: Int = 1, paused: Boolean = false) =
            rule(d(2026, 12, 1), paused = paused, interval = interval)
                .copy(type = type, amountCents = cents, frequency = frequency)

        val summary = RuleState.monthlySummary(
            listOf(
                r(TransactionType.EXPENSE, 52_000, Frequency.MONTHLY),
                r(TransactionType.EXPENSE, 1_990, Frequency.MONTHLY),
                r(TransactionType.EXPENSE, 28_600, Frequency.YEARLY),
                r(TransactionType.INCOME, 290_000, Frequency.MONTHLY),
                r(TransactionType.TRANSFER, 30_000, Frequency.MONTHLY),
                r(TransactionType.EXPENSE, 99_999, Frequency.MONTHLY, paused = true),
            ),
        )
        assertEquals(MonthlySummary(expenseCents = 56_373, incomeCents = 290_000), summary)
    }

    @Test
    fun `monthly summary converts weeks and intervals exactly and rounds once`() {
        fun r(cents: Long, frequency: Frequency, interval: Int) =
            rule(d(2026, 12, 1), interval = interval).copy(amountCents = cents, frequency = frequency)

        // 10 € týždenne = 43,333… €; každé 2 týždne = 21,666… €; spolu 65 € presne.
        assertEquals(6_500, RuleState.monthlySummary(listOf(r(1_000, Frequency.WEEKLY, 1), r(1_000, Frequency.WEEKLY, 2))).expenseCents)
        // Každé 3 mesiace 100 € = 33,33 €.
        assertEquals(3_333, RuleState.monthlySummary(listOf(r(10_000, Frequency.MONTHLY, 3))).expenseCents)
        // Ukončené pravidlo sa nepočíta.
        val finished = r(10_000, Frequency.MONTHLY, 1).copy(endDate = d(2026, 11, 30))
        assertEquals(0, RuleState.monthlySummary(listOf(finished)).expenseCents)
    }
}

class RuleEditingTest {

    private val today = d(2026, 11, 17)

    private val input = RuleInput(
        name = " Nájom ",
        type = TransactionType.EXPENSE,
        amountCents = 52_000,
        accountId = 1,
        categoryId = 10,
        frequency = Frequency.MONTHLY,
        interval = 1,
        nextDate = d(2026, 12, 1),
        endDate = null,
    )

    private val existing = RecurringRule(
        id = 5,
        name = "Nájom",
        type = TransactionType.EXPENSE,
        amountCents = 50_000,
        accountId = 1,
        categoryId = 10,
        frequency = Frequency.MONTHLY,
        interval = 1,
        anchorDate = d(2026, 1, 31),
        nextDate = d(2026, 11, 30),
        endDate = null,
        paused = false,
    )

    @Test
    fun `new rule is anchored at its next payment`() {
        val draft = RuleEditing.newRule(input)
        assertEquals(d(2026, 12, 1), draft.anchorDate)
        assertEquals(d(2026, 12, 1), draft.nextDate)
        assertFalse(draft.paused)
    }

    @Test
    fun `editing other fields keeps the anchor`() {
        val edited = RuleEditing.applyEdit(existing, input.copy(amountCents = 54_000, nextDate = existing.nextDate), today)
        assertEquals(d(2026, 1, 31), edited.anchorDate)
        assertEquals(d(2026, 11, 30), edited.nextDate)
        assertEquals(54_000, edited.amountCents)
    }

    @Test
    fun `changing next payment frequency or interval re-anchors the series`() {
        assertEquals(d(2026, 12, 1), RuleEditing.applyEdit(existing, input, today).anchorDate)
        val newFrequency = input.copy(nextDate = existing.nextDate, frequency = Frequency.YEARLY)
        assertEquals(d(2026, 11, 30), RuleEditing.applyEdit(existing, newFrequency, today).anchorDate)
        val newInterval = input.copy(nextDate = existing.nextDate, interval = 2)
        assertEquals(d(2026, 11, 30), RuleEditing.applyEdit(existing, newInterval, today).anchorDate)
    }

    @Test
    fun `paused flag survives an edit`() {
        assertTrue(RuleEditing.applyEdit(existing.copy(paused = true), input, today).paused)
    }

    @Test
    fun `extending a finished rule skips missed occurrences`() {
        val finished = existing.copy(nextDate = d(2026, 7, 31), endDate = d(2026, 6, 30))
        val extended = RuleEditing.applyEdit(finished, input.copy(nextDate = finished.nextDate, endDate = null), today)
        assertEquals(d(2026, 11, 30), extended.nextDate)
        assertEquals(d(2026, 1, 31), extended.anchorDate)
    }

    @Test
    fun `end date before the start is rejected`() {
        assertTrue(RuleEditing.isEndValid(input))
        assertTrue(RuleEditing.isEndValid(input.copy(endDate = d(2026, 12, 1))))
        assertFalse(RuleEditing.isEndValid(input.copy(endDate = d(2026, 11, 30))))
    }

    @Test
    fun `backfill count is asked for a new or moved next payment in the past`() {
        assertEquals(0, RuleEditing.backfillCount(null, input, today))
        // Dnešná platba sa zapíše bez otázky (formulár to uvádza pri poli).
        assertEquals(0, RuleEditing.backfillCount(null, input.copy(nextDate = today), today))
        assertEquals(2, RuleEditing.backfillCount(null, input.copy(nextDate = d(2026, 10, 17)), today))
        assertEquals(3, RuleEditing.backfillCount(null, input.copy(nextDate = d(2026, 9, 17)), today))
        // Koniec obmedzí počet.
        assertEquals(2, RuleEditing.backfillCount(null, input.copy(nextDate = d(2026, 9, 17), endDate = d(2026, 10, 17)), today))
        // Úprava bez posunu dátumu sa nepýta.
        assertEquals(0, RuleEditing.backfillCount(existing.copy(nextDate = d(2026, 9, 17)), input.copy(nextDate = d(2026, 9, 17)), today))
        // Posun do minulosti pri úprave sa pýta.
        assertEquals(2, RuleEditing.backfillCount(existing, input.copy(nextDate = d(2026, 10, 1)), today))
        // Pozastavené pravidlo nič nezapíše.
        assertEquals(0, RuleEditing.backfillCount(existing.copy(paused = true), input.copy(nextDate = d(2026, 10, 1)), today))
    }
}
