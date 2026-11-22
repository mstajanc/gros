package io.github.mstajanc.gros.data

import io.github.mstajanc.gros.data.db.toDomain
import io.github.mstajanc.gros.domain.RuleInput
import io.github.mstajanc.gros.domain.model.Frequency
import io.github.mstajanc.gros.domain.model.Transaction
import io.github.mstajanc.gros.domain.model.TransactionDraft
import io.github.mstajanc.gros.domain.model.TransactionType
import io.github.mstajanc.gros.domain.rules.Violation
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

private fun d(year: Int, month: Int, day: Int): LocalDate = LocalDate.of(year, month, day)

class RecurringGeneratorTest : DatabaseTest() {

    private suspend fun ruleInput(
        nextDate: LocalDate,
        frequency: Frequency = Frequency.MONTHLY,
        interval: Int = 1,
        endDate: LocalDate? = null,
        name: String = "Nájom",
        cents: Long = 52_000,
    ): RuleInput = RuleInput(
        name = name,
        type = TransactionType.EXPENSE,
        amountCents = cents,
        accountId = account(),
        // Názvy kategórií sú jedinečné (G1); pri ďalšom pravidle sa použije už vytvorená.
        categoryId = categories.observeCategories().first().firstOrNull { it.name == "Bývanie" }?.id
            ?: expenseCategory("Bývanie"),
        frequency = frequency,
        interval = interval,
        nextDate = nextDate,
        endDate = endDate,
    )

    private suspend fun generated(): List<Transaction> =
        db.transactionDao().getAll().map { it.toDomain() }.sortedBy { it.date }

    @Test
    fun `new rule due today writes one transaction with the rule name as note`() = runTest {
        val ruleId = rules.create(ruleInput(nextDate = today))

        assertEquals(1, generator.run())

        val tx = generated().single()
        assertEquals(today, tx.date)
        assertEquals(52_000, tx.amountCents)
        assertEquals("Nájom", tx.note)
        assertEquals("najom", tx.noteSearch)
        assertEquals(ruleId, tx.recurringRuleId)
        assertEquals(today.plusMonths(1), rules.get(ruleId)!!.nextDate)
    }

    @Test
    fun `three missed months are caught up`() = runTest {
        clock.setDate(d(2026, 8, 10))
        val ruleId = rules.create(ruleInput(nextDate = d(2026, 8, 15)))
        clock.setDate(d(2026, 11, 16))

        assertEquals(4, generator.run())

        assertEquals(listOf(d(2026, 8, 15), d(2026, 9, 15), d(2026, 10, 15), d(2026, 11, 15)), generated().map { it.date })
        assertEquals(d(2026, 12, 15), rules.get(ruleId)!!.nextDate)
    }

    @Test
    fun `monthly rule from the 31st and the 29th of February`() = runTest {
        clock.setDate(d(2026, 1, 1))
        val endOfMonth = rules.create(ruleInput(nextDate = d(2026, 1, 31), name = "Koniec mesiaca"))
        val leap = rules.create(ruleInput(nextDate = d(2028, 2, 29), frequency = Frequency.YEARLY, name = "Prestupný"))
        clock.setDate(d(2026, 5, 31))

        generator.run()
        assertEquals(
            listOf(d(2026, 1, 31), d(2026, 2, 28), d(2026, 3, 31), d(2026, 4, 30), d(2026, 5, 31)),
            generated().filter { it.recurringRuleId == endOfMonth }.map { it.date },
        )

        clock.setDate(d(2031, 3, 1))
        generator.run()
        assertEquals(
            listOf(d(2028, 2, 29), d(2029, 2, 28), d(2030, 2, 28), d(2031, 2, 28)),
            generated().filter { it.recurringRuleId == leap }.map { it.date },
        )
        assertEquals(d(2032, 2, 29), rules.get(leap)!!.nextDate)
    }

    @Test
    fun `three month interval`() = runTest {
        clock.setDate(d(2026, 1, 1))
        rules.create(ruleInput(nextDate = d(2026, 1, 15), interval = 3))
        clock.setDate(d(2026, 12, 31))

        generator.run()

        assertEquals(listOf(d(2026, 1, 15), d(2026, 4, 15), d(2026, 7, 15), d(2026, 10, 15)), generated().map { it.date })
    }

    @Test
    fun `occurrence on the end date is still written and then the rule is finished`() = runTest {
        clock.setDate(d(2026, 9, 1))
        val ruleId = rules.create(ruleInput(nextDate = d(2026, 9, 15), endDate = d(2026, 11, 15)))
        clock.setDate(d(2027, 2, 1))

        generator.run()
        generator.run()

        assertEquals(listOf(d(2026, 9, 15), d(2026, 10, 15), d(2026, 11, 15)), generated().map { it.date })
        assertEquals(d(2026, 12, 15), rules.get(ruleId)!!.nextDate)
    }

    @Test
    fun `running twice or concurrently creates no duplicates`() = runTest {
        clock.setDate(d(2026, 6, 1))
        rules.create(ruleInput(nextDate = d(2026, 6, 1)))
        clock.setDate(d(2026, 11, 17))

        val results = listOf(async { generator.run() }, async { generator.run() }).awaitAll()
        generator.run()

        assertEquals(6, results.sum())
        assertEquals(6, generated().size)
        assertEquals(6, generated().map { it.date }.toSet().size)
    }

