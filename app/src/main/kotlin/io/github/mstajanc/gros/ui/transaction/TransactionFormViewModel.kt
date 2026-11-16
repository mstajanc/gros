package io.github.mstajanc.gros.ui.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mstajanc.gros.AppContainer
import io.github.mstajanc.gros.R
import io.github.mstajanc.gros.domain.AmountInput
import io.github.mstajanc.gros.domain.FormSelection
import io.github.mstajanc.gros.domain.TransactionFormRules
import io.github.mstajanc.gros.domain.model.Account
import io.github.mstajanc.gros.domain.model.Category
import io.github.mstajanc.gros.domain.model.CategoryKind
import io.github.mstajanc.gros.domain.model.Transaction
import io.github.mstajanc.gros.domain.model.TransactionDraft
import io.github.mstajanc.gros.domain.model.TransactionType
import io.github.mstajanc.gros.domain.rules.InvariantViolationException
import io.github.mstajanc.gros.ui.common.SnackbarEvent
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Rozpracovaný formulár – presne to, čo sa ukladá do `SavedStateHandle`. */
data class FormDraft(
    val selection: FormSelection,
    val amountText: String = "",
    val date: LocalDate,
    val note: String = "",
    val noteVisible: Boolean = false,
)

data class TransactionFormState(
    val loaded: Boolean = false,
    val isEdit: Boolean = false,
    val draft: FormDraft? = null,
    val today: LocalDate,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val suggestions: List<String> = emptyList(),
) {
    val selection: FormSelection? get() = draft?.selection
    val amount: AmountInput get() = AmountInput(draft?.amountText.orEmpty())
    val canSave: Boolean get() = selection?.let { TransactionFormRules.canSave(it, amount.cents) } ?: false
    val transferAvailable: Boolean get() = TransactionFormRules.isTransferAvailable(accounts)

    private val kind: CategoryKind?
        get() = when (selection?.type) {
            TransactionType.EXPENSE -> CategoryKind.EXPENSE
            TransactionType.INCOME -> CategoryKind.INCOME
            else -> null
        }

    /** Aktívne hlavné kategórie druhu v poradí používateľa; vybraná ostáva aj archivovaná. */
    val mainCategories: List<Category>
        get() = categories
            .filter { it.isMain && it.kind == kind && (!it.archived || it.id == selection?.categoryId) }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))

    /** Aktívne podkategórie vybranej hlavnej kategórie. */
    val subcategories: List<Category>
        get() {
            val mainId = selection?.categoryId ?: return emptyList()
            return categories
                .filter { it.parentId == mainId && (!it.archived || it.id == selection?.subcategoryId) }
                .sortedWith(compareBy({ it.sortOrder }, { it.id }))
        }

    /** Účty, ktoré sa dajú vybrať: aktívne a tie, ktoré záznam už používa. */
    val selectableAccounts: List<Account>
        get() = accounts
            .filter { !it.archived || it.id == selection?.accountId || it.id == selection?.toAccountId }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))

    fun accountName(id: Long?): String? = accounts.firstOrNull { it.id == id }?.name
}

sealed interface FormEvent {
    data object Close : FormEvent
}

/**
 * Nová transakcia, úprava a duplikát. Rozpracovaný formulár prežije otočenie obrazovky
 * aj obnovenie procesu; systémové Späť ho zahodí bez dialógu.
 */
