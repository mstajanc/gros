package io.github.mstajanc.gros.data

import io.github.mstajanc.gros.data.repository.CategoryUsage
import io.github.mstajanc.gros.domain.model.Frequency
import io.github.mstajanc.gros.domain.model.RecurringRuleDraft
import io.github.mstajanc.gros.domain.model.TransactionType
import io.github.mstajanc.gros.domain.rules.Violation
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Správa kategórií v M5: názvy, presun, povýšenie a zlúčenie (G1–G3, G11). */
class CategoryEditRepositoryTest : DatabaseTest() {

    private fun rule(accountId: Long, categoryId: Long) = RecurringRuleDraft(
        name = "Pravidlo",
        type = TransactionType.EXPENSE,
        amountCents = 1_000,
        accountId = accountId,
        categoryId = categoryId,
        frequency = Frequency.MONTHLY,
        interval = 1,
        anchorDate = today.plusDays(1),
        nextDate = today.plusDays(1),
    )

    @Test
    fun `duplicate or too long names are rejected on create and rename`() = runTest {
        val food = expenseCategory("Potraviny")
        val car = expenseCategory("Auto")
        expenseCategory("Tankovanie", parentId = car)

        assertViolation(Violation.NAME_DUPLICATE) { expenseCategory(" POTRAVINY ") }
        assertViolation(Violation.NAME_DUPLICATE) { expenseCategory("tankovanie", parentId = car) }
        assertViolation(Violation.NAME_TOO_LONG) { expenseCategory("x".repeat(41)) }
        assertViolation(Violation.NAME_DUPLICATE) { categories.rename(food, "auto") }
        // Rovnaký názov v inom druhu alebo pod iným rodičom je v poriadku.
        incomeCategory("Potraviny")
        expenseCategory("Tankovanie")
        categories.rename(food, "POTRAVINY")
        assertEquals("POTRAVINY", categories.get(food)!!.name)
    }

    @Test
    fun `subcategory moves under another main category, takes its color and goes last`() = runTest {
        val car = expenseCategory("Auto", color = 2)
        val fuel = expenseCategory("Tankovanie", parentId = car)
        val kids = expenseCategory("Deti", color = 4)
        expenseCategory("Škola", parentId = kids)

        categories.move(fuel, newParentId = kids)

        val moved = categories.get(fuel)!!
        assertEquals(kids, moved.parentId)
        assertEquals(4, moved.color)
        assertEquals(1, moved.sortOrder)
    }

    @Test
    fun `subcategory is promoted to the end of main categories and keeps its color`() = runTest {
        val car = expenseCategory("Auto", color = 2)
        val fuel = expenseCategory("Tankovanie", parentId = car)
        expenseCategory("Deti")

        categories.move(fuel, newParentId = null)

        val promoted = categories.get(fuel)!!
        assertNull(promoted.parentId)
        assertEquals(2, promoted.color)
        assertEquals(2, promoted.sortOrder)
    }

    @Test
    fun `promotion is rejected when a main category has the same name`() = runTest {
        val car = expenseCategory("Auto")
        val other = expenseCategory("Deti", parentId = car)
        expenseCategory("Deti")

        assertViolation(Violation.NAME_DUPLICATE) { categories.move(other, newParentId = null) }
    }

    @Test
    fun `main category without subcategories moves under another one and hands over its budget`() = runTest {
        val food = expenseCategory("Potraviny", color = 0)
        val car = expenseCategory("Auto", color = 2)
        val budget = budgets.create(food, 30_000)

        categories.move(food, newParentId = car)

        assertEquals(car, categories.get(food)!!.parentId)
        assertEquals(2, categories.get(food)!!.color)
        // Rozpočet patrí len hlavnej kategórii; cieľ ho nemal, takže prejde naň (G11).
        assertEquals(car, budgets.get(budget)!!.categoryId)
    }

    @Test
    fun `budget of a demoted category is dropped when the new parent already has one`() = runTest {
        val food = expenseCategory("Potraviny")
        val car = expenseCategory("Auto")
        val foodBudget = budgets.create(food, 30_000)
        val carBudget = budgets.create(car, 10_000)

        categories.move(food, newParentId = car)

        assertNull(budgets.get(foodBudget))
        assertEquals(10_000, budgets.get(carBudget)!!.monthlyLimitCents)
    }

    @Test
    fun `main category with subcategories cannot be moved under another one`() = runTest {
        val car = expenseCategory("Auto")
        expenseCategory("Tankovanie", parentId = car)
        val kids = expenseCategory("Deti")

        assertViolation(Violation.CATEGORY_HAS_SUBCATEGORIES) { categories.move(car, newParentId = kids) }
    }

