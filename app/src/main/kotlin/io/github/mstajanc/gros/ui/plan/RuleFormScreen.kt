package io.github.mstajanc.gros.ui.plan

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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mstajanc.gros.R
import io.github.mstajanc.gros.domain.MoneyFormatter
import io.github.mstajanc.gros.domain.PluralForm
import io.github.mstajanc.gros.domain.RuleStatus
import io.github.mstajanc.gros.domain.SlovakDates
import io.github.mstajanc.gros.domain.SlovakText
import io.github.mstajanc.gros.domain.model.Frequency
import io.github.mstajanc.gros.domain.model.TransactionType
import io.github.mstajanc.gros.ui.common.AmountSheet
import io.github.mstajanc.gros.ui.common.CenteredContent
import io.github.mstajanc.gros.ui.common.ConfirmDialog
import io.github.mstajanc.gros.ui.common.DateDialog
import io.github.mstajanc.gros.ui.common.FieldTile
import io.github.mstajanc.gros.ui.common.OptionSheet
import io.github.mstajanc.gros.ui.common.PrimaryButton
import io.github.mstajanc.gros.ui.common.ScreenHeader
import io.github.mstajanc.gros.ui.common.ScreenPadding
import io.github.mstajanc.gros.ui.common.SecondaryButton
import io.github.mstajanc.gros.ui.common.SegmentedControl
import io.github.mstajanc.gros.ui.common.SheetOption
import io.github.mstajanc.gros.ui.common.TextFieldTile
import io.github.mstajanc.gros.ui.common.grosViewModel
import io.github.mstajanc.gros.ui.theme.GrosShapes
import io.github.mstajanc.gros.ui.theme.GrosTheme

private val TYPES = listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER)
private val FREQUENCIES = listOf(Frequency.WEEKLY, Frequency.MONTHLY, Frequency.YEARLY)

private enum class RuleSheet { AMOUNT, TYPE, CATEGORY, ACCOUNT, TO_ACCOUNT, REPEAT, NEXT_DATE, END, END_DATE, DELETE }