    @Test
    fun `paused rule writes nothing`() = runTest {
        val ruleId = rules.create(ruleInput(nextDate = today))
        rules.setPaused(ruleId, true)

        assertEquals(0, generator.run())
        assertTrue(generated().isEmpty())
    }

    @Test
    fun `moving or deleting a generated transaction neither blocks nor repeats the next occurrence`() = runTest {
        clock.setDate(d(2026, 10, 1))
        val ruleId = rules.create(ruleInput(nextDate = d(2026, 10, 1)))
        generator.run()
        val first = generated().single()

        // Zmena dátumu vygenerovanej transakcie.
        transactions.update(
            first.id,
            TransactionDraft(first.type, first.amountCents, d(2026, 10, 5), first.accountId, categoryId = first.categoryId, note = first.note),
        )
        generator.run()
        assertEquals(1, generated().size)

        // Zmazanie vygenerovanej transakcie výskyt nezopakuje.
        transactions.delete(first.id)
        generator.run()
        assertTrue(generated().isEmpty())

        // Ďalší výskyt príde normálne.
        clock.setDate(d(2026, 11, 1))
        generator.run()
        assertEquals(listOf(d(2026, 11, 1)), generated().map { it.date })
        assertEquals(d(2026, 12, 1), rules.get(ruleId)!!.nextDate)
    }

    @Test
    fun `resume skips missed occurrences and edit re-anchors`() = runTest {
        clock.setDate(d(2026, 6, 10))
        val input = ruleInput(nextDate = d(2026, 7, 10))
        val ruleId = rules.create(input)
        rules.setPaused(ruleId, true)
        clock.setDate(d(2026, 11, 17))

        rules.setPaused(ruleId, false)
        assertEquals(d(2026, 12, 10), rules.get(ruleId)!!.nextDate)
        assertEquals(0, generator.run())

        rules.edit(ruleId, input.copy(nextDate = d(2026, 12, 31), amountCents = 54_000))
        val edited = rules.get(ruleId)!!
        assertEquals(d(2026, 12, 31), edited.anchorDate)
        assertEquals(54_000, edited.amountCents)
        assertFalse(edited.paused)
    }

    @Test
    fun `rule cannot end before it starts or be resumed on an archived account`() = runTest {
        val input = ruleInput(nextDate = d(2026, 12, 1))
        assertViolation(Violation.END_BEFORE_START) { rules.create(input.copy(endDate = d(2026, 11, 30))) }

        val ruleId = rules.create(input)
        account("Druhý")
        accounts.archive(input.accountId)
        assertTrue(rules.get(ruleId)!!.paused)
        assertViolation(Violation.ACCOUNT_ARCHIVED) { rules.setPaused(ruleId, false) }
    }

    @Test
    fun `archiving an account pauses its active rules and reports them`() = runTest {
        val checking = account("Bežný účet")
        val savings = account("Sporenie")
        account("Hotovosť")
        val housing = expenseCategory("Bývanie")
        val rent = rules.create(ruleInput(nextDate = d(2026, 12, 1)).copy(accountId = checking, categoryId = housing))
        val saving = rules.create(
            RuleInput(
                name = "Sporenie", type = TransactionType.TRANSFER, amountCents = 30_000, accountId = checking,
                toAccountId = savings, frequency = Frequency.MONTHLY, interval = 1, nextDate = d(2026, 12, 15), endDate = null,
            ),
        )
        val other = rules.create(ruleInput(nextDate = d(2026, 12, 1), name = "Iné"))

        assertEquals(listOf("Sporenie"), accounts.activeRulesUsing(savings))
        assertEquals(listOf("Sporenie"), accounts.archive(savings))

        assertTrue(rules.get(saving)!!.paused)
        assertFalse(rules.get(rent)!!.paused)
        assertFalse(rules.get(other)!!.paused)
    }

    @Test
    fun `archiving a main category pauses rules in its subcategories too`() = runTest {
        val accountId = account()
        expenseCategory("Potraviny")
        val car = expenseCategory("Auto", color = 2)
        val insurance = expenseCategory("Poistenie", parentId = car)
        val ruleId = rules.create(
            RuleInput(
                name = "Poistenie auta", type = TransactionType.EXPENSE, amountCents = 28_600, accountId = accountId,
                categoryId = insurance, frequency = Frequency.YEARLY, interval = 1, nextDate = d(2027, 3, 3), endDate = null,
            ),
        )

        assertEquals(listOf("Poistenie auta"), categories.archive(car))
        assertTrue(rules.get(ruleId)!!.paused)
        // Obnovenie pravidla v archivovanej kategórii nie je možné.
        assertViolation(Violation.CATEGORY_ARCHIVED) { rules.setPaused(ruleId, false) }
    }

    @Test
    fun `deleting a rule keeps generated transactions`() = runTest {
        val ruleId = rules.create(ruleInput(nextDate = today))
        generator.run()

        rules.delete(ruleId)

        val kept = generated().single()
        assertNull(kept.recurringRuleId)
        assertEquals("Nájom", kept.note)
        assertTrue(rules.observeRules().first().isEmpty())
    }
}
