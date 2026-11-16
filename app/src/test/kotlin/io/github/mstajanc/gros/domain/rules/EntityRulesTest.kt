package io.github.mstajanc.gros.domain.rules

import io.github.mstajanc.gros.domain.Money
import io.github.mstajanc.gros.domain.model.Account
import io.github.mstajanc.gros.domain.model.AccountType
import io.github.mstajanc.gros.domain.model.Category
import io.github.mstajanc.gros.domain.model.CategoryKind
import io.github.mstajanc.gros.domain.model.Frequency
import io.github.mstajanc.gros.domain.model.RecurringRuleDraft
import io.github.mstajanc.gros.domain.model.TransactionType
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class AccountRulesTest {

    private val account = Account(id = 1, name = "Bežný účet", type = AccountType.CHECKING, openingBalanceCents = 0)

    @Test
    fun `name must not be blank`() {
        assertNull(AccountRules.validateName("Hotovosť"))
        assertEquals(Violation.NAME_BLANK, AccountRules.validateName("   "))
        assertEquals(Violation.NAME_BLANK, AccountRules.validateName(""))
    }

    @Test
    fun `entered balance may be negative but is limited in size`() {
        assertNull(AccountRules.validateEnteredBalance(0))
        assertNull(AccountRules.validateEnteredBalance(-Money.MAX_CENTS))
        assertNull(AccountRules.validateEnteredBalance(Money.MAX_CENTS))
        assertEquals(Violation.AMOUNT_TOO_LARGE, AccountRules.validateEnteredBalance(Money.MAX_CENTS + 1))
        assertEquals(Violation.AMOUNT_TOO_LARGE, AccountRules.validateEnteredBalance(-Money.MAX_CENTS - 1))
    }

    @Test
    fun `last active account cannot be archived`() {
        assertEquals(Violation.LAST_ACTIVE_ACCOUNT, AccountRules.canArchive(account, activeAccountCount = 1))
        assertNull(AccountRules.canArchive(account, activeAccountCount = 2))
    }

    @Test
    fun `account in use cannot be deleted`() {
        assertEquals(
            Violation.ACCOUNT_IN_USE,
            AccountRules.canDelete(account, activeAccountCount = 3, referenced = true),
        )
        assertNull(AccountRules.canDelete(account, activeAccountCount = 3, referenced = false))
    }

    @Test
    fun `last active account cannot be deleted but an archived one can`() {
        assertEquals(
            Violation.LAST_ACTIVE_ACCOUNT,
            AccountRules.canDelete(account, activeAccountCount = 1, referenced = false),
        )
        assertNull(AccountRules.canDelete(account.copy(archived = true), activeAccountCount = 1, referenced = false))
    }
}

class CategoryRulesTest {

    private val car = Category(id = 1, name = "Auto", kind = CategoryKind.EXPENSE, color = 2)
    private val fuel = Category(id = 2, parentId = 1, name = "Tankovanie", kind = CategoryKind.EXPENSE, color = 2)
    private val salary = Category(id = 3, name = "Výplata", kind = CategoryKind.INCOME, color = 0)

    @Test
    fun `main category needs a name and a palette color`() {
        assertNull(CategoryRules.validateNew("Deti", CategoryKind.EXPENSE, color = 3, parentId = null, parent = null))
        assertEquals(
            Violation.NAME_BLANK,
            CategoryRules.validateNew(" ", CategoryKind.EXPENSE, color = 3, parentId = null, parent = null),
        )
        assertEquals(
            Violation.COLOR_OUT_OF_RANGE,
            CategoryRules.validateNew("Deti", CategoryKind.EXPENSE, color = 6, parentId = null, parent = null),
        )
        assertEquals(
            Violation.COLOR_OUT_OF_RANGE,
            CategoryRules.validateNew("Deti", CategoryKind.EXPENSE, color = -1, parentId = null, parent = null),
        )
    }

