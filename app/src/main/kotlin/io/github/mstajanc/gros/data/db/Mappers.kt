package io.github.mstajanc.gros.data.db

import io.github.mstajanc.gros.domain.AccountFlows
import io.github.mstajanc.gros.domain.model.Account
import io.github.mstajanc.gros.domain.model.Budget
import io.github.mstajanc.gros.domain.model.Category
import io.github.mstajanc.gros.domain.model.RecurringRule
import io.github.mstajanc.gros.domain.model.Transaction

fun AccountEntity.toDomain() = Account(id, name, type, openingBalanceCents, includeInTotal, archived, sortOrder)

fun Account.toEntity() = AccountEntity(id, name, type, openingBalanceCents, includeInTotal, archived, sortOrder)

fun CategoryEntity.toDomain() = Category(id, parentId, name, kind, color, archived, sortOrder)

fun Category.toEntity() = CategoryEntity(id, parentId, name, kind, color, archived, sortOrder)

fun TransactionEntity.toDomain() = Transaction(
    id = id,
    type = type,
    amountCents = amountCents,
    date = date,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    note = note,
    noteSearch = noteSearch,
    recurringRuleId = recurringRuleId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun Transaction.toEntity() = TransactionEntity(
    id = id,
    type = type,
    amountCents = amountCents,
    date = date,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    note = note,
    noteSearch = noteSearch,
    recurringRuleId = recurringRuleId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun RecurringRuleEntity.toDomain() = RecurringRule(
    id = id,
    name = name,
    type = type,
    amountCents = amountCents,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    frequency = frequency,
    interval = interval,
    anchorDate = anchorDate,
    nextDate = nextDate,
    endDate = endDate,
    paused = paused,
)

fun RecurringRule.toEntity() = RecurringRuleEntity(
    id = id,
    name = name,
    type = type,
    amountCents = amountCents,
    accountId = accountId,
    toAccountId = toAccountId,
    categoryId = categoryId,
    frequency = frequency,
    interval = interval,
    anchorDate = anchorDate,
    nextDate = nextDate,
    endDate = endDate,
    paused = paused,
)

fun BudgetEntity.toDomain() = Budget(id, categoryId, monthlyLimitCents)

fun AccountFlowRow.toDomain() = AccountFlows(incomeCents, expenseCents, transferInCents, transferOutCents)
