package io.github.mstajanc.gros.data.repository

import androidx.room3.withWriteTransaction
import io.github.mstajanc.gros.data.db.GrosDatabase
import io.github.mstajanc.gros.data.db.RecurringRuleEntity
import io.github.mstajanc.gros.data.db.toDomain
import io.github.mstajanc.gros.data.db.toEntity
import io.github.mstajanc.gros.domain.RuleEditing
import io.github.mstajanc.gros.domain.RuleInput
import io.github.mstajanc.gros.domain.RuleState
import io.github.mstajanc.gros.domain.RuleStatus
import io.github.mstajanc.gros.domain.model.RecurringRule
import io.github.mstajanc.gros.domain.model.RecurringRuleDraft
import io.github.mstajanc.gros.domain.rules.InvariantViolationException
import io.github.mstajanc.gros.domain.rules.RecurringRuleRules
import io.github.mstajanc.gros.domain.rules.Violation
import io.github.mstajanc.gros.domain.rules.throwIfViolated
import io.github.mstajanc.gros.domain.rules.toPosting
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Opakované platby: uloženie, úprava, pozastavenie a zmazanie s invariantmi.
 * Transakcie z pravidiel zapisuje [io.github.mstajanc.gros.data.recurring.RecurringGenerator].
 */
class RecurringRuleRepository(private val db: GrosDatabase, private val clock: Clock) {

    private val rules = db.recurringRuleDao()

    private fun today(): LocalDate = LocalDate.now(clock)

    fun observeRules(): Flow<List<RecurringRule>> = rules.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun get(id: Long): RecurringRule? = rules.getById(id)?.toDomain()

    /** Nové pravidlo z formulára; kotvou je „Ďalšia platba“. */
    suspend fun create(input: RuleInput): Long = create(RuleEditing.newRule(input))

    suspend fun create(draft: RecurringRuleDraft): Long = db.withWriteTransaction {
        validate(draft)
        rules.insert(draft.toEntity(id = 0))
    }

    /** Úprava z formulára; mení len budúce výskyty (pravidlo 5, B11). */
    suspend fun edit(id: Long, input: RuleInput) {
        db.withWriteTransaction {
            val existing = rules.getById(id)?.toDomain() ?: throw InvariantViolationException(Violation.NOT_FOUND)
            val draft = RuleEditing.applyEdit(existing, input, today())
            validate(draft)
            rules.update(draft.toEntity(id))
        }
    }

    suspend fun update(id: Long, draft: RecurringRuleDraft) {
        db.withWriteTransaction {
            if (rules.getById(id) == null) throw InvariantViolationException(Violation.NOT_FOUND)
            validate(draft)
            rules.update(draft.toEntity(id))
        }
    }

    /**
     * Pozastavenie nič nemení na dátumoch. Obnovenie zmeškané výskyty nedobieha (pravidlo 8)
     * a vyžaduje aktívny účet a kategóriu.
     */
    suspend fun setPaused(id: Long, paused: Boolean) {
        db.withWriteTransaction {
            val existing = rules.getById(id)?.toDomain() ?: throw InvariantViolationException(Violation.NOT_FOUND)
            if (existing.paused == paused) return@withWriteTransaction
            val updated = if (paused) existing.copy(paused = true) else RuleState.resume(existing, today())
            if (!paused) validate(updated.toDraft())
            rules.update(updated.toEntity())
        }
    }

    /** Zmazanie pravidla nemaže už zapísané transakcie; ich `recurringRuleId` sa nastaví na null. */
    suspend fun delete(id: Long) {
        db.withWriteTransaction { rules.deleteById(id) }
    }

    private suspend fun validate(draft: RecurringRuleDraft) {
        val refs = db.loadPostingRefs(draft.toPosting())
        val category = refs.category
        val parent = category?.parentId?.let { db.categoryDao().getById(it) }
        val categoryArchived = category != null && (category.archived || parent?.archived == true)
        RecurringRuleRules.validate(draft, refs, categoryArchived).throwIfViolated()
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

fun RecurringRule.toDraft() = RecurringRuleDraft(
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

/**
 * Pravidlo 10: archivácia účtu alebo kategórie pozastaví aktívne pravidlá, ktoré na ne odkazujú.
 * Volá sa vo vnútri DB transakcie archivácie; vráti názvy pozastavených pravidiel.
 */
internal suspend fun GrosDatabase.pauseRules(entities: List<RecurringRuleEntity>): List<String> {
    val active = entities.filter { RuleState.status(it.toDomain()) == RuleStatus.ACTIVE }
    active.forEach { recurringRuleDao().update(it.copy(paused = true)) }
    return active.map { it.name }
}

/** Názvy aktívnych pravidiel, ktoré by archivácia pozastavila. */
internal fun List<RecurringRuleEntity>.activeNames(): List<String> =
    filter { RuleState.status(it.toDomain()) == RuleStatus.ACTIVE }.map { it.name }
