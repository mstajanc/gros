package io.github.mstajanc.gros.ui.accounts

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.mstajanc.gros.AppContainer
import io.github.mstajanc.gros.R
import io.github.mstajanc.gros.domain.MoneyFormatter
import io.github.mstajanc.gros.domain.RuleState
import io.github.mstajanc.gros.domain.RuleStatus
import io.github.mstajanc.gros.domain.model.AccountType
import io.github.mstajanc.gros.domain.rules.InvariantViolationException
import io.github.mstajanc.gros.ui.common.AmountSheet
import io.github.mstajanc.gros.ui.common.CenteredContent
import io.github.mstajanc.gros.ui.common.ConfirmDialog
import io.github.mstajanc.gros.ui.common.FieldTile
import io.github.mstajanc.gros.ui.common.OptionSheet
import io.github.mstajanc.gros.ui.common.PrimaryButton
import io.github.mstajanc.gros.ui.common.ScreenHeader
import io.github.mstajanc.gros.ui.common.ScreenPadding
import io.github.mstajanc.gros.ui.common.SecondaryButton
import io.github.mstajanc.gros.ui.common.SheetOption
import io.github.mstajanc.gros.ui.common.SwitchRow
import io.github.mstajanc.gros.ui.common.TextFieldTile
import io.github.mstajanc.gros.ui.common.grosViewModel
import io.github.mstajanc.gros.ui.theme.GrosTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@StringRes
fun AccountType.labelRes(): Int = when (this) {
    AccountType.CHECKING -> R.string.account_type_checking
    AccountType.CASH -> R.string.account_type_cash
    AccountType.SAVINGS -> R.string.account_type_savings
    AccountType.INVESTMENT -> R.string.account_type_investment
    AccountType.OTHER -> R.string.account_type_other
}

data class AccountFormState(
    val loaded: Boolean = false,
    val isEdit: Boolean = false,
    val name: String = "",
    val type: AccountType = AccountType.CHECKING,
    val includeInTotal: Boolean = true,
    /** Len pri novom účte. */
    val openingCents: Long = 0,
    /** Len pri úprave: aktuálny zostatok. */
    val balanceCents: Long = 0,
    val archived: Boolean = false,
    /** Posledný aktívny účet sa nedá archivovať. */
    val canArchive: Boolean = false,
    /** Natvrdo sa dá zmazať len účet bez transakcií a pravidiel. */
    val canDelete: Boolean = false,
    /** Aktívne opakované platby, ktoré archivácia pozastaví (pravidlo 10). */
    val affectedRules: List<String> = emptyList(),
) {
    val canSave: Boolean get() = name.isNotBlank()
}

class AccountFormViewModel(private val container: AppContainer, private val handle: SavedStateHandle) : ViewModel() {

    private val accountId: Long? = handle.get<Long>(ARG_ID)?.takeIf { it > 0 }
    private val accounts = container.accountRepository

    private val name = handle.getStateFlow(KEY_NAME, "")
    private val type = handle.getStateFlow(KEY_TYPE, AccountType.CHECKING.name)
    private val includeInTotal = handle.getStateFlow(KEY_INCLUDE, true)
    private val opening = handle.getStateFlow(KEY_OPENING, 0L)
    private val loaded = handle.getStateFlow(KEY_LOADED, false)

    private val _close = Channel<Unit>(Channel.BUFFERED)
    val close = _close.receiveAsFlow()

    private val form = combine(name, type, includeInTotal, opening, loaded) { name, type, include, opening, loaded ->
        AccountFormState(
            loaded = loaded,
            isEdit = accountId != null,
            name = name,
            type = AccountType.valueOf(type),
            includeInTotal = include,
            openingCents = opening,
        )
    }

    val state: StateFlow<AccountFormState> = combine(
        form,
        accounts.observeBalances(),
        container.recurringRuleRepository.observeRules(),
    ) { form, balances, rules ->
        val current = balances.firstOrNull { it.account.id == accountId }
        val activeCount = balances.count { !it.account.archived }
        form.copy(
            balanceCents = current?.balanceCents ?: 0,
            archived = current?.account?.archived ?: false,
            canArchive = current != null && !current.account.archived && activeCount > 1,
            canDelete = current != null && !accounts.isReferenced(current.account.id) &&
                (current.account.archived || activeCount > 1),
            affectedRules = rules
                .filter { (it.accountId == accountId || it.toAccountId == accountId) && RuleState.status(it) == RuleStatus.ACTIVE }
                .map { it.name },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountFormState())

    init {
        viewModelScope.launch {
            if (!loaded.value) {
                val existing = accountId?.let { accounts.get(it) }
                if (existing != null) {
                    handle[KEY_NAME] = existing.name
                    handle[KEY_TYPE] = existing.type.name
                    handle[KEY_INCLUDE] = existing.includeInTotal
                }
                handle[KEY_LOADED] = true
            }
        }
    }

    fun onName(value: String) { handle[KEY_NAME] = value }
    fun onType(value: AccountType) { handle[KEY_TYPE] = value.name }
    fun onIncludeInTotal(value: Boolean) { handle[KEY_INCLUDE] = value }
    fun onOpening(cents: Long) { handle[KEY_OPENING] = cents }

    fun save() = launchSafely {
        val current = state.value
        if (accountId == null) {
            accounts.create(current.name, current.type, current.openingCents, current.includeInTotal)
        } else {
            accounts.update(accountId, current.name, current.type, current.includeInTotal)
        }
        _close.send(Unit)
    }

    /** Oprava zostatku neuzatvára formulár; zostatok sa prepíše hneď. */
    fun correctBalance(actualCents: Long) = launchSafely { accountId?.let { accounts.correctBalance(it, actualCents) } }

    fun archive() = launchSafely {
        accountId?.let { accounts.archive(it) }
        _close.send(Unit)
    }

    fun restore() = launchSafely { accountId?.let { accounts.restore(it) } }

    fun delete() = launchSafely {
        accountId?.let { accounts.delete(it) }
        _close.send(Unit)
    }

    /** Porušenie invariantu (napr. posledný aktívny účet) nechá formulár otvorený bez zmeny dát. */
    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (_: InvariantViolationException) {
            }
        }
    }

    companion object {
        const val ARG_ID = "id"
        private const val KEY_NAME = "name"
        private const val KEY_TYPE = "type"
        private const val KEY_INCLUDE = "include"
        private const val KEY_OPENING = "opening"
        private const val KEY_LOADED = "loaded"
    }
}