class TransactionFormViewModel(
    private val container: AppContainer,
    private val handle: SavedStateHandle,
) : ViewModel() {

    private val sourceId: Long? = handle.get<Long>(ARG_ID)?.takeIf { it > 0 }
    private val duplicate: Boolean = handle.get<Boolean>(ARG_DUPLICATE) ?: false
    private val editId: Long? = sourceId.takeIf { !duplicate }

    private val transactions = container.transactionRepository
    private val draft = MutableStateFlow(restoreDraft())
    private val suggestions = MutableStateFlow<List<String>>(emptyList())
    private var original: Transaction? = null

    private val _events = Channel<FormEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<TransactionFormState> = combine(
        draft,
        container.accountRepository.observeAccounts(),
        container.categoryRepository.observeCategories(),
        suggestions,
    ) { draft, accounts, categories, suggestions ->
        TransactionFormState(
            loaded = draft != null,
            isEdit = editId != null,
            draft = draft,
            today = container.today(),
            accounts = accounts,
            categories = categories,
            suggestions = suggestions,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionFormState(today = container.today()))

    init {
        viewModelScope.launch {
            original = editId?.let { transactions.get(it) }
            if (draft.value == null) setDraft(initialDraft())
        }
    }

    private suspend fun initialDraft(): FormDraft {
        val today = container.today()
        val source = sourceId?.let { transactions.get(it) }
        val categories = container.categoryRepository.observeCategories().first()
        if (source != null) {
            val category = categories.firstOrNull { it.id == source.categoryId }
            return FormDraft(
                selection = FormSelection(
                    type = source.type,
                    categoryId = category?.parentId ?: category?.id,
                    subcategoryId = category?.id?.takeIf { category.parentId != null },
                    accountId = source.accountId,
                    toAccountId = source.toAccountId,
                ),
                amountText = AmountInput.fromCents(source.amountCents).text,
                // Duplikát má dnešný dátum (rozhodnutie B6).
                date = if (duplicate) today else source.date,
                note = source.note.orEmpty(),
                noteVisible = source.note != null,
            )
        }
        val accounts = container.accountRepository.observeAccounts().first()
        val requested = handle.get<String>(ARG_TYPE)?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() }
        val type = if (requested == TransactionType.TRANSFER && !TransactionFormRules.isTransferAvailable(accounts)) {
            TransactionType.EXPENSE
        } else {
            requested ?: TransactionType.EXPENSE
        }
        return FormDraft(
            selection = FormSelection(
                type = type,
                accountId = TransactionFormRules.defaultAccountId(
                    container.settingsRepository.getLastUsedAccountId(),
                    accounts,
                ),
            ),
            date = today,
        )
    }

    private fun change(transform: (FormDraft) -> FormDraft) {
        val current = draft.value ?: return
        setDraft(transform(current))
    }

    private fun changeSelection(transform: (FormSelection) -> FormSelection) =
        change { it.copy(selection = transform(it.selection)) }

    fun onType(type: TransactionType) {
        if (type == TransactionType.TRANSFER && !state.value.transferAvailable) return
        changeSelection { TransactionFormRules.onTypeChanged(it, type) }
    }

    fun onAmount(input: AmountInput) = change { it.copy(amountText = input.text) }

    /** Výber hlavnej kategórie zruší podkategóriu; tá je vždy nepovinná. */
    fun onCategory(categoryId: Long) = changeSelection {
        if (it.categoryId == categoryId) it else it.copy(categoryId = categoryId, subcategoryId = null)
    }

    /** Druhé ťuknutie na vybranú podkategóriu ju odznačí. */
    fun onSubcategory(subcategoryId: Long) = changeSelection {
        it.copy(subcategoryId = subcategoryId.takeIf { id -> id != it.subcategoryId })
    }

    fun onAccount(accountId: Long) = changeSelection {
        it.copy(accountId = accountId, toAccountId = it.toAccountId.takeIf { to -> to != accountId })
    }

    fun onToAccount(accountId: Long) = changeSelection { it.copy(toAccountId = accountId) }

    fun onDate(date: LocalDate) {
        if (TransactionFormRules.isDateAllowed(date, container.today())) change { it.copy(date = date) }
    }

    fun onShowNote() = change { it.copy(noteVisible = true) }

    fun onNote(note: String) {
        change { it.copy(note = note) }
        viewModelScope.launch {
            val found = transactions.suggestNotes(note)
            // Návrh zhodný s tým, čo je už napísané, nič nepridáva.
            suggestions.value = found.filter { it != note.trim() }
        }
    }

    fun onSuggestion(note: String) {
        change { it.copy(note = note) }
        suggestions.value = emptyList()
    }

    fun save() {
        val current = draft.value ?: return
        val selection = current.selection
        val amountCents = AmountInput(current.amountText).cents
        val accountId = selection.accountId ?: return
        if (!TransactionFormRules.canSave(selection, amountCents)) return
        val isTransfer = selection.type == TransactionType.TRANSFER
        val transactionDraft = TransactionDraft(
            type = selection.type,
            amountCents = amountCents,
            date = current.date,
            accountId = accountId,
            toAccountId = selection.toAccountId.takeIf { isTransfer },
            categoryId = selection.effectiveCategoryId.takeIf { !isTransfer },
            note = current.note,
        )
        viewModelScope.launch {
            try {
                val before = original
                if (editId != null && before != null) {
                    transactions.update(editId, transactionDraft)
                    // Späť po uložení úpravy vráti pôvodné hodnoty (rozhodnutie B7).
                    container.snackbarBus.show(SnackbarEvent(R.string.snackbar_saved) { transactions.restore(before) })
                } else {
                    val newId = transactions.create(transactionDraft)
                    container.snackbarBus.show(SnackbarEvent(R.string.snackbar_saved) { transactions.delete(newId) })
                }
                container.settingsRepository.setLastUsedAccountId(accountId)
                _events.send(FormEvent.Close)
            } catch (_: InvariantViolationException) {
                // Formulár neplatný zápis nepustí; ak sa dáta medzitým zmenili, ostáva otvorený.
            }
        }
    }

    fun delete() {
        val id = editId ?: return
        viewModelScope.launch {
            val deleted = transactions.delete(id)
            if (deleted != null) {
                container.snackbarBus.show(SnackbarEvent(R.string.snackbar_deleted) { transactions.restore(deleted) })
            }
            _events.send(FormEvent.Close)
        }
    }

    // --- uloženie rozpracovaného formulára -------------------------------------------------

    private fun setDraft(value: FormDraft) {
        draft.update { value }
        handle[KEY_SAVED] = true
        handle[KEY_TYPE] = value.selection.type.name
        handle[KEY_CATEGORY] = value.selection.categoryId
        handle[KEY_SUBCATEGORY] = value.selection.subcategoryId
        handle[KEY_ACCOUNT] = value.selection.accountId
        handle[KEY_TO_ACCOUNT] = value.selection.toAccountId
        handle[KEY_AMOUNT] = value.amountText
        handle[KEY_DATE] = value.date.toString()
        handle[KEY_NOTE] = value.note
        handle[KEY_NOTE_VISIBLE] = value.noteVisible
    }

    private fun restoreDraft(): FormDraft? {
        if (handle.get<Boolean>(KEY_SAVED) != true) return null
        return FormDraft(
            selection = FormSelection(
                type = TransactionType.valueOf(handle.get<String>(KEY_TYPE) ?: TransactionType.EXPENSE.name),
                categoryId = handle[KEY_CATEGORY],
                subcategoryId = handle[KEY_SUBCATEGORY],
                accountId = handle[KEY_ACCOUNT],
                toAccountId = handle[KEY_TO_ACCOUNT],
            ),
            amountText = handle[KEY_AMOUNT] ?: "",
            date = LocalDate.parse(handle.get<String>(KEY_DATE) ?: container.today().toString()),
            note = handle[KEY_NOTE] ?: "",
            noteVisible = handle[KEY_NOTE_VISIBLE] ?: false,
        )
    }

    companion object {
        const val ARG_ID = "id"
        const val ARG_DUPLICATE = "duplicate"
        const val ARG_TYPE = "type"

        private const val KEY_SAVED = "draft_saved"
        private const val KEY_TYPE = "draft_type"
        private const val KEY_CATEGORY = "draft_category"
        private const val KEY_SUBCATEGORY = "draft_subcategory"
        private const val KEY_ACCOUNT = "draft_account"
        private const val KEY_TO_ACCOUNT = "draft_to_account"
        private const val KEY_AMOUNT = "draft_amount"
        private const val KEY_DATE = "draft_date"
        private const val KEY_NOTE = "draft_note"
        private const val KEY_NOTE_VISIBLE = "draft_note_visible"
    }
}
