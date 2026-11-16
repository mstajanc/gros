package io.github.mstajanc.gros.data.repository

import androidx.room3.withWriteTransaction
import io.github.mstajanc.gros.data.db.AccountEntity
import io.github.mstajanc.gros.data.db.CategoryEntity
import io.github.mstajanc.gros.data.db.GrosDatabase
import io.github.mstajanc.gros.domain.model.AccountType
import io.github.mstajanc.gros.domain.model.CategoryKind
import io.github.mstajanc.gros.domain.rules.AccountRules
import io.github.mstajanc.gros.domain.rules.CategoryRules
import io.github.mstajanc.gros.domain.rules.InvariantViolationException
import io.github.mstajanc.gros.domain.rules.Violation
import io.github.mstajanc.gros.domain.rules.throwIfViolated

data class InitialAccount(
    val name: String,
    val type: AccountType,
    val openingBalanceCents: Long,
    val includeInTotal: Boolean,
)

data class InitialCategory(
    val name: String,
    val kind: CategoryKind,
    val color: Int,
    val subcategories: List<String> = emptyList(),
)

/** Výsledok sprievodcu prvým spustením: účty a kategórie zapísané naraz. */
class SetupRepository(private val db: GrosDatabase) {

    /**
     * Zapíše všetko v jednej DB transakcii. Vyžaduje aspoň jeden účet a aspoň jednu hlavnú
     * kategóriu z každého druhu; pri chybe sa nezapíše nič.
     */
    suspend fun createInitialData(accounts: List<InitialAccount>, categories: List<InitialCategory>) {
        db.withWriteTransaction {
            if (accounts.isEmpty()) throw InvariantViolationException(Violation.LAST_ACTIVE_ACCOUNT)
            for (kind in CategoryKind.entries) {
                if (categories.none { it.kind == kind }) {
                    throw InvariantViolationException(Violation.LAST_ACTIVE_MAIN_CATEGORY)
                }
            }

            accounts.forEachIndexed { index, account ->
                AccountRules.validateName(account.name).throwIfViolated()
                AccountRules.validateEnteredBalance(account.openingBalanceCents).throwIfViolated()
                db.accountDao().insert(
                    AccountEntity(
                        name = account.name.trim(),
                        type = account.type,
                        openingBalanceCents = account.openingBalanceCents,
                        includeInTotal = account.includeInTotal,
                        archived = false,
                        sortOrder = index,
                    ),
                )
            }

            for (kind in CategoryKind.entries) {
                categories.filter { it.kind == kind }.forEachIndexed { index, category ->
                    CategoryRules.validateNew(category.name, kind, category.color, parentId = null, parent = null)
                        .throwIfViolated()
                    val parentId = db.categoryDao().insert(
                        CategoryEntity(
                            parentId = null,
                            name = category.name.trim(),
                            kind = kind,
                            color = category.color,
                            archived = false,
                            sortOrder = index,
                        ),
                    )
                    category.subcategories.forEachIndexed { subIndex, name ->
                        CategoryRules.validateName(name).throwIfViolated()
                        db.categoryDao().insert(
                            CategoryEntity(
                                parentId = parentId,
                                name = name.trim(),
                                kind = kind,
                                color = category.color,
                                archived = false,
                                sortOrder = subIndex,
                            ),
                        )
                    }
                }
            }
        }
    }
}
