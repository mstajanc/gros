package io.github.mstajanc.gros.ui.accounts

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.mstajanc.gros.AppContainer
import io.github.mstajanc.gros.R
import io.github.mstajanc.gros.domain.AccountBalance
import io.github.mstajanc.gros.domain.BalanceCalculator
import io.github.mstajanc.gros.domain.MoneyFormatter
import io.github.mstajanc.gros.domain.TotalBalance
import io.github.mstajanc.gros.domain.TransactionFormRules
import io.github.mstajanc.gros.domain.model.Account
import io.github.mstajanc.gros.ui.common.AmountText
import io.github.mstajanc.gros.ui.common.CenteredContent
import io.github.mstajanc.gros.ui.common.CircleIconButton
import io.github.mstajanc.gros.ui.common.GrosCard
import io.github.mstajanc.gros.ui.common.GrosIcon
import io.github.mstajanc.gros.ui.common.ReorderableColumn
import io.github.mstajanc.gros.ui.common.ScreenHeader
import io.github.mstajanc.gros.ui.common.ScreenPadding
import io.github.mstajanc.gros.ui.common.SecondaryButton
import io.github.mstajanc.gros.ui.common.accountsOfText
import io.github.mstajanc.gros.ui.common.grosViewModel
import io.github.mstajanc.gros.ui.common.switchColors
import io.github.mstajanc.gros.ui.theme.GrosShapes
import io.github.mstajanc.gros.ui.theme.GrosTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AccountsState(
    val loaded: Boolean = false,
    val active: List<AccountBalance> = emptyList(),
    val archived: List<AccountBalance> = emptyList(),
    val total: TotalBalance = TotalBalance(0, 0, 0),
) {
    val transferAvailable: Boolean get() = TransactionFormRules.isTransferAvailable(active.map { it.account })
}

class AccountsViewModel(private val container: AppContainer) : ViewModel() {

    private val accounts = container.accountRepository

    val state: StateFlow<AccountsState> = accounts.observeBalances().map { balances ->
        AccountsState(
            loaded = true,
            active = balances.filter { !it.account.archived },
            archived = balances.filter { it.account.archived },
            total = BalanceCalculator.total(balances),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsState())

    fun setIncludeInTotal(account: Account, include: Boolean) {
        viewModelScope.launch { accounts.update(account.id, account.name, account.type, include) }
    }

    fun restore(accountId: Long) {
        viewModelScope.launch { accounts.restore(accountId) }
    }

    /** Uloží nové poradie aktívnych účtov; archivované ostávajú za nimi. */
    fun reorder(activeIds: List<Long>) {
        val archivedIds = state.value.archived.map { it.account.id }
        viewModelScope.launch { accounts.reorder(activeIds + archivedIds) }
    }
}

@Composable
fun AccountsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onAccount: (Long) -> Unit,
    onTransfer: () -> Unit,
) {
    val viewModel = grosViewModel { container, _ -> AccountsViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    CenteredContent(modifier = Modifier.padding(contentPadding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(stringResource(R.string.accounts_title), onBack = onBack) {
                SecondaryButton(
                    stringResource(R.string.accounts_add),
                    onClick = onAdd,
                    contentColor = MaterialTheme.colorScheme.primary,
                )
            }
            if (!state.loaded) return@Column

            GrosCard(large = true) {
                Text(
                    stringResource(
                        R.string.accounts_total,
                        accountsOfText(state.total.includedCount, state.total.activeCount),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AmountText(
                    MoneyFormatter.format(state.total.totalCents),
                    style = MaterialTheme.typography.displayMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            ReorderableAccounts(
                accounts = state.active,
                onReorder = viewModel::reorder,
                onAccount = onAccount,
                onEdit = onEdit,
                onIncludeInTotal = viewModel::setIncludeInTotal,
            )

            SecondaryButton(
                stringResource(R.string.accounts_transfer),
                onClick = onTransfer,
                modifier = Modifier.fillMaxWidth(),
                contentColor = GrosTheme.colors.transfer,
                containerColor = GrosTheme.colors.transferContainer,
                enabled = state.transferAvailable,
                leading = { GrosIcon(R.drawable.ic_arrow_right_left, null, tint = GrosTheme.colors.transfer) },
            )
            Text(
                stringResource(R.string.accounts_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.archived.isNotEmpty()) {
                Text(
                    stringResource(R.string.accounts_archived),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                )
                state.archived.forEach { balance ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(GrosShapes.cardMedium)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable(role = Role.Button) { onAccount(balance.account.id) }
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                balance.account.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            AmountText(
                                MoneyFormatter.format(balance.balanceCents),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        CircleIconButton(
                            R.drawable.ic_pencil,
                            stringResource(R.string.accounts_edit, balance.account.name),
                            onClick = { onEdit(balance.account.id) },
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        SecondaryButton(
                            stringResource(R.string.accounts_restore),
                            onClick = { viewModel.restore(balance.account.id) },
                            contentColor = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** Zoznam aktívnych účtov, ktorých poradie sa mení potiahnutím za úchyt (B16). */
@Composable
private fun ReorderableAccounts(
    accounts: List<AccountBalance>,
    onReorder: (List<Long>) -> Unit,
    onAccount: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onIncludeInTotal: (Account, Boolean) -> Unit,
) {
    ReorderableColumn(
        items = accounts,
        key = { it.account.id },
        label = { it.account.name },
        onReorder = onReorder,
    ) { balance, handle, modifier ->
        val id = balance.account.id
        AccountCard(
            balance = balance,
            onClick = { onAccount(id) },
            onEdit = { onEdit(id) },
            onIncludeInTotal = { onIncludeInTotal(balance.account, it) },
            modifier = modifier,
            handle = handle,
        )
    }
}

@Composable
private fun AccountCard(
    balance: AccountBalance,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onIncludeInTotal: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    handle: @Composable () -> Unit,
) {
    val account = balance.account
    val included = account.includeInTotal
    val stateText = stringResource(if (included) R.string.accounts_in_total else R.string.accounts_out_of_total)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(GrosShapes.cardLarge)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 4.dp, end = 14.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        handle()
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(account.name, style = MaterialTheme.typography.titleMedium)
            AmountText(
                MoneyFormatter.format(balance.balanceCents),
                // Účty mimo súčtu sú stlmené.
                color = if (included) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        CircleIconButton(
            R.drawable.ic_pencil,
            stringResource(R.string.accounts_edit, account.name),
            onClick = onEdit,
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Column(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(GrosShapes.cardSmall)
                .clickable(role = Role.Switch) { onIncludeInTotal(!included) }
                .semantics { stateDescription = stateText }
                .padding(horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Switch(checked = included, onCheckedChange = null, colors = switchColors())
            Text(stateText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
