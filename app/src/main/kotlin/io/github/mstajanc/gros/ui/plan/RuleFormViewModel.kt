package io.github.mstajanc.gros.ui.plan

import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mstajanc.gros.AppContainer
import io.github.mstajanc.gros.R
import io.github.mstajanc.gros.domain.FormSelection
import io.github.mstajanc.gros.domain.Money
import io.github.mstajanc.gros.domain.RuleEditing
import io.github.mstajanc.gros.domain.RuleInput
import io.github.mstajanc.gros.domain.RuleState
import io.github.mstajanc.gros.domain.RuleStatus
import io.github.mstajanc.gros.domain.TransactionFormRules
import io.github.mstajanc.gros.domain.model.Account
import io.github.mstajanc.gros.domain.model.Category
import io.github.mstajanc.gros.domain.model.CategoryKind
import io.github.mstajanc.gros.domain.model.Frequency
import io.github.mstajanc.gros.domain.model.RecurringRule
import io.github.mstajanc.gros.domain.model.TransactionType
import io.github.mstajanc.gros.domain.rules.InvariantViolationException
import io.github.mstajanc.gros.domain.rules.Violation
import java.time.LocalDate
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Rozpracovaný formulár pravidla – presne to, čo sa ukladá do `SavedStateHandle`. */
data class RuleDraftState(
    val name: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val amountCents: Long = 0,
    val accountId: Long? = null,
    val toAccountId: Long? = null,
    val categoryId: Long? = null,
    val frequency: Frequency = Frequency.MONTHLY,
    val interval: Int = 1,
    val nextDate: LocalDate,
    val endDate: LocalDate? = null,
)

data class RuleFormState(
    val loaded: Boolean = false,
    val draft: RuleDraftState? = null,
    val existing: RecurringRule? = null,
    val today: LocalDate,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    @StringRes val error: Int? = null,
) {
    val isEdit: Boolean get() = existing != null
    val status: RuleStatus? get() = existing?.let(RuleState::status)
    val transferAvailable: Boolean get() = TransactionFormRules.isTransferAvailable(accounts)

    /** Vstup pre doménu; `null`, kým chýba účet. */
    val input: RuleInput?
        get() {
            val d = draft ?: return null
            return RuleInput(
                name = d.name,
                type = d.type,
                amountCents = d.amountCents,
                accountId = d.accountId ?: return null,
                toAccountId = d.toAccountId,
                categoryId = d.categoryId,
                frequency = d.frequency,
                interval = d.interval,
                nextDate = d.nextDate,
                endDate = d.endDate,
            )
        }

    /** Kotva, od ktorej sa kontroluje koniec: pri úprave bez posunu dátumu ostáva pôvodná. */
    private val anchor: LocalDate?
        get() {
            val d = draft ?: return null
            val old = existing ?: return d.nextDate
            val reanchored = d.nextDate != old.nextDate || d.frequency != old.frequency || d.interval != old.interval
            return if (reanchored) d.nextDate else old.anchorDate
        }

    val canSave: Boolean
        get() {
            val d = draft ?: return false
            val input = input ?: return false
            val target = when (d.type) {
                TransactionType.TRANSFER -> d.toAccountId != null && d.toAccountId != d.accountId
                else -> d.categoryId != null
            }
            return d.name.isNotBlank() && d.amountCents in 1..Money.MAX_CENTS && target &&
                RuleEditing.isEndValid(input, anchor ?: d.nextDate)
        }

    /** Hlavné aktívne kategórie druhu a pod každou jej aktívne podkategórie. */
    val categoryChoices: List<Category>
        get() {
            val kind = if (draft?.type == TransactionType.INCOME) CategoryKind.INCOME else CategoryKind.EXPENSE
            val order = compareBy<Category>({ it.sortOrder }, { it.id })
            return categories.filter { it.isMain && it.kind == kind && !it.archived }.sortedWith(order).flatMap { main ->
                listOf(main) + categories.filter { it.parentId == main.id && !it.archived }.sortedWith(order)
            }
        }

    val activeAccounts: List<Account>
        get() = accounts.filter { !it.archived }.sortedWith(compareBy({ it.sortOrder }, { it.id }))

    fun accountName(id: Long?): String? = accounts.firstOrNull { it.id == id }?.name

    /** „Auto · Tankovanie“ alebo „Auto“. */
    fun categoryTitle(id: Long?): String? {
        val category = categories.firstOrNull { it.id == id } ?: return null
        val parent = category.parentId?.let { parentId -> categories.firstOrNull { it.id == parentId } }
        return if (parent == null) category.name else "${parent.name} · ${category.name}"
    }
}

