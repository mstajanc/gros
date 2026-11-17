package io.github.mstajanc.gros.domain

import io.github.mstajanc.gros.domain.model.Account
import io.github.mstajanc.gros.domain.model.Category
import io.github.mstajanc.gros.domain.model.Transaction
import io.github.mstajanc.gros.domain.model.TransactionType
import java.time.LocalDate
import java.time.YearMonth

/** Transakcie jedného dňa; [netCents] = príjmy − výdavky dňa, bez prevodov. */
data class DayGroup(val date: LocalDate, val netCents: Long, val transactions: List<Transaction>)

/** Súčet nad zoznamom: počet všetkých transakcií, sumy len príjmov a výdavkov. */
data class ListSummary(val count: Int, val incomeCents: Long, val expenseCents: Long)

/** Suma so znamienkom a spôsob, akým sa má znamienko zobraziť. */
data class DisplayAmount(val cents: Long, val sign: SignDisplay)

object TransactionList {

    /** Zoskupí už zoradené transakcie po dňoch; poradie dní aj transakcií ostáva. */
    fun groupByDay(transactions: List<Transaction>): List<DayGroup> =
        transactions.groupBy { it.date }.map { (date, items) ->
            DayGroup(date = date, netCents = items.sumOf(::netContribution), transactions = items)
        }

    /** Prevody sa do súčtu príjmov a výdavkov nepočítajú. */
    fun summarize(transactions: List<Transaction>): ListSummary = ListSummary(
        count = transactions.size,
        incomeCents = transactions.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents },
        expenseCents = transactions.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents },
    )

    /**
     * Výdavok má „−“, príjem „+“, prevod je bez znamienka. Pri filtri jedného účtu má prevod
     * znamienko podľa smeru: „−“ z účtu, „+“ na účet.
     */
    fun displayAmount(transaction: Transaction, accountFilterId: Long?): DisplayAmount = when (transaction.type) {
        TransactionType.EXPENSE -> DisplayAmount(-transaction.amountCents, SignDisplay.NEGATIVE_ONLY)
        TransactionType.INCOME -> DisplayAmount(transaction.amountCents, SignDisplay.ALWAYS)
        TransactionType.TRANSFER -> when (accountFilterId) {
            null -> DisplayAmount(transaction.amountCents, SignDisplay.NEVER)
            transaction.accountId -> DisplayAmount(-transaction.amountCents, SignDisplay.NEGATIVE_ONLY)
            transaction.toAccountId -> DisplayAmount(transaction.amountCents, SignDisplay.ALWAYS)
            else -> DisplayAmount(transaction.amountCents, SignDisplay.NEVER)
        }
    }

    private fun netContribution(transaction: Transaction): Long = when (transaction.type) {
        TransactionType.INCOME -> transaction.amountCents
        TransactionType.EXPENSE -> -transaction.amountCents
        TransactionType.TRANSFER -> 0L
    }
}

/** Obdobie filtra: Mesiac, Rok alebo Celé obdobie (rozhodnutie A4, bez vlastného rozsahu). */
sealed interface Period {
    /** Prvý deň obdobia vrátane; `null` = bez obmedzenia. */
    val from: LocalDate?

    /** Posledný deň obdobia vrátane; `null` = bez obmedzenia. */
    val to: LocalDate?

    fun previous(): Period
    fun next(): Period

    data class Month(val month: YearMonth) : Period {
        override val from: LocalDate get() = month.atDay(1)
        override val to: LocalDate get() = month.atEndOfMonth()
        override fun previous(): Period = Month(month.minusMonths(1))
        override fun next(): Period = Month(month.plusMonths(1))
    }

    data class Year(val year: Int) : Period {
        override val from: LocalDate get() = LocalDate.of(year, 1, 1)
        override val to: LocalDate get() = LocalDate.of(year, 12, 31)
        override fun previous(): Period = Year(year - 1)
        override fun next(): Period = Year(year + 1)
    }

    data object All : Period {
        override val from: LocalDate? get() = null
        override val to: LocalDate? get() = null
        override fun previous(): Period = this
        override fun next(): Period = this
    }
}

