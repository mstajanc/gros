package io.github.mstajanc.gros

import android.content.Context
import io.github.mstajanc.gros.data.db.GrosDatabase
import io.github.mstajanc.gros.data.repository.AccountRepository
import io.github.mstajanc.gros.data.repository.BudgetRepository
import io.github.mstajanc.gros.data.repository.CategoryRepository
import io.github.mstajanc.gros.data.repository.RecurringRuleRepository
import io.github.mstajanc.gros.data.repository.SetupRepository
import io.github.mstajanc.gros.data.repository.TransactionRepository
import io.github.mstajanc.gros.data.settings.SettingsRepository
import io.github.mstajanc.gros.ui.common.SnackbarBus
import java.time.Clock
import java.time.LocalDate

/** Ručný kontajner závislostí (namiesto Hiltu). Všetko vzniká až pri prvom použití. */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    /** Jediný zdroj aktuálneho času a dátumu; v testoch sa nahrádza pevnými hodinami. */
    val clock: Clock = Clock.systemDefaultZone()

    fun today(): LocalDate = LocalDate.now(clock)

    val database: GrosDatabase by lazy { GrosDatabase.create(appContext) }

    val accountRepository: AccountRepository by lazy { AccountRepository(database) }
    val categoryRepository: CategoryRepository by lazy { CategoryRepository(database) }
    val transactionRepository: TransactionRepository by lazy { TransactionRepository(database, clock) }
    val recurringRuleRepository: RecurringRuleRepository by lazy { RecurringRuleRepository(database) }
    val budgetRepository: BudgetRepository by lazy { BudgetRepository(database) }
    val setupRepository: SetupRepository by lazy { SetupRepository(database) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    /** Snackbary, ktoré majú prežiť zatvorenie obrazovky (Uložené, Späť). */
    val snackbarBus = SnackbarBus()
}