sealed interface RuleFormEvent {
    data object Close : RuleFormEvent

    /** Pred uložením treba potvrdiť spätné zápisy (pravidlo 6, B11). */
    data class ConfirmBackfill(val count: Int) : RuleFormEvent
}

class RuleFormViewModel(private val container: AppContainer, private val handle: SavedStateHandle) : ViewModel() {

    private val ruleId: Long? = handle.get<Long>(ARG_ID)?.takeIf { it > 0 }
    private val rules = container.recurringRuleRepository
    private val draft = MutableStateFlow(restoreDraft())
    private val existing = MutableStateFlow<RecurringRule?>(null)
    private val error = MutableStateFlow<Int?>(null)

    private val _events = Channel<RuleFormEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val state: StateFlow<RuleFormState> = combine(
        combine(draft, existing, error) { d, e, err -> Triple(d, e, err) },
        container.accountRepository.observeAccounts(),
        container.categoryRepository.observeCategories(),
    ) { (d, e, err), accounts, categories ->
        RuleFormState(
            loaded = d != null && (ruleId == null || e != null),
            draft = d,
            existing = e,
            today = container.today(),
            accounts = accounts,
            categories = categories,
            error = err,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RuleFormState(today = container.today()))

    init {
        viewModelScope.launch {
            val rule = ruleId?.let { rules.get(it) }
            existing.value = rule
            if (draft.value == null) setDraft(initialDraft(rule))
        }
    }

    private suspend fun initialDraft(rule: RecurringRule?): RuleDraftState {
        if (rule != null) {
            return RuleDraftState(
                name = rule.name,
                type = rule.type,
                amountCents = rule.amountCents,
                accountId = rule.accountId,
                toAccountId = rule.toAccountId,
                categoryId = rule.categoryId,
                frequency = rule.frequency,
                interval = rule.interval,
                nextDate = rule.nextDate,
                endDate = rule.endDate,
            )
        }
        val accounts = container.accountRepository.observeAccounts().first()
        // Nové pravidlo má predvolenú „Ďalšiu platbu“ dnes (pravidlo 6).
        return RuleDraftState(
            accountId = TransactionFormRules.defaultAccountId(container.settingsRepository.getLastUsedAccountId(), accounts),
            nextDate = container.today(),
        )
    }

    private fun change(transform: (RuleDraftState) -> RuleDraftState) {
        val current = draft.value ?: return
        error.value = null
        setDraft(transform(current))
    }

    fun onName(value: String) = change { it.copy(name = value) }
    fun onAmount(cents: Long) = change { it.copy(amountCents = cents) }

    fun onType(type: TransactionType) {
        if (type == TransactionType.TRANSFER && !state.value.transferAvailable) return
        change {
            // Rovnaké pravidlá ako vo formulári transakcie: zmena typu zruší kategóriu aj cieľový účet.
            val selection = TransactionFormRules.onTypeChanged(
                FormSelection(it.type, it.categoryId, null, it.accountId, it.toAccountId),
                type,
            )
            it.copy(type = selection.type, categoryId = selection.categoryId, toAccountId = selection.toAccountId)
        }
    }

    fun onCategory(id: Long) = change { it.copy(categoryId = id) }
    fun onAccount(id: Long) = change { it.copy(accountId = id, toAccountId = it.toAccountId.takeIf { to -> to != id }) }
    fun onToAccount(id: Long) = change { it.copy(toAccountId = id) }
    fun onRepeat(frequency: Frequency, interval: Int) = change { it.copy(frequency = frequency, interval = interval.coerceIn(1, 99)) }
    fun onNextDate(date: LocalDate) = change { it.copy(nextDate = date) }
    fun onEndDate(date: LocalDate?) = change { it.copy(endDate = date) }

    /** Uloží pravidlo; ak by vznikli spätné zápisy a nie sú potvrdené, najprv sa opýta. */
    fun save(confirmedBackfill: Boolean = false) {
        val current = state.value
        val input = current.input ?: return
        if (!current.canSave) return
        val backfill = RuleEditing.backfillCount(current.existing, input, current.today)
        if (backfill > 0 && !confirmedBackfill) {
            _events.trySend(RuleFormEvent.ConfirmBackfill(backfill))
            return
        }
        launchSafely {
            if (ruleId == null) rules.create(input) else rules.edit(ruleId, input)
            container.settingsRepository.setLastUsedAccountId(input.accountId)
            container.runRecurring()
            _events.send(RuleFormEvent.Close)
        }
    }

    /** Pozastavenie a obnovenie sa uložia hneď; po obnovení beží generátor (pravidlo 1). */
    fun togglePaused() {
        val rule = existing.value ?: return
        launchSafely {
            rules.setPaused(rule.id, !rule.paused)
            if (rule.paused) container.runRecurring()
            _events.send(RuleFormEvent.Close)
        }
    }

    fun delete() {
        val id = ruleId ?: return
        launchSafely {
            rules.delete(id)
            _events.send(RuleFormEvent.Close)
        }
    }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: InvariantViolationException) {
                error.value = when (e.violation) {
                    Violation.ACCOUNT_ARCHIVED, Violation.CATEGORY_ARCHIVED -> R.string.rule_error_archived
                    else -> R.string.rule_error_generic
                }
            }
        }
    }

    // --- uloženie rozpracovaného formulára -------------------------------------------------

    private fun setDraft(value: RuleDraftState) {
        draft.value = value
        handle[KEY_SAVED] = true
        handle[KEY_NAME] = value.name
        handle[KEY_TYPE] = value.type.name
        handle[KEY_AMOUNT] = value.amountCents
        handle[KEY_ACCOUNT] = value.accountId
        handle[KEY_TO_ACCOUNT] = value.toAccountId
        handle[KEY_CATEGORY] = value.categoryId
        handle[KEY_FREQUENCY] = value.frequency.name
        handle[KEY_INTERVAL] = value.interval
        handle[KEY_NEXT] = value.nextDate.toString()
        handle[KEY_END] = value.endDate?.toString()
    }

    private fun restoreDraft(): RuleDraftState? {
        if (handle.get<Boolean>(KEY_SAVED) != true) return null
        return RuleDraftState(
            name = handle[KEY_NAME] ?: "",
            type = TransactionType.valueOf(handle[KEY_TYPE] ?: TransactionType.EXPENSE.name),
            amountCents = handle[KEY_AMOUNT] ?: 0L,
            accountId = handle[KEY_ACCOUNT],
            toAccountId = handle[KEY_TO_ACCOUNT],
            categoryId = handle[KEY_CATEGORY],
            frequency = Frequency.valueOf(handle[KEY_FREQUENCY] ?: Frequency.MONTHLY.name),
            interval = handle[KEY_INTERVAL] ?: 1,
            nextDate = LocalDate.parse(handle[KEY_NEXT] ?: container.today().toString()),
            endDate = handle.get<String>(KEY_END)?.let(LocalDate::parse),
        )
    }

    companion object {
        const val ARG_ID = "id"
        private const val KEY_SAVED = "rule_saved"
        private const val KEY_NAME = "rule_name"
        private const val KEY_TYPE = "rule_type"
        private const val KEY_AMOUNT = "rule_amount"
        private const val KEY_ACCOUNT = "rule_account"
        private const val KEY_TO_ACCOUNT = "rule_to_account"
        private const val KEY_CATEGORY = "rule_category"
        private const val KEY_FREQUENCY = "rule_frequency"
        private const val KEY_INTERVAL = "rule_interval"
        private const val KEY_NEXT = "rule_next"
        private const val KEY_END = "rule_end"
    }
}