    @Test
    fun `subcategory needs an existing main parent of the same kind`() {
        assertNull(CategoryRules.validateNew("Servis", CategoryKind.EXPENSE, color = 0, parentId = car.id, parent = car))
        assertEquals(
            Violation.PARENT_NOT_FOUND,
            CategoryRules.validateNew("Servis", CategoryKind.EXPENSE, color = 0, parentId = 99, parent = null),
        )
        assertEquals(
            Violation.PARENT_KIND_MISMATCH,
            CategoryRules.validateNew("Servis", CategoryKind.EXPENSE, color = 0, parentId = salary.id, parent = salary),
        )
    }

    @Test
    fun `categories have at most two levels`() {
        assertEquals(
            Violation.PARENT_NOT_MAIN,
            CategoryRules.validateNew("Diesel", CategoryKind.EXPENSE, color = 0, parentId = fuel.id, parent = fuel),
        )
    }

    @Test
    fun `subcategory takes the color of its parent`() {
        assertEquals(2, CategoryRules.effectiveColor(requested = 5, parent = car))
        assertEquals(5, CategoryRules.effectiveColor(requested = 5, parent = null))
    }

    @Test
    fun `color can be changed only on a main category`() {
        assertNull(CategoryRules.canSetColor(car, color = 4))
        assertEquals(Violation.COLOR_OUT_OF_RANGE, CategoryRules.canSetColor(car, color = 9))
        assertEquals(Violation.SUBCATEGORY_COLOR_INHERITED, CategoryRules.canSetColor(fuel, color = 4))
    }

    @Test
    fun `last active main category of a kind cannot be archived`() {
        assertEquals(Violation.LAST_ACTIVE_MAIN_CATEGORY, CategoryRules.canArchive(salary, activeMainCountOfKind = 1))
        assertNull(CategoryRules.canArchive(salary, activeMainCountOfKind = 2))
    }

    @Test
    fun `subcategory can always be archived`() {
        assertNull(CategoryRules.canArchive(fuel, activeMainCountOfKind = 1))
    }

    @Test
    fun `category with subcategories or references cannot be deleted`() {
        assertEquals(
            Violation.CATEGORY_HAS_SUBCATEGORIES,
            CategoryRules.canDelete(car, activeMainCountOfKind = 5, hasSubcategories = true, referenced = false),
        )
        assertEquals(
            Violation.CATEGORY_IN_USE,
            CategoryRules.canDelete(car, activeMainCountOfKind = 5, hasSubcategories = false, referenced = true),
        )
        assertNull(CategoryRules.canDelete(car, activeMainCountOfKind = 5, hasSubcategories = false, referenced = false))
        assertNull(CategoryRules.canDelete(fuel, activeMainCountOfKind = 1, hasSubcategories = false, referenced = false))
    }

    @Test
    fun `last active main category of a kind cannot be deleted`() {
        assertEquals(
            Violation.LAST_ACTIVE_MAIN_CATEGORY,
            CategoryRules.canDelete(salary, activeMainCountOfKind = 1, hasSubcategories = false, referenced = false),
        )
    }
}

class BudgetRulesTest {

    private val food = Category(id = 1, name = "Potraviny", kind = CategoryKind.EXPENSE, color = 0)
    private val fuel = Category(id = 2, parentId = 9, name = "Tankovanie", kind = CategoryKind.EXPENSE, color = 2)
    private val salary = Category(id = 3, name = "Výplata", kind = CategoryKind.INCOME, color = 0)

    @Test
    fun `budget is allowed only on a main expense category`() {
        assertNull(BudgetRules.validate(food, limitCents = 45_000, categoryHasBudget = false))
        assertEquals(Violation.CATEGORY_NOT_FOUND, BudgetRules.validate(null, 45_000, categoryHasBudget = false))
        assertEquals(Violation.BUDGET_CATEGORY_NOT_MAIN, BudgetRules.validate(fuel, 45_000, categoryHasBudget = false))
        assertEquals(
            Violation.BUDGET_CATEGORY_NOT_EXPENSE,
            BudgetRules.validate(salary, 45_000, categoryHasBudget = false),
        )
    }

