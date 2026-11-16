package io.github.mstajanc.gros.domain

import io.github.mstajanc.gros.domain.model.Account
import io.github.mstajanc.gros.domain.model.AccountType
import io.github.mstajanc.gros.domain.model.Transaction
import io.github.mstajanc.gros.domain.model.TransactionType
import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.Test

class BalanceCalculatorTest {

    private val day = LocalDate.of(2026, 11, 15)

    private fun account(
        id: Long,
        opening: Long = 0,
        includeInTotal: Boolean = true,
        archived: Boolean = false,
    ) = Account(
        id = id,
        name = "Účet $id",
        type = AccountType.CHECKING,
        openingBalanceCents = opening,
        includeInTotal = includeInTotal,
        archived = archived,
        sortOrder = id.toInt(),
    )

    private fun expense(accountId: Long, cents: Long) = tx(TransactionType.EXPENSE, accountId, cents, categoryId = 1)
    private fun income(accountId: Long, cents: Long) = tx(TransactionType.INCOME, accountId, cents, categoryId = 2)
    private fun transfer(from: Long, to: Long, cents: Long) = tx(TransactionType.TRANSFER, from, cents, toAccountId = to)

    private fun tx(
        type: TransactionType,
        accountId: Long,
        cents: Long,
        toAccountId: Long? = null,
        categoryId: Long? = null,
    ) = Transaction(
        type = type,
        amountCents = cents,
        date = day,
        accountId = accountId,
        toAccountId = toAccountId,
        categoryId = categoryId,
        createdAt = 0,
        updatedAt = 0,
    )

    @Test
    fun `balance is opening plus income minus expense plus transfers in minus transfers out`() {
        val flows = AccountFlows(
            incomeCents = 290_000,
            expenseCents = 131_240,
            transferInCents = 5_000,
            transferOutCents = 30_000,
        )
        assertEquals(100_000 + 290_000 - 131_240 + 5_000 - 30_000, BalanceCalculator.balance(100_000, flows))
    }

    @Test
    fun `account without transactions has its opening balance`() {
        val balances = BalanceCalculator.balances(listOf(account(1, opening = 8_500)), emptyMap())
        assertEquals(8_500, balances.single().balanceCents)
    }

    @Test
    fun `flows are grouped by account`() {
        val flows = BalanceCalculator.flowsByAccount(
            listOf(income(1, 290_000), expense(1, 4_230), expense(1, 6_120), expense(2, 2_400)),
        )
        assertEquals(AccountFlows(incomeCents = 290_000, expenseCents = 10_350), flows[1])
        assertEquals(AccountFlows(expenseCents = 2_400), flows[2])
    }

    @Test
    fun `transfer changes both balances but is neither income nor expense`() {
        val flows = BalanceCalculator.flowsByAccount(listOf(transfer(from = 1, to = 2, cents = 30_000)))
        assertEquals(AccountFlows(transferOutCents = 30_000), flows[1])
        assertEquals(AccountFlows(transferInCents = 30_000), flows[2])

        val balances = BalanceCalculator.balances(
            listOf(account(1, opening = 100_000), account(2, opening = 600_000)),
            flows,
        ).associate { it.account.id to it.balanceCents }
        assertEquals(70_000, balances[1])
        assertEquals(630_000, balances[2])
    }

    @Test
    fun `transfer between two included accounts does not change the total`() {
        val accounts = listOf(account(1, opening = 100_000), account(2, opening = 600_000))
        val before = BalanceCalculator.total(BalanceCalculator.balances(accounts, emptyMap()))
        val flows = BalanceCalculator.flowsByAccount(listOf(transfer(1, 2, 30_000)))
        val after = BalanceCalculator.total(BalanceCalculator.balances(accounts, flows))
        assertEquals(before.totalCents, after.totalCents)
    }

    @Test
    fun `negative balance is allowed`() {
        val flows = BalanceCalculator.flowsByAccount(listOf(expense(1, 5_000)))
        val balances = BalanceCalculator.balances(listOf(account(1, opening = 1_000)), flows)
        assertEquals(-4_000, balances.single().balanceCents)
    }

    @Test
    fun `balances keep the order of accounts`() {
        val accounts = listOf(account(3), account(1), account(2))
        assertEquals(listOf(3L, 1L, 2L), BalanceCalculator.balances(accounts, emptyMap()).map { it.account.id })
    }

    @Test
    fun `total sums only active accounts included in total and reports N of M`() {
        val accounts = listOf(
            account(1, opening = 214_050),
            account(2, opening = 8_500),
            account(3, opening = 630_000),
            account(4, opening = 1_245_000, includeInTotal = false),
            account(5, opening = 99_900, archived = true),
        )
        val total = BalanceCalculator.total(BalanceCalculator.balances(accounts, emptyMap()))
        assertEquals(852_550, total.totalCents)
        assertEquals(3, total.includedCount)
        assertEquals(4, total.activeCount)
    }

    @Test
    fun `expenses of an account outside the total still change its own balance only`() {
        val accounts = listOf(account(1, opening = 10_000), account(2, opening = 50_000, includeInTotal = false))
        val flows = BalanceCalculator.flowsByAccount(listOf(expense(2, 7_000)))
        val balances = BalanceCalculator.balances(accounts, flows)
        assertEquals(43_000, balances.first { it.account.id == 2L }.balanceCents)
        assertEquals(10_000, BalanceCalculator.total(balances).totalCents)
    }

    @Test
    fun `balance correction moves the opening balance by the difference`() {
        // Účet: počiatočný stav 1 000 €, zostatok po transakciách 2 140,50 €, skutočnosť 2 100 €.
        val newOpening = BalanceCalculator.openingBalanceForCorrection(
            currentOpeningCents = 100_000,
            currentBalanceCents = 214_050,
            actualBalanceCents = 210_000,
        )
        assertEquals(95_950, newOpening)
        // Po oprave vychádza zostatok presne na zadanú hodnotu.
        val flows = AccountFlows(incomeCents = 114_050)
        assertEquals(210_000, BalanceCalculator.balance(newOpening, flows))
    }
}
