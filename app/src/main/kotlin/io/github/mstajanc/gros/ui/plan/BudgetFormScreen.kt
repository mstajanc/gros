package io.github.mstajanc.gros.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import io.github.mstajanc.gros.domain.Money
import io.github.mstajanc.gros.domain.MoneyFormatter
import io.github.mstajanc.gros.domain.model.Budget
import io.github.mstajanc.gros.domain.model.Category
import io.github.mstajanc.gros.domain.model.CategoryKind
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
import io.github.mstajanc.gros.ui.common.grosViewModel
import io.github.mstajanc.gros.ui.theme.GrosShapes
import io.github.mstajanc.gros.ui.theme.GrosTheme
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class BudgetFormState(
    val loaded: Boolean = false,
    val existing: Budget? = null,
    val categoryId: Long? = null,
    val limitCents: Long = 0,
    val categories: List<Category> = emptyList(),
    val budgets: List<Budget> = emptyList(),
) {
    val isEdit: Boolean get() = existing != null

    /** Aktívne hlavné výdavkové kategórie, ktoré ešte rozpočet nemajú. */
    val candidates: List<Category>
        get() {
            val taken = budgets.map { it.categoryId }.toSet()
            return categories
                .filter { it.isMain && it.kind == CategoryKind.EXPENSE && !it.archived && it.id !in taken }
                .sortedWith(compareBy({ it.sortOrder }, { it.id }))
        }

    val categoryName: String? get() = categories.firstOrNull { it.id == categoryId }?.name
    val canSave: Boolean get() = categoryId != null && limitCents in 1..Money.MAX_CENTS
}

class BudgetFormViewModel(private val container: AppContainer, private val handle: SavedStateHandle) : ViewModel() {

    private val budgetId: Long? = handle.get<Long>(ARG_ID)?.takeIf { it > 0 }
    private val repository = container.budgetRepository

    private val categoryId = handle.getStateFlow<Long?>(KEY_CATEGORY, null)
    private val limit = handle.getStateFlow(KEY_LIMIT, 0L)
    private val loaded = handle.getStateFlow(KEY_LOADED, budgetId == null)

    private val _close = Channel<Unit>(Channel.BUFFERED)
    val close = _close.receiveAsFlow()

    val state: StateFlow<BudgetFormState> = combine(
        combine(categoryId, limit, loaded) { c, l, ld -> Triple(c, l, ld) },
        repository.observeBudgets(),
        container.categoryRepository.observeCategories(),
    ) { (category, limitCents, isLoaded), budgets, categories ->
        BudgetFormState(
            loaded = isLoaded,
            existing = budgets.firstOrNull { it.id == budgetId },
            categoryId = category,
            limitCents = limitCents,
            categories = categories,
            budgets = budgets,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetFormState())

    init {
        viewModelScope.launch {
            if (!loaded.value && budgetId != null) {
                repository.get(budgetId)?.let {
                    handle[KEY_CATEGORY] = it.categoryId
                    handle[KEY_LIMIT] = it.monthlyLimitCents
                }
                handle[KEY_LOADED] = true
            }
        }
    }

    fun onCategory(id: Long) {
        handle[KEY_CATEGORY] = id
    }

    fun onLimit(cents: Long) {
        handle[KEY_LIMIT] = cents
    }

    /** Zmena limitu platí pre aktuálny a budúce mesiace; história limitov sa neukladá. */
    fun save() {
        val current = state.value
        val category = current.categoryId ?: return
        if (!current.canSave) return
        launchSafely {
            if (budgetId == null) repository.create(category, current.limitCents) else repository.updateLimit(budgetId, current.limitCents)
            _close.send(Unit)
        }
    }

    fun delete() {
        val id = budgetId ?: return
        launchSafely {
            repository.delete(id)
            _close.send(Unit)
        }
    }

    /** Formulár neplatný zápis nepustí; ak sa dáta medzitým zmenili, ostáva otvorený. */
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
        private const val KEY_CATEGORY = "budget_category"
        private const val KEY_LIMIT = "budget_limit"
        private const val KEY_LOADED = "budget_loaded"
    }
}

private enum class BudgetDialog { CATEGORY, LIMIT, DELETE }

@Composable
fun BudgetFormScreen(contentPadding: PaddingValues, onClose: () -> Unit) {
    val viewModel = grosViewModel { container, handle -> BudgetFormViewModel(container, handle) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<BudgetDialog?>(null) }

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
            ScreenHeader(stringResource(if (state.isEdit) R.string.budget_edit else R.string.budget_new), onBack = onClose)
            if (!state.loaded) return@Column

            if (state.isEdit) {
                // Kategória rozpočtu sa po vytvorení nemení; mení sa len limit.
                FieldTile(
                    label = stringResource(R.string.budget_category),
                    value = state.categoryName.orEmpty(),
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (state.candidates.isEmpty()) {
                Text(
                    stringResource(R.string.budget_no_categories),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FieldTile(
                    label = stringResource(R.string.budget_category),
                    value = state.categoryName ?: stringResource(R.string.rule_category_pick),
                    onClick = { dialog = BudgetDialog.CATEGORY },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FieldTile(
                label = stringResource(R.string.budget_limit),
                value = MoneyFormatter.format(state.limitCents),
                onClick = { dialog = BudgetDialog.LIMIT },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.budget_info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, GrosShapes.cardMedium)
                    .padding(16.dp),
            )
            PrimaryButton(stringResource(R.string.action_save), onClick = viewModel::save, enabled = state.canSave)
            if (state.isEdit) {
                SecondaryButton(
                    stringResource(R.string.action_delete),
                    onClick = { dialog = BudgetDialog.DELETE },
                    modifier = Modifier.fillMaxWidth(),
                    contentColor = GrosTheme.colors.warning,
                )
            }
        }
    }

    when (dialog) {
        BudgetDialog.CATEGORY -> OptionSheet(
            title = stringResource(R.string.budget_category),
            options = state.candidates.map { SheetOption(it.id, it.name) },
            selected = state.categoryId,
            onSelect = {
                viewModel.onCategory(it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        BudgetDialog.LIMIT -> AmountSheet(
            title = stringResource(R.string.budget_limit),
            initialCents = state.limitCents,
            allowNegative = false,
            onConfirm = {
                viewModel.onLimit(it)
                dialog = null
            },
            onDismiss = { dialog = null },
        )

        BudgetDialog.DELETE -> ConfirmDialog(
            title = stringResource(R.string.budget_delete_title),
            text = stringResource(R.string.budget_delete_text),
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
