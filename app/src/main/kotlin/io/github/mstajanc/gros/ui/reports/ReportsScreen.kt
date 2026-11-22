package io.github.mstajanc.gros.ui.reports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.mstajanc.gros.AppContainer
import io.github.mstajanc.gros.R
import io.github.mstajanc.gros.domain.CategoryShare
import io.github.mstajanc.gros.domain.MoneyFormatter
import io.github.mstajanc.gros.domain.MonthTotals
import io.github.mstajanc.gros.domain.Period
import io.github.mstajanc.gros.domain.PeriodTotals
import io.github.mstajanc.gros.domain.Reports
import io.github.mstajanc.gros.domain.SignDisplay
import io.github.mstajanc.gros.domain.SlovakDates
import io.github.mstajanc.gros.ui.common.AmountText
import io.github.mstajanc.gros.ui.common.CenteredContent
import io.github.mstajanc.gros.ui.common.CircleIconButton
import io.github.mstajanc.gros.ui.common.GrosCard
import io.github.mstajanc.gros.ui.common.ProgressBar
import io.github.mstajanc.gros.ui.common.ScreenHeader
import io.github.mstajanc.gros.ui.common.ScreenPadding
import io.github.mstajanc.gros.ui.common.SegmentedControl
import io.github.mstajanc.gros.ui.common.SummaryTile
import io.github.mstajanc.gros.ui.common.grosViewModel
import io.github.mstajanc.gros.ui.theme.GrosShapes
import io.github.mstajanc.gros.ui.theme.GrosTheme
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class ReportState(
    val loaded: Boolean = false,
    val period: Period,
    val today: LocalDate,
    val totals: PeriodTotals = PeriodTotals(0, 0),
    val shares: List<CategoryShare> = emptyList(),
    /** Len v ročnom pohľade. */
    val months: List<MonthTotals> = emptyList(),
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
)

/** Report za mesiac alebo rok; počíta zo všetkých účtov, bez prevodov, podkategórie do hlavnej. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModel(private val container: AppContainer, private val handle: SavedStateHandle) : ViewModel() {

    private val reports = container.reportRepository
    private val today = container.today()
    private val periodText = handle.getStateFlow(KEY_PERIOD, YearMonth.from(today).toString())

    private fun currentPeriod(text: String): Period =
        if (text.length == 4) Period.Year(text.toInt()) else Period.Month(YearMonth.parse(text))

    val state: StateFlow<ReportState> = periodText.flatMapLatest { text ->
        val period = currentPeriod(text)
        val months: Flow<List<MonthTotals>> = if (period is Period.Year) {
            reports.observeMonthlyTotals(period.year).map { byMonth -> Reports.yearMonths(period.year, byMonth) }
        } else {
            flowOf(emptyList())
        }
        combine(
            reports.observeTotals(period),
            reports.observeExpenseByCategory(period),
            container.categoryRepository.observeCategories(),
            months,
            reports.observeEarliestDate(),
        ) { totals, byCategory, categories, monthTotals, earliest ->
            ReportState(
                loaded = true,
                period = period,
                today = today,
                totals = totals,
                shares = Reports.categoryShares(byCategory, categories),
                months = monthTotals,
                canGoBack = Reports.canGoBack(period, earliest),
                canGoForward = Reports.canGoForward(period, today),
            )
        }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ReportState(period = Period.Month(YearMonth.from(today)), today = today),
    )

    private fun setPeriod(period: Period) {
        handle[KEY_PERIOD] = when (period) {
            is Period.Month -> period.month.toString()
            is Period.Year -> period.year.toString()
            Period.All -> YearMonth.from(today).toString()
        }
    }

    /** Prepnutie Mesiac / Rok zachová rok aktuálne zobrazeného obdobia. */
    fun showYear(year: Boolean) {
        val period = state.value.period
        when {
            year && period is Period.Month -> setPeriod(Period.Year(period.month.year))
            !year && period is Period.Year -> setPeriod(
                Period.Month(if (period.year == today.year) YearMonth.from(today) else YearMonth.of(period.year, 12)),
            )
        }
    }

    fun previous() {
        if (state.value.canGoBack) setPeriod(state.value.period.previous())
    }

    fun next() {
        if (state.value.canGoForward) setPeriod(state.value.period.next())
    }

    private companion object {
        const val KEY_PERIOD = "report_period"
    }
}

/** Otvorenie transakcií z reportu: kategória, len priame transakcie („bez podkategórie“) a obdobie reportu. */
fun interface OpenCategory {
    fun open(categoryId: Long, exact: Boolean, period: Period)
}

