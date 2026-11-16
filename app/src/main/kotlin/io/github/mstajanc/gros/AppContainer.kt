package io.github.mstajanc.gros

import android.content.Context
import io.github.mstajanc.gros.data.db.GrosDatabase
import io.github.mstajanc.gros.data.repository.AccountRepository
import io.github.mstajanc.gros.data.repository.BudgetRepository
import io.github.mstajanc.gros.data.repository.CategoryRepository
import io.github.mstajanc.gros.data.repository.RecurringRuleRepository
import io.github.mstajanc.gros.data.repository.TransactionRepository
import java.time.Clock

/** Ručný kontajner závislostí (namiesto Hiltu). Všetko vzniká až pri prvom použití. */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    /** Jediný zdroj aktuálneho času a dátumu; v testoch sa nahrádza pevnými hodinami. */
    val clock: Clock = Clock.systemDefaultZone()

    val database: GrosDatabase by lazy { GrosDatabase.create(appContext) }

    val accountRepository: AccountRepository by lazy { AccountRepository(database) }
    val categoryRepository: CategoryRepository by lazy { CategoryRepository(database) }
    val transactionRepository: TransactionRepository by lazy { TransactionRepository(database, clock) }
    val recurringRuleRepository: RecurringRuleRepository by lazy { RecurringRuleRepository(database) }
    val budgetRepository: BudgetRepository by lazy { BudgetRepository(database) }
}