private enum class AccountDialog { TYPE, OPENING, CORRECT, ARCHIVE, DELETE }

@Composable
fun AccountFormScreen(contentPadding: PaddingValues, onClose: () -> Unit) {
    val viewModel = grosViewModel { container, handle -> AccountFormViewModel(container, handle) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<AccountDialog?>(null) }

    LaunchedEffect(viewModel) { viewModel.close.collect { onClose() } }

    CenteredContent(modifier = Modifier.padding(contentPadding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(
                stringResource(if (state.isEdit) R.string.account_edit else R.string.account_new),
                onBack = onClose,
            )
            if (!state.loaded) return@Column

            TextFieldTile(stringResource(R.string.account_name), state.name, viewModel::onName)
            FieldTile(
                label = stringResource(R.string.account_type),
                value = stringResource(state.type.labelRes()),
                onClick = { dialog = AccountDialog.TYPE },
                modifier = Modifier.fillMaxWidth(),
            )
            SwitchRow(stringResource(R.string.accounts_in_total), state.includeInTotal, viewModel::onIncludeInTotal)

            if (state.isEdit) {
                FieldTile(
                    label = stringResource(R.string.account_balance_label),
                    value = MoneyFormatter.format(state.balanceCents),
                    onClick = { dialog = AccountDialog.CORRECT },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.account_correct_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FieldTile(
                    label = stringResource(R.string.account_opening),
                    value = MoneyFormatter.format(state.openingCents),
                    onClick = { dialog = AccountDialog.OPENING },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            PrimaryButton(stringResource(R.string.action_save), onClick = viewModel::save, enabled = state.canSave)

            if (state.isEdit) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.archived) {
                        SecondaryButton(
                            stringResource(R.string.accounts_restore),
                            onClick = viewModel::restore,
                            modifier = Modifier.weight(1f),
                            contentColor = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        SecondaryButton(
                            stringResource(R.string.account_archive),
                            onClick = {
                                // Účet s nenulovým zostatkom (B4) alebo s aktívnymi opakovanými platbami
                                // (pravidlo 10) sa archivuje až po potvrdení.
                                if (state.balanceCents != 0L || state.affectedRules.isNotEmpty()) {
                                    dialog = AccountDialog.ARCHIVE
                                } else {
                                    viewModel.archive()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = state.canArchive,
                        )
                    }
                    if (state.canDelete) {
                        SecondaryButton(
                            stringResource(R.string.action_delete),
                            onClick = { dialog = AccountDialog.DELETE },
                            modifier = Modifier.weight(1f),
                            contentColor = GrosTheme.colors.warning,
                        )
                    }
                }
                if (!state.archived && !state.canArchive) {
                    Text(
                        stringResource(R.string.account_archive_last),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    when (dialog) {
        AccountDialog.TYPE -> OptionSheet(
            title = stringResource(R.string.account_type),
            options = AccountType.entries.map { SheetOption(it, stringResource(it.labelRes())) },
            selected = state.type,
            onSelect = {
                viewModel.onType(it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        AccountDialog.OPENING -> AmountSheet(
            title = stringResource(R.string.account_opening),
            initialCents = state.openingCents,
            allowNegative = true,
            onConfirm = {
                viewModel.onOpening(it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        AccountDialog.CORRECT -> AmountSheet(
            title = stringResource(R.string.account_correct_title),
            initialCents = state.balanceCents,
            allowNegative = true,
            onConfirm = {
                viewModel.correctBalance(it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        AccountDialog.ARCHIVE -> ConfirmDialog(
            title = stringResource(R.string.account_archive_confirm_title),
            text = listOfNotNull(
                if (state.balanceCents != 0L) {
                    stringResource(R.string.account_archive_confirm_text, MoneyFormatter.format(state.balanceCents))
                } else {
                    null
                },
                if (state.affectedRules.isNotEmpty()) {
                    stringResource(R.string.account_archive_rules, state.affectedRules.joinToString(", "))
                } else {
                    null
                },
            ).joinToString("\n\n"),
            confirmLabel = stringResource(R.string.account_archive),
            onConfirm = {
                dialog = null
                viewModel.archive()
            },
            onDismiss = { dialog = null },
        )

        AccountDialog.DELETE -> ConfirmDialog(
            title = stringResource(R.string.account_delete_confirm_title),
            text = stringResource(R.string.account_delete_confirm_text),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                dialog = null
                viewModel.delete()
            },
            onDismiss = { dialog = null },
        )

        null -> Unit
    }
}
