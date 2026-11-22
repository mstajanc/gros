package io.github.mstajanc.gros.data.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import androidx.room3.Update
import io.github.mstajanc.gros.domain.model.CategoryKind
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder, id")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Query("SELECT COUNT(*) FROM accounts")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM accounts WHERE archived = 0")
    suspend fun countActive(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM accounts")
    suspend fun maxSortOrder(): Int

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("UPDATE accounts SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder, id")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): CategoryEntity?

    @Query("SELECT COUNT(*) FROM categories WHERE parentId = :parentId")
    suspend fun countChildren(parentId: Long): Int

    @Query("SELECT id FROM categories WHERE parentId = :parentId")
    suspend fun getChildIds(parentId: Long): List<Long>

    @Query("SELECT COUNT(*) FROM categories WHERE parentId IS NULL AND archived = 0 AND kind = :kind")
    suspend fun countActiveMain(kind: CategoryKind): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM categories WHERE parentId IS NULL AND kind = :kind")
    suspend fun maxMainSortOrder(kind: CategoryKind): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM categories WHERE parentId = :parentId")
    suspend fun maxChildSortOrder(parentId: Long): Int

    @Insert
    suspend fun insert(category: CategoryEntity): Long

    @Update
    suspend fun update(category: CategoryEntity)

    @Query("UPDATE categories SET color = :color WHERE parentId = :parentId")
    suspend fun setChildrenColor(parentId: Long, color: Int)

    @Query("UPDATE categories SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: Long)
}

/** Výdavky jednej kategórie za obdobie. */
data class CategorySumRow(val categoryId: Long, val cents: Long)

/** Príjmy a výdavky za obdobie (prevody sa nepočítajú). */
data class TotalsRow(val incomeCents: Long, val expenseCents: Long)

/** Príjmy a výdavky jedného mesiaca; `month` je text `RRRR-MM`. */
data class MonthTotalsRow(val month: String, val incomeCents: Long, val expenseCents: Long)

/** Súčty pohybov jedného účtu spočítané v SQL. */
data class AccountFlowRow(
    val accountId: Long,
    val incomeCents: Long,
    val expenseCents: Long,
    val transferInCents: Long,
    val transferOutCents: Long,
)