@Composable
fun RuleFormScreen(contentPadding: PaddingValues, onClose: () -> Unit) {
    val viewModel = grosViewModel { container, handle -> RuleFormViewModel(container, handle) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheet by rememberSaveable { mutableStateOf<RuleSheet?>(null) }
    var backfill by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                RuleFormEvent.Close -> onClose()
                is RuleFormEvent.ConfirmBackfill -> backfill = event.count
            }
        }
    }

    val draft = state.draft
    CenteredContent(modifier = Modifier.padding(contentPadding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(stringResource(if (state.isEdit) R.string.rule_edit else R.string.rule_new), onBack = onClose)
            if (!state.loaded || draft == null) return@Column
            val isTransfer = draft.type == TransactionType.TRANSFER

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextFieldTile(
                    stringResource(R.string.rule_name),
                    draft.name,
                    viewModel::onName,
                    modifier = Modifier.weight(1f),
                )
                FieldTile(
                    label = stringResource(R.string.rule_amount),
                    value = MoneyFormatter.format(draft.amountCents),
                    onClick = { sheet = RuleSheet.AMOUNT },
                    modifier = Modifier.weight(1f),
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, GrosShapes.cardLarge)
                    .padding(vertical = 4.dp),
            ) {
                FormRow(stringResource(R.string.rule_type), typeLabel(draft.type)) { sheet = RuleSheet.TYPE }
                if (isTransfer) {
                    FormRow(stringResource(R.string.form_from_account), state.accountName(draft.accountId).orEmpty()) {
                        sheet = RuleSheet.ACCOUNT
                    }
                    FormRow(
                        stringResource(R.string.form_to_account),
                        state.accountName(draft.toAccountId) ?: stringResource(R.string.form_to_account_pick),
                    ) { sheet = RuleSheet.TO_ACCOUNT }
                } else {
                    FormRow(
                        stringResource(R.string.rule_category),
                        state.categoryTitle(draft.categoryId) ?: stringResource(R.string.rule_category_pick),
                    ) { sheet = RuleSheet.CATEGORY }
                    FormRow(stringResource(R.string.form_account), state.accountName(draft.accountId).orEmpty()) {
                        sheet = RuleSheet.ACCOUNT
                    }
                }
                FormRow(stringResource(R.string.rule_repeat), repeatLong(draft.frequency, draft.interval, draft.nextDate)) {
                    sheet = RuleSheet.REPEAT
                }
                // Nové pravidlo s dnešnou alebo skoršou platbou sa zapíše hneď po uložení (pravidlo 6).
                val writesNow = !draft.nextDate.isAfter(state.today) &&
                    (!state.isEdit || draft.nextDate != state.existing?.nextDate) &&
                    state.existing?.paused != true
                FormRow(
                    stringResource(R.string.rule_next),
                    SlovakDates.fullDate(draft.nextDate),
                    supporting = if (writesNow) stringResource(R.string.rule_next_now) else null,
                ) { sheet = RuleSheet.NEXT_DATE }
                FormRow(
                    stringResource(R.string.rule_end),
                    draft.endDate?.let(SlovakDates::fullDate) ?: stringResource(R.string.rule_end_none),
                    divider = false,
                ) { sheet = RuleSheet.END }
            }

            Text(
                stringResource(if (state.isEdit) R.string.rule_info_edit else R.string.rule_info_new),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, GrosShapes.cardMedium)
                    .padding(16.dp),
            )

            state.error?.let {
                Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = GrosTheme.colors.warning)
            }

            PrimaryButton(
                stringResource(if (state.isEdit) R.string.rule_save_changes else R.string.action_save),
                onClick = { viewModel.save() },
                enabled = state.canSave,
            )
            if (state.isEdit) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Ukončené pravidlo sa nepozastavuje; obnoví sa predĺžením konca.
                    if (state.status != RuleStatus.FINISHED) {
                        SecondaryButton(
                            stringResource(if (state.existing?.paused == true) R.string.rule_resume else R.string.rule_pause),
                            onClick = viewModel::togglePaused,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    SecondaryButton(
                        stringResource(R.string.action_delete),
                        onClick = { sheet = RuleSheet.DELETE },
                        modifier = Modifier.weight(1f),
                        contentColor = GrosTheme.colors.warning,
                    )
                }
            }
        }
    }

    if (draft == null) return
    when (sheet) {
        RuleSheet.AMOUNT -> AmountSheet(
            title = stringResource(R.string.rule_amount),
            initialCents = draft.amountCents,
            allowNegative = false,
            onConfirm = {
                viewModel.onAmount(it)
                sheet = null
            },
            onDismiss = { sheet = null },
        )

        RuleSheet.TYPE -> OptionSheet(
            title = stringResource(R.string.rule_type),
            options = TYPES.filter { it != TransactionType.TRANSFER || state.transferAvailable }
                .map { SheetOption(it, typeLabel(it)) },
            selected = draft.type,
            onSelect = {
                viewModel.onType(it)
                sheet = null
            },
            onDismiss = { sheet = null },
        )

        RuleSheet.CATEGORY -> OptionSheet(
            title = stringResource(R.string.rule_category),
            options = state.categoryChoices.map { SheetOption(it.id, it.name, indent = it.parentId != null) },
            selected = draft.categoryId,
            onSelect = {
                viewModel.onCategory(it)
                sheet = null
            },
            onDismiss = { sheet = null },
        )

        RuleSheet.ACCOUNT -> OptionSheet(
            title = stringResource(if (draft.type == TransactionType.TRANSFER) R.string.form_from_account else R.string.form_account),
            options = state.activeAccounts.map { SheetOption(it.id, it.name) },
            selected = draft.accountId,
            onSelect = {
                viewModel.onAccount(it)
                sheet = null
            },
            onDismiss = { sheet = null },
        )

        RuleSheet.TO_ACCOUNT -> OptionSheet(
            title = stringResource(R.string.form_to_account),
            options = state.activeAccounts.filter { it.id != draft.accountId }.map { SheetOption(it.id, it.name) },
            selected = draft.toAccountId,
            onSelect = {
                viewModel.onToAccount(it)
                sheet = null
            },
            onDismiss = { sheet = null },
        )

        RuleSheet.REPEAT -> RepeatSheet(
            frequency = draft.frequency,
            interval = draft.interval,
            onConfirm = { frequency, interval ->
                viewModel.onRepeat(frequency, interval)
                sheet = null
            },
            onDismiss = { sheet = null },
        )

        RuleSheet.NEXT_DATE -> DateDialog(
            date = draft.nextDate,
            onConfirm = {
                viewModel.onNextDate(it)
                sheet = null
            },
            onDismiss = { sheet = null },
        )

        RuleSheet.END -> OptionSheet(
            title = stringResource(R.string.rule_end),
            options = listOf(
                SheetOption(false, stringResource(R.string.rule_end_none)),
                SheetOption(true, stringResource(R.string.rule_end_pick)),
            ),
            selected = draft.endDate != null,
            onSelect = { pickDate ->
                if (pickDate) {
                    sheet = RuleSheet.END_DATE
                } else {
                    viewModel.onEndDate(null)
                    sheet = null
                }
            },
            onDismiss = { sheet = null },
        )

        RuleSheet.END_DATE -> DateDialog(
            date = draft.endDate ?: draft.nextDate,
            // Koniec nesmie byť pred začiatkom radu.
            minDate = minOf(draft.nextDate, state.existing?.anchorDate ?: draft.nextDate),
            onConfirm = {
                viewModel.onEndDate(it)
                sheet = null
            },
            onDismiss = { sheet = null },
        )

        RuleSheet.DELETE -> ConfirmDialog(
            title = stringResource(R.string.rule_delete_title),
            text = stringResource(R.string.rule_delete_text),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                sheet = null
                viewModel.delete()
            },
            onDismiss = { sheet = null },
        )

        null -> Unit
    }

    if (backfill > 0) {
        ConfirmDialog(
            title = stringResource(R.string.rule_backfill_title),
            text = stringResource(
                when (SlovakText.pluralForm(backfill)) {
                    PluralForm.ONE -> R.string.rule_backfill_one
                    PluralForm.FEW -> R.string.rule_backfill_few
                    PluralForm.MANY -> R.string.rule_backfill_many
                },
                backfill,
            ),
            confirmLabel = stringResource(R.string.action_save),
            onConfirm = {
                backfill = 0
                viewModel.save(confirmedBackfill = true)
            },
            onDismiss = { backfill = 0 },
            destructive = false,
        )
    }
}

