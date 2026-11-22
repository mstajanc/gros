package io.github.mstajanc.gros.domain

import io.github.mstajanc.gros.domain.model.Budget
import io.github.mstajanc.gros.domain.model.Category
import java.time.LocalDate
import java.time.YearMonth

/** Príjmy a výdavky za obdobie (bez prevodov). */
data class PeriodTotals(val incomeCents: Long, val expenseCents: Long) {
    val balanceCents: Long get() = incomeCents - expenseCents
}

/** Rozpočet jednej hlavnej kategórie v aktuálnom mesiaci. */
data class BudgetLine(val budget: Budget, val category: Category, val spentCents: Long) {
    val limitCents: Long get() = budget.monthlyLimitCents
    val remainingCents: Long get() = limitCents - spentCents

    /** Oranžový stav až nad limitom; presne 100 % je ešte v poriadku (A6). */
    val isOver: Boolean get() = spentCents > limitCents
    val progress: Float get() = fraction(spentCents, limitCents)
}

/** Súhrnná karta rozpočtov (mockup 08); prekročenia sa započítajú. */
data class BudgetSummary(val limitCents: Long, val spentCents: Long) {
    val remainingCents: Long get() = limitCents - spentCents
    val isOver: Boolean get() = spentCents > limitCents
    val progress: Float get() = fraction(spentCents, limitCents)
}

private fun fraction(part: Long, whole: Long): Float =
    if (whole <= 0) 0f else (part.toDouble() / whole).coerceIn(0.0, 1.0).toFloat()

object BudgetCalculator {

    /**
     * Rozpočty zobrazených (nearchivovaných) hlavných kategórií v poradí kategórií. Čerpanie zahŕňa
     * výdavky kategórie a všetkých jej podkategórií v mesiaci; [expenseByCategory] sú výdavky mesiaca
     * po kategóriách (bez prevodov, zo všetkých účtov).
     */
    fun lines(budgets: List<Budget>, categories: List<Category>, expenseByCategory: Map<Long, Long>): List<BudgetLine> {
        val byId = categories.associateBy { it.id }
        val spent = Reports.expenseByMainCategory(expenseByCategory, categories)
        return budgets
            .mapNotNull { budget ->
                val category = byId[budget.categoryId]?.takeIf { it.isMain && !it.archived } ?: return@mapNotNull null
                BudgetLine(budget, category, spent[category.id] ?: 0)
            }
            .sortedWith(compareBy({ it.category.sortOrder }, { it.category.id }))
    }

    fun summary(lines: List<BudgetLine>): BudgetSummary =
        BudgetSummary(limitCents = lines.sumOf { it.limitCents }, spentCents = lines.sumOf { it.spentCents })

    /** „ešte N dní“ = počet dní mesiaca − dnešný deň. */
    fun daysLeft(today: LocalDate): Int = today.lengthOfMonth() - today.dayOfMonth
}

/** Riadok rozpisu hlavnej kategórie; `category == null` je „bez podkategórie“. */
data class SubcategoryShare(val category: Category?, val cents: Long, val fraction: Float)

/** Hlavná kategória v reporte: súčet vrátane podkategórií, podiel na výdavkoch a dĺžka pruhu. */
data class CategoryShare(
    val category: Category,
    val cents: Long,
    val percent: Int,
    /** Dĺžka pruhu voči najväčšej kategórii (B13). */
    val fraction: Float,
    /** Rozpis po podkategóriách; prázdny, ak v podkategóriách nie sú výdavky. */
    val subcategories: List<SubcategoryShare>,
) {
    val expandable: Boolean get() = subcategories.isNotEmpty()
}

data class MonthTotals(val month: YearMonth, val totals: PeriodTotals)

object Reports {

    /** Podiel na celé percento, zaokrúhlený matematicky (ako mockup 09); pri nulovom celku 0. */
    fun percent(part: Long, total: Long): Int = if (total <= 0) 0 else ((part * 200 + total) / (2 * total)).toInt()

