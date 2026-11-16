package io.github.mstajanc.gros.data

import io.github.mstajanc.gros.data.db.AccountEntity
import io.github.mstajanc.gros.data.db.BudgetEntity
import io.github.mstajanc.gros.data.db.CategoryEntity
import io.github.mstajanc.gros.data.db.TransactionEntity
import io.github.mstajanc.gros.domain.model.AccountType
import io.github.mstajanc.gros.domain.model.CategoryKind
import io.github.mstajanc.gros.domain.model.TransactionType
import java.io.File
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Cudzie kľúče sú poslednou poistkou pod repozitármi. Testy idú zámerne priamo cez DAO,
 * aby overili, že ich databáza naozaj vynucuje.
 */
class SchemaTest : DatabaseTest() {

    private suspend fun insertAccount(name: String = "Účet") = db.accountDao().insert(
        AccountEntity(
            name = name,
            type = AccountType.CHECKING,
            openingBalanceCents = 0,
            includeInTotal = true,
            archived = false,
            sortOrder = 0,
        ),
    )

    private suspend fun insertCategory(parentId: Long? = null) = db.categoryDao().insert(
        CategoryEntity(
            parentId = parentId,
            name = "Kategória",
            kind = CategoryKind.EXPENSE,
            color = 0,
            archived = false,
            sortOrder = 0,
        ),
    )

    private fun transactionRow(accountId: Long, categoryId: Long?, toAccountId: Long? = null) = TransactionEntity(
        type = if (toAccountId == null) TransactionType.EXPENSE else TransactionType.TRANSFER,
        amountCents = 100,
        date = LocalDate.of(2026, 11, 15),
        accountId = accountId,
        toAccountId = toAccountId,
        categoryId = categoryId,
        note = null,
        noteSearch = null,
        recurringRuleId = null,
        createdAt = 1,
        updatedAt = 1,
    )

    @Test
    fun `date round-trips as ISO text`() = runTest {
        val accountId = insertAccount()
        val categoryId = insertCategory()
        val id = db.transactionDao().insert(transactionRow(accountId, categoryId))

        assertEquals(LocalDate.of(2026, 11, 15), db.transactionDao().getById(id)!!.date)
    }

    @Test
    fun `account referenced by a transaction cannot be deleted`() = runTest {
        val source = insertAccount("Z")
        val target = insertAccount("Na")
        db.transactionDao().insert(transactionRow(source, categoryId = null, toAccountId = target))

        assertFails { db.accountDao().deleteById(source) }
        assertFails { db.accountDao().deleteById(target) }
        assertNotNull(db.accountDao().getById(source))
        assertNotNull(db.accountDao().getById(target))
    }

    @Test
    fun `category referenced by a transaction cannot be deleted`() = runTest {
        val accountId = insertAccount()
        val categoryId = insertCategory()
        db.transactionDao().insert(transactionRow(accountId, categoryId))

        assertFails { db.categoryDao().deleteById(categoryId) }
        assertNotNull(db.categoryDao().getById(categoryId))
    }

    @Test
    fun `main category with subcategories cannot be deleted`() = runTest {
        val parent = insertCategory()
        insertCategory(parentId = parent)

        assertFails { db.categoryDao().deleteById(parent) }
        assertNotNull(db.categoryDao().getById(parent))
    }

    @Test
    fun `transaction cannot point to a missing account or category`() = runTest {
        val accountId = insertAccount()
        val categoryId = insertCategory()

        assertFails { db.transactionDao().insert(transactionRow(accountId = 999, categoryId = categoryId)) }
        assertFails { db.transactionDao().insert(transactionRow(accountId = accountId, categoryId = 999)) }
        assertTrue(db.transactionDao().getAll().isEmpty())
    }

    @Test
    fun `budget is unique per category and is deleted with it`() = runTest {
        val categoryId = insertCategory()
        val budgetId = db.budgetDao().insert(BudgetEntity(categoryId = categoryId, monthlyLimitCents = 10_000))

        assertFails { db.budgetDao().insert(BudgetEntity(categoryId = categoryId, monthlyLimitCents = 20_000)) }

        db.categoryDao().deleteById(categoryId)
        assertNull(db.budgetDao().getById(budgetId))
    }

    @Test
    fun `schema version 1 is exported with all five tables`() {
        val schema = File("schemas/io.github.mstajanc.gros.data.db.GrosDatabase/1.json")
        assertTrue(schema.isFile, "Chýba exportovaná schéma ${schema.absolutePath}")
        val json = schema.readText()
        for (table in listOf("accounts", "categories", "transactions", "recurring_rules", "budgets")) {
            assertTrue(json.contains("\"tableName\": \"$table\""), "V schéme chýba tabuľka $table")
        }
        assertTrue(json.contains("\"parentId\""), "V schéme chýba Category.parentId")
    }
}
