package io.github.mstajanc.gros.ui.transaction

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mstajanc.gros.R
import io.github.mstajanc.gros.domain.RelativeDay
import io.github.mstajanc.gros.domain.SlovakDates
import io.github.mstajanc.gros.domain.model.TransactionType
import io.github.mstajanc.gros.ui.common.AmountKeypad
import io.github.mstajanc.gros.ui.common.AmountText
import io.github.mstajanc.gros.ui.common.CenteredContent
import io.github.mstajanc.gros.ui.common.DateDialog
import io.github.mstajanc.gros.ui.common.FieldTile
import io.github.mstajanc.gros.ui.common.GrosTextField
import io.github.mstajanc.gros.ui.common.OptionSheet
import io.github.mstajanc.gros.ui.common.PrimaryButton
import io.github.mstajanc.gros.ui.common.ScreenHeader
import io.github.mstajanc.gros.ui.common.ScreenPadding
import io.github.mstajanc.gros.ui.common.SecondaryButton
import io.github.mstajanc.gros.ui.common.SegmentedControl
import io.github.mstajanc.gros.ui.common.SheetOption
import io.github.mstajanc.gros.ui.common.grosViewModel
import io.github.mstajanc.gros.ui.common.outlined
import io.github.mstajanc.gros.ui.theme.GrosShapes
import io.github.mstajanc.gros.ui.theme.GrosTheme
import java.time.LocalDate

private val TYPES = listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER)

/** Počet hlavných kategórií pred čipom „Viac“. */
private const val SHOWN_CATEGORIES = 7

private enum class Picker { ACCOUNT, TO_ACCOUNT, DATE, ALL_CATEGORIES }

@Composable
fun TransactionFormScreen(onClose: () -> Unit, onDuplicate: () -> Unit) {
    val viewModel = grosViewModel { container, handle -> TransactionFormViewModel(container, handle) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                FormEvent.Close -> onClose()
            }
        }
    }

    var picker by rememberSaveable { mutableStateOf<Picker?>(null) }
    var noteFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val openPicker: (Picker) -> Unit = {
        focusManager.clearFocus()
        viewModel.hideKeypad()
        picker = it
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        CenteredContent(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            val selection = state.selection
            if (!state.loaded || selection == null) return@CenteredContent

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                // Pri výške okna ≥ 600 dp sú klávesnica a Uložiť prichytené dole; inak sa posúva všetko.
                // Polia formulára sú v oboch prípadoch na rovnakom mieste, aby sa pri zmene výšky
                // (napr. otvorenie systémovej klávesnice) nestratil rozpísaný text ani fokus.
                val pinned = maxHeight >= 600.dp
                val bottom: @Composable ColumnScope.() -> Unit = {
                    // Kým sa píše poznámka systémovou klávesnicou, klávesnica appky ustúpi.
                    if (state.draft?.keypadVisible == true && !noteFocused) {
                        AmountKeypad(input = state.amount, onInput = viewModel::onAmount)
                    }
                    PrimaryButton(stringResource(R.string.action_save), onClick = viewModel::save, enabled = state.canSave)
                    if (state.isEdit) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SecondaryButton(
                                stringResource(R.string.form_duplicate),
                                onClick = onDuplicate,
                                modifier = Modifier.weight(1f),
                            )
                            SecondaryButton(
                                stringResource(R.string.action_delete),
                                onClick = viewModel::delete,
                                modifier = Modifier.weight(1f),
                                contentColor = GrosTheme.colors.warning,
                            )
                        }
                    }
                }

                Column(modifier = Modifier.fillMaxSize().padding(horizontal = ScreenPadding)) {
                    ScreenHeader(
                        title = stringResource(if (state.isEdit) R.string.form_edit else R.string.form_new),
                        modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
                        onBack = onClose,
                        backIcon = R.drawable.ic_x,
                        backDescription = stringResource(R.string.action_close),
                    )
                    Column(
                        modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        FormFields(
                            state = state,
                            viewModel = viewModel,
                            onPick = openPicker,
                            onAmountClick = {
                                focusManager.clearFocus()
                                viewModel.onToggleKeypad()
                            },
                            onNoteFocus = { focused ->
                                noteFocused = focused
                                if (focused) viewModel.hideKeypad()
                            },
                        )
                        if (!pinned) bottom()
                    }
                    if (pinned) {
                        Column(
                            modifier = Modifier.padding(bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            content = bottom,
                        )
                    }
                }
            }

            when (picker) {
                Picker.ACCOUNT -> OptionSheet(
                    title = stringResource(
                        if (selection.type == TransactionType.TRANSFER) R.string.form_from_account else R.string.form_account,
                    ),
                    options = state.selectableAccounts.map { SheetOption(it.id, it.name) },
                    selected = selection.accountId,
                    onSelect = {
                        viewModel.onAccount(it)
                        picker = null
                    },
                    onDismiss = { picker = null },
                )

                Picker.TO_ACCOUNT -> OptionSheet(
                    title = stringResource(R.string.form_to_account),
                    options = state.selectableAccounts
                        .filter { it.id != selection.accountId }
                        .map { SheetOption(it.id, it.name) },
                    selected = selection.toAccountId,
                    onSelect = {
                        viewModel.onToAccount(it)
                        picker = null
                    },
                    onDismiss = { picker = null },
                )

                Picker.ALL_CATEGORIES -> OptionSheet(
                    title = stringResource(R.string.form_all_categories),
                    options = state.mainCategories.map { SheetOption(it.id, it.name) },
                    selected = selection.categoryId,
                    onSelect = {
                        viewModel.onCategory(it)
                        picker = null
                    },
                    onDismiss = { picker = null },
                )

                Picker.DATE -> DateDialog(
                    date = state.draft?.date ?: state.today,
                    maxDate = state.today,
                    onConfirm = {
                        viewModel.onDate(it)
                        picker = null
                    },
                    onDismiss = { picker = null },
                )

                null -> Unit
            }
        }
    }
}

