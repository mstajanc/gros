package io.github.mstajanc.gros.ui.transactions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mstajanc.gros.R
import io.github.mstajanc.gros.domain.DayGroup
import io.github.mstajanc.gros.domain.MoneyFormatter
import io.github.mstajanc.gros.domain.Period
import io.github.mstajanc.gros.domain.RelativeDay
import io.github.mstajanc.gros.domain.SignDisplay
import io.github.mstajanc.gros.domain.SlovakDates
import io.github.mstajanc.gros.domain.model.Category
import io.github.mstajanc.gros.domain.model.Transaction
import io.github.mstajanc.gros.ui.common.AmountText
import io.github.mstajanc.gros.ui.common.CenteredContent
import io.github.mstajanc.gros.ui.common.CircleIconButton
import io.github.mstajanc.gros.ui.common.FilterChip
import io.github.mstajanc.gros.ui.common.GrosTextField
import io.github.mstajanc.gros.ui.common.OptionSheet
import io.github.mstajanc.gros.ui.common.PrimaryButton
import io.github.mstajanc.gros.ui.common.RowStyle
import io.github.mstajanc.gros.ui.common.ScreenHeader
import io.github.mstajanc.gros.ui.common.ScreenPadding
import io.github.mstajanc.gros.ui.common.SheetOption
import io.github.mstajanc.gros.ui.common.SummaryTile
import io.github.mstajanc.gros.ui.common.TransactionRow
import io.github.mstajanc.gros.ui.common.grosViewModel
import io.github.mstajanc.gros.ui.common.outlined
import io.github.mstajanc.gros.ui.common.transactionCountText
import io.github.mstajanc.gros.ui.theme.GrosShapes
import io.github.mstajanc.gros.ui.theme.GrosTheme
import java.time.YearMonth
import kotlinx.coroutines.launch

private enum class FilterSheet { PERIOD, ACCOUNT, CATEGORY }

private enum class PeriodKind { MONTH, YEAR, ALL }

private val Period.kind: PeriodKind
    get() = when (this) {
        is Period.Month -> PeriodKind.MONTH
        is Period.Year -> PeriodKind.YEAR
        Period.All -> PeriodKind.ALL
    }

@Composable
private fun periodLabel(period: Period): String = when (period) {
    is Period.Month -> SlovakDates.monthYear(period.month)
    is Period.Year -> period.year.toString()
    Period.All -> stringResource(R.string.filter_period_all)
}

