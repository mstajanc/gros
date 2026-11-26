package io.github.mstajanc.gros.data.backup

import androidx.room3.withReadTransaction
import androidx.room3.withWriteTransaction
import io.github.mstajanc.gros.data.db.AccountEntity
import io.github.mstajanc.gros.data.db.BudgetEntity
import io.github.mstajanc.gros.data.db.CategoryEntity
import io.github.mstajanc.gros.data.db.GrosDatabase
import io.github.mstajanc.gros.data.db.RecurringRuleEntity
import io.github.mstajanc.gros.data.db.TransactionEntity
import io.github.mstajanc.gros.domain.NoteNormalizer
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Súhrn otvorenej zálohy pre potvrdzovací dialóg importu. */
data class BackupSummary(val accounts: Int, val transactions: Int)

/**
 * Export a import šifrovanej zálohy. Serializácia aj šifrovanie prebiehajú v pamäti; zápis do
 * súboru robí volajúci cez systémový dialóg. Import nahradí všetky dáta v jednej DB transakcii.
 */
class BackupRepository(private val db: GrosDatabase) {

    private val backup = db.backupDao()

    /** Zašifrovaný obsah súboru zálohy. Odvodenie kľúča trvá pár sekúnd, preto beží mimo hlavného vlákna. */
    suspend fun export(password: CharArray, iterations: Int = BackupCrypto.DEFAULT_ITERATIONS): ByteArray {
        val data = snapshot()
        return withContext(Dispatchers.Default) { BackupCrypto.encrypt(BackupCodec.encode(data), password, iterations) }
    }

    /** Dešifruje, načíta a overí zálohu; dáta v appke sa nemenia. */
    suspend fun open(file: ByteArray, password: CharArray): BackupData = withContext(Dispatchers.Default) {
        val data = BackupCodec.decode(BackupCrypto.decrypt(file, password), GrosDatabase.VERSION)
        BackupValidator.validate(data)
        data
    }

    fun summary(data: BackupData) = BackupSummary(accounts = data.accounts.size, transactions = data.transactions.size)

    /** Nahradí všetky dáta obsahom zálohy; `noteSearch` sa prepočíta. Pri chybe ostane všetko po starom. */
    suspend fun restore(data: BackupData) {
        BackupValidator.validate(data)
        db.withWriteTransaction {
            backup.deleteTransactions()
            backup.deleteBudgets()
            backup.deleteRules()
            backup.deleteSubcategories()
            backup.deleteCategories()
            backup.deleteAccounts()

            backup.insertAccounts(data.accounts.map { it.toEntity() })
            // Hlavné kategórie pred podkategóriami kvôli cudziemu kľúču `parentId`.
            backup.insertCategories(data.categories.sortedBy { it.parentId != null }.map { it.toEntity() })
            backup.insertRules(data.recurringRules.map { it.toEntity() })
            backup.insertTransactions(data.transactions.map { it.toEntity() })
            backup.insertBudgets(data.budgets.map { it.toEntity() })
        }
    }

    suspend fun snapshot(): BackupData = db.withReadTransaction {
        BackupData(
            schemaVersion = GrosDatabase.VERSION,
            accounts = db.accountDao().getAll().map { it.toBackup() },
            categories = db.categoryDao().getAll().map { it.toBackup() },
            transactions = db.transactionDao().getAll().map { it.toBackup() },
            recurringRules = db.recurringRuleDao().getAll().map { it.toBackup() },
            budgets = backup.getBudgets().map { it.toBackup() },
        )
    }
}

private fun AccountEntity.toBackup() = BackupAccount(id, name, type, openingBalanceCents, includeInTotal, archived, sortOrder)
private fun BackupAccount.toEntity() = AccountEntity(id, name, type, openingBalanceCents, includeInTotal, archived, sortOrder)

private fun CategoryEntity.toBackup() = BackupCategory(id, parentId, name, kind, color, archived, sortOrder)
private fun BackupCategory.toEntity() = CategoryEntity(id, parentId, name, kind, color, archived, sortOrder)

private fun TransactionEntity.toBackup() = BackupTransaction(
    id = id,
    type = type,
    amountCents = amountCents,
    date = date.toString(),
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    note = note,
    recurringRuleId = recurringRuleId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun BackupTransaction.toEntity() = TransactionEntity(
    id = id,
    type = type,
    amountCents = amountCents,
    date = LocalDate.parse(date),
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    note = note,
    noteSearch = NoteNormalizer.toSearch(note),
    recurringRuleId = recurringRuleId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun RecurringRuleEntity.toBackup() = BackupRule(
    id = id,
    name = name,
    type = type,
    amountCents = amountCents,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    frequency = frequency,
    interval = interval,
    anchorDate = anchorDate.toString(),
    nextDate = nextDate.toString(),
    endDate = endDate?.toString(),
    paused = paused,
)

private fun BackupRule.toEntity() = RecurringRuleEntity(
    id = id,
    name = name,
    type = type,
    amountCents = amountCents,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    frequency = frequency,
    interval = interval,
    anchorDate = LocalDate.parse(anchorDate),
    nextDate = LocalDate.parse(nextDate),
    endDate = endDate?.let(LocalDate::parse),
    paused = paused,
)

private fun BudgetEntity.toBackup() = BackupBudget(id, categoryId, monthlyLimitCents)
private fun BackupBudget.toEntity() = BudgetEntity(id, categoryId, monthlyLimitCents)
