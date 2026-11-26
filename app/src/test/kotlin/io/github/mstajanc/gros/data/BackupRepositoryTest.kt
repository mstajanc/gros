package io.github.mstajanc.gros.data

import io.github.mstajanc.gros.data.backup.BackupError
import io.github.mstajanc.gros.data.backup.BackupException
import io.github.mstajanc.gros.data.backup.BackupRepository
import io.github.mstajanc.gros.data.backup.BackupSummary
import io.github.mstajanc.gros.domain.model.Frequency
import io.github.mstajanc.gros.domain.model.RecurringRuleDraft
import io.github.mstajanc.gros.domain.model.TransactionType
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** Akceptačné kritérium M5: export → vymazanie dát → import vráti identické dáta. */
class BackupRepositoryTest : DatabaseTest() {

    private val backup by lazy { BackupRepository(db) }
    private val password get() = "heslo zálohy 42".toCharArray()

    /** Rýchle odvodenie kľúča v testoch; predvolený počet overuje BackupCryptoTest. */
    private val fast = 1_000

    private data class Tables(
        val accounts: List<Any>,
        val categories: List<Any>,
        val transactions: List<Any>,
        val rules: List<Any>,
        val budgets: List<Any>,
    )

    private suspend fun tables() = Tables(
        db.accountDao().getAll(),
        db.categoryDao().getAll(),
        db.transactionDao().getAll(),
        db.recurringRuleDao().getAll(),
        db.backupDao().getBudgets(),
    )

    private suspend fun fillSampleData() {
        val bank = account("Bežný účet", opening = 120_000)
        val cash = account("Hotovosť", opening = -2_500, includeInTotal = false)
        val savings = account("Sporenie")
        accounts.archive(savings)
        val food = expenseCategory("Potraviny", color = 0)
        val car = expenseCategory("Auto", color = 2)
        val fuel = expenseCategory("Tankovanie", parentId = car)
        val old = expenseCategory("Staré", color = 5)
        categories.archive(old)
        val salary = incomeCategory("Výplata")
        val rule = rules.create(
            RecurringRuleDraft(
                name = "Nájom",
                type = TransactionType.EXPENSE,
                amountCents = 52_000,
                accountId = bank,
                categoryId = food,
                frequency = Frequency.MONTHLY,
                interval = 1,
                anchorDate = today.minusMonths(1),
                nextDate = today.plusDays(3),
                endDate = today.plusYears(1),
            ),
        )
        transactions.create(expense(bank, fuel, 6_120, note = "Shell Alza"))
        transactions.create(expense(cash, food, 1_250))
        transactions.create(income(bank, salary, 180_000))
        transactions.create(transfer(bank, cash, 10_000))
        transactions.create(expense(bank, food, 52_000, note = "Nájom"), recurringRuleId = rule)
        rules.setPaused(rule, true)
        budgets.create(car, 20_000)
        budgets.create(food, 40_000)
    }

    @Test
    fun `export, wipe and import returns identical data`() = runTest {
        fillSampleData()
        val before = tables()
        val file = backup.export(password, iterations = fast)

        // Vymazanie dát: nahradenie úplne inými dátami.
        backup.restore(backup.snapshot().copy(transactions = emptyList(), recurringRules = emptyList(), budgets = emptyList()))
        assertEquals(0, db.transactionDao().getAll().size)

        val data = backup.open(file, password)
        assertEquals(BackupSummary(accounts = 3, transactions = 5), backup.summary(data))
        backup.restore(data)

        assertEquals(before, tables())
    }

    @Test
    fun `wrong password leaves current data untouched`() = runTest {
        fillSampleData()
        val file = backup.export(password, iterations = fast)
        val before = tables()

        val error = assertFailsWith<BackupException> { backup.open(file, "iné heslo 0000".toCharArray()) }
        assertEquals(BackupError.WRONG_PASSWORD_OR_CORRUPTED, error.error)
        assertEquals(before, tables())
    }

    @Test
    fun `invalid content is rejected before anything is replaced`() = runTest {
        fillSampleData()
        val before = tables()
        val broken = backup.snapshot().let { it.copy(transactions = it.transactions.map { tx -> tx.copy(accountId = 999) }) }

        val error = assertFailsWith<BackupException> { backup.restore(broken) }
        assertEquals(BackupError.INVALID_CONTENT, error.error)
        assertEquals(before, tables())
    }

    @Test
    fun `note search is recomputed on import`() = runTest {
        fillSampleData()
        val file = backup.export(password, iterations = fast)
        db.backupDao().deleteTransactions()

        backup.restore(backup.open(file, password))

        val shell = db.transactionDao().getAll().first { it.note == "Shell Alza" }
        assertEquals("shell alza", shell.noteSearch)
    }
}