// Prevod sa počíta dvakrát: ako odchod zo zdrojového účtu a ako príchod na cieľový.
private const val ACCOUNT_FLOWS_SQL = """
    SELECT accountId,
           SUM(incomeCents) AS incomeCents,
           SUM(expenseCents) AS expenseCents,
           SUM(transferInCents) AS transferInCents,
           SUM(transferOutCents) AS transferOutCents
    FROM (
        SELECT accountId,
               CASE WHEN type = 'INCOME' THEN amountCents ELSE 0 END AS incomeCents,
               CASE WHEN type = 'EXPENSE' THEN amountCents ELSE 0 END AS expenseCents,
               0 AS transferInCents,
               CASE WHEN type = 'TRANSFER' THEN amountCents ELSE 0 END AS transferOutCents
        FROM transactions
        UNION ALL
        SELECT toAccountId AS accountId, 0, 0, amountCents, 0
        FROM transactions
        WHERE type = 'TRANSFER' AND toAccountId IS NOT NULL
    )
    GROUP BY accountId
"""

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions ORDER BY date DESC, createdAt DESC, id DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId OR toAccountId = :accountId")
    suspend fun countByAccount(accountId: Long): Int

    @Query("SELECT COUNT(*) FROM transactions WHERE categoryId = :categoryId")
    suspend fun countByCategory(categoryId: Long): Int

    /**
     * Zoznam transakcií od najnovšej. Každý filter je nepovinný a dajú sa kombinovať:
     * obdobie (vrátane oboch krajných dní), účet (aj ako cieľ prevodu), kategórie a hľadanie
     * v poznámke alebo v názve kategórie ([queryCategoryIds] sú kategórie, ktorých názov hľadaný text obsahuje).
     */
    @Query(
        """
        SELECT * FROM transactions
        WHERE (:from IS NULL OR date >= :from)
          AND (:to IS NULL OR date <= :to)
          AND (:accountId IS NULL OR accountId = :accountId OR toAccountId = :accountId)
          AND (:filterCategories = 0 OR categoryId IN (:categoryIds))
          AND (:notePattern IS NULL OR noteSearch LIKE :notePattern ESCAPE '\' OR categoryId IN (:queryCategoryIds))
        ORDER BY date DESC, createdAt DESC, id DESC
        """,
    )
    fun observeFiltered(
        from: LocalDate?,
        to: LocalDate?,
        accountId: Long?,
        filterCategories: Boolean,
        categoryIds: List<Long>,
        notePattern: String?,
        queryCategoryIds: List<Long>,
    ): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY date DESC, createdAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    /** Predošlé poznámky obsahujúce hľadaný text: najprv najčastejšie, pri zhode novšie. */
    @Query(
        """
        SELECT note FROM transactions
        WHERE note IS NOT NULL AND noteSearch LIKE :notePattern ESCAPE '\'
        GROUP BY note
        ORDER BY COUNT(*) DESC, MAX(createdAt) DESC
        LIMIT :limit
        """,
    )
    suspend fun suggestNotes(notePattern: String, limit: Int): List<String>

    /** Výdavky po kategóriách za obdobie (krajné dni vrátane, `null` = bez obmedzenia). */
    @Query(
        """
        SELECT categoryId, SUM(amountCents) AS cents FROM transactions
        WHERE type = 'EXPENSE' AND categoryId IS NOT NULL
          AND (:from IS NULL OR date >= :from) AND (:to IS NULL OR date <= :to)
        GROUP BY categoryId
        """,
    )
    fun observeExpenseByCategory(from: LocalDate?, to: LocalDate?): Flow<List<CategorySumRow>>

    @Query(
        """
        SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE 0 END), 0) AS incomeCents,
               COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountCents ELSE 0 END), 0) AS expenseCents
        FROM transactions
        WHERE (:from IS NULL OR date >= :from) AND (:to IS NULL OR date <= :to)
        """,
    )
    fun observeTotals(from: LocalDate?, to: LocalDate?): Flow<TotalsRow>

    @Query(
        """
        SELECT substr(date, 1, 7) AS month,
               COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amountCents ELSE 0 END), 0) AS incomeCents,
               COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amountCents ELSE 0 END), 0) AS expenseCents
        FROM transactions
        WHERE date >= :from AND date <= :to
        GROUP BY month
        """,
    )
    fun observeMonthlyTotals(from: LocalDate, to: LocalDate): Flow<List<MonthTotalsRow>>

    /** Dátum najstaršej transakcie (ISO text) alebo `null`. */
    @Query("SELECT MIN(date) FROM transactions")
    fun observeEarliestDate(): Flow<String?>

    @Query(ACCOUNT_FLOWS_SQL)
    fun observeAccountFlows(): Flow<List<AccountFlowRow>>

    @Query(ACCOUNT_FLOWS_SQL)
    suspend fun getAccountFlows(): List<AccountFlowRow>

    @Insert
    suspend fun insert(transaction: TransactionEntity): Long

    @Update
    suspend fun update(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface RecurringRuleDao {
    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun getById(id: Long): RecurringRuleEntity?

    @Query("SELECT * FROM recurring_rules ORDER BY nextDate, id")
    suspend fun getAll(): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules ORDER BY nextDate, id")
    fun observeAll(): Flow<List<RecurringRuleEntity>>

    /** Nepozastavené a neukončené pravidlá, ktorých platba je dnes alebo skôr. */
    @Query(
        """
        SELECT * FROM recurring_rules
        WHERE paused = 0 AND nextDate <= :today AND (endDate IS NULL OR nextDate <= endDate)
        ORDER BY nextDate, id
        """,
    )
    suspend fun getDue(today: LocalDate): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE accountId = :accountId OR toAccountId = :accountId ORDER BY id")
    suspend fun getUsingAccount(accountId: Long): List<RecurringRuleEntity>

    @Query("SELECT * FROM recurring_rules WHERE categoryId IN (:categoryIds) ORDER BY id")
    suspend fun getUsingCategories(categoryIds: List<Long>): List<RecurringRuleEntity>

    @Query("SELECT COUNT(*) FROM recurring_rules WHERE accountId = :accountId OR toAccountId = :accountId")
    suspend fun countByAccount(accountId: Long): Int

    @Query("SELECT COUNT(*) FROM recurring_rules WHERE categoryId = :categoryId")
    suspend fun countByCategory(categoryId: Long): Int

    @Insert
    suspend fun insert(rule: RecurringRuleEntity): Long

    @Update
    suspend fun update(rule: RecurringRuleEntity)

    @Query("DELETE FROM recurring_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY id")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE id = :id")
    suspend fun getById(id: Long): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId")
    suspend fun getByCategory(categoryId: Long): BudgetEntity?

    @Insert
    suspend fun insert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteById(id: Long)
}
