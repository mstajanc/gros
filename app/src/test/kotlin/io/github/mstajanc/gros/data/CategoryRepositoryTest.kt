package io.github.mstajanc.gros.data

import io.github.mstajanc.gros.domain.model.CategoryKind
import io.github.mstajanc.gros.domain.model.Frequency
import io.github.mstajanc.gros.domain.model.RecurringRuleDraft
import io.github.mstajanc.gros.domain.model.TransactionType
import io.github.mstajanc.gros.domain.rules.Violation
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class CategoryRepositoryTest : DatabaseTest() {

    @Test
    fun `main categories are ordered within their kind`() = runTest {
        val food = expenseCategory("Potraviny", color = 0)
        val housing = expenseCategory(" Bývanie ", color = 1)
        val salary = incomeCategory("Výplata")

        assertEquals(0, categories.get(food)!!.sortOrder)
        assertEquals(1, categories.get(housing)!!.sortOrder)
        assertEquals(0, categories.get(salary)!!.sortOrder)
        assertEquals("Bývanie", categories.get(housing)!!.name)
        assertTrue(categories.get(housing)!!.isMain)
    }

    @Test
    fun `subcategory takes kind and color of its parent and is ordered within it`() = runTest {
        val car = expenseCategory("Auto", color = 2)
        val fuel = categories.create("Tankovanie", CategoryKind.EXPENSE, color = 5, parentId = car)
        val service = categories.create("Servis", CategoryKind.EXPENSE, color = 0, parentId = car)

        val stored = categories.get(fuel)!!
        assertEquals(car, stored.parentId)
        assertEquals(CategoryKind.EXPENSE, stored.kind)
        assertEquals(2, stored.color)
        assertEquals(0, stored.sortOrder)
        assertEquals(1, categories.get(service)!!.sortOrder)
    }

    @Test
    fun `categories never have a third level`() = runTest {
        val car = expenseCategory("Auto")
        val fuel = expenseCategory("Tankovanie", parentId = car)

        assertViolation(Violation.PARENT_NOT_MAIN) { expenseCategory("Diesel", parentId = fuel) }
        assertEquals(2, categories.observeCategories().first().size)
    }

    @Test
    fun `subcategory must have the kind of its parent`() = runTest {
        val salary = incomeCategory("Výplata")
        assertViolation(Violation.PARENT_KIND_MISMATCH) { expenseCategory("Bonus", parentId = salary) }
    }

    @Test
    fun `invalid category is rejected`() = runTest {
        assertViolation(Violation.NAME_BLANK) { expenseCategory("  ") }
        assertViolation(Violation.COLOR_OUT_OF_RANGE) { expenseCategory("Deti", color = 6) }
        assertViolation(Violation.PARENT_NOT_FOUND) { expenseCategory("Servis", parentId = 999) }
        assertTrue(categories.observeCategories().first().isEmpty())
    }

    @Test
    fun `rename keeps everything else`() = runTest {
        val id = expenseCategory("Potraviny", color = 3)
        categories.rename(id, " Jedlo ")

        val renamed = categories.get(id)!!
        assertEquals("Jedlo", renamed.name)
        assertEquals(3, renamed.color)
        assertEquals(CategoryKind.EXPENSE, renamed.kind)
        assertViolation(Violation.NAME_BLANK) { categories.rename(id, "") }
        assertViolation(Violation.NOT_FOUND) { categories.rename(999, "X") }
    }

    @Test
    fun `color of a main category propagates to its subcategories`() = runTest {
        val car = expenseCategory("Auto", color = 2)
        val fuel = expenseCategory("Tankovanie", parentId = car)
        val other = expenseCategory("Deti", color = 3)

        categories.setColor(car, 4)

        assertEquals(4, categories.get(car)!!.color)
        assertEquals(4, categories.get(fuel)!!.color)
        assertEquals(3, categories.get(other)!!.color)
        assertViolation(Violation.SUBCATEGORY_COLOR_INHERITED) { categories.setColor(fuel, 1) }
        assertViolation(Violation.COLOR_OUT_OF_RANGE) { categories.setColor(car, 6) }
    }

    @Test
    fun `last active main category of a kind cannot be archived`() = runTest {
        val salary = incomeCategory("Výplata")
        expenseCategory("Potraviny")
        expenseCategory("Bývanie")

        assertViolation(Violation.LAST_ACTIVE_MAIN_CATEGORY) { categories.archive(salary) }
        assertFalse(categories.get(salary)!!.archived)

        val other = incomeCategory("Ostatné príjmy")
        categories.archive(salary)
        assertTrue(categories.get(salary)!!.archived)
        assertViolation(Violation.LAST_ACTIVE_MAIN_CATEGORY) { categories.archive(other) }
    }

    @Test
    fun `archiving a main category keeps the flags of its subcategories`() = runTest {
        expenseCategory("Potraviny")
        val car = expenseCategory("Auto")
        val fuel = expenseCategory("Tankovanie", parentId = car)
        val service = expenseCategory("Servis", parentId = car)
        categories.archive(service)

        categories.archive(car)
        assertTrue(categories.get(car)!!.archived)
        assertFalse(categories.get(fuel)!!.archived)
        assertTrue(categories.get(service)!!.archived)

        categories.restore(car)
        assertFalse(categories.get(car)!!.archived)
        assertFalse(categories.get(fuel)!!.archived)
        assertTrue(categories.get(service)!!.archived)
    }

    @Test
    fun `subcategory can be archived even when it is the only one`() = runTest {
        val car = expenseCategory("Auto")
        val fuel = expenseCategory("Tankovanie", parentId = car)
        categories.archive(fuel)
        assertTrue(categories.get(fuel)!!.archived)
    }

    @Test
    fun `category with subcategories cannot be deleted`() = runTest {
        expenseCategory("Potraviny")
        val car = expenseCategory("Auto")
        val fuel = expenseCategory("Tankovanie", parentId = car)

        assertViolation(Violation.CATEGORY_HAS_SUBCATEGORIES) { categories.delete(car) }

        categories.delete(fuel)
        categories.delete(car)
        assertNull(categories.get(car))
    }

    @Test
    fun `category used by a transaction or a rule cannot be deleted`() = runTest {
        val accountId = account()
        expenseCategory("Potraviny")
        val car = expenseCategory("Auto")
        val fuel = expenseCategory("Tankovanie", parentId = car)
        val housing = expenseCategory("Bývanie")
        transactions.create(expense(accountId, fuel, 6_120))
        rules.create(
            RecurringRuleDraft(
                name = "Nájom",
                type = TransactionType.EXPENSE,
                amountCents = 52_000,
                accountId = accountId,
                categoryId = housing,
                frequency = Frequency.MONTHLY,
                interval = 1,
                anchorDate = today,
                nextDate = today,
            ),
        )

        assertViolation(Violation.CATEGORY_IN_USE) { categories.delete(fuel) }
        assertViolation(Violation.CATEGORY_IN_USE) { categories.delete(housing) }
        assertNotNull(categories.get(fuel))
        assertNotNull(categories.get(housing))
    }

    @Test
    fun `last active main category of a kind cannot be deleted`() = runTest {
        val salary = incomeCategory("Výplata")
        assertViolation(Violation.LAST_ACTIVE_MAIN_CATEGORY) { categories.delete(salary) }
        assertNotNull(categories.get(salary))
    }

    @Test
    fun `deleting a category removes its budget`() = runTest {
        expenseCategory("Potraviny")
        val car = expenseCategory("Auto")
        val budget = budgets.create(car, 20_000)

        categories.delete(car)

        assertNull(budgets.get(budget))
    }

    @Test
    fun `reorder changes the order of siblings`() = runTest {
        val a = expenseCategory("A")
        val b = expenseCategory("B")
        val c = expenseCategory("C")

        categories.reorder(listOf(b, c, a))

        assertEquals(listOf(b, c, a), categories.observeCategories().first().map { it.id })
    }
}
