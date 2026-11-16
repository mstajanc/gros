package io.github.mstajanc.gros.data.db

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import io.github.mstajanc.gros.domain.model.AccountType
import io.github.mstajanc.gros.domain.model.CategoryKind
import io.github.mstajanc.gros.domain.model.Frequency
import io.github.mstajanc.gros.domain.model.TransactionType
import java.time.LocalDate

// Schéma v1. Po M1 je zmrazená: každá zmena znamená novú verziu, migráciu a test (rozhodnutie B17).
// Zostatky sa neukladajú; počítajú sa z transakcií.

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val openingBalanceCents: Long,
    val includeInTotal: Boolean,
    val archived: Boolean,
    val sortOrder: Int,
)

@Entity(
    tableName = "categories",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("parentId")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long?,
    val name: String,
    val kind: CategoryKind,
    val color: Int,
    val archived: Boolean,
    val sortOrder: Int,
)

/** Volá sa `TransactionEntity`, aby nekolidovala s anotáciou `@Transaction`. */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["toAccountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = RecurringRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["recurringRuleId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("date"),
        Index("accountId"),
        Index("categoryId"),
        Index("toAccountId"),
        Index("recurringRuleId"),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val amountCents: Long,
    /** ISO text `RRRR-MM-DD`. */
    val date: LocalDate,
    val accountId: Long,
    val toAccountId: Long?,
    val categoryId: Long?,
    val note: String?,
    val noteSearch: String?,
    val recurringRuleId: Long?,
    /** Epoch milisekundy v UTC. */
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "recurring_rules",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["toAccountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("accountId"), Index("toAccountId"), Index("categoryId")],
)
data class RecurringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TransactionType,
    val amountCents: Long,
    val accountId: Long,
    val toAccountId: Long?,
    val categoryId: Long?,
    val frequency: Frequency,
    val interval: Int,
    val anchorDate: LocalDate,
    val nextDate: LocalDate,
    val endDate: LocalDate?,
    val paused: Boolean,
)

@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["categoryId"], unique = true)],
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,
    val monthlyLimitCents: Long,
)
