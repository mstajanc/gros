package io.github.mstajanc.gros.domain.model

import java.time.LocalDate

enum class AccountType { CHECKING, CASH, SAVINGS, INVESTMENT, OTHER }

enum class CategoryKind { EXPENSE, INCOME }

enum class TransactionType { EXPENSE, INCOME, TRANSFER }

enum class Frequency { WEEKLY, MONTHLY, YEARLY }

data class Account(
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    val openingBalanceCents: Long,
    val includeInTotal: Boolean = true,
    val archived: Boolean = false,
    val sortOrder: Int = 0,
)

data class Category(
    val id: Long = 0,
    /** `null` = hlavná kategória, inak podkategória hlavnej kategórie `parentId`. */
    val parentId: Long? = null,
    val name: String,
    val kind: CategoryKind,
    /** Index do palety kategórií; podkategória má vždy farbu rodiča. */
    val color: Int,
    val archived: Boolean = false,
    /** Poradie v rámci rodiča. */
    val sortOrder: Int = 0,
) {
    val isMain: Boolean get() = parentId == null
}

data class Transaction(
    val id: Long = 0,
    val type: TransactionType,
    /** Vždy > 0; smer určuje `type`. */
    val amountCents: Long,
    val date: LocalDate,
    /** Účet výdavku či príjmu; pri prevode zdrojový účet. */
    val accountId: Long,
    /** Len pri prevode: cieľový účet. */
    val toAccountId: Long? = null,
    /** Povinná pri výdavku a príjme, pri prevode prázdna. */
    val categoryId: Long? = null,
    val note: String? = null,
    /** Odvodené z `note`: malé písmená bez diakritiky. */
    val noteSearch: String? = null,
    val recurringRuleId: Long? = null,
    /** Epoch milisekundy (UTC); určuje poradie v rámci dňa. */
    val createdAt: Long,
    val updatedAt: Long,
)

data class RecurringRule(
    val id: Long = 0,
    val name: String,
    val type: TransactionType,
    val amountCents: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val frequency: Frequency,
    /** Každých N periód, N ≥ 1. */
    val interval: Int,
    val anchorDate: LocalDate,
    val nextDate: LocalDate,
    /** Vrátane; `null` = bez konca. */
    val endDate: LocalDate? = null,
    val paused: Boolean = false,
)

data class Budget(
    val id: Long = 0,
    /** Hlavná výdavková kategória; najviac jeden rozpočet na kategóriu. */
    val categoryId: Long,
    val monthlyLimitCents: Long,
)

/** Vstup pre vytvorenie alebo úpravu transakcie; odvodené polia dopĺňa repozitár. */
data class TransactionDraft(
    val type: TransactionType,
    val amountCents: Long,
    val date: LocalDate,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val note: String? = null,
)

/** Vstup pre vytvorenie alebo úpravu opakovaného pravidla. */
data class RecurringRuleDraft(
    val name: String,
    val type: TransactionType,
    val amountCents: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val frequency: Frequency,
    val interval: Int,
    val anchorDate: LocalDate,
    val nextDate: LocalDate,
    val endDate: LocalDate? = null,
    val paused: Boolean = false,
)
