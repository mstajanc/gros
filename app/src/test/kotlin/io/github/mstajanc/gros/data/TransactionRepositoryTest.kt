package io.github.mstajanc.gros.data

import io.github.mstajanc.gros.domain.model.Frequency
import io.github.mstajanc.gros.domain.model.RecurringRuleDraft
import io.github.mstajanc.gros.domain.model.TransactionDraft
import io.github.mstajanc.gros.domain.model.TransactionType
import io.github.mstajanc.gros.domain.rules.Violation
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TransactionRepositoryTest : DatabaseTest() {

    @Test
    fun `create stores the expense with derived fields`() = runTest {
        val accountId = account()
        val food = expenseCategory()

        val id = transactions.create(expense(accountId, food, 1_290, note = "  Alza kábel USB-C "))

        val stored = transactions.get(id)!!
        assertEquals(TransactionType.EXPENSE, stored.type)
        assertEquals(1_290, stored.amountCents)
        assertEquals(today, stored.date)
        assertEquals(accountId, stored.accountId)
        assertEquals(food, stored.categoryId)
        assertNull(stored.toAccountId)
        assertEquals("Alza kábel USB-C", stored.note)
        assertEquals("alza kabel usb-c", stored.noteSearch)
        assertNull(stored.recurringRuleId)
        assertEquals(clock.millis(), stored.createdAt)
        assertEquals(clock.millis(), stored.updatedAt)
    }

    @Test
    fun `blank note is stored as null`() = runTest {
        val id = transactions.create(expense(account(), expenseCategory(), 100, note = "   "))
        val stored = transactions.get(id)!!
        assertNull(stored.note)
        assertNull(stored.noteSearch)
    }

    @Test
    fun `expense can be saved to a main category and to a subcategory`() = runTest {
        val accountId = account()
        val car = expenseCategory("Auto", color = 2)
        val fuel = expenseCategory("Tankovanie", parentId = car)

        val inMain = transactions.create(expense(accountId, car, 5_000))
        val inSub = transactions.create(expense(accountId, fuel, 6_120))

        assertEquals(car, transactions.get(inMain)!!.categoryId)
        assertEquals(fuel, transactions.get(inSub)!!.categoryId)
    }

    @Test
    fun `transfer is one row with two accounts and no category`() = runTest {
        val checking = account("Bežný účet")
        val savings = account("Sporenie")

        val id = transactions.create(transfer(checking, savings, 30_000))

        val stored = transactions.get(id)!!
        assertEquals(TransactionType.TRANSFER, stored.type)
        assertEquals(checking, stored.accountId)
        assertEquals(savings, stored.toAccountId)
        assertNull(stored.categoryId)
        assertEquals(1, db.transactionDao().getAll().size)
    }

    @Test
    fun `invalid transactions are rejected and nothing is stored`() = runTest {
        val checking = account("Bežný účet")
        val savings = account("Sporenie")
        val food = expenseCategory()
        val salary = incomeCategory()

        assertViolation(Violation.AMOUNT_NOT_POSITIVE) { transactions.create(expense(checking, food, 0)) }
        assertViolation(Violation.AMOUNT_NOT_POSITIVE) { transactions.create(transfer(checking, savings, -5)) }
        assertViolation(Violation.AMOUNT_TOO_LARGE) { transactions.create(expense(checking, food, 1_000_000_000)) }
        assertViolation(Violation.ACCOUNT_NOT_FOUND) { transactions.create(expense(999, food, 100)) }
        assertViolation(Violation.CATEGORY_MISSING) {
            transactions.create(TransactionDraft(TransactionType.EXPENSE, 100, today, checking))
        }
        assertViolation(Violation.CATEGORY_NOT_FOUND) { transactions.create(expense(checking, 999, 100)) }
        assertViolation(Violation.CATEGORY_KIND_MISMATCH) { transactions.create(expense(checking, salary, 100)) }
        assertViolation(Violation.CATEGORY_KIND_MISMATCH) { transactions.create(income(checking, food, 100)) }
        assertViolation(Violation.TARGET_ACCOUNT_NOT_ALLOWED) {
            transactions.create(expense(checking, food, 100).copy(toAccountId = savings))
        }
        assertViolation(Violation.TRANSFER_TARGET_MISSING) {
            transactions.create(TransactionDraft(TransactionType.TRANSFER, 100, today, checking))
        }
        assertViolation(Violation.TRANSFER_SAME_ACCOUNT) { transactions.create(transfer(checking, checking, 100)) }
        assertViolation(Violation.TRANSFER_TARGET_NOT_FOUND) { transactions.create(transfer(checking, 999, 100)) }
        assertViolation(Violation.TRANSFER_WITH_CATEGORY) {
            transactions.create(transfer(checking, savings, 100).copy(categoryId = food))
        }

        assertTrue(db.transactionDao().getAll().isEmpty())
    }

    @Test
    fun `update changes all fields but keeps creation time and rule link`() = runTest {
        val checking = account("Bežný účet")
        val cash = account("Hotovosť")
        val food = expenseCategory("Potraviny")
        val fun_ = expenseCategory("Zábava", color = 4)
        val id = transactions.create(expense(checking, food, 1_000, note = "Lidl"))
        val created = transactions.get(id)!!

        clock.advanceSeconds(90)
        transactions.update(
            id,
            TransactionDraft(
                type = TransactionType.EXPENSE,
                amountCents = 2_400,
                date = today.minusDays(3),
                accountId = cash,
                categoryId = fun_,
                note = "Kino Úsmev",
            ),
        )

        val updated = transactions.get(id)!!
        assertEquals(2_400, updated.amountCents)
        assertEquals(today.minusDays(3), updated.date)
        assertEquals(cash, updated.accountId)
        assertEquals(fun_, updated.categoryId)
        assertEquals("Kino Úsmev", updated.note)
        assertEquals("kino usmev", updated.noteSearch)
        assertEquals(created.createdAt, updated.createdAt)
        assertEquals(created.updatedAt + 90_000, updated.updatedAt)
    }

    @Test
    fun `type can be changed when the fields follow the new type`() = runTest {
        val checking = account("Bežný účet")
        val savings = account("Sporenie")
        val food = expenseCategory()
        val salary = incomeCategory()
        val id = transactions.create(expense(checking, food, 1_000))

        // Výdavok → príjem si vyžiada kategóriu príjmu.
        assertViolation(Violation.CATEGORY_KIND_MISMATCH) { transactions.update(id, income(checking, food, 1_000)) }
        transactions.update(id, income(checking, salary, 1_000))
        assertEquals(TransactionType.INCOME, transactions.get(id)!!.type)

        // Zmena na prevod kategóriu odstráni a vyžiada cieľový účet.
        assertViolation(Violation.TRANSFER_WITH_CATEGORY) {
            transactions.update(id, transfer(checking, savings, 1_000).copy(categoryId = salary))
        }
        transactions.update(id, transfer(checking, savings, 1_000))
        val asTransfer = transactions.get(id)!!
        assertEquals(TransactionType.TRANSFER, asTransfer.type)
        assertEquals(savings, asTransfer.toAccountId)
        assertNull(asTransfer.categoryId)
    }

    @Test
    fun `failed update leaves the transaction unchanged`() = runTest {
        val accountId = account()
        val food = expenseCategory()
        val id = transactions.create(expense(accountId, food, 1_000))
        val before = transactions.get(id)

        assertViolation(Violation.AMOUNT_NOT_POSITIVE) { transactions.update(id, expense(accountId, food, 0)) }
        assertViolation(Violation.NOT_FOUND) { transactions.update(999, expense(accountId, food, 100)) }

        assertEquals(before, transactions.get(id))
    }

    @Test
    fun `transaction of an archived account and category can still be edited`() = runTest {
        val old = account("Starý účet")
        account("Bežný účet")
        expenseCategory("Potraviny")
        val oldCategory = expenseCategory("Staré")
        val id = transactions.create(expense(old, oldCategory, 1_000))
        accounts.archive(old)
        categories.archive(oldCategory)

        transactions.update(id, expense(old, oldCategory, 1_500, note = "oprava"))

        assertEquals(1_500, transactions.get(id)!!.amountCents)
    }

    @Test
    fun `deleted transaction is restored with its original id and times`() = runTest {
        val accountId = account()
        val food = expenseCategory()
        transactions.create(expense(accountId, food, 500))
        val id = transactions.create(expense(accountId, food, 4_230, note = "Lidl"))
        val original = transactions.get(id)!!

        val deleted = transactions.delete(id)
        assertEquals(original, deleted)
        assertNull(transactions.get(id))

        clock.advanceSeconds(3)
        transactions.create(expense(accountId, food, 700))
        transactions.restore(deleted!!)

        assertEquals(original, transactions.get(id))
        assertEquals(3, db.transactionDao().getAll().size)
    }

    @Test
    fun `delete of a missing transaction returns null`() = runTest {
        assertNull(transactions.delete(12345))
    }

    @Test
    fun `restore after an edit brings back the original values`() = runTest {
        val accountId = account()
        val food = expenseCategory()
        val id = transactions.create(expense(accountId, food, 4_230, note = "Lidl"))
        val original = transactions.get(id)!!

        clock.advanceSeconds(10)
        transactions.update(id, expense(accountId, food, 9_999, note = "Preklep"))
        transactions.restore(original)

        assertEquals(original, transactions.get(id))
        assertEquals(1, db.transactionDao().getAll().size)
    }

    @Test
    fun `restore drops the link to a rule that was deleted meanwhile`() = runTest {
        val accountId = account()
        val housing = expenseCategory("Bývanie")
        val ruleId = rules.create(
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
        val id = transactions.create(expense(accountId, housing, 52_000, note = "Nájom"), recurringRuleId = ruleId)
        val deleted = transactions.delete(id)!!
        assertEquals(ruleId, deleted.recurringRuleId)

        rules.delete(ruleId)
        transactions.restore(deleted)

        val restored = transactions.get(id)
        assertNotNull(restored)
        assertNull(restored.recurringRuleId)
    }
}