@Composable
private fun typeLabel(type: TransactionType): String = stringResource(
    when (type) {
        TransactionType.EXPENSE -> R.string.type_expense
        TransactionType.INCOME -> R.string.type_income
        TransactionType.TRANSFER -> R.string.type_transfer
    },
)

/** Riadok karty podľa mockupu 07: popis vľavo, hodnota vpravo, celý riadok je ťukací. */
@Composable
private fun FormRow(
    label: String,
    value: String,
    supporting: String? = null,
    divider: Boolean = true,
    onClick: () -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(value, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.End)
                if (supporting != null) {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
        if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

/** Frekvencia a interval („každé 3 mesiace“). */
@Composable
private fun RepeatSheet(
    frequency: Frequency,
    interval: Int,
    onConfirm: (Frequency, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var selected by rememberSaveable { mutableStateOf(frequency) }
    var count by rememberSaveable { mutableIntStateOf(interval) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.rule_repeat),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                stringResource(R.string.repeat_frequency),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.repeat_weekly),
                    stringResource(R.string.repeat_monthly),
                    stringResource(R.string.repeat_yearly),
                ),
                selectedIndex = FREQUENCIES.indexOf(selected),
                onSelect = { selected = FREQUENCIES[it] },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val decrease = stringResource(R.string.repeat_decrease)
                val increase = stringResource(R.string.repeat_increase)
                SecondaryButton(
                    "−",
                    onClick = { count = (count - 1).coerceAtLeast(1) },
                    enabled = count > 1,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.semantics { contentDescription = decrease },
                )
                Text(
                    repeatShort(selected, count),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                SecondaryButton(
                    "+",
                    onClick = { count = (count + 1).coerceAtMost(99) },
                    enabled = count < 99,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.semantics { contentDescription = increase },
                )
            }
            PrimaryButton(stringResource(R.string.action_done), onClick = { onConfirm(selected, count) })
        }
    }
}