@Composable
fun ReportsScreen(contentPadding: PaddingValues, onOpenCategory: OpenCategory) {
    val viewModel = grosViewModel { container, handle -> ReportsViewModel(container, handle) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(setOf<Long>()) }
    val isYear = state.period is Period.Year

    CenteredContent(modifier = Modifier.padding(contentPadding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ScreenHeader(stringResource(R.string.nav_reports)) {
                SegmentedControl(
                    options = listOf(stringResource(R.string.report_month), stringResource(R.string.report_year)),
                    selectedIndex = if (isYear) 1 else 0,
                    onSelect = { viewModel.showYear(it == 1) },
                    modifier = Modifier.widthIn(max = 200.dp),
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PeriodArrow(R.drawable.ic_chevron_left, stringResource(R.string.period_previous), state.canGoBack, viewModel::previous)
                Text(
                    periodLabel(state.period),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
                PeriodArrow(R.drawable.ic_chevron_right, stringResource(R.string.period_next), state.canGoForward, viewModel::next)
            }
            if (!state.loaded) return@Column

            BalanceCard(state.totals)
            if (isYear) MonthChart(state.months)

            Text(
                stringResource(R.string.report_by_category),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            if (state.shares.isEmpty()) {
                Text(
                    stringResource(R.string.report_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.shares.forEach { share ->
                val id = share.category.id
                val isExpanded = id in expanded
                CategoryRow(
                    share = share,
                    expanded = isExpanded,
                    onClick = {
                        // Kategória bez podkategórií otvorí transakcie; s podkategóriami sa rozbalí (špecifikácia).
                        if (share.expandable) {
                            expanded = if (isExpanded) expanded - id else expanded + id
                        } else {
                            onOpenCategory.open(id, false, state.period)
                        }
                    },
                )
                if (isExpanded) {
                    share.subcategories.forEach { sub ->
                        SubcategoryRow(
                            name = sub.category?.name ?: stringResource(R.string.report_no_subcategory),
                            cents = sub.cents,
                            fraction = sub.fraction,
                            color = GrosTheme.colors.category(share.category.color).content,
                            onClick = {
                                if (sub.category != null) {
                                    onOpenCategory.open(sub.category.id, false, state.period)
                                } else {
                                    onOpenCategory.open(id, true, state.period)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun periodLabel(period: Period): String = when (period) {
    is Period.Month -> SlovakDates.monthYear(period.month)
    is Period.Year -> period.year.toString()
    Period.All -> stringResource(R.string.filter_period_all)
}

@Composable
private fun PeriodArrow(icon: Int, description: String, enabled: Boolean, onClick: () -> Unit) {
    // Vypnutá šípka ostáva na mieste, aby nadpis neposkakoval (B13).
    Box(modifier = Modifier.alpha(if (enabled) 1f else 0.3f)) {
        CircleIconButton(icon, description, onClick = { if (enabled) onClick() })
    }
}

@Composable
private fun BalanceCard(totals: PeriodTotals) {
    GrosCard(large = true) {
        Text(
            stringResource(R.string.report_balance),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AmountText(
            MoneyFormatter.format(totals.balanceCents, SignDisplay.ALWAYS),
            style = MaterialTheme.typography.displayMedium,
            color = if (totals.balanceCents >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryTile(
                label = stringResource(R.string.report_income),
                amount = MoneyFormatter.format(totals.incomeCents),
                modifier = Modifier.weight(1f),
            )
            SummaryTile(
                label = stringResource(R.string.report_expenses),
                amount = MoneyFormatter.format(totals.expenseCents),
                modifier = Modifier.weight(1f),
                amountColor = GrosTheme.colors.expense,
            )
        }
    }
}

/** Riadok hlavnej kategórie (mockup 09): názov, „520,00 € · 40 %“ a pruh vo farbe kategórie. */
@Composable
private fun CategoryRow(share: CategoryShare, expanded: Boolean, onClick: () -> Unit) {
    val color = GrosTheme.colors.category(share.category.color).content
    val stateText = when {
        !share.expandable -> null
        expanded -> stringResource(R.string.report_expanded)
        else -> stringResource(R.string.report_collapsed)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GrosShapes.cardSmall)
            .clickable(role = Role.Button, onClick = onClick)
            .then(if (stateText != null) Modifier.semantics { stateDescription = stateText } else Modifier)
            .padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(share.category.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            AmountText(
                stringResource(R.string.report_share, MoneyFormatter.format(share.cents), share.percent),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        ProgressBar(fraction = share.fraction, color = color)
    }
}

/** Riadok rozpisu podkategórie; pruh voči najväčšiemu riadku rozpisu. */
@Composable
private fun SubcategoryRow(name: String, cents: Long, fraction: Float, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GrosShapes.cardSmall)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 20.dp, top = 4.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            AmountText(MoneyFormatter.format(cents), style = MaterialTheme.typography.bodyLarge)
        }
        ProgressBar(fraction = fraction, color = color.copy(alpha = 0.7f), height = 6.dp)
    }
}

/** Ročný graf: 12 dvojíc stĺpcov (príjmy akcent, výdavky sivé) voči najväčšej mesačnej hodnote. */
@Composable
private fun MonthChart(months: List<MonthTotals>) {
    val income = MaterialTheme.colorScheme.primary
    val expense = MaterialTheme.colorScheme.onSurfaceVariant
    val max = months.maxOfOrNull { maxOf(it.totals.incomeCents, it.totals.expenseCents) }?.takeIf { it > 0 } ?: 1L
    GrosCard {
        Text(
            stringResource(R.string.report_by_month),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 12.dp).semantics { heading() },
        )
        Row(modifier = Modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            months.forEach { month ->
                val description = stringResource(
                    R.string.report_month_description,
                    SlovakDates.monthName(month.month),
                    MoneyFormatter.format(month.totals.incomeCents),
                    MoneyFormatter.format(month.totals.expenseCents),
                )
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clearAndSetSemantics { contentDescription = description },
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Bar(month.totals.incomeCents.toFloat() / max, income, Modifier.weight(1f))
                    Bar(month.totals.expenseCents.toFloat() / max, expense, Modifier.weight(1f))
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            months.forEach { month ->
                Text(
                    SlovakDates.shortMonth(month.month.monthValue),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier.weight(1f).clearAndSetSemantics { },
                )
            }
        }
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LegendItem(stringResource(R.string.report_income), income)
            LegendItem(stringResource(R.string.report_expenses), expense)
        }
    }
}

@Composable
private fun Bar(fraction: Float, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    val value = fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxHeight(if (value > 0f) maxOf(value, 0.02f) else 0f)
            .clip(GrosShapes.cardSmall)
            .background(color),
    )
}

@Composable
private fun LegendItem(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(10.dp).clip(GrosShapes.pill).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
