package io.github.mstajanc.gros.data.backup

import io.github.mstajanc.gros.domain.model.AccountType
import io.github.mstajanc.gros.domain.model.CategoryKind
import io.github.mstajanc.gros.domain.model.Frequency
import io.github.mstajanc.gros.domain.model.TransactionType
import kotlinx.serialization.Serializable

/**
 * Obsah zálohy: všetkých 5 tabuliek s pôvodnými `id`. Nastavenia appky v zálohe nie sú a odvodený
 * stĺpec `noteSearch` sa pri importe prepočíta. Dátumy sú ISO text `RRRR-MM-DD`.
 */
@Serializable
data class BackupData(
    val schemaVersion: Int,
    val accounts: List<BackupAccount>,
    val categories: List<BackupCategory>,
    val transactions: List<BackupTransaction>,
    val recurringRules: List<BackupRule>,
    val budgets: List<BackupBudget>,
)

@Serializable
data class BackupAccount(
    val id: Long,
    val name: String,
    val type: AccountType,
    val openingBalanceCents: Long,
    val includeInTotal: Boolean,
    val archived: Boolean,
    val sortOrder: Int,
)

@Serializable
data class BackupCategory(
    val id: Long,
    val parentId: Long?,
    val name: String,
    val kind: CategoryKind,
    val color: Int,
    val archived: Boolean,
    val sortOrder: Int,
)

@Serializable
data class BackupTransaction(
    val id: Long,
    val type: TransactionType,
    val amountCents: Long,
    val date: String,
    val accountId: Long,
    val toAccountId: Long?,
    val categoryId: Long?,
    val note: String?,
    val recurringRuleId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupRule(
    val id: Long,
    val name: String,
    val type: TransactionType,
    val amountCents: Long,
    val accountId: Long,
    val toAccountId: Long?,
    val categoryId: Long?,
    val frequency: Frequency,
    val interval: Int,
    val anchorDate: String,
    val nextDate: String,
    val endDate: String?,
    val paused: Boolean,
)

@Serializable
data class BackupBudget(val id: Long, val categoryId: Long, val monthlyLimitCents: Long)