@Composable
fun TransactionsScreen(onEdit: (Long) -> Unit, contentPadding: PaddingValues) {
    val viewModel = grosViewModel { container, handle -> TransactionsViewModel(container, handle) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<FilterSheet?>(null) }

    BackHandler(enabled = state.searchActive, onBack = viewModel::closeSearch)

    CenteredContent(modifier = Modifier.padding(contentPadding)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(horizontal = ScreenPadding).padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.searchActive) {
                    SearchBar(query = state.filter.query, onQuery = viewModel::setQuery, onClose = viewModel::closeSearch)
                } else {
                    ScreenHeader(title = stringResource(R.string.nav_transactions)) {
                        CircleIconButton(R.drawable.ic_search, stringResource(R.string.search_open), viewModel::openSearch)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(periodLabel(state.filter.period), selected = true, onClick = { sheet = FilterSheet.PERIOD })
                    FilterChip(
                        text = state.filter.accountId?.let(state.lookup::accountName)
                            ?: stringResource(R.string.filter_all_accounts),
                        selected = state.filter.accountId != null,
                        onClick = { sheet = FilterSheet.ACCOUNT },
                    )
                    FilterChip(
                        text = state.filter.categoryId?.let(state.lookup::categoryTitle)
                            ?: stringResource(R.string.filter_all_categories),
                        selected = state.filter.categoryId != null,
                        onClick = { sheet = FilterSheet.CATEGORY },
                    )
                }
            }

            if (state.loaded) {
                LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp)) {
                    item(key = "summary") { Summary(state) }
                    if (state.transactions.isEmpty()) {
                        item(key = "empty") { InfoText(stringResource(R.string.list_empty)) }
                    } else if (state.searchActive) {
                        // V režime hľadania je titulkom poznámka a zoznam nie je delený po dňoch.
                        items(state.transactions, key = { it.id }) { transaction ->
                            SwipeRow(transaction, state, RowStyle.SEARCH, onEdit, viewModel::delete)
                        }
                    } else {
                        state.groups.forEach { group ->
                            item(key = "day-${group.date}") { DayHeader(group, state) }
                            items(group.transactions, key = { it.id }) { transaction ->
                                SwipeRow(transaction, state, RowStyle.LIST, onEdit, viewModel::delete)
                            }
                        }
                    }
                    if (state.searchActive) {
                        item(key = "search-info") { InfoText(stringResource(R.string.search_info)) }
                    }
                }
            }
        }
    }

    when (sheet) {
        FilterSheet.PERIOD -> PeriodSheet(
            period = state.filter.period,
            currentMonth = YearMonth.from(state.today),
            onPeriod = viewModel::setPeriod,
            onPrevious = viewModel::previousPeriod,
            onNext = viewModel::nextPeriod,
            onDismiss = { sheet = null },
        )

        FilterSheet.ACCOUNT -> OptionSheet(
            title = stringResource(R.string.filter_account_title),
            // Archivované účty ostávajú vo filtroch.
            options = listOf(SheetOption<Long?>(null, stringResource(R.string.filter_all_accounts))) +
                state.lookup.accounts.values
                    .sortedWith(compareBy({ it.archived }, { it.sortOrder }, { it.id }))
                    .map {
                        SheetOption<Long?>(
                            it.id,
                            it.name,
                            if (it.archived) stringResource(R.string.label_archived) else null,
                        )
                    },
            selected = state.filter.accountId,
            onSelect = {
                viewModel.setAccount(it)
                sheet = null
            },
            onDismiss = { sheet = null },
        )

        FilterSheet.CATEGORY -> OptionSheet(
            title = stringResource(R.string.filter_category_title),
            options = listOf(SheetOption<Long?>(null, stringResource(R.string.filter_category_all))) +
                categoryOptions(state.lookup.categories.values.toList(), stringResource(R.string.label_archived)),
            selected = state.filter.categoryId,
            onSelect = {
                viewModel.setCategory(it)
                sheet = null
            },
            onDismiss = { sheet = null },
        )

        null -> Unit
    }
}

/** Hlavné kategórie nasledované svojimi podkategóriami („Auto“, „Auto · Tankovanie“). */
private fun categoryOptions(categories: List<Category>, archivedLabel: String): List<SheetOption<Long?>> {
    val order = compareBy<Category>({ it.kind }, { it.archived }, { it.sortOrder }, { it.id })
    return categories.filter { it.isMain }.sortedWith(order).flatMap { main ->
        val children = categories.filter { it.parentId == main.id }.sortedWith(order)
        listOf(SheetOption<Long?>(main.id, main.name, archivedLabel.takeIf { main.archived })) +
            children.map {
                SheetOption<Long?>(it.id, "${main.name} · ${it.name}", archivedLabel.takeIf { _ -> it.archived || main.archived })
            }
    }
}

@Composable
private fun SearchBar(query: String, onQuery: (String) -> Unit, onClose: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val hint = stringResource(R.string.search_hint)
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircleIconButton(R.drawable.ic_chevron_left, stringResource(R.string.action_back), onClose)
        Box(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .background(MaterialTheme.colorScheme.surface, GrosShapes.pill)
                .outlined(MaterialTheme.colorScheme.primary, GrosShapes.pill)
                .padding(horizontal = 18.dp, vertical = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) {
                Text(hint, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            GrosTextField(
                value = query,
                onValueChange = onQuery,
                label = hint,
                maxLength = 100,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            )
        }
    }
}

/** Nad výsledkom je vždy počet transakcií a obe sumy; pri filtri účtu aj jeho zostatok. */
@Composable
private fun Summary(state: TransactionsState) {
    Column(
        modifier = Modifier.padding(horizontal = ScreenPadding).padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            transactionCountText(state.summary.count),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryTile(
                label = stringResource(R.string.tx_expenses),
                amount = MoneyFormatter.format(-state.summary.expenseCents),
                modifier = Modifier.weight(1f),
                containerColor = MaterialTheme.colorScheme.surface,
            )
            SummaryTile(
                label = stringResource(R.string.tx_income),
                amount = MoneyFormatter.format(state.summary.incomeCents, SignDisplay.ALWAYS),
                modifier = Modifier.weight(1f),
                amountColor = MaterialTheme.colorScheme.primary,
                containerColor = MaterialTheme.colorScheme.surface,
            )
        }
        val balance = state.accountBalanceCents
        if (balance != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, GrosShapes.cardSmall)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.account_balance_label),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                AmountText(MoneyFormatter.format(balance))
            }
        }
    }
}

