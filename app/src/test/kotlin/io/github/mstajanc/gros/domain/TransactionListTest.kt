package io.github.mstajanc.gros.domain

import io.github.mstajanc.gros.domain.model.Account
import io.github.mstajanc.gros.domain.model.AccountType
import io.github.mstajanc.gros.domain.model.Category
import io.github.mstajanc.gros.domain.model.CategoryKind
import io.github.mstajanc.gros.domain.model.Transaction
import io.github.mstajanc.gros.domain.model.TransactionType
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class TransactionListTest {

    private val today = LocalDate.of(2026, 11, 15)

    private fun tx(type: TransactionType, cents: Long, date: LocalDate = today, account: Long = 1, to: Long? = null) =
        Transaction(
            type = type,
            amountCents = cents,
            date = date,
            accountId = account,
            toAccountId = to,
            categoryId = if (type == TransactionType.TRANSFER) null else 1,
            createdAt = 0,
            updatedAt = 0,
        )

    @Test
    fun `transactions are grouped by day in the given order`() {
        val list = listOf(
            tx(TransactionType.EXPENSE, 4_230),
            tx(TransactionType.TRANSFER, 30_000, to = 2),
            tx(TransactionType.EXPENSE, 6_120, today.minusDays(1)),
            tx(TransactionType.INCOME, 290_000, today.minusDays(5)),
        )
        val groups = TransactionList.groupByDay(list)

        assertEquals(listOf(today, today.minusDays(1), today.minusDays(5)), groups.map { it.date })
        assertEquals(listOf(2, 1, 1), groups.map { it.transactions.size })
    }

    @Test
    fun `day header is income minus expenses without transfers`() {
        val groups = TransactionList.groupByDay(
            listOf(
                tx(TransactionType.EXPENSE, 4_230),
                tx(TransactionType.TRANSFER, 30_000, to = 2),
                tx(TransactionType.INCOME, 1_000),
            ),
        )
        assertEquals(1_000 - 4_230, groups.single().netCents)
    }

    @Test
    fun `summary counts everything but sums only income and expenses`() {
        val summary = TransactionList.summarize(
            listOf(
                tx(TransactionType.EXPENSE, 1_290),
                tx(TransactionType.EXPENSE, 18_900),
                tx(TransactionType.INCOME, 290_000),
                tx(TransactionType.TRANSFER, 30_000, to = 2),
            ),
        )
        assertEquals(ListSummary(count = 4, incomeCents = 290_000, expenseCents = 20_190), summary)
        assertEquals(ListSummary(0, 0, 0), TransactionList.summarize(emptyList()))
    }

    @Test
    fun `expense is negative income positive and transfer unsigned`() {
        assertEquals(
            DisplayAmount(-4_230, SignDisplay.NEGATIVE_ONLY),
            TransactionList.displayAmount(tx(TransactionType.EXPENSE, 4_230), accountFilterId = null),
        )
        assertEquals(
            DisplayAmount(290_000, SignDisplay.ALWAYS),
            TransactionList.displayAmount(tx(TransactionType.INCOME, 290_000), accountFilterId = null),
        )
        assertEquals(
            DisplayAmount(30_000, SignDisplay.NEVER),
            TransactionList.displayAmount(tx(TransactionType.TRANSFER, 30_000, to = 2), accountFilterId = null),
        )
    }

    @Test
    fun `transfer gets a sign by direction when one account is filtered`() {
        val transfer = tx(TransactionType.TRANSFER, 30_000, account = 1, to = 2)
        assertEquals(DisplayAmount(-30_000, SignDisplay.NEGATIVE_ONLY), TransactionList.displayAmount(transfer, 1))
        assertEquals(DisplayAmount(30_000, SignDisplay.ALWAYS), TransactionList.displayAmount(transfer, 2))
    }
}

class TransactionFilterTest {

    @Test
    fun `month period covers the whole calendar month`() {
        val period = Period.Month(YearMonth.of(2028, 2))
        assertEquals(LocalDate.of(2028, 2, 1), period.from)
        assertEquals(LocalDate.of(2028, 2, 29), period.to)
    }

    @Test
    fun `year period covers the whole year and all has no bounds`() {
        assertEquals(LocalDate.of(2026, 1, 1), Period.Year(2026).from)
        assertEquals(LocalDate.of(2026, 12, 31), Period.Year(2026).to)
        assertNull(Period.All.from)
        assertNull(Period.All.to)
    }

    @Test
    fun `periods step backwards and forwards`() {
        assertEquals(Period.Month(YearMonth.of(2026, 12)), Period.Month(YearMonth.of(2027, 1)).previous())
        assertEquals(Period.Month(YearMonth.of(2027, 1)), Period.Month(YearMonth.of(2026, 12)).next())
        assertEquals(Period.Year(2025), Period.Year(2026).previous())
        assertEquals(Period.All, Period.All.next())
    }

    @Test
    fun `main category filter includes its subcategories`() {
        val car = Category(id = 1, name = "Auto", kind = CategoryKind.EXPENSE, color = 2)
        val fuel = Category(id = 2, parentId = 1, name = "Tankovanie", kind = CategoryKind.EXPENSE, color = 2)
        val service = Category(id = 3, parentId = 1, name = "Servis", kind = CategoryKind.EXPENSE, color = 2)
        val food = Category(id = 4, name = "Potraviny", kind = CategoryKind.EXPENSE, color = 0)
        val all = listOf(car, fuel, service, food)

        assertEquals(setOf(1L, 2L, 3L), TransactionFilter.categoryIds(1, all).toSet())
        assertEquals(listOf(2L), TransactionFilter.categoryIds(2, all))
        assertEquals(listOf(4L), TransactionFilter.categoryIds(4, all))
    }

