package io.github.mstajanc.gros.data

import android.content.Context
import androidx.room3.Room
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import io.github.mstajanc.gros.data.db.GrosDatabase
import io.github.mstajanc.gros.data.recurring.RecurringGenerator
import io.github.mstajanc.gros.data.repository.AccountRepository
import io.github.mstajanc.gros.data.repository.BudgetRepository
import io.github.mstajanc.gros.data.repository.CategoryRepository
import io.github.mstajanc.gros.data.repository.RecurringRuleRepository
import io.github.mstajanc.gros.data.repository.TransactionRepository
import io.github.mstajanc.gros.domain.model.AccountType
import io.github.mstajanc.gros.domain.model.CategoryKind
import io.github.mstajanc.gros.domain.model.TransactionDraft
import io.github.mstajanc.gros.domain.model.TransactionType
import io.github.mstajanc.gros.domain.rules.InvariantViolationException
import io.github.mstajanc.gros.domain.rules.Violation
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Hodiny, ktoré sa v teste dajú posunúť; nahrádzajú systémový čas. */
class MutableClock(private var instant: Instant, private val zone: ZoneId = ZoneId.of("Europe/Bratislava")) : Clock() {
    override fun getZone(): ZoneId = zone
    override fun withZone(zone: ZoneId): Clock = MutableClock(instant, zone)
    override fun instant(): Instant = instant
    fun advanceSeconds(seconds: Long) {
        instant = instant.plusSeconds(seconds)
    }

    /** Nastaví hodiny na daný deň o 10:00 UTC. */
    fun setDate(date: LocalDate) {
        instant = date.atTime(10, 0).toInstant(ZoneOffset.UTC)
    }
}

/**
 * Základ testov nad skutočnou schémou Room v pamäti (Robolectric, bez emulátora),
 * s rovnakým ovládačom SQLite ako v appke.
 */
@RunWith(RobolectricTestRunner::class)
abstract class DatabaseTest {

    protected lateinit var db: GrosDatabase
    protected lateinit var accounts: AccountRepository
    protected lateinit var categories: CategoryRepository
    protected lateinit var transactions: TransactionRepository
    protected lateinit var rules: RecurringRuleRepository
    protected lateinit var budgets: BudgetRepository
    protected lateinit var generator: RecurringGenerator

    protected val clock = MutableClock(LocalDate.of(2026, 11, 15).atTime(10, 0).toInstant(ZoneOffset.UTC))
    protected val today: LocalDate get() = LocalDate.now(clock)

    @Before
    fun openDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder<GrosDatabase>(context)
            .setDriver(AndroidSQLiteDriver())
            .build()
        accounts = AccountRepository(db)
        categories = CategoryRepository(db)
        transactions = TransactionRepository(db, clock)
        rules = RecurringRuleRepository(db, clock)
        generator = RecurringGenerator(db, clock)
        budgets = BudgetRepository(db)
    }

    @After
    fun closeDatabase() {
        db.close()
    }

    protected suspend fun account(name: String = "Bežný účet", opening: Long = 0, includeInTotal: Boolean = true): Long =
        accounts.create(name, AccountType.CHECKING, opening, includeInTotal)

    protected suspend fun expenseCategory(name: String = "Potraviny", color: Int = 0, parentId: Long? = null): Long =
        categories.create(name, CategoryKind.EXPENSE, color, parentId)

    protected suspend fun incomeCategory(name: String = "Výplata", color: Int = 0): Long =
        categories.create(name, CategoryKind.INCOME, color)

    protected fun expense(accountId: Long, categoryId: Long, cents: Long, note: String? = null) =
        TransactionDraft(TransactionType.EXPENSE, cents, today, accountId, categoryId = categoryId, note = note)

    protected fun income(accountId: Long, categoryId: Long, cents: Long) =
        TransactionDraft(TransactionType.INCOME, cents, today, accountId, categoryId = categoryId)

    protected fun transfer(fromId: Long, toId: Long, cents: Long) =
        TransactionDraft(TransactionType.TRANSFER, cents, today, fromId, toAccountId = toId)

    /** Overí, že blok zlyhá na danom invariante. */
    protected suspend fun assertViolation(expected: Violation, block: suspend () -> Unit) {
        val error = assertFailsWith<InvariantViolationException> { block() }
        assertEquals(expected, error.violation)
    }
}