@Composable
private fun DayHeader(group: DayGroup, state: TransactionsState) {
    val label = when (SlovakDates.relativeDay(group.date, state.today)) {
        RelativeDay.TODAY -> stringResource(R.string.day_today)
        RelativeDay.YESTERDAY -> stringResource(R.string.day_yesterday)
        RelativeDay.OTHER -> SlovakDates.weekdayDate(group.date, state.today)
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(top = 14.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        AmountText(
            MoneyFormatter.format(group.netCents, SignDisplay.ALWAYS),
            style = MaterialTheme.typography.bodyMedium,
            color = if (group.netCents > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Riadok, ktorý sa potiahnutím zmaže; Späť je v snackbare. */
@Composable
private fun SwipeRow(
    transaction: Transaction,
    state: TransactionsState,
    style: RowStyle,
    onEdit: (Long) -> Unit,
    onDelete: (Transaction) -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    SwipeToDismissBox(
        state = dismissState,
        onDismiss = {
            // Stav sa ukladá podľa kľúča riadka; bez návratu do pokoja by sa transakcia
            // obnovená cez Späť vrátila ako „odtiahnutá“ a hneď sa zmazala znova.
            scope.launch { dismissState.snapTo(SwipeToDismissBoxValue.Settled) }
            onDelete(transaction)
        },
        backgroundContent = {
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = ScreenPadding),
                contentAlignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                    Alignment.CenterStart
                } else {
                    Alignment.CenterEnd
                },
            ) {
                Text(
                    stringResource(R.string.action_delete),
                    style = MaterialTheme.typography.labelLarge,
                    color = GrosTheme.colors.warning,
                )
            }
        },
    ) {
        TransactionRow(
            transaction = transaction,
            lookup = state.lookup,
            style = style,
            today = state.today,
            accountFilterId = state.filter.accountId,
            onClick = { onEdit(transaction.id) },
            modifier = Modifier.background(MaterialTheme.colorScheme.background),
        )
    }
}

@Composable
private fun InfoText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding, vertical = 24.dp),
    )
}

/** Mesiac / Rok / Celé obdobie; pri mesiaci a roku sa dá krokovať šípkami. */
@Composable
private fun PeriodSheet(
    period: Period,
    currentMonth: YearMonth,
    onPeriod: (Period) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit,
) {
    OptionSheet(
        title = stringResource(R.string.filter_period_title),
        options = listOf(
            SheetOption(PeriodKind.MONTH, stringResource(R.string.filter_period_month)),
            SheetOption(PeriodKind.YEAR, stringResource(R.string.filter_period_year)),
            SheetOption(PeriodKind.ALL, stringResource(R.string.filter_period_all)),
        ),
        selected = period.kind,
        onSelect = { kind ->
            when (kind) {
                PeriodKind.MONTH -> if (period !is Period.Month) onPeriod(Period.Month(currentMonth))
                PeriodKind.YEAR -> if (period !is Period.Year) onPeriod(Period.Year(currentMonth.year))
                PeriodKind.ALL -> {
                    onPeriod(Period.All)
                    onDismiss()
                }
            }
        },
        onDismiss = onDismiss,
    ) {
        if (period != Period.All) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircleIconButton(
                    R.drawable.ic_chevron_left,
                    stringResource(R.string.period_previous),
                    onPrevious,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    periodLabel(period),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                CircleIconButton(
                    R.drawable.ic_chevron_right,
                    stringResource(R.string.period_next),
                    onNext,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
            PrimaryButton(stringResource(R.string.action_done), onClick = onDismiss)
        }
    }
}
