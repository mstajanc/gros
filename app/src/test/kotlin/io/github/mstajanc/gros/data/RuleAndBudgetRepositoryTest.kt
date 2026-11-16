package io.github.mstajanc.gros.data

import io.github.mstajanc.gros.domain.Money
import io.github.mstajanc.gros.domain.model.Frequency
import io.github.mstajanc.gros.domain.model.RecurringRuleDraft
import io.github.mstajanc.gros.domain.model.TransactionType
import io.github.mstajanc.gros.domain.rules.Violation
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RecurringRuleRepositoryTest : DatabaseTest() {

    private fun rent(accountId: Long, categoryId: Long) = RecurringRuleDraft(
        name = " Nájom ",
        type = TransactionType.EXPENSE,
        amountCents = 52_000,
        accountId = accountId,
        categoryId = categoryId,
        frequency = Frequency.MONTHLY,
        interval = 1,
        anchorDate = today,
        nextDate = today,
    )

    @Test
    fun `create and update store the rule`() = runTest {
        val accountId = account()
        val housing = expenseCategory("Bývanie")

        val id = rules.create(rent(accountId, housing))
        val stored = rules.get(id)!!
        assertEquals("Nájom", stored.name)
        assertEquals(52_000, stored.amountCents)
        assertEquals(Frequency.MONTHLY, stored.frequency)
        assertEquals(today, stored.nextDate)
        assertNull(stored.endDate)

        rules.update(
            id,
            rent(accountId, housing).copy(
                amountCents = 54_000,
                frequency = Frequency.YEARLY,
                interval = 2,
                endDate = today.plusYears(4),
                paused = true,
            ),
        )
        val updated = rules.get(id)!!
        assertEquals(54_000, updated.amountCents)
        assertEquals(Frequency.YEARLY, updated.frequency)
        assertEquals(2, updated.interval)
        assertEquals(today.plusYears(4), updated.endDate)
        assertTrue(updated.paused)
    }

    @Test
    fun `transfer rule needs two different accounts`() = runTest {
        val checking = account("Bežný účet")
        val savings = account("Sporenie")
        val transfer = rent(checking, 0).copy(
            name = "Sporenie",
            type = TransactionType.TRANSFER,
            categoryId = null,
            toAccountId = savings,
        )

        assertNotNull(rules.get(rules.create(transfer)))
        assertViolation(Violation.TRANSFER_SAME_ACCOUNT) { rules.create(transfer.copy(toAccountId = checking)) }
        assertViolation(Violation.TRANSFER_TARGET_MISSING) { rules.create(transfer.copy(toAccountId = null)) }
    }

    @Test
    fun `invalid rules are rejected`() = runTest {
        val accountId = account("Bežný účet")
        val archived = account("Starý účet")
        accounts.archive(archived)
        val housing = expenseCategory("Bývanie")
        val salary = incomeCategory()

        assertViolation(Violation.NAME_BLANK) { rules.create(rent(accountId, housing).copy(name = " ")) }
        assertViolation(Violation.AMOUNT_NOT_POSITIVE) { rules.create(rent(accountId, housing).copy(amountCents = 0)) }
        assertViolation(Violation.INTERVAL_NOT_POSITIVE) { rules.create(rent(accountId, housing).copy(interval = 0)) }
        assertViolation(Violation.CATEGORY_KIND_MISMATCH) { rules.create(rent(accountId, salary)) }
        assertViolation(Violation.ACCOUNT_ARCHIVED) { rules.create(rent(archived, housing)) }
        assertViolation(Violation.NOT_FOUND) { rules.update(999, rent(accountId, housing)) }
        assertTrue(db.recurringRuleDao().getAll().isEmpty())
    }

    @Test
    fun `deleting a rule keeps its transactions and clears their link`() = runTest {
        val accountId = account()
        val housing = expenseCategory("Bývanie")
        val ruleId = rules.create(rent(accountId, housing))
        val generated = transactions.create(expense(accountId, housing, 52_000, note = "Nájom"), recurringRuleId = ruleId)
        val manual = transactions.create(expense(accountId, housing, 1_000))
        assertEquals(ruleId, transactions.get(generated)!!.recurringRuleId)

        rules.delete(ruleId)

        assertNull(rules.get(ruleId))
        val kept = transactions.get(generated)
        assertNotNull(kept)
        assertNull(kept.recurringRuleId)
        assertEquals(52_000, kept.amountCents)
        assertNotNull(transactions.get(manual))
    }
}

class BudgetRepositoryTest : DatabaseTest() {

    @Test
    fun `budget can be set on a main expense category`() = runTest {
        val food = expenseCategory("Potraviny")

        val id = budgets.create(food, 45_000)

        val stored = budgets.get(id)!!
        assertEquals(food, stored.categoryId)
        assertEquals(45_000, stored.monthlyLimitCents)
        assertEquals(listOf(id), budgets.observeBudgets().first().map { it.id })
    }

    @Test
    fun `budget is rejected on a subcategory an income category and an unknown category`() = runTest {
        val car = expenseCategory("Auto")
        val fuel = expenseCategory("Tankovanie", parentId = car)
        val salary = incomeCategory()

        assertViolation(Violation.BUDGET_CATEGORY_NOT_MAIN) { budgets.create(fuel, 10_000) }
        assertViolation(Violation.BUDGET_CATEGORY_NOT_EXPENSE) { budgets.create(salary, 10_000) }
        assertViolation(Violation.CATEGORY_NOT_FOUND) { budgets.create(999, 10_000) }
        assertTrue(budgets.observeBudgets().first().isEmpty())
    }

    @Test
    fun `a category has at most one budget`() = runTest {
        val food = expenseCategory("Potraviny")
        budgets.create(food, 45_000)

        assertViolation(Violation.BUDGET_ALREADY_EXISTS) { budgets.create(food, 50_000) }
        assertEquals(1, budgets.observeBudgets().first().size)
    }

    @Test
    fun `limit must be positive and within the maximum`() = runTest {
        val food = expenseCategory("Potraviny")
        assertViolation(Violation.AMOUNT_NOT_POSITIVE) { budgets.create(food, 0) }
        assertViolation(Violation.AMOUNT_TOO_LARGE) { budgets.create(food, Money.MAX_CENTS + 1) }

        val id = budgets.create(food, 45_000)
        assertViolation(Violation.AMOUNT_NOT_POSITIVE) { budgets.updateLimit(id, -1) }
        assertViolation(Violation.NOT_FOUND) { budgets.updateLimit(999, 100) }
        assertEquals(45_000, budgets.get(id)!!.monthlyLimitCents)
    }

    @Test
    fun `limit can be changed and the budget deleted`() = runTest {
        val food = expenseCategory("Potraviny")
        val id = budgets.create(food, 45_000)

        budgets.updateLimit(id, 50_000)
        assertEquals(50_000, budgets.get(id)!!.monthlyLimitCents)

        budgets.delete(id)
        assertNull(budgets.get(id))
        assertNotNull(categories.get(food))
    }
}
