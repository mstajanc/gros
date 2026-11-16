package io.github.mstajanc.gros.data

import io.github.mstajanc.gros.data.db.toDomain
import io.github.mstajanc.gros.domain.BalanceCalculator
import io.github.mstajanc.gros.domain.Money
import io.github.mstajanc.gros.domain.model.AccountType
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

class AccountRepositoryTest : DatabaseTest() {

    private suspend fun balanceOf(accountId: Long): Long =
        accounts.observeBalances().first().first { it.account.id == accountId }.balanceCents

    @Test
    fun `create trims the name and appends to the end of the order`() = runTest {
        val first = accounts.create("  Bežný účet ", AccountType.CHECKING, 214_050)
        val second = accounts.create("Hotovosť", AccountType.CASH, 8_500, includeInTotal = false)

        val stored = accounts.observeAccounts().first()
        assertEquals(listOf(first, second), stored.map { it.id })
        assertEquals("Bežný účet", stored[0].name)
        assertEquals(listOf(0, 1), stored.map { it.sortOrder })
        assertEquals(AccountType.CASH, stored[1].type)
        assertFalse(stored[1].includeInTotal)
        assertFalse(stored[1].archived)
    }

    @Test
    fun `create rejects blank name and oversized opening balance`() = runTest {
        assertViolation(Violation.NAME_BLANK) { accounts.create("  ", AccountType.CASH, 0) }
        assertViolation(Violation.AMOUNT_TOO_LARGE) { accounts.create("Účet", AccountType.CASH, Money.MAX_CENTS + 1) }
        assertTrue(accounts.observeAccounts().first().isEmpty())
    }

    @Test
    fun `opening balance may be negative`() = runTest {
        val id = account(opening = -15_000)
        assertEquals(-15_000, balanceOf(id))
    }

    @Test
    fun `balance is opening plus income minus expense plus transfers in minus transfers out`() = runTest {
        val checking = account("Bežný účet", opening = 100_000)
        val savings = account("Sporenie", opening = 600_000)
        val food = expenseCategory()
        val salary = incomeCategory()

        transactions.create(income(checking, salary, 290_000))
        transactions.create(expense(checking, food, 4_230))
        transactions.create(expense(checking, food, 6_120))
        transactions.create(transfer(checking, savings, 30_000))
        transactions.create(transfer(savings, checking, 5_000))

        assertEquals(100_000 + 290_000 - 4_230 - 6_120 - 30_000 + 5_000, balanceOf(checking))
        assertEquals(600_000 + 30_000 - 5_000, balanceOf(savings))
    }

    @Test
    fun `balances computed in SQL match the domain calculation`() = runTest {
        val a = account("A", opening = 10_000)
        val b = account("B", opening = -2_500)
        val c = account("C", opening = 0)
        val food = expenseCategory()
        val salary = incomeCategory()
        transactions.create(income(a, salary, 123_456))
        transactions.create(expense(a, food, 999))
        transactions.create(expense(b, food, 12_345))
        transactions.create(transfer(a, b, 50_000))
        transactions.create(transfer(b, c, 7_700))
        transactions.create(transfer(c, a, 1))

        val allAccounts = db.accountDao().getAll().map { it.toDomain() }
        val allTransactions = db.transactionDao().getAll().map { it.toDomain() }
        val expected = BalanceCalculator.balances(allAccounts, BalanceCalculator.flowsByAccount(allTransactions))

        assertEquals(expected, accounts.observeBalances().first())
    }

    @Test
    fun `transfer changes only the two balances and not the total`() = runTest {
        val checking = account("Bežný účet", opening = 100_000)
        val savings = account("Sporenie", opening = 600_000)
        val before = accounts.observeTotal().first()

        transactions.create(transfer(checking, savings, 30_000))

        assertEquals(70_000, balanceOf(checking))
        assertEquals(630_000, balanceOf(savings))
        assertEquals(before, accounts.observeTotal().first())
    }

    @Test
    fun `total counts only active accounts included in total`() = runTest {
        val checking = account("Bežný účet", opening = 214_050)
        account("Hotovosť", opening = 8_500)
        account("Sporenie", opening = 630_000)
        val investments = account("Investície", opening = 1_245_000, includeInTotal = false)
        val old = account("Starý účet", opening = 99_900)
        accounts.archive(old)

        val total = accounts.observeTotal().first()
        assertEquals(852_550, total.totalCents)
        assertEquals(3, total.includedCount)
        assertEquals(4, total.activeCount)

        // Výdavok z účtu mimo súčtu „Spolu“ nezmení; výdavok zo zahrnutého áno.
        val food = expenseCategory()
        transactions.create(expense(investments, food, 10_000))
        assertEquals(852_550, accounts.observeTotal().first().totalCents)
        transactions.create(expense(checking, food, 10_000))
        assertEquals(842_550, accounts.observeTotal().first().totalCents)
    }