    @Test
    fun `merge moves transactions and rules to the target and deletes the source`() = runTest {
        val accountId = account()
        val food = expenseCategory("Potraviny")
        val groceries = expenseCategory("Nákupy")
        val tx = transactions.create(expense(accountId, groceries, 1_250))
        val ruleId = rules.create(rule(accountId, groceries))

        assertEquals(CategoryUsage(transactions = 1, rules = 1, subcategories = 0), categories.usage(groceries))
        categories.merge(sourceId = groceries, targetId = food)

        assertNull(categories.get(groceries))
        assertEquals(food, transactions.get(tx)!!.categoryId)
        assertEquals(food, rules.get(ruleId)!!.categoryId)
    }

    @Test
    fun `subcategory merges into its own main category`() = runTest {
        val accountId = account()
        val car = expenseCategory("Auto")
        val fuel = expenseCategory("Tankovanie", parentId = car)
        val tx = transactions.create(expense(accountId, fuel, 6_000))

        categories.merge(sourceId = fuel, targetId = car)

        assertEquals(car, transactions.get(tx)!!.categoryId)
        assertEquals(listOf(car), categories.observeCategories().first().map { it.id })
    }

    @Test
    fun `merging a main category moves its subcategories and joins same-named ones`() = runTest {
        val accountId = account()
        val car = expenseCategory("Auto", color = 2)
        val carOther = expenseCategory("Ostatné", parentId = car)
        val fuel = expenseCategory("Tankovanie", parentId = car)
        val transport = expenseCategory("Doprava", color = 3)
        val transportOther = expenseCategory("ostatné", parentId = transport)
        val tx = transactions.create(expense(accountId, carOther, 500))

        categories.merge(sourceId = car, targetId = transport)

        assertNull(categories.get(car))
        // Rovnomenná podkategória sa zlúči do existujúcej, aby nevznikla duplicita (G11).
        assertNull(categories.get(carOther))
        assertEquals(transportOther, transactions.get(tx)!!.categoryId)
        val movedFuel = categories.get(fuel)!!
        assertEquals(transport, movedFuel.parentId)
        assertEquals(3, movedFuel.color)
        assertEquals(1, movedFuel.sortOrder)
    }

    @Test
    fun `merge keeps the target budget and otherwise hands over the source budget`() = runTest {
        val food = expenseCategory("Potraviny")
        val groceries = expenseCategory("Nákupy")
        val kids = expenseCategory("Deti")
        val groceriesBudget = budgets.create(groceries, 5_000)
        val foodBudget = budgets.create(food, 30_000)
        val kidsBudgetSource = expenseCategory("Hračky")
        val toysBudget = budgets.create(kidsBudgetSource, 2_000)

        categories.merge(sourceId = groceries, targetId = food)
        categories.merge(sourceId = kidsBudgetSource, targetId = kids)

        assertNull(budgets.get(groceriesBudget))
        assertEquals(30_000, budgets.get(foodBudget)!!.monthlyLimitCents)
        assertEquals(kids, budgets.get(toysBudget)!!.categoryId)
    }

    @Test
    fun `main category merged into a subcategory hands its budget to that main category`() = runTest {
        val food = expenseCategory("Potraviny")
        val car = expenseCategory("Auto")
        val fuel = expenseCategory("Tankovanie", parentId = car)
        val budget = budgets.create(food, 30_000)

        categories.merge(sourceId = food, targetId = fuel)

        assertEquals(car, budgets.get(budget)!!.categoryId)
    }

    @Test
    fun `invalid merges leave data untouched`() = runTest {
        val car = expenseCategory("Auto")
        val fuel = expenseCategory("Tankovanie", parentId = car)
        val kids = expenseCategory("Deti")
        val salary = incomeCategory("Výplata")
        val old = expenseCategory("Staré")
        categories.archive(old)

        assertViolation(Violation.MERGE_INTO_OWN_SUBCATEGORY) { categories.merge(car, fuel) }
        assertViolation(Violation.MERGE_TARGET_NOT_MAIN) { categories.merge(car, expenseCategory("Škola", parentId = kids)) }
        assertViolation(Violation.CATEGORY_KIND_MISMATCH) { categories.merge(kids, salary) }
        assertViolation(Violation.TARGET_ARCHIVED) { categories.merge(kids, old) }
        assertEquals(car, categories.get(fuel)!!.parentId)
    }
}
