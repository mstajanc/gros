package io.github.mstajanc.gros.ui.overview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.mstajanc.gros.AppContainer
import io.github.mstajanc.gros.R
import io.github.mstajanc.gros.domain.AccountBalance
import io.github.mstajanc.gros.domain.BalanceCalculator
import io.github.mstajanc.gros.domain.ListSummary
import io.github.mstajanc.gros.domain.MoneyFormatter
import io.github.mstajanc.gros.domain.Period
import io.github.mstajanc.gros.domain.SignDisplay
import io.github.mstajanc.gros.domain.SlovakDates
import io.github.mstajanc.gros.domain.TotalBalance
import io.github.mstajanc.gros.domain.TransactionFilter
import io.github.mstajanc.gros.domain.TransactionList
import io.github.mstajanc.gros.domain.model.Transaction
import io.github.mstajanc.gros.ui.common.AmountText
import io.github.mstajanc.gros.ui.common.CenteredContent
import io.github.mstajanc.gros.ui.common.GrosCard
import io.github.mstajanc.gros.ui.common.Lookup
import io.github.mstajanc.gros.ui.common.RowStyle
import io.github.mstajanc.gros.ui.common.ScreenHeader
import io.github.mstajanc.gros.ui.common.ScreenPadding
import io.github.mstajanc.gros.ui.common.SummaryTile
import io.github.mstajanc.gros.ui.common.TransactionRow
import io.github.mstajanc.gros.ui.common.accountsOfText
import io.github.mstajanc.gros.ui.common.grosViewModel
import io.github.mstajanc.gros.ui.theme.GrosShapes
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Počet posledných transakcií na Prehľade. */
private const val RECENT_COUNT = 5

data class OverviewState(
    val loaded: Boolean = false,
    val today: LocalDate,
    val total: TotalBalance = TotalBalance(0, 0, 0),
    /** Nearchivované účty v poradí používateľa. */
    val accounts: List<AccountBalance> = emptyList(),
    /** Príjmy a výdavky aktuálneho mesiaca zo všetkých účtov, aj tých mimo súčtu. */
    val month: ListSummary = ListSummary(0, 0, 0),
    val recent: List<Transaction> = emptyList(),
    val lookup: Lookup = Lookup(),
)

class OverviewViewModel(container: AppContainer) : ViewModel() {

    private val today = container.today()

    val state: StateFlow<OverviewState> = combine(
        container.accountRepository.observeBalances(),
        container.categoryRepository.observeCategories(),
        container.transactionRepository.observeFiltered(
            TransactionFilter(Period.Month(YearMonth.from(today))),
            categoryIds = null,
        ),
        container.transactionRepository.observeRecent(RECENT_COUNT),
    ) { balances, categories, monthTransactions, recent ->
        OverviewState(
            loaded = true,
            today = container.today(),
            total = BalanceCalculator.total(balances),
            accounts = balances.filter { !it.account.archived },
            month = TransactionList.summarize(monthTransactions),
            recent = recent,
            lookup = Lookup.of(balances.map { it.account }, categories),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), OverviewState(today = today))
}

@Composable
fun OverviewScreen(
    contentPadding: PaddingValues,
    onManageAccounts: () -> Unit,
    onAllTransactions: () -> Unit,
    onAccount: (Long) -> Unit,
    onEdit: (Long) -> Unit,
) {
    val viewModel = grosViewModel { container, _ -> OverviewViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val monthName = SlovakDates.monthName(YearMonth.from(state.today))

    CenteredContent(modifier = Modifier.padding(contentPadding)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ScreenHeader(stringResource(R.string.nav_overview), modifier = Modifier.padding(horizontal = ScreenPadding))
            if (!state.loaded) return@Column

            GrosCard(modifier = Modifier.padding(horizontal = ScreenPadding), large = true) {
                Text(
                    stringResource(
                        R.string.overview_total,
                        accountsOfText(state.total.includedCount, state.total.activeCount),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AmountText(
                    MoneyFormatter.format(state.total.totalCents),
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.padding(top = 6.dp, bottom = 14.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryTile(
                        label = stringResource(R.string.overview_income, monthName),
                        amount = MoneyFormatter.format(state.month.incomeCents, SignDisplay.ALWAYS),
                        modifier = Modifier.weight(1f),
                        amountColor = MaterialTheme.colorScheme.primary,
                    )
                    SummaryTile(
                        label = stringResource(R.string.overview_expenses, monthName),
                        amount = MoneyFormatter.format(-state.month.expenseCents),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            SectionHeader(stringResource(R.string.overview_accounts), stringResource(R.string.overview_manage), onManageAccounts)
            // Vodorovne posuvný pruh všetkých nearchivovaných účtov; účty mimo súčtu sú stlmené.
            LazyRow(
                contentPadding = PaddingValues(horizontal = ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.accounts, key = { it.account.id }) { balance ->
                    Column(
                        modifier = Modifier
                            .widthIn(min = 132.dp)
                            .heightIn(min = 64.dp)
                            .clip(GrosShapes.cardMedium)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable(role = Role.Button) { onAccount(balance.account.id) }
                            .alpha(if (balance.account.includeInTotal) 1f else 0.6f)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            balance.account.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        AmountText(MoneyFormatter.format(balance.balanceCents))
                    }
                }
            }

            Column {
                SectionHeader(stringResource(R.string.overview_recent), stringResource(R.string.overview_all), onAllTransactions)
                if (state.recent.isEmpty()) {
                    Text(
                        stringResource(R.string.overview_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = ScreenPadding, vertical = 12.dp),
                    )
                }
                state.recent.forEach { transaction ->
                    TransactionRow(
                        transaction = transaction,
                        lookup = state.lookup,
                        style = RowStyle.OVERVIEW,
                        today = state.today,
                        accountFilterId = null,
                        onClick = { onEdit(transaction.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = ScreenPadding, end = ScreenPadding - 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
        Text(
            action,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(GrosShapes.pill)
                .clickable(role = Role.Button, onClick = onAction)
                .padding(horizontal = 8.dp, vertical = 14.dp),
        )
    }
}
