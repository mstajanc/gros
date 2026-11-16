package io.github.mstajanc.gros.data.repository

import androidx.room3.withWriteTransaction
import io.github.mstajanc.gros.data.db.BudgetEntity
import io.github.mstajanc.gros.data.db.GrosDatabase
import io.github.mstajanc.gros.data.db.toDomain
import io.github.mstajanc.gros.domain.model.Budget
import io.github.mstajanc.gros.domain.rules.BudgetRules
import io.github.mstajanc.gros.domain.rules.InvariantViolationException
import io.github.mstajanc.gros.domain.rules.Violation
import io.github.mstajanc.gros.domain.rules.throwIfViolated
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Mesačné limity. Rozpočet patrí len hlavnej výdavkovej kategórii, každá má najviac jeden
 * a limit je > 0. Výpočet čerpania pribudne v M4.
 */
class BudgetRepository(private val db: GrosDatabase) {

    private val budgets = db.budgetDao()
    private val categories = db.categoryDao()

    fun observeBudgets(): Flow<List<Budget>> = budgets.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun get(id: Long): Budget? = budgets.getById(id)?.toDomain()

    suspend fun create(categoryId: Long, monthlyLimitCents: Long): Long = db.withWriteTransaction {
        BudgetRules.validate(
            category = categories.getById(categoryId)?.toDomain(),
            limitCents = monthlyLimitCents,
            categoryHasBudget = budgets.getByCategory(categoryId) != null,
        ).throwIfViolated()
        budgets.insert(BudgetEntity(categoryId = categoryId, monthlyLimitCents = monthlyLimitCents))
    }

    /** Zmena limitu platí pre aktuálny a budúce mesiace; história limitov sa neukladá. */
    suspend fun updateLimit(id: Long, monthlyLimitCents: Long) {
        db.withWriteTransaction {
            val existing = budgets.getById(id) ?: throw InvariantViolationException(Violation.NOT_FOUND)
            BudgetRules.validateLimit(monthlyLimitCents).throwIfViolated()
            budgets.update(existing.copy(monthlyLimitCents = monthlyLimitCents))
        }
    }

    suspend fun delete(id: Long) {
        db.withWriteTransaction { budgets.deleteById(id) }
    }
}
