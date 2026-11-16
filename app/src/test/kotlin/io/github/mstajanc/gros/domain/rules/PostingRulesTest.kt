package io.github.mstajanc.gros.domain.rules

import io.github.mstajanc.gros.domain.Money
import io.github.mstajanc.gros.domain.model.Account
import io.github.mstajanc.gros.domain.model.AccountType
import io.github.mstajanc.gros.domain.model.Category
import io.github.mstajanc.gros.domain.model.CategoryKind
import io.github.mstajanc.gros.domain.model.TransactionType
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class PostingRulesTest {

    private val checking = Account(id = 1, name = "Bežný účet", type = AccountType.CHECKING, openingBalanceCents = 0)
    private val savings = Account(id = 2, name = "Sporenie", type = AccountType.SAVINGS, openingBalanceCents = 0)
    private val food = Category(id = 10, name = "Potraviny", kind = CategoryKind.EXPENSE, color = 0)
    private val fuel = Category(id = 11, parentId = 12, name = "Tankovanie", kind = CategoryKind.EXPENSE, color = 2)
    private val salary = Category(id = 20, name = "Výplata", kind = CategoryKind.INCOME, color = 0)

    private fun expense(amount: Long = 4_230, category: Category? = food, categoryId: Long? = category?.id) =
        PostingRules.validate(
            Posting(TransactionType.EXPENSE, amount, checking.id, toAccountId = null, categoryId = categoryId),
            PostingRefs(account = checking, toAccount = null, category = category),
        )

    private fun transfer(
        amount: Long = 30_000,
        toAccountId: Long? = savings.id,
        toAccount: Account? = savings,
        categoryId: Long? = null,
    ) = PostingRules.validate(
        Posting(TransactionType.TRANSFER, amount, checking.id, toAccountId = toAccountId, categoryId = categoryId),
        PostingRefs(account = checking, toAccount = toAccount, category = null),
    )

    @Test
    fun `valid expense income and transfer pass`() {
        assertNull(expense())
        assertNull(
            PostingRules.validate(
                Posting(TransactionType.INCOME, 290_000, checking.id, null, salary.id),
                PostingRefs(checking, null, salary),
            ),
        )
        assertNull(transfer())
    }

    @Test
    fun `expense may use a main category or a subcategory`() {
        assertNull(expense(category = food))
        assertNull(expense(category = fuel))
    }

    @Test
    fun `amount must be positive`() {
        assertEquals(Violation.AMOUNT_NOT_POSITIVE, expense(amount = 0))
        assertEquals(Violation.AMOUNT_NOT_POSITIVE, expense(amount = -1))
        assertEquals(Violation.AMOUNT_NOT_POSITIVE, transfer(amount = 0))
    }

    @Test
    fun `amount must not exceed the maximum`() {
        assertNull(expense(amount = Money.MAX_CENTS))
        assertEquals(Violation.AMOUNT_TOO_LARGE, expense(amount = Money.MAX_CENTS + 1))
    }

    @Test
    fun `account must exist`() {
        val result = PostingRules.validate(
            Posting(TransactionType.EXPENSE, 100, accountId = 99, toAccountId = null, categoryId = food.id),
            PostingRefs(account = null, toAccount = null, category = food),
        )
        assertEquals(Violation.ACCOUNT_NOT_FOUND, result)
    }

    @Test
    fun `expense and income require a category`() {
        assertEquals(Violation.CATEGORY_MISSING, expense(category = null, categoryId = null))
        assertEquals(Violation.CATEGORY_NOT_FOUND, expense(category = null, categoryId = 999))
    }

    @Test
    fun `category kind must match the transaction type`() {
        assertEquals(Violation.CATEGORY_KIND_MISMATCH, expense(category = salary))
        val income = PostingRules.validate(
            Posting(TransactionType.INCOME, 100, checking.id, null, food.id),
            PostingRefs(checking, null, food),
        )
        assertEquals(Violation.CATEGORY_KIND_MISMATCH, income)
    }

    @Test
    fun `expense and income must not have a target account`() {
        val result = PostingRules.validate(
            Posting(TransactionType.EXPENSE, 100, checking.id, toAccountId = savings.id, categoryId = food.id),
            PostingRefs(checking, savings, food),
        )
        assertEquals(Violation.TARGET_ACCOUNT_NOT_ALLOWED, result)
    }

    @Test
    fun `transfer requires an existing different target account`() {
        assertEquals(Violation.TRANSFER_TARGET_MISSING, transfer(toAccountId = null, toAccount = null))
        assertEquals(Violation.TRANSFER_TARGET_NOT_FOUND, transfer(toAccountId = 99, toAccount = null))
        assertEquals(Violation.TRANSFER_SAME_ACCOUNT, transfer(toAccountId = checking.id, toAccount = checking))
    }

    @Test
    fun `transfer must not have a category`() {
        assertEquals(Violation.TRANSFER_WITH_CATEGORY, transfer(categoryId = food.id))
    }

    @Test
    fun `archived account and category stay valid on existing records`() {
        val archivedAccount = checking.copy(archived = true)
        val archivedCategory = food.copy(archived = true)
        val result = PostingRules.validate(
            Posting(TransactionType.EXPENSE, 100, archivedAccount.id, null, archivedCategory.id),
            PostingRefs(archivedAccount, null, archivedCategory),
        )
        assertNull(result)
    }
}
