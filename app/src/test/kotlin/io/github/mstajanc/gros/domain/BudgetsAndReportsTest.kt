package io.github.mstajanc.gros.domain

import io.github.mstajanc.gros.domain.model.Budget
import io.github.mstajanc.gros.domain.model.Category
import io.github.mstajanc.gros.domain.model.CategoryKind
import java.time.LocalDate
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

/** Kategórie a výdavky podľa mockupov 08 a 09 (november 2026). */
private object Mockup {
    val food = Category(id = 1, name = "Potraviny", kind = CategoryKind.EXPENSE, color = 0, sortOrder = 0)
    val housing = Category(id = 2, name = "Bývanie", kind = CategoryKind.EXPENSE, color = 1, sortOrder = 1)
    val rent = Category(id = 21, parentId = 2, name = "Nájom", kind = CategoryKind.EXPENSE, color = 1, sortOrder = 0)
    val car = Category(id = 3, name = "Auto", kind = CategoryKind.EXPENSE, color = 2, sortOrder = 2)
    val fuel = Category(id = 31, parentId = 3, name = "Tankovanie", kind = CategoryKind.EXPENSE, color = 2, sortOrder = 0)
    val service = Category(id = 32, parentId = 3, name = "Servis", kind = CategoryKind.EXPENSE, color = 2, sortOrder = 1)
    val kids = Category(id = 4, name = "Deti", kind = CategoryKind.EXPENSE, color = 3, sortOrder = 3)
    val fun_ = Category(id = 5, name = "Zábava", kind = CategoryKind.EXPENSE, color = 4, sortOrder = 4)
    val clothes = Category(id = 6, name = "Oblečenie", kind = CategoryKind.EXPENSE, color = 1, sortOrder = 5)
    val other = Category(id = 7, name = "Ostatné", kind = CategoryKind.EXPENSE, color = 5, sortOrder = 6)
    val salary = Category(id = 8, name = "Výplata", kind = CategoryKind.INCOME, color = 0, sortOrder = 0)
    val categories = listOf(food, housing, rent, car, fuel, service, kids, fun_, clothes, other, salary)

    /** Výdavky po kategóriách (v centoch); Auto má 148,70 € = 61,20 priamo + 50,00 Tankovanie + 37,50 Servis. */
    val expenses = mapOf(
        food.id to 38_620L,
        rent.id to 52_000L,
        car.id to 6_120L,
        fuel.id to 5_000L,
        service.id to 3_750L,
        kids.id to 11_250L,
        fun_.id to 8_400L,
        other.id to 6_100L,
    )
}

class BudgetCalculatorTest {

    private val budgets = listOf(
        Budget(id = 1, categoryId = Mockup.food.id, monthlyLimitCents = 45_000),
        Budget(id = 2, categoryId = Mockup.car.id, monthlyLimitCents = 20_000),
        Budget(id = 3, categoryId = Mockup.kids.id, monthlyLimitCents = 15_000),
        Budget(id = 4, categoryId = Mockup.fun_.id, monthlyLimitCents = 8_000),
    )

    @Test
    fun `budget lines match mockup 08`() {
        val lines = BudgetCalculator.lines(budgets, Mockup.categories, Mockup.expenses)

        assertEquals(listOf("Potraviny", "Auto", "Deti", "Zábava"), lines.map { it.category.name })
        assertEquals(listOf(38_620L, 14_870L, 11_250L, 8_400L), lines.map { it.spentCents })
        assertEquals(listOf(6_380L, 5_130L, 3_750L, -400L), lines.map { it.remainingCents })
        assertEquals(listOf(false, false, false, true), lines.map { it.isOver })
    }

    @Test
    fun `summary card matches mockup 08`() {
        val summary = BudgetCalculator.summary(BudgetCalculator.lines(budgets, Mockup.categories, Mockup.expenses))
        assertEquals(88_000, summary.limitCents)
        assertEquals(73_140, summary.spentCents)
        assertEquals(14_860, summary.remainingCents)
        assertFalse(summary.isOver)
    }

    @Test
    fun `exactly the limit is not over and progress is capped`() {
        val exact = BudgetCalculator.lines(listOf(budgets[3]), Mockup.categories, mapOf(Mockup.fun_.id to 8_000L)).single()
        assertFalse(exact.isOver)
        assertEquals(0, exact.remainingCents)
        assertEquals(1f, exact.progress)

        val over = BudgetCalculator.lines(listOf(budgets[3]), Mockup.categories, Mockup.expenses).single()
        assertEquals(1f, over.progress)
        val empty = BudgetCalculator.lines(listOf(budgets[0]), Mockup.categories, emptyMap()).single()
        assertEquals(0f, empty.progress)
    }

    @Test
    fun `overspent summary is negative`() {
        val summary = BudgetCalculator.summary(
            BudgetCalculator.lines(listOf(budgets[3]), Mockup.categories, mapOf(Mockup.fun_.id to 9_000L)),
        )
        assertEquals(-1_000, summary.remainingCents)
        assertTrue(summary.isOver)
    }

