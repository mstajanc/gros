package io.github.mstajanc.gros.data.recurring

import androidx.room3.withWriteTransaction
import io.github.mstajanc.gros.data.db.GrosDatabase
import io.github.mstajanc.gros.data.db.TransactionEntity
import io.github.mstajanc.gros.data.db.toDomain
import io.github.mstajanc.gros.domain.NoteNormalizer
import io.github.mstajanc.gros.domain.RuleState
import io.github.mstajanc.gros.domain.RuleStatus
import io.github.mstajanc.gros.domain.schedule
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Generátor opakovaných platieb (pravidlá 1–4). Pre každé nepozastavené pravidlo s `nextDate ≤ dnes`
 * zapíše transakcie s dátumami výskytov a posunie `nextDate` za dnešok, takže zmeškané platby sa dobehnú.
 *
 * Transakcie jedného pravidla a posun `nextDate` prebehnú v jednej DB transakcii a celý beh je pod
 * jedným zámkom, takže dvojitý beh nevytvorí duplicitu. Vygenerovaná transakcia je bežná transakcia:
 * jej zmena či zmazanie pravidlo neovplyvní.
 */
class RecurringGenerator(private val db: GrosDatabase, private val clock: Clock) {

    private val mutex = Mutex()

    /** Spustí generátor; vráti počet zapísaných transakcií. */
    suspend fun run(): Int = mutex.withLock {
        val today = LocalDate.now(clock)
        var created = 0
        for (due in db.recurringRuleDao().getDue(today)) {
            created += db.withWriteTransaction<Int> { generate(due.id, today) }
        }
        created
    }

    private suspend fun generate(ruleId: Long, today: LocalDate): Int {
        val rules = db.recurringRuleDao()
        // Pravidlo sa číta znova vo vnútri transakcie, aby sa nepoužil zastaraný stav.
        val entity = rules.getById(ruleId) ?: return 0
        val rule = entity.toDomain()
        if (RuleState.status(rule) != RuleStatus.ACTIVE) return 0
        val dates = rule.schedule.dueDates(from = rule.nextDate, today = today, endDate = rule.endDate)
        if (dates.isEmpty()) return 0

        val now = clock.millis()
        val note = NoteNormalizer.clean(rule.name)
        for (date in dates) {
            db.transactionDao().insert(
                TransactionEntity(
                    type = rule.type,
                    amountCents = rule.amountCents,
                    date = date,
                    accountId = rule.accountId,
                    toAccountId = rule.toAccountId,
                    categoryId = rule.categoryId,
                    note = note,
                    noteSearch = NoteNormalizer.toSearch(note),
                    recurringRuleId = rule.id,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        rules.update(entity.copy(nextDate = rule.schedule.nextAfter(dates.last())))
        return dates.size
    }
}
