package io.github.mstajanc.gros.data.backup

import io.github.mstajanc.gros.domain.model.AccountType
import io.github.mstajanc.gros.domain.model.CategoryKind
import io.github.mstajanc.gros.domain.model.Frequency
import io.github.mstajanc.gros.domain.model.TransactionType
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.junit.Test

/** Formát obsahu zálohy (JSON + gzip) a jeho validácia. */
class BackupContentTest {

    private val valid = BackupData(
        schemaVersion = 1,
        accounts = listOf(
            BackupAccount(1, "Bežný účet", AccountType.CHECKING, 120_000, true, false, 0),
            BackupAccount(2, "Hotovosť", AccountType.CASH, 5_000, true, false, 1),
        ),
        categories = listOf(
            BackupCategory(10, null, "Auto", CategoryKind.EXPENSE, 2, false, 0),
            BackupCategory(11, 10, "Tankovanie", CategoryKind.EXPENSE, 2, false, 0),
            BackupCategory(20, null, "Výplata", CategoryKind.INCOME, 0, false, 0),
        ),
        transactions = listOf(
            BackupTransaction(100, TransactionType.EXPENSE, 6_120, "2026-11-20", 1, null, 11, "Shell", 200, 1L, 2L),
            BackupTransaction(101, TransactionType.INCOME, 180_000, "2026-11-01", 1, null, 20, null, null, 3L, 3L),
            BackupTransaction(102, TransactionType.TRANSFER, 10_000, "2026-11-02", 1, 2, null, null, null, 4L, 4L),
        ),
        recurringRules = listOf(
            BackupRule(200, "Tankovanie", TransactionType.EXPENSE, 6_120, 1, null, 11, Frequency.MONTHLY, 1, "2026-10-20", "2026-12-20", null, false),
        ),
        budgets = listOf(BackupBudget(300, 10, 20_000)),
    )

    private fun invalid(data: BackupData) = assertNotNull(BackupValidator.problem(data))

    private fun gzip(text: String): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(text.encodeToByteArray()) }
        return out.toByteArray()
    }

    private fun assertError(expected: BackupError, block: () -> Unit) {
        assertEquals(expected, assertFailsWith<BackupException> { block() }.error)
    }

    @Test
    fun `codec round trips all tables`() {
        assertEquals(valid, BackupCodec.decode(BackupCodec.encode(valid), currentSchemaVersion = 1))
    }

    @Test
    fun `newer schema version is rejected`() {
        val newer = BackupCodec.encode(valid.copy(schemaVersion = 2))
        assertError(BackupError.NEWER_SCHEMA) { BackupCodec.decode(newer, currentSchemaVersion = 1) }
    }

    @Test
    fun `broken gzip or json is invalid content`() {
        assertError(BackupError.INVALID_CONTENT) { BackupCodec.decode("nie je gzip".encodeToByteArray(), 1) }
        assertError(BackupError.INVALID_CONTENT) { BackupCodec.decode(gzip("{\"schemaVersion\": 1}"), 1) }
        assertError(BackupError.INVALID_CONTENT) { BackupCodec.decode(gzip("[1, 2]"), 1) }
        assertError(BackupError.INVALID_CONTENT) { BackupCodec.decode(gzip("{ nie je json"), 1) }
        // Neznáme pole znamená cudzí alebo novší formát.
        assertError(BackupError.INVALID_CONTENT) {
            BackupCodec.decode(gzip("""{"schemaVersion":1,"accounts":[],"categories":[],"transactions":[],"recurringRules":[],"budgets":[],"extra":1}"""), 1)
        }
    }

    @Test
    fun `valid backup passes`() {
        assertNull(BackupValidator.problem(valid))
    }

    @Test
    fun `duplicate ids are rejected`() {
        invalid(valid.copy(accounts = valid.accounts + valid.accounts.first()))
        invalid(valid.copy(transactions = valid.transactions + valid.transactions.first().copy(amountCents = 1)))
    }

    @Test
    fun `at least one active account and active main category of each kind is required`() {
        invalid(valid.copy(accounts = valid.accounts.map { it.copy(archived = true) }))
        invalid(valid.copy(categories = valid.categories.map { if (it.id == 20L) it.copy(archived = true) else it }))
    }

    @Test
    fun `categories keep two levels with the kind of their parent`() {
        invalid(valid.copy(categories = valid.categories + BackupCategory(12, 11, "Diesel", CategoryKind.EXPENSE, 2, false, 0)))
        invalid(valid.copy(categories = valid.categories + BackupCategory(21, 10, "Bonus", CategoryKind.INCOME, 2, false, 1)))
        invalid(valid.copy(categories = valid.categories + BackupCategory(13, 99, "Sirota", CategoryKind.EXPENSE, 2, false, 1)))
        invalid(valid.copy(categories = valid.categories.map { if (it.id == 10L) it.copy(color = 6) else it }))
    }

    @Test
    fun `transactions reference existing rows and follow posting rules`() {
        val tx = valid.transactions
        invalid(valid.copy(transactions = tx + tx[0].copy(id = 110, accountId = 9)))
        invalid(valid.copy(transactions = tx + tx[0].copy(id = 110, categoryId = 20)))
        invalid(valid.copy(transactions = tx + tx[2].copy(id = 110, categoryId = 10)))
        invalid(valid.copy(transactions = tx + tx[2].copy(id = 110, toAccountId = 1)))
        invalid(valid.copy(transactions = tx + tx[0].copy(id = 110, amountCents = 0)))
        invalid(valid.copy(transactions = tx + tx[0].copy(id = 110, recurringRuleId = 999)))
        invalid(valid.copy(transactions = tx + tx[0].copy(id = 110, date = "2026-13-01")))
    }

    @Test
    fun `rules and budgets follow their invariants`() {
        invalid(valid.copy(recurringRules = valid.recurringRules.map { it.copy(interval = 0) }))
        invalid(valid.copy(recurringRules = valid.recurringRules.map { it.copy(name = " ") }))
        invalid(valid.copy(budgets = listOf(BackupBudget(300, 11, 20_000))))
        invalid(valid.copy(budgets = listOf(BackupBudget(300, 20, 20_000))))
        invalid(valid.copy(budgets = valid.budgets + BackupBudget(301, 10, 5_000)))
    }
}