    @Test
    fun `budget of an archived category is hidden`() {
        val categories = Mockup.categories.map { if (it.id == Mockup.kids.id) it.copy(archived = true) else it }
        val lines = BudgetCalculator.lines(budgets, categories, Mockup.expenses)
        assertEquals(listOf("Potraviny", "Auto", "Zábava"), lines.map { it.category.name })
    }

    @Test
    fun `days left in the month`() {
        assertEquals(15, BudgetCalculator.daysLeft(LocalDate.of(2026, 11, 15)))
        assertEquals(0, BudgetCalculator.daysLeft(LocalDate.of(2026, 11, 30)))
        assertEquals(28, BudgetCalculator.daysLeft(LocalDate.of(2028, 2, 1)))
    }
}

class ReportsTest {

    @Test
    fun `category shares match mockup 09`() {
        val shares = Reports.categoryShares(Mockup.expenses, Mockup.categories)

        assertEquals(listOf("Bývanie", "Potraviny", "Auto", "Deti", "Zábava", "Ostatné"), shares.map { it.category.name })
        assertEquals(listOf(52_000L, 38_620L, 14_870L, 11_250L, 8_400L, 6_100L), shares.map { it.cents })
        assertEquals(listOf(40, 29, 11, 9, 6, 5), shares.map { it.percent })
        assertEquals(1f, shares.first().fraction)
        assertEquals(6_100f / 52_000f, shares.last().fraction, 0.0001f)
    }

    @Test
    fun `subcategory breakdown includes a row without subcategory`() {
        val car = Reports.categoryShares(Mockup.expenses, Mockup.categories).single { it.category.id == Mockup.car.id }

        assertTrue(car.expandable)
        // Zoradené od najväčšej; „bez podkategórie“ (null) je riadok s priamymi výdavkami.
        assertEquals(listOf(null, "Tankovanie", "Servis"), car.subcategories.map { it.category?.name })
        assertEquals(listOf(6_120L, 5_000L, 3_750L), car.subcategories.map { it.cents })
        assertEquals(1f, car.subcategories.first().fraction)
    }

    @Test
    fun `category with spending only in one subcategory and none directly`() {
        val housing = Reports.categoryShares(Mockup.expenses, Mockup.categories).single { it.category.id == Mockup.housing.id }
        assertTrue(housing.expandable)
        assertEquals(listOf("Nájom"), housing.subcategories.map { it.category?.name })
    }

    @Test
    fun `category without subcategory spending is not expandable`() {
        val food = Reports.categoryShares(Mockup.expenses, Mockup.categories).single { it.category.id == Mockup.food.id }
        assertFalse(food.expandable)
        assertTrue(food.subcategories.isEmpty())
    }

    @Test
    fun `percent rounds half up and handles zero total`() {
        assertEquals(40, Reports.percent(52_000, 131_240))
        assertEquals(5, Reports.percent(6_100, 131_240))
        assertEquals(50, Reports.percent(1, 2))
        assertEquals(0, Reports.percent(0, 0))
        assertTrue(Reports.categoryShares(emptyMap(), Mockup.categories).isEmpty())
    }

    @Test
    fun `year has twelve months even without data`() {
        val months = Reports.yearMonths(
            2026,
            mapOf(YearMonth.of(2026, 3) to PeriodTotals(290_000, 131_240)),
        )
        assertEquals(12, months.size)
        assertEquals(YearMonth.of(2026, 1), months.first().month)
        assertEquals(PeriodTotals(290_000, 131_240), months[2].totals)
        assertEquals(PeriodTotals(0, 0), months[0].totals)
        assertEquals(158_760, months[2].totals.balanceCents)
    }

    @Test
    fun `period navigation stops at the current period and the oldest transaction`() {
        val today = LocalDate.of(2026, 11, 22)
        val november = Period.Month(YearMonth.of(2026, 11))
        assertFalse(Reports.canGoForward(november, today))
        assertTrue(Reports.canGoForward(Period.Month(YearMonth.of(2026, 10)), today))
        assertFalse(Reports.canGoForward(Period.Year(2026), today))
        assertTrue(Reports.canGoForward(Period.Year(2025), today))

        val earliest = LocalDate.of(2026, 9, 3)
        assertTrue(Reports.canGoBack(november, earliest))
        assertFalse(Reports.canGoBack(Period.Month(YearMonth.of(2026, 9)), earliest))
        assertFalse(Reports.canGoBack(Period.Year(2026), earliest))
        assertTrue(Reports.canGoBack(Period.Year(2027), earliest))
        assertFalse(Reports.canGoBack(november, null))
    }

    @Test
    fun `short month names`() {
        assertEquals("jan", SlovakDates.shortMonth(1))
        assertEquals("máj", SlovakDates.shortMonth(5))
        assertEquals("dec", SlovakDates.shortMonth(12))
    }
}