/** Filter zoznamu transakcií; účet aj kategória sú výber jednej položky (rozhodnutie A5). */
data class TransactionFilter(
    val period: Period,
    val accountId: Long? = null,
    val categoryId: Long? = null,
    val query: String = "",
) {
    companion object {
        /** Výber hlavnej kategórie zahŕňa aj jej podkategórie; podkategória len seba. */
        fun categoryIds(categoryId: Long, categories: List<Category>): List<Long> =
            listOf(categoryId) + categories.filter { it.parentId == categoryId }.map { it.id }

        /**
         * Kategórie, ktorých názov alebo názov ich hlavnej kategórie obsahuje hľadaný text
         * (bez ohľadu na veľkosť písmen a diakritiku; rozhodnutie C1).
         */
        fun categoryIdsMatching(query: String, categories: List<Category>): List<Long> {
            val needle = NoteNormalizer.toSearch(query) ?: return emptyList()
            val byId = categories.associateBy { it.id }
            return categories.filter { category ->
                val parent = category.parentId?.let(byId::get)
                NoteNormalizer.normalize(category.name).contains(needle) ||
                    (parent != null && NoteNormalizer.normalize(parent.name).contains(needle))
            }.map { it.id }
        }
    }
}

/** Čo má používateľ vo formulári transakcie vybrané. */
data class FormSelection(
    val type: TransactionType,
    /** Hlavná kategória. */
    val categoryId: Long? = null,
    /** Nepovinná podkategória vybranej hlavnej kategórie. */
    val subcategoryId: Long? = null,
    val accountId: Long? = null,
    val toAccountId: Long? = null,
) {
    /** Kategória, ktorá sa uloží: podkategória, ak je vybraná, inak hlavná. */
    val effectiveCategoryId: Long? get() = subcategoryId ?: categoryId
}

object TransactionFormRules {

    /** Uložiť je aktívne až pri sume > 0 a vybranej kategórii, pri prevode pri dvoch rôznych účtoch. */
    fun canSave(selection: FormSelection, amountCents: Long): Boolean {
        if (amountCents <= 0 || amountCents > Money.MAX_CENTS || selection.accountId == null) return false
        return when (selection.type) {
            TransactionType.TRANSFER ->
                selection.toAccountId != null && selection.toAccountId != selection.accountId

            TransactionType.EXPENSE, TransactionType.INCOME -> selection.categoryId != null
        }
    }

    /**
     * Zmena typu: výdavok ↔ príjem vynuluje kategóriu a vyžiada novú; zmena na prevod kategóriu
     * odstráni a vyžiada cieľový účet; odchod z prevodu cieľový účet zahodí.
     */
    fun onTypeChanged(selection: FormSelection, newType: TransactionType): FormSelection =
        if (newType == selection.type) {
            selection
        } else {
            selection.copy(type = newType, categoryId = null, subcategoryId = null, toAccountId = null)
        }

    /** Posledný použitý účet, ak je stále aktívny; inak prvý aktívny podľa poradia. */
    fun defaultAccountId(lastUsedId: Long?, accounts: List<Account>): Long? {
        val active = accounts.filter { !it.archived }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
        return active.firstOrNull { it.id == lastUsedId }?.id ?: active.firstOrNull()?.id
    }

    /** Typ Prevod je dostupný až pri dvoch aktívnych účtoch. */
    fun isTransferAvailable(accounts: List<Account>): Boolean = accounts.count { !it.archived } >= 2

    /** Dátum transakcie sa pri zadávaní nedá nastaviť do budúcnosti. */
    fun isDateAllowed(date: LocalDate, today: LocalDate): Boolean = !date.isAfter(today)
}

/** Našepkávanie poznámky: od 2 znakov, najviac [LIMIT] návrhov. */
object NoteSuggestions {
    const val MIN_LENGTH = 2
    const val LIMIT = 5

    /** Vzor pre `LIKE` nad `noteSearch`; `null`, kým je vstup príliš krátky. */
    fun pattern(input: String): String? =
        if (input.trim().length < MIN_LENGTH) null else NoteNormalizer.containsPattern(input)
}