    /** Výdavky zrátané do hlavných kategórií (podkategória sa počíta do svojej hlavnej). */
    fun expenseByMainCategory(byCategory: Map<Long, Long>, categories: List<Category>): Map<Long, Long> {
        val byId = categories.associateBy { it.id }
        val result = HashMap<Long, Long>()
        for ((categoryId, cents) in byCategory) {
            val category = byId[categoryId] ?: continue
            val mainId = category.parentId ?: category.id
            result[mainId] = (result[mainId] ?: 0) + cents
        }
        return result
    }

    /**
     * Hlavné kategórie s výdavkami od najväčšej (všetky, nie len prvých N; B13). Pri rovnakej sume
     * rozhoduje poradie kategórií. Rozpis obsahuje podkategórie s výdavkami a riadok „bez podkategórie“,
     * ak má hlavná kategória aj priame výdavky.
     */
    fun categoryShares(byCategory: Map<Long, Long>, categories: List<Category>): List<CategoryShare> {
        val byId = categories.associateBy { it.id }
        val mainTotals = expenseByMainCategory(byCategory, categories).filterValues { it > 0 }
        val total = mainTotals.values.sum()
        val max = mainTotals.values.maxOrNull() ?: return emptyList()
        return mainTotals
            .mapNotNull { (mainId, cents) ->
                val main = byId[mainId] ?: return@mapNotNull null
                CategoryShare(
                    category = main,
                    cents = cents,
                    percent = percent(cents, total),
                    fraction = cents.toFloat() / max,
                    subcategories = subcategoryShares(main, cents, byCategory, categories),
                )
            }
            .sortedWith(compareByDescending<CategoryShare> { it.cents }.thenBy { it.category.sortOrder }.thenBy { it.category.id })
    }

    private fun subcategoryShares(
        main: Category,
        mainCents: Long,
        byCategory: Map<Long, Long>,
        categories: List<Category>,
    ): List<SubcategoryShare> {
        val subs = categories.filter { it.parentId == main.id }
            .mapNotNull { sub -> byCategory[sub.id]?.takeIf { it > 0 }?.let { sub to it } }
        if (subs.isEmpty()) return emptyList()
        val direct = byCategory[main.id] ?: 0
        val rows = subs.map { (sub, cents) -> SubcategoryShare(sub, cents, cents.toFloat() / mainCents) } +
            listOfNotNull(if (direct > 0) SubcategoryShare(null, direct, direct.toFloat() / mainCents) else null)
        val max = rows.maxOf { it.cents }.toFloat()
        return rows
            .map { it.copy(fraction = it.cents / max) }
            .sortedWith(compareByDescending<SubcategoryShare> { it.cents }.thenBy { it.category?.sortOrder ?: -1 })
    }

    /** 12 mesiacov roka; mesiac bez transakcií má nuly. */
    fun yearMonths(year: Int, byMonth: Map<YearMonth, PeriodTotals>): List<MonthTotals> =
        (1..12).map { m ->
            val month = YearMonth.of(year, m)
            MonthTotals(month, byMonth[month] ?: PeriodTotals(0, 0))
        }

    /** Šípka dopredu je za aktuálnym mesiacom či rokom vypnutá (B13). */
    fun canGoForward(period: Period, today: LocalDate): Boolean = when (period) {
        is Period.Month -> period.month.isBefore(YearMonth.from(today))
        is Period.Year -> period.year < today.year
        Period.All -> false
    }

    /** Šípka dozadu končí pri mesiaci (roku) najstaršej transakcie. */
    fun canGoBack(period: Period, earliest: LocalDate?): Boolean {
        earliest ?: return false
        return when (period) {
            is Period.Month -> period.month.isAfter(YearMonth.from(earliest))
            is Period.Year -> period.year > earliest.year
            Period.All -> false
        }
    }
}
