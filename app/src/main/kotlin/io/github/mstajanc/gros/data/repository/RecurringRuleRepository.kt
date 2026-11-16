package io.github.mstajanc.gros.data.repository

import androidx.room3.withWriteTransaction
import io.github.mstajanc.gros.data.db.GrosDatabase
import io.github.mstajanc.gros.data.db.RecurringRuleEntity
import io.github.mstajanc.gros.data.db.toDomain
import io.github.mstajanc.gros.domain.model.RecurringRule
import io.github.mstajanc.gros.domain.model.RecurringRuleDraft
import io.github.mstajanc.gros.domain.rules.InvariantViolationException
import io.github.mstajanc.gros.domain.rules.RecurringRuleRules
import io.github.mstajanc.gros.domain.rules.Violation
import io.github.mstajanc.gros.domain.rules.throwIfViolated
import io.github.mstajanc.gros.domain.rules.toPosting

/**
 * Uloženie a zmazanie opakovaných pravidiel s invariantmi.
 * Generátor transakcií a výpočet ďalších výskytov pribudnú v M3.
 */
class RecurringRuleRepository(private val db: GrosDatabase) {

    private val rules = db.recurringRuleDao()

    suspend fun get(id: Long): RecurringRule? = rules.getById(id)?.toDomain()

    suspend fun create(draft: RecurringRuleDraft): Long = db.withWriteTransaction {
        validate(draft)
        rules.insert(draft.toEntity(id = 0))
    }

    suspend fun update(id: Long, draft: RecurringRuleDraft) {
        db.withWriteTransaction {
            if (rules.getById(id) == null) throw InvariantViolationException(Violation.NOT_FOUND)
            validate(draft)
            rules.update(draft.toEntity(id))
        }
    }

    /** Zmazanie pravidla nemaže už zapísané transakcie; ich `recurringRuleId` sa nastaví na null. */
    suspend fun delete(id: Long) {
        db.withWriteTransaction { rules.deleteById(id) }
    }

    private suspend fun validate(draft: RecurringRuleDraft) {
        RecurringRuleRules.validate(draft, db.loadPostingRefs(draft.toPosting())).throwIfViolated()
    }

    private fun RecurringRuleDraft.toEntity(id: Long) = RecurringRuleEntity(
        id = id,
        name = name.trim(),
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
}