@Composable
private fun ColumnScope.FormFields(
    state: TransactionFormState,
    viewModel: TransactionFormViewModel,
    onPick: (Picker) -> Unit,
    onAmountClick: () -> Unit,
    onNoteFocus: (Boolean) -> Unit,
) {
    val draft = state.draft ?: return
    val selection = draft.selection
    val isTransfer = selection.type == TransactionType.TRANSFER

    SegmentedControl(
        options = listOf(
            stringResource(R.string.type_expense),
            stringResource(R.string.type_income),
            stringResource(R.string.type_transfer),
        ),
        selectedIndex = TYPES.indexOf(selection.type),
        onSelect = { viewModel.onType(TYPES[it]) },
        // Typ Prevod je dostupný až pri dvoch aktívnych účtoch.
        enabled = { TYPES[it] != TransactionType.TRANSFER || state.transferAvailable },
    )

    // Ťuknutie na sumu otvorí alebo skryje klávesnicu appky (rozhodnutie C3).
    val amountLabel = stringResource(R.string.form_amount)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GrosShapes.cardMedium)
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(
                    if (draft.keypadVisible) R.string.form_amount_hide_keypad else R.string.form_amount_edit,
                ),
                onClick = onAmountClick,
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(amountLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AmountText(state.amount.display + " €", style = MaterialTheme.typography.displayLarge)
        if (!draft.keypadVisible) {
            Text(
                stringResource(R.string.form_amount_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (isTransfer) {
        FieldTile(
            label = stringResource(R.string.form_to_account),
            value = state.accountName(selection.toAccountId) ?: stringResource(R.string.form_to_account_pick),
            onClick = { onPick(Picker.TO_ACCOUNT) },
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        CategoryChips(state, onCategory = viewModel::onCategory, onMore = { onPick(Picker.ALL_CATEGORIES) })
        val subcategories = state.subcategories
        if (subcategories.isNotEmpty()) {
            ChipSection(stringResource(R.string.form_subcategory)) {
                subcategories.forEach { sub ->
                    ChoiceChip(
                        text = sub.name,
                        selected = sub.id == selection.subcategoryId,
                        onClick = { viewModel.onSubcategory(sub.id) },
                    )
                }
            }
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FieldTile(
            label = stringResource(if (isTransfer) R.string.form_from_account else R.string.form_account),
            value = state.accountName(selection.accountId).orEmpty(),
            onClick = { onPick(Picker.ACCOUNT) },
            modifier = Modifier.weight(1f),
        )
        FieldTile(
            label = stringResource(R.string.form_date),
            value = dateFieldText(draft.date, state.today),
            onClick = { onPick(Picker.DATE) },
            modifier = Modifier.weight(1f),
        )
    }

    NoteField(
        draft = draft,
        suggestions = state.suggestions,
        onShow = viewModel::onShowNote,
        onChange = viewModel::onNote,
        onSuggestion = viewModel::onSuggestion,
        onFocus = onNoteFocus,
    )
}

@Composable
private fun dateFieldText(date: LocalDate, today: LocalDate): String {
    val short = "${date.dayOfMonth}. ${date.monthValue}."
    return when (SlovakDates.relativeDay(date, today)) {
        RelativeDay.TODAY -> stringResource(R.string.form_date_relative, stringResource(R.string.day_today), short)
        RelativeDay.YESTERDAY -> stringResource(R.string.form_date_relative, stringResource(R.string.day_yesterday), short)
        RelativeDay.OTHER -> SlovakDates.fullDate(date)
    }
}

/** Popis a čipy v riadkoch za sebou; čipy sú široké podľa textu (rozhodnutie C2). */
@Composable
private fun ChipSection(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

/** Prvých 7 hlavných kategórií v poradí používateľa a čip „Viac“ so zoznamom všetkých. */
@Composable
private fun CategoryChips(state: TransactionFormState, onCategory: (Long) -> Unit, onMore: () -> Unit) {
    val all = state.mainCategories
    val selectedId = state.selection?.categoryId
    val shown = all.take(SHOWN_CATEGORIES)
    // Vybraná kategória mimo prvých siedmich sa ukáže na mieste čipu „Viac“.
    val selectedElsewhere = all.firstOrNull { it.id == selectedId && it !in shown }

    ChipSection(stringResource(R.string.form_category)) {
        // Všetky kategórie druhu môžu byť archivované; bez kategórie sa výdavok ani príjem uložiť nedá.
        if (all.isEmpty()) {
            Text(
                stringResource(R.string.form_no_categories),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        shown.forEach { category ->
            ChoiceChip(category.name, selected = category.id == selectedId, onClick = { onCategory(category.id) })
        }
        if (all.size > SHOWN_CATEGORIES) {
            ChoiceChip(
                text = selectedElsewhere?.name ?: stringResource(R.string.form_more),
                selected = selectedElsewhere != null,
                onClick = onMore,
                outlinedOnly = true,
                description = stringResource(R.string.form_all_categories),
            )
        }
    }
}

/** Zaoblený čip voľby: text vždy na jeden riadok, vybraný má akcentový obrys. */
@Composable
private fun ChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    outlinedOnly: Boolean = false,
    description: String? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = when {
            selected -> scheme.primary
            outlinedOnly -> scheme.onSurfaceVariant
            else -> scheme.onSurface
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clip(GrosShapes.pill)
            .background(
                when {
                    selected -> scheme.primaryContainer
                    outlinedOnly -> scheme.background
                    else -> scheme.surface
                },
            )
            .then(
                when {
                    selected -> Modifier.outlined(scheme.primary, GrosShapes.pill)
                    outlinedOnly -> Modifier.outlined(scheme.outline, GrosShapes.pill)
                    else -> Modifier
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                this.selected = selected
                if (description != null && !selected) contentDescription = description
            }
            .padding(horizontal = 18.dp, vertical = 13.dp),
    )
}

/** Poznámka je nepovinná a predvolene skrytá za „+ Poznámka“; pri písaní našepkáva predošlé zápisy. */
@Composable
private fun NoteField(
    draft: FormDraft,
    suggestions: List<String>,
    onShow: () -> Unit,
    onChange: (String) -> Unit,
    onSuggestion: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
) {
    if (!draft.noteVisible) {
        Text(
            stringResource(R.string.form_add_note),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .clip(GrosShapes.cardSmall)
                .clickable(role = Role.Button, onClick = onShow)
                .padding(horizontal = 4.dp, vertical = 12.dp),
        )
        return
    }

    val focusRequester = remember { FocusRequester() }
    val bringIntoView = remember { BringIntoViewRequester() }
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    val label = stringResource(R.string.form_note)
    // Pole sa otvára ťuknutím na „+ Poznámka“, preto hneď dostane fokus; pri úprave nie.
    var requestFocus by rememberSaveable { mutableStateOf(draft.note.isEmpty()) }
    LaunchedEffect(Unit) {
        if (requestFocus) {
            focusRequester.requestFocus()
            requestFocus = false
        }
    }
    // Pole a návrhy pod ním sa posunú nad systémovú klávesnicu.
    LaunchedEffect(focused, suggestions) {
        if (focused) bringIntoView.bringIntoView()
    }

    Column(modifier = Modifier.bringIntoViewRequester(bringIntoView), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, GrosShapes.cardSmall)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            GrosTextField(
                value = draft.note,
                onValueChange = onChange,
                label = label,
                maxLength = 200,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 28.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        focused = it.isFocused
                        onFocus(it.isFocused)
                    },
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )
        }
        if (suggestions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.form_note_suggestions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.forEach { suggestion ->
                        ChoiceChip(text = suggestion, selected = false, onClick = { onSuggestion(suggestion) })
                    }
                }
            }
        }
    }
}
