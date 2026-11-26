package io.github.mstajanc.gros

import android.content.Context
import io.github.mstajanc.gros.data.backup.BackupData
import io.github.mstajanc.gros.data.backup.BackupRepository
import io.github.mstajanc.gros.data.db.GrosDatabase
import io.github.mstajanc.gros.data.recurring.RecurringGenerator
import io.github.mstajanc.gros.data.repository.AccountRepository
import io.github.mstajanc.gros.data.repository.BudgetRepository
import io.github.mstajanc.gros.data.repository.CategoryRepository
import io.github.mstajanc.gros.data.repository.RecurringRuleRepository
import io.github.mstajanc.gros.data.repository.ReportRepository
import io.github.mstajanc.gros.data.repository.SetupRepository
import io.github.mstajanc.gros.data.repository.TransactionRepository
import io.github.mstajanc.gros.data.settings.SettingsRepository
import io.github.mstajanc.gros.domain.AppLock
import io.github.mstajanc.gros.ui.common.SnackbarAction
import io.github.mstajanc.gros.ui.common.SnackbarBus
import io.github.mstajanc.gros.ui.common.SnackbarEvent
import java.time.Clock
import java.time.LocalDate

/** Ručný kontajner závislostí (namiesto Hiltu). Všetko vzniká až pri prvom použití. */
class AppContainer(context: Context) {

    /** Kontext aplikácie (napr. pre ContentResolver pri zálohe). */
    val appContext: Context = context.applicationContext

    /** Jediný zdroj aktuálneho času a dátumu; v testoch sa nahrádza pevnými hodinami. */
    val clock: Clock = Clock.systemDefaultZone()

    fun today(): LocalDate = LocalDate.now(clock)

    val database: GrosDatabase by lazy { GrosDatabase.create(appContext) }

    val accountRepository: AccountRepository by lazy { AccountRepository(database) }
    val categoryRepository: CategoryRepository by lazy { CategoryRepository(database) }
    val transactionRepository: TransactionRepository by lazy { TransactionRepository(database, clock) }
    val recurringRuleRepository: RecurringRuleRepository by lazy { RecurringRuleRepository(database, clock) }
    val recurringGenerator: RecurringGenerator by lazy { RecurringGenerator(database, clock) }
    val budgetRepository: BudgetRepository by lazy { BudgetRepository(database) }
    val reportRepository: ReportRepository by lazy { ReportRepository(database) }
    val setupRepository: SetupRepository by lazy { SetupRepository(database) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }
    val backupRepository: BackupRepository by lazy { BackupRepository(database) }

    /** Zámok appky žije v procese, aby prežil otočenie obrazovky. */
    val appLock = AppLock { clock.millis() }

    /**
     * Import zálohy: nahradí dáta, vynuluje posledný použitý účet a spustí generátor opakovaní,
     * takže sa dobehnú zmeškané platby zo staršej zálohy (import, krok 4).
     */
    suspend fun restoreBackup(data: BackupData) {
        backupRepository.restore(data)
        settingsRepository.clearLastUsedAccountId()
        runRecurring()
    }

    /** Snackbary, ktoré majú prežiť zatvorenie obrazovky (Uložené, Späť). */
    val snackbarBus = SnackbarBus()

    /**
     * Spustí generátor opakovaných platieb; ak niečo pribudlo, zobrazí snackbar
     * „Zapísané opakované platby: N“ s akciou Zobraziť (pravidlo 1).
     */
    suspend fun runRecurring() {
        val created = recurringGenerator.run()
        if (created > 0) {
            snackbarBus.show(
                SnackbarEvent(
                    R.string.snackbar_recurring_written,
                    formatArgs = listOf(created),
                    action = SnackbarAction.SHOW_ALL_TRANSACTIONS,
                ),
            )
        }
    }
}
