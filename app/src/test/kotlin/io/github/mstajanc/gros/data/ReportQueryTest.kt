package io.github.mstajanc.gros.data

import io.github.mstajanc.gros.data.repository.ReportRepository
import io.github.mstajanc.gros.domain.BudgetCalculator
import io.github.mstajanc.gros.domain.Period
import io.github.mstajanc.gros.domain.PeriodTotals
import io.github.mstajanc.gros.domain.Reports
import io.github.mstajanc.gros.domain.TransactionFilter
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * Akceptačné kritérium M4: čísla rozpočtov a reportov sedia s ručným výpočtom.
 * Testovacie dáta zodpovedajú mockupom 08 (rozpočty) a 09 (report za november 2026).
 */
class ReportQueryTest : DatabaseTest() {

    private val november = Period.Month(YearMonth.of(2026, 11))
    private fun nov(day: Int) = LocalDate.of(2026, 11, day)

    private data class Fixture(
        val checking: Long,
        val cash: Long,
        val savings: Long,
        val investments: Long,
        val food: Long,
        val housing: Long,
        val rent: Long,
        val car: Long,
        val fuel: Long,
        val kids: Long,
        val fun_: Long,
        val other: Long,
        val salary: Long,
    )

    private suspend fun fixture(): Fixture {
        val f = Fixture(
            checking = account("Bežný účet", opening = 214_050),
            cash = account("Hotovosť", opening = 8_500),
            savings = account("Sporenie", opening = 630_000),
            investments = account("Investície", opening = 1_245_000, includeInTotal = false),
            food = expenseCategory("Potraviny", color = 0),
            housing = expenseCategory("Bývanie", color = 1),
            rent = 0, car = 0, fuel = 0, kids = 0, fun_ = 0, other = 0, salary = 0,
        )
        val car = expenseCategory("Auto", color = 2)
        val withIds = f.copy(
            rent = expenseCategory("Nájom", parentId = f.housing),
            car = car,
            fuel = expenseCategory("Tankovanie", parentId = car),
            kids = expenseCategory("Deti", color = 3),
            fun_ = expenseCategory("Zábava", color = 4),
            other = expenseCategory("Ostatné", color = 5),
            salary = incomeCategory("Výplata"),
        )
        with(withIds) {
            suspend fun spend(account: Long, category: Long, cents: Long, day: Int) =
                transactions.create(expense(account, category, cents).copy(date = nov(day)))

            transactions.create(income(checking, salary, 290_000).copy(date = nov(10)))
            spend(checking, rent, 52_000, 1)
            spend(checking, food, 18_900, 12)
            spend(checking, food, 4_230, 15)
            spend(cash, food, 15_490, 14)
            spend(checking, car, 6_120, 14)
            spend(checking, fuel, 8_750, 3)
            spend(cash, kids, 11_250, 5)
            spend(cash, fun_, 2_400, 12)
            // Výdavok z účtu mimo súčtu sa v reportoch aj rozpočtoch počíta.
            spend(investments, fun_, 6_000, 8)
            spend(checking, other, 6_100, 8)
            // Prevod sa do reportov ani rozpočtov nepočíta.
            transactions.create(transfer(checking, savings, 30_000).copy(date = nov(15)))
            // Mimo novembra: posledný deň októbra a prvý deň decembra.
            transactions.create(expense(checking, food, 7_777).copy(date = LocalDate.of(2026, 10, 31)))
            transactions.create(expense(checking, food, 99_999).copy(date = LocalDate.of(2026, 12, 1)))
        }
        return withIds
    }

    @Test
    fun `monthly report matches mockup 09`() = runTest {
        val f = fixture()
        val reports = ReportRepository(db)

        assertEquals(PeriodTotals(290_000, 131_240), reports.observeTotals(november).first())
        assertEquals(158_760, reports.observeTotals(november).first().balanceCents)

        val shares = Reports.categoryShares(reports.observeExpenseByCategory(november).first(), categories.observeCategories().first())
        assertEquals(listOf("Bývanie", "Potraviny", "Auto", "Deti", "Zábava", "Ostatné"), shares.map { it.category.name })
        assertEquals(listOf(52_000L, 38_620L, 14_870L, 11_250L, 8_400L, 6_100L), shares.map { it.cents })
        assertEquals(listOf(40, 29, 11, 9, 6, 5), shares.map { it.percent })

        val car = shares.single { it.category.id == f.car }
        assertEquals(listOf("Tankovanie", null), car.subcategories.map { it.category?.name })
        assertEquals(listOf(8_750L, 6_120L), car.subcategories.map { it.cents })
    }

    @Test
    fun `budgets match mockup 08`() = runTest {
        val f = fixture()
        budgets.create(f.food, 45_000)
        budgets.create(f.car, 20_000)
        budgets.create(f.kids, 15_000)
        budgets.create(f.fun_, 8_000)

        val lines = BudgetCalculator.lines(
            budgets.observeBudgets().first(),
            categories.observeCategories().first(),
            ReportRepository(db).observeExpenseByCategory(november).first(),
        )
        assertEquals(listOf("Potraviny", "Auto", "Deti", "Zábava"), lines.map { it.category.name })
        assertEquals(listOf(38_620L, 14_870L, 11_250L, 8_400L), lines.map { it.spentCents })
        assertEquals(listOf(6_380L, 5_130L, 3_750L, -400L), lines.map { it.remainingCents })

        val summary = BudgetCalculator.summary(lines)
        assertEquals(14_860, summary.remainingCents)
        assertEquals(88_000, summary.limitCents)
    }

    @Test
    fun `balance correction changes neither reports nor budgets`() = runTest {
        val f = fixture()
        val reports = ReportRepository(db)
        val before = reports.observeTotals(november).first()
        val byCategoryBefore = reports.observeExpenseByCategory(november).first()

        accounts.correctBalance(f.checking, actualBalanceCents = 1_000)

        assertEquals(before, reports.observeTotals(november).first())
        assertEquals(byCategoryBefore, reports.observeExpenseByCategory(november).first())
    }

    @Test
    fun `yearly report has monthly totals and the earliest date`() = runTest {
        fixture()
        val reports = ReportRepository(db)

        val months = Reports.yearMonths(2026, reports.observeMonthlyTotals(2026).first())
        assertEquals(PeriodTotals(0, 7_777), months[9].totals)
        assertEquals(PeriodTotals(290_000, 131_240), months[10].totals)
        assertEquals(PeriodTotals(0, 99_999), months[11].totals)
        assertEquals(PeriodTotals(290_000, 131_240 + 7_777 + 99_999), reports.observeTotals(Period.Year(2026)).first())
        assertEquals(LocalDate.of(2026, 10, 31), reports.observeEarliestDate().first())
    }

    @Test
    fun `earliest date is null without transactions`() = runTest {
        assertNull(ReportRepository(db).observeEarliestDate().first())
        assertEquals(PeriodTotals(0, 0), ReportRepository(db).observeTotals(november).first())
    }

    @Test
    fun `exact category filter shows only transactions without subcategory`() = runTest {
        val f = fixture()
        val all = categories.observeCategories().first()
        suspend fun list(filter: TransactionFilter) =
            transactions.observeFiltered(filter, filter.categoryIds(all), all).first()

        val whole = list(TransactionFilter(november, categoryId = f.car))
        val direct = list(TransactionFilter(november, categoryId = f.car, exactCategory = true))

        assertEquals(setOf(6_120L, 8_750L), whole.map { it.amountCents }.toSet())
        assertEquals(listOf(6_120L), direct.map { it.amountCents })
    }
}
