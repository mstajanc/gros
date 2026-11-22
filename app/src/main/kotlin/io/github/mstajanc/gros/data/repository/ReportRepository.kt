package io.github.mstajanc.gros.data.repository

import io.github.mstajanc.gros.data.db.GrosDatabase
import io.github.mstajanc.gros.domain.PeriodTotals
import io.github.mstajanc.gros.domain.Period
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Súčty pre rozpočty a reporty. Počítajú sa zo všetkých účtov (aj mimo súčtu) a bez prevodov;
 * oprava zostatku ich nemení, lebo nevytvára transakciu.
 */
class ReportRepository(db: GrosDatabase) {

    private val transactions = db.transactionDao()

    fun observeTotals(period: Period): Flow<PeriodTotals> =
        transactions.observeTotals(period.from, period.to).map { PeriodTotals(it.incomeCents, it.expenseCents) }

    /** Výdavky po kategóriách (hlavných aj podkategóriách, tak ako sú zapísané). */
    fun observeExpenseByCategory(period: Period): Flow<Map<Long, Long>> =
        transactions.observeExpenseByCategory(period.from, period.to)
            .map { rows -> rows.associate { it.categoryId to it.cents } }

    fun observeMonthlyTotals(year: Int): Flow<Map<YearMonth, PeriodTotals>> =
        transactions.observeMonthlyTotals(LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)).map { rows ->
            rows.associate { YearMonth.parse(it.month) to PeriodTotals(it.incomeCents, it.expenseCents) }
        }

    fun observeEarliestDate(): Flow<LocalDate?> = transactions.observeEarliestDate().map { it?.let(LocalDate::parse) }
}