    @Test
    fun `limit must be positive and within the maximum`() {
        assertEquals(Violation.AMOUNT_NOT_POSITIVE, BudgetRules.validateLimit(0))
        assertEquals(Violation.AMOUNT_NOT_POSITIVE, BudgetRules.validateLimit(-100))
        assertEquals(Violation.AMOUNT_TOO_LARGE, BudgetRules.validateLimit(Money.MAX_CENTS + 1))
        assertNull(BudgetRules.validateLimit(1))
        assertEquals(Violation.AMOUNT_NOT_POSITIVE, BudgetRules.validate(food, 0, categoryHasBudget = false))
    }

    @Test
    fun `a category has at most one budget`() {
        assertEquals(Violation.BUDGET_ALREADY_EXISTS, BudgetRules.validate(food, 45_000, categoryHasBudget = true))
    }
}

class RecurringRuleRulesTest {

    private val checking = Account(id = 1, name = "Bežný účet", type = AccountType.CHECKING, openingBalanceCents = 0)
    private val savings = Account(id = 2, name = "Sporenie", type = AccountType.SAVINGS, openingBalanceCents = 0)
    private val housing = Category(id = 10, name = "Bývanie", kind = CategoryKind.EXPENSE, color = 1)
    private val start = LocalDate.of(2026, 12, 1)

    private fun rent(
        name: String = "Nájom",
        amount: Long = 52_000,
        interval: Int = 1,
    ) = RecurringRuleDraft(
        name = name,
        type = TransactionType.EXPENSE,
        amountCents = amount,
        accountId = checking.id,
        categoryId = housing.id,
        frequency = Frequency.MONTHLY,
        interval = interval,
        anchorDate = start,
        nextDate = start,
    )

    private fun validate(draft: RecurringRuleDraft, account: Account? = checking, toAccount: Account? = null) =
        RecurringRuleRules.validate(draft, PostingRefs(account, toAccount, housing.takeIf { draft.categoryId != null }))

    @Test
    fun `valid rule passes`() {
        assertNull(validate(rent()))
    }

    @Test
    fun `name is required`() {
        assertEquals(Violation.NAME_BLANK, validate(rent(name = "  ")))
    }

    @Test
    fun `amount must be positive`() {
        assertEquals(Violation.AMOUNT_NOT_POSITIVE, validate(rent(amount = 0)))
    }

    @Test
    fun `interval must be at least one`() {
        assertEquals(Violation.INTERVAL_NOT_POSITIVE, validate(rent(interval = 0)))
        assertNull(validate(rent(interval = 3)))
    }

    @Test
    fun `rule needs an active account`() {
        assertEquals(Violation.ACCOUNT_ARCHIVED, validate(rent(), account = checking.copy(archived = true)))
    }

    @Test
    fun `transfer rule needs two different active accounts and no category`() {
        val transfer = rent().copy(type = TransactionType.TRANSFER, categoryId = null, toAccountId = savings.id)
        assertNull(validate(transfer, toAccount = savings))
        assertEquals(Violation.ACCOUNT_ARCHIVED, validate(transfer, toAccount = savings.copy(archived = true)))
        assertEquals(
            Violation.TRANSFER_SAME_ACCOUNT,
            validate(transfer.copy(toAccountId = checking.id), toAccount = checking),
        )
        assertEquals(Violation.TRANSFER_TARGET_MISSING, validate(transfer.copy(toAccountId = null)))
    }

    @Test
    fun `category must match the rule type`() {
        val income = rent().copy(type = TransactionType.INCOME)
        assertEquals(Violation.CATEGORY_KIND_MISMATCH, validate(income))
    }
}