    @Test
    fun `balance correction sets the balance to the entered value without creating a transaction`() = runTest {
        val id = account(opening = 100_000)
        val food = expenseCategory()
        transactions.create(expense(id, food, 4_230))
        assertEquals(95_770, balanceOf(id))

        accounts.correctBalance(id, actualBalanceCents = 90_000)

        assertEquals(90_000, balanceOf(id))
        assertEquals(94_230, accounts.get(id)!!.openingBalanceCents)
        assertEquals(1, db.transactionDao().getAll().size)
    }

    @Test
    fun `balance correction may go negative and rejects oversized values`() = runTest {
        val id = account(opening = 100_000)
        accounts.correctBalance(id, actualBalanceCents = -500)
        assertEquals(-500, balanceOf(id))
        assertViolation(Violation.AMOUNT_TOO_LARGE) { accounts.correctBalance(id, Money.MAX_CENTS + 1) }
        assertEquals(-500, balanceOf(id))
    }

    @Test
    fun `update changes name type and total flag but keeps the opening balance`() = runTest {
        val id = account("Bežný účet", opening = 100_000)
        accounts.update(id, " Hlavný účet ", AccountType.SAVINGS, includeInTotal = false)

        val updated = accounts.get(id)!!
        assertEquals("Hlavný účet", updated.name)
        assertEquals(AccountType.SAVINGS, updated.type)
        assertFalse(updated.includeInTotal)
        assertEquals(100_000, updated.openingBalanceCents)
        assertViolation(Violation.NAME_BLANK) { accounts.update(id, " ", AccountType.SAVINGS, true) }
        assertViolation(Violation.NOT_FOUND) { accounts.update(999, "X", AccountType.CASH, true) }
    }

    @Test
    fun `last active account cannot be archived`() = runTest {
        val only = account("Bežný účet")
        assertViolation(Violation.LAST_ACTIVE_ACCOUNT) { accounts.archive(only) }
        assertFalse(accounts.get(only)!!.archived)

        val second = account("Hotovosť")
        accounts.archive(only)
        assertTrue(accounts.get(only)!!.archived)
        assertViolation(Violation.LAST_ACTIVE_ACCOUNT) { accounts.archive(second) }
    }

    @Test
    fun `archived account can be restored and keeps its transactions`() = runTest {
        val first = account("Bežný účet", opening = 1_000)
        account("Hotovosť")
        val food = expenseCategory()
        transactions.create(expense(first, food, 400))

        accounts.archive(first)
        assertEquals(600, balanceOf(first))
        accounts.restore(first)

        assertFalse(accounts.get(first)!!.archived)
        assertEquals(600, balanceOf(first))
    }

    @Test
    fun `account used by a transaction cannot be deleted`() = runTest {
        val source = account("Bežný účet")
        val target = account("Sporenie")
        account("Hotovosť")
        transactions.create(transfer(source, target, 1_000))

        assertViolation(Violation.ACCOUNT_IN_USE) { accounts.delete(source) }
        assertViolation(Violation.ACCOUNT_IN_USE) { accounts.delete(target) }
        assertNotNull(accounts.get(source))
        assertNotNull(accounts.get(target))
    }

    @Test
    fun `account used by a recurring rule cannot be deleted`() = runTest {
        val used = account("Bežný účet")
        account("Hotovosť")
        val housing = expenseCategory("Bývanie")
        rules.create(
            RecurringRuleDraft(
                name = "Nájom",
                type = TransactionType.EXPENSE,
                amountCents = 52_000,
                accountId = used,
                categoryId = housing,
                frequency = Frequency.MONTHLY,
                interval = 1,
                anchorDate = today,
                nextDate = today,
            ),
        )

        assertViolation(Violation.ACCOUNT_IN_USE) { accounts.delete(used) }
    }

    @Test
    fun `unused account can be deleted but never the last active one`() = runTest {
        val first = account("Bežný účet")
        val second = account("Hotovosť")

        accounts.delete(second)
        assertNull(accounts.get(second))
        assertViolation(Violation.LAST_ACTIVE_ACCOUNT) { accounts.delete(first) }
        assertNotNull(accounts.get(first))
    }

    @Test
    fun `reorder changes the order of accounts`() = runTest {
        val a = account("A")
        val b = account("B")
        val c = account("C")

        accounts.reorder(listOf(c, a, b))

        assertEquals(listOf(c, a, b), accounts.observeAccounts().first().map { it.id })
    }
}
