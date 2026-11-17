package io.github.mstajanc.gros.data

import io.github.mstajanc.gros.data.repository.InitialAccount
import io.github.mstajanc.gros.data.repository.InitialCategory
import io.github.mstajanc.gros.data.repository.SetupRepository
import io.github.mstajanc.gros.domain.Period
import io.github.mstajanc.gros.domain.TransactionFilter
import io.github.mstajanc.gros.domain.TransactionList
import io.github.mstajanc.gros.domain.model.AccountType
import io.github.mstajanc.gros.domain.model.CategoryKind
import io.github.mstajanc.gros.domain.model.Transaction
import io.github.mstajanc.gros.domain.rules.Violation
import java.time.YearMonth
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TransactionQueryTest : DatabaseTest() {

    private val november = Period.Month(YearMonth.of(2026, 11))

    private suspend fun list(
        period: Period = Period.All,
        accountId: Long? = null,
        categoryId: Long? = null,
        query: String = "",
    ): List<Transaction> {
        val all = categories.observeCategories().first()
        val ids = categoryId?.let { TransactionFilter.categoryIds(it, all) }
        return transactions.observeFiltered(TransactionFilter(period, accountId, categoryId, query), ids, all).first()
    }

    @Test
    fun `list is ordered from the newest and by creation time within a day`() = runTest {
        val accountId = account()
        val food = expenseCategory()
        val old = transactions.create(expense(accountId, food, 100).copy(date = today.minusDays(2)))
        val first = transactions.create(expense(accountId, food, 200))
        clock.advanceSeconds(60)
        val second = transactions.create(expense(accountId, food, 300))

        assertEquals(listOf(second, first, old), list().map { it.id })
        assertEquals(listOf(second, first), transactions.observeRecent(2).first().map { it.id })
    }

    @Test
    fun `period filter includes both boundary days`() = runTest {
        val accountId = account()
        val food = expenseCategory()
        val firstDay = transactions.create(expense(accountId, food, 100).copy(date = november.from))
        val lastDay = transactions.create(expense(accountId, food, 100).copy(date = november.to))
        transactions.create(expense(accountId, food, 100).copy(date = november.from.minusDays(1)))
        transactions.create(expense(accountId, food, 100).copy(date = november.to.plusDays(1)))

        assertEquals(setOf(firstDay, lastDay), list(period = november).map { it.id }.toSet())
        assertEquals(4, list(period = Period.Year(2026)).size)
        assertEquals(4, list(period = Period.All).size)
        assertTrue(list(period = Period.Year(2025)).isEmpty())
    }

    @Test
    fun `account filter shows its transactions including transfers from and to it`() = runTest {
        val checking = account("Bežný účet")
        val savings = account("Sporenie")
        val cash = account("Hotovosť")
        val food = expenseCategory()
        val own = transactions.create(expense(savings, food, 100))
        val incoming = transactions.create(transfer(checking, savings, 30_000))
        val outgoing = transactions.create(transfer(savings, cash, 5_000))
        transactions.create(expense(checking, food, 700))
        transactions.create(transfer(checking, cash, 900))

        assertEquals(setOf(own, incoming, outgoing), list(accountId = savings).map { it.id }.toSet())
    }

    @Test
    fun `main category filter includes subcategories and a subcategory can be chosen alone`() = runTest {
        val accountId = account()
        val car = expenseCategory("Auto", color = 2)
        val fuel = expenseCategory("Tankovanie", parentId = car)
        val food = expenseCategory("Potraviny")
        val direct = transactions.create(expense(accountId, car, 5_000))
        val inSub = transactions.create(expense(accountId, fuel, 6_120))
        transactions.create(expense(accountId, food, 4_230))

        assertEquals(setOf(direct, inSub), list(categoryId = car).map { it.id }.toSet())
        assertEquals(listOf(inSub), list(categoryId = fuel).map { it.id })
    }

    @Test
    fun `search ignores case and diacritics across all accounts and the sum matches`() = runTest {
        val checking = account("Bežný účet")
        val savings = account("Sporenie")
        val cash = account("Hotovosť")
        val other = expenseCategory("Ostatné", color = 5)
        transactions.create(expense(checking, other, 1_290, note = "Alza kábel USB-C").copy(date = today.minusDays(7)))
        transactions.create(expense(savings, other, 18_900, note = "ALZA slúchadlá").copy(date = today.minusDays(25)))
        transactions.create(expense(cash, other, 5_679, note = "alza školská taška").copy(date = today.minusDays(73)))
        transactions.create(expense(checking, other, 4_230, note = "Lidl"))
        transactions.create(expense(checking, other, 999))

        val found = list(query = "alza")
        assertEquals(3, found.size)
        assertEquals(1_290 + 18_900 + 5_679L, TransactionList.summarize(found).expenseCents)
        assertEquals(1, list(query = "SKOLSKA").size)
        assertEquals(1, list(query = "školská").size)
    }

    @Test
    fun `search finds category and subcategory names as well as notes`() = runTest {
        val accountId = account()
        val car = expenseCategory("Auto", color = 2)
        val fuel = expenseCategory("Tankovanie", parentId = car)
        val food = expenseCategory("Potraviny")
        val inSub = transactions.create(expense(accountId, fuel, 6_120))
        val inMain = transactions.create(expense(accountId, car, 5_000))
        val noteOnly = transactions.create(expense(accountId, food, 1_000, note = "tankovanie kanistra"))
        transactions.create(expense(accountId, food, 4_230, note = "Lidl"))

        assertEquals(setOf(inSub, noteOnly), list(query = "TANK").map { it.id }.toSet())
        assertEquals(setOf(inSub, inMain), list(query = "auto").map { it.id }.toSet())
        assertEquals(6_120 + 1_000L, TransactionList.summarize(list(query = "tank")).expenseCents)
    }

    @Test
    fun `search treats LIKE wildcards literally`() = runTest {
        val accountId = account()
        val food = expenseCategory()
        val percent = transactions.create(expense(accountId, food, 100, note = "zľava 50% Tesco"))
        transactions.create(expense(accountId, food, 100, note = "zlava 500 Tesco"))
        val underscore = transactions.create(expense(accountId, food, 100, note = "kod_a1"))
        transactions.create(expense(accountId, food, 100, note = "kodXa1"))

        assertEquals(listOf(percent), list(query = "50%").map { it.id })
        assertEquals(listOf(underscore), list(query = "kod_a").map { it.id })
    }

    @Test
    fun `filters combine with each other and with search`() = runTest {
        val checking = account("Bežný účet")
        val cash = account("Hotovosť")
        val food = expenseCategory("Potraviny")
        val fun_ = expenseCategory("Zábava", color = 4)
        val wanted = transactions.create(expense(checking, food, 100, note = "Lidl"))
        transactions.create(expense(cash, food, 100, note = "Lidl"))
        transactions.create(expense(checking, fun_, 100, note = "Lidl hračka"))
        transactions.create(expense(checking, food, 100, note = "Lidl").copy(date = today.minusMonths(2)))
        transactions.create(expense(checking, food, 100, note = "Billa"))

        assertEquals(
            listOf(wanted),
            list(period = november, accountId = checking, categoryId = food, query = "lidl").map { it.id },
        )
    }

    @Test
    fun `note suggestions are ranked by use then by recency and limited to five`() = runTest {
        val accountId = account()
        val food = expenseCategory()
        suspend fun use(note: String, times: Int = 1) = repeat(times) {
            clock.advanceSeconds(1)
            transactions.create(expense(accountId, food, 100, note = note))
        }
        use("Lidl", 3)
        use("Lidl Petržalka", 2)
        use("lidl online")
        use("Lidl+ kupón")
        use("Lidl akcia")
        use("Lidl pečivo")
        use("Billa", 5)

        assertEquals(
            listOf("Lidl", "Lidl Petržalka", "Lidl pečivo", "Lidl akcia", "Lidl+ kupón"),
            transactions.suggestNotes("li"),
        )
        assertEquals(listOf("Lidl Petržalka"), transactions.suggestNotes("PETRZ"))
        assertTrue(transactions.suggestNotes("l").isEmpty())
        assertTrue(transactions.suggestNotes("xyz").isEmpty())
    }

    @Test
    fun `initial setup writes accounts and two-level categories at once`() = runTest {
        SetupRepository(db).createInitialData(
            accounts = listOf(
                InitialAccount(" Bežný účet ", AccountType.CHECKING, 214_050, includeInTotal = true),
                InitialAccount("Investície", AccountType.INVESTMENT, 1_245_000, includeInTotal = false),
            ),
            categories = listOf(
                InitialCategory("Potraviny", CategoryKind.EXPENSE, 0),
                InitialCategory("Auto", CategoryKind.EXPENSE, 2, listOf("Tankovanie", "Servis")),
                InitialCategory("Výplata", CategoryKind.INCOME, 0),
            ),
        )

        val storedAccounts = accounts.observeAccounts().first()
        assertEquals(listOf("Bežný účet", "Investície"), storedAccounts.map { it.name })
        assertEquals(214_050, accounts.observeTotal().first().totalCents)

        val storedCategories = categories.observeCategories().first()
        val car = storedCategories.single { it.name == "Auto" }
        val subs = storedCategories.filter { it.parentId == car.id }
        assertEquals(listOf("Tankovanie", "Servis"), subs.sortedBy { it.sortOrder }.map { it.name })
        assertTrue(subs.all { it.color == 2 && it.kind == CategoryKind.EXPENSE })
        assertEquals(5, storedCategories.size)
    }

    @Test
    fun `initial setup needs an account and a main category of each kind`() = runTest {
        val setup = SetupRepository(db)
        val account = InitialAccount("Bežný účet", AccountType.CHECKING, 0, true)
        val expenseOnly = listOf(InitialCategory("Potraviny", CategoryKind.EXPENSE, 0))

        assertViolation(Violation.LAST_ACTIVE_ACCOUNT) {
            setup.createInitialData(emptyList(), expenseOnly + InitialCategory("Výplata", CategoryKind.INCOME, 0))
        }
        assertViolation(Violation.LAST_ACTIVE_MAIN_CATEGORY) { setup.createInitialData(listOf(account), expenseOnly) }
        assertViolation(Violation.NAME_BLANK) {
            setup.createInitialData(
                listOf(account, account.copy(name = " ")),
                expenseOnly + InitialCategory("Výplata", CategoryKind.INCOME, 0),
            )
        }
        assertTrue(accounts.observeAccounts().first().isEmpty())
        assertTrue(categories.observeCategories().first().isEmpty())
    }
}
