package io.github.mstajanc.gros.domain

import io.github.mstajanc.gros.domain.model.Frequency
import io.github.mstajanc.gros.domain.model.RecurringRule
import io.github.mstajanc.gros.domain.model.RecurringRuleDraft
import io.github.mstajanc.gros.domain.model.TransactionType
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Rad výskytov opakovanej platby: `anchor` + k × `interval` periód (k ≥ 0).
 * Každý výskyt sa počíta z kotvy, nie z predchádzajúceho výskytu, takže platba z 31. dňa
 * padne na posledný deň kratšieho mesiaca a potom sa vráti na 31.
 */
data class Schedule(val anchor: LocalDate, val frequency: Frequency, val interval: Int) {

    init {
        require(interval >= 1) { "interval musí byť aspoň 1" }
    }

    fun occurrence(k: Long): LocalDate {
        val steps = k * interval
        return when (frequency) {
            Frequency.WEEKLY -> anchor.plusWeeks(steps)
            Frequency.MONTHLY -> anchor.plusMonths(steps)
            Frequency.YEARLY -> anchor.plusYears(steps)
        }
    }

    /** Prvý výskyt radu neskorší než [date]. */
    fun nextAfter(date: LocalDate): LocalDate = firstMatching(date) { it.isAfter(date) }

    /** Prvý výskyt radu v deň [date] alebo neskôr. */
    fun firstOnOrAfter(date: LocalDate): LocalDate = firstMatching(date) { !it.isBefore(date) }

    /**
     * Výskyty od [from] (vrátane, [from] je výskytom radu) do [today] vrátane, najviac do [endDate] vrátane –
     * teda platby, ktoré má generátor zapísať.
     */
    fun dueDates(from: LocalDate, today: LocalDate, endDate: LocalDate?): List<LocalDate> {
        val last = if (endDate != null && endDate.isBefore(today)) endDate else today
        val result = mutableListOf<LocalDate>()
        var date = from
        while (!date.isAfter(last)) {
            result += date
            date = nextAfter(date)
        }
        return result
    }

    private inline fun firstMatching(date: LocalDate, accept: (LocalDate) -> Boolean): LocalDate {
        if (date.isBefore(anchor)) return anchor
        val unit = when (frequency) {
            Frequency.WEEKLY -> ChronoUnit.WEEKS
            Frequency.MONTHLY -> ChronoUnit.MONTHS
            Frequency.YEARLY -> ChronoUnit.YEARS
        }
        // Odhad počtu periód a potom krok späť, aby sa nič nepreskočilo.
        var k = maxOf(0L, unit.between(anchor, date) / interval - 1)
        while (!accept(occurrence(k))) k++
        return occurrence(k)
    }
}

val RecurringRule.schedule: Schedule get() = Schedule(anchorDate, frequency, interval)

enum class RuleStatus { ACTIVE, PAUSED, FINISHED }

/** Súhrn pravidiel prepočítaný na mesiac; prevody sa nepočítajú. */
data class MonthlySummary(val expenseCents: Long, val incomeCents: Long)

object RuleState {

    /** Ukončené pravidlo (`nextDate` za `endDate`) má prednosť pred pozastaveným. */
    fun status(rule: RecurringRule): RuleStatus = when {
        rule.endDate != null && rule.nextDate.isAfter(rule.endDate) -> RuleStatus.FINISHED
        rule.paused -> RuleStatus.PAUSED
        else -> RuleStatus.ACTIVE
    }

    /** Obnovenie pozastaveného pravidla zmeškané výskyty nedobieha (pravidlo 8). */
    fun resume(rule: RecurringRule, today: LocalDate): RecurringRule = rule.copy(
        paused = false,
        nextDate = if (rule.nextDate.isBefore(today)) rule.schedule.firstOnOrAfter(today) else rule.nextDate,
    )

    /**
     * Mesačný ekvivalent aktívnych pravidiel (pravidlo 11): týždenné × 52 / 12, ročné / 12,
     * všetko delené intervalom. Sčíta sa presne a zaokrúhli sa až výsledok.
     */
    fun monthlySummary(rules: List<RecurringRule>): MonthlySummary {
        var expense = BigDecimal.ZERO
        var income = BigDecimal.ZERO
        for (rule in rules) {
            if (status(rule) != RuleStatus.ACTIVE) continue
            val monthly = monthlyEquivalent(rule)
            when (rule.type) {
                TransactionType.EXPENSE -> expense += monthly
                TransactionType.INCOME -> income += monthly
                TransactionType.TRANSFER -> Unit
            }
        }
        return MonthlySummary(expense.roundCents(), income.roundCents())
    }

