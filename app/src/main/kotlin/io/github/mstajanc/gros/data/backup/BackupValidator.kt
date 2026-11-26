package io.github.mstajanc.gros.data.backup

import io.github.mstajanc.gros.domain.Money
import io.github.mstajanc.gros.domain.model.CategoryKind
import io.github.mstajanc.gros.domain.model.TransactionType
import io.github.mstajanc.gros.domain.rules.CategoryRules
import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Kontrola obsahu zálohy pred zápisom: jedinečné `id`, referencie medzi tabuľkami a invarianty
 * dátového modelu. Nevalidná záloha sa odmietne celá; nič sa neopravuje potichu.
 */
object BackupValidator {

    /** Vráti popis prvého problému (pre testy a ladenie; používateľ vidí len všeobecnú chybu) alebo `null`. */
    fun problem(data: BackupData): String? {
        val accounts = data.accounts.associateBy { it.id }
        val categories = data.categories.associateBy { it.id }
        val rules = data.recurringRules.associateBy { it.id }

        fun duplicate(ids: List<Long>) = ids.size != ids.toSet().size
        when {
            duplicate(data.accounts.map { it.id }) -> return "duplicitné id účtu"
            duplicate(data.categories.map { it.id }) -> return "duplicitné id kategórie"
            duplicate(data.transactions.map { it.id }) -> return "duplicitné id transakcie"
            duplicate(data.recurringRules.map { it.id }) -> return "duplicitné id pravidla"
            duplicate(data.budgets.map { it.id }) -> return "duplicitné id rozpočtu"
        }

        // Účty: aspoň jeden aktívny, názvy neprázdne, počiatočný stav v povolenom rozsahu.
        if (data.accounts.none { !it.archived }) return "žiadny aktívny účet"
        data.accounts.forEach {
            if (it.name.isBlank()) return "účet bez názvu"
            if (it.openingBalanceCents !in -Money.MAX_CENTS..Money.MAX_CENTS) return "počiatočný stav mimo rozsahu"
        }

        // Kategórie: najviac dve úrovne, rodič existuje a má rovnaký druh, farba z palety.
        data.categories.forEach { category ->
            if (category.name.isBlank()) return "kategória bez názvu"
            if (category.color !in 0 until CategoryRules.COLOR_COUNT) return "farba kategórie mimo palety"
            val parentId = category.parentId ?: return@forEach
            val parent = categories[parentId] ?: return "chýba rodič kategórie"
            if (parent.parentId != null) return "tretia úroveň kategórií"
            if (parent.kind != category.kind) return "podkategória iného druhu"
        }
        CategoryKind.entries.forEach { kind ->
            if (data.categories.none { it.parentId == null && it.kind == kind && !it.archived }) {
                return "chýba aktívna hlavná kategória druhu $kind"
            }
        }

        fun posting(type: TransactionType, amount: Long, accountId: Long, toAccountId: Long?, categoryId: Long?): String? {
            if (amount !in 1..Money.MAX_CENTS) return "suma mimo rozsahu"
            if (accountId !in accounts) return "chýba účet"
            return if (type == TransactionType.TRANSFER) {
                when {
                    toAccountId == null || toAccountId !in accounts -> "chýba cieľový účet prevodu"
                    toAccountId == accountId -> "prevod na ten istý účet"
                    categoryId != null -> "prevod s kategóriou"
                    else -> null
                }
            } else {
                val category = categoryId?.let(categories::get)
                val expected = if (type == TransactionType.EXPENSE) CategoryKind.EXPENSE else CategoryKind.INCOME
                when {
                    toAccountId != null -> "cieľový účet mimo prevodu"
                    category == null -> "chýba kategória"
                    category.kind != expected -> "kategória iného druhu"
                    else -> null
                }
            }
        }

        data.transactions.forEach { tx ->
            posting(tx.type, tx.amountCents, tx.accountId, tx.toAccountId, tx.categoryId)?.let { return "transakcia: $it" }
            if (!isDate(tx.date)) return "transakcia: neplatný dátum"
            if (tx.recurringRuleId != null && tx.recurringRuleId !in rules) return "transakcia: chýba pravidlo"
        }
        data.recurringRules.forEach { rule ->
            posting(rule.type, rule.amountCents, rule.accountId, rule.toAccountId, rule.categoryId)?.let { return "pravidlo: $it" }
            if (rule.name.isBlank()) return "pravidlo bez názvu"
            if (rule.interval < 1) return "pravidlo: interval menší než 1"
            if (!isDate(rule.anchorDate) || !isDate(rule.nextDate) || (rule.endDate != null && !isDate(rule.endDate))) {
                return "pravidlo: neplatný dátum"
            }
        }

        if (duplicate(data.budgets.map { it.categoryId })) return "dva rozpočty jednej kategórie"
        data.budgets.forEach { budget ->
            val category = categories[budget.categoryId] ?: return "rozpočet: chýba kategória"
            if (category.parentId != null || category.kind != CategoryKind.EXPENSE) return "rozpočet mimo hlavnej výdavkovej kategórie"
            if (budget.monthlyLimitCents !in 1..Money.MAX_CENTS) return "rozpočet: limit mimo rozsahu"
        }
        return null
    }

    fun validate(data: BackupData) {
        if (problem(data) != null) throw BackupException(BackupError.INVALID_CONTENT)
    }

    private fun isDate(text: String): Boolean = try {
        LocalDate.parse(text)
        true
    } catch (_: DateTimeParseException) {
        false
    }
}