    @Test
    fun `search text matches category names and their main category`() {
        val car = Category(id = 1, name = "Auto", kind = CategoryKind.EXPENSE, color = 2)
        val fuel = Category(id = 2, parentId = 1, name = "Tankovanie", kind = CategoryKind.EXPENSE, color = 2)
        val service = Category(id = 3, parentId = 1, name = "Servis", kind = CategoryKind.EXPENSE, color = 2)
        val food = Category(id = 4, name = "Potraviny", kind = CategoryKind.EXPENSE, color = 0)
        val clothes = Category(id = 5, name = "Oblečenie", kind = CategoryKind.EXPENSE, color = 1)
        val all = listOf(car, fuel, service, food, clothes)

        assertEquals(setOf(2L), TransactionFilter.categoryIdsMatching("TANK", all).toSet())
        // Názov hlavnej kategórie zahŕňa aj jej podkategórie.
        assertEquals(setOf(1L, 2L, 3L), TransactionFilter.categoryIdsMatching("auto", all).toSet())
        assertEquals(setOf(5L), TransactionFilter.categoryIdsMatching("oblecenie", all).toSet())
        assertTrue(TransactionFilter.categoryIdsMatching("xyz", all).isEmpty())
        assertTrue(TransactionFilter.categoryIdsMatching("  ", all).isEmpty())
    }
}

class TransactionFormRulesTest {

    private val expense = FormSelection(TransactionType.EXPENSE, categoryId = 10, subcategoryId = 11, accountId = 1)

    private fun account(id: Long, archived: Boolean = false, order: Int = id.toInt()) =
        Account(id, "Účet $id", AccountType.CHECKING, 0, archived = archived, sortOrder = order)

    @Test
    fun `save needs a positive amount an account and a category`() {
        assertTrue(TransactionFormRules.canSave(expense, 4_230))
        assertTrue(TransactionFormRules.canSave(expense.copy(subcategoryId = null), 4_230))
        assertFalse(TransactionFormRules.canSave(expense, 0))
        assertFalse(TransactionFormRules.canSave(expense.copy(categoryId = null, subcategoryId = null), 4_230))
        assertFalse(TransactionFormRules.canSave(expense.copy(accountId = null), 4_230))
    }

    @Test
    fun `transfer needs two different accounts`() {
        val transfer = FormSelection(TransactionType.TRANSFER, accountId = 1, toAccountId = 2)
        assertTrue(TransactionFormRules.canSave(transfer, 100))
        assertFalse(TransactionFormRules.canSave(transfer.copy(toAccountId = null), 100))
        assertFalse(TransactionFormRules.canSave(transfer.copy(toAccountId = 1), 100))
    }

    @Test
    fun `subcategory is optional and wins when chosen`() {
        assertEquals(11L, expense.effectiveCategoryId)
        assertEquals(10L, expense.copy(subcategoryId = null).effectiveCategoryId)
    }

    @Test
    fun `switching between expense and income clears the category`() {
        val income = TransactionFormRules.onTypeChanged(expense, TransactionType.INCOME)
        assertEquals(TransactionType.INCOME, income.type)
        assertNull(income.categoryId)
        assertNull(income.subcategoryId)
        assertEquals(1L, income.accountId)
        assertEquals(expense, TransactionFormRules.onTypeChanged(expense, TransactionType.EXPENSE))
    }

    @Test
    fun `switching to transfer removes the category and asks for a target account`() {
        val transfer = TransactionFormRules.onTypeChanged(expense, TransactionType.TRANSFER)
        assertNull(transfer.categoryId)
        assertNull(transfer.subcategoryId)
        assertNull(transfer.toAccountId)
        assertFalse(TransactionFormRules.canSave(transfer, 100))

        val back = TransactionFormRules.onTypeChanged(transfer.copy(toAccountId = 2), TransactionType.EXPENSE)
        assertNull(back.toAccountId)
        assertNull(back.categoryId)
    }

    @Test
    fun `default account is the last used one when it is still active`() {
        val accounts = listOf(account(1), account(2), account(3, archived = true))
        assertEquals(2L, TransactionFormRules.defaultAccountId(lastUsedId = 2, accounts))
        assertEquals(1L, TransactionFormRules.defaultAccountId(lastUsedId = 3, accounts))
        assertEquals(1L, TransactionFormRules.defaultAccountId(lastUsedId = 99, accounts))
        assertEquals(1L, TransactionFormRules.defaultAccountId(lastUsedId = null, accounts))
        assertNull(TransactionFormRules.defaultAccountId(null, listOf(account(3, archived = true))))
    }

    @Test
    fun `default account follows the user order`() {
        val accounts = listOf(account(1, order = 2), account(2, order = 0), account(3, order = 1))
        assertEquals(2L, TransactionFormRules.defaultAccountId(null, accounts))
    }

    @Test
    fun `transfer is available only with two active accounts`() {
        assertFalse(TransactionFormRules.isTransferAvailable(listOf(account(1), account(2, archived = true))))
        assertTrue(TransactionFormRules.isTransferAvailable(listOf(account(1), account(2))))
    }

    @Test
    fun `date cannot be in the future`() {
        val today = LocalDate.of(2026, 11, 15)
        assertTrue(TransactionFormRules.isDateAllowed(today, today))
        assertTrue(TransactionFormRules.isDateAllowed(today.minusYears(3), today))
        assertFalse(TransactionFormRules.isDateAllowed(today.plusDays(1), today))
    }
}