    private fun monthlyEquivalent(rule: RecurringRule): BigDecimal {
        val amount = BigDecimal.valueOf(rule.amountCents)
        val perMonth = when (rule.frequency) {
            Frequency.WEEKLY -> amount.multiply(BigDecimal.valueOf(52)).divide(BigDecimal.valueOf(12), MATH)
            Frequency.MONTHLY -> amount
            Frequency.YEARLY -> amount.divide(BigDecimal.valueOf(12), MATH)
        }
        return perMonth.divide(BigDecimal.valueOf(rule.interval.toLong()), MATH)
    }

    private fun BigDecimal.roundCents(): Long = setScale(0, RoundingMode.HALF_UP).toLong()

    private val MATH = MathContext.DECIMAL128
}

/** Vstup formulára opakovanej platby. */
data class RuleInput(
    val name: String,
    val type: TransactionType,
    val amountCents: Long,
    val accountId: Long,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val frequency: Frequency,
    val interval: Int,
    val nextDate: LocalDate,
    val endDate: LocalDate?,
)

object RuleEditing {

    /** Nové pravidlo má kotvu v „Ďalšej platbe“. */
    fun newRule(input: RuleInput): RecurringRuleDraft = input.toDraft(anchor = input.nextDate, paused = false)

    /**
     * Úprava mení len budúce výskyty (pravidlo 5). Zmena „Ďalšej platby“, frekvencie alebo intervalu
     * nastaví kotvu na novú „Ďalšiu platbu“. Predĺženie alebo zrušenie konca ukončeného pravidla
     * zmeškané výskyty nedobieha (B11).
     */
    fun applyEdit(existing: RecurringRule, input: RuleInput, today: LocalDate): RecurringRuleDraft {
        val reanchored = input.nextDate != existing.nextDate ||
            input.frequency != existing.frequency ||
            input.interval != existing.interval
        val anchor = if (reanchored) input.nextDate else existing.anchorDate
        var nextDate = input.nextDate
        val wasFinished = RuleState.status(existing) == RuleStatus.FINISHED
        val endsLater = input.endDate == null || existing.endDate == null || input.endDate.isAfter(existing.endDate)
        if (!reanchored && wasFinished && endsLater && nextDate.isBefore(today)) {
            nextDate = Schedule(anchor, input.frequency, input.interval).firstOnOrAfter(today)
        }
        return input.copy(nextDate = nextDate).toDraft(anchor = anchor, paused = existing.paused)
    }

    /** Koniec nesmie byť pred začiatkom radu. */
    fun isEndValid(input: RuleInput, anchor: LocalDate = input.nextDate): Boolean =
        input.endDate == null || !input.endDate.isBefore(anchor)

    /**
     * Koľko transakcií sa po uložení zapíše spätne – pýta sa pri novom pravidle alebo pri posune
     * „Ďalšej platby“ do minulosti (pravidlo 6, B11). Dnešná platba sa zapíše bez otázky;
     * pri dátume v minulosti počet zahŕňa všetky zápisy vrátane dnešného. Pozastavené pravidlo nič nezapíše.
     */
    fun backfillCount(existing: RecurringRule?, input: RuleInput, today: LocalDate): Int {
        if (!input.nextDate.isBefore(today)) return 0
        if (existing != null && (existing.paused || existing.nextDate == input.nextDate)) return 0
        return Schedule(input.nextDate, input.frequency, input.interval)
            .dueDates(from = input.nextDate, today = today, endDate = input.endDate)
            .size
    }

    private fun RuleInput.toDraft(anchor: LocalDate, paused: Boolean) = RecurringRuleDraft(
        name = name.trim(),
        type = type,
        amountCents = amountCents,
        accountId = accountId,
        toAccountId = toAccountId.takeIf { type == TransactionType.TRANSFER },
        categoryId = categoryId.takeIf { type != TransactionType.TRANSFER },
        frequency = frequency,
        interval = interval,
        anchorDate = anchor,
        nextDate = nextDate,
        endDate = endDate,
        paused = paused,
    )
}
