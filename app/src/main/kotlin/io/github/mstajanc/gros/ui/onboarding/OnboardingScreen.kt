package io.github.mstajanc.gros.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.mstajanc.gros.AppContainer
import io.github.mstajanc.gros.R
import io.github.mstajanc.gros.data.repository.InitialAccount
import io.github.mstajanc.gros.data.repository.InitialCategory
import io.github.mstajanc.gros.domain.MoneyFormatter
import io.github.mstajanc.gros.domain.model.AccountType
import io.github.mstajanc.gros.domain.model.CategoryKind
import io.github.mstajanc.gros.domain.rules.InvariantViolationException
import io.github.mstajanc.gros.ui.accounts.labelRes
import io.github.mstajanc.gros.ui.common.AmountSheet
import io.github.mstajanc.gros.ui.common.CategoryBadge
import io.github.mstajanc.gros.ui.common.CenteredContent
import io.github.mstajanc.gros.ui.common.FieldTile
import io.github.mstajanc.gros.ui.common.GrosIcon
import io.github.mstajanc.gros.ui.common.OptionSheet
import io.github.mstajanc.gros.ui.backup.rememberBackupFlow
import io.github.mstajanc.gros.ui.common.PrimaryButton
import io.github.mstajanc.gros.ui.common.ScreenPadding
import io.github.mstajanc.gros.ui.common.SecondaryButton
import io.github.mstajanc.gros.ui.common.SheetOption
import io.github.mstajanc.gros.ui.common.SwitchRow
import io.github.mstajanc.gros.ui.common.TextFieldTile
import io.github.mstajanc.gros.ui.common.grosViewModel
import io.github.mstajanc.gros.ui.common.outlined
import io.github.mstajanc.gros.ui.theme.GrosShapes
import io.github.mstajanc.gros.ui.theme.GrosTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Predvolená kategória ponúkaná v sprievodcovi (špecifikácia, Biznis pravidlá – Podkategórie). */
data class CategoryTemplate(
    @StringRes val name: Int,
    val kind: CategoryKind,
    val color: Int,
    val subcategories: List<Int> = emptyList(),
)

val DEFAULT_CATEGORIES: List<CategoryTemplate> = listOf(
    CategoryTemplate(R.string.cat_food, CategoryKind.EXPENSE, color = 0),
    CategoryTemplate(
        R.string.cat_housing, CategoryKind.EXPENSE, color = 1,
        subcategories = listOf(R.string.cat_rent, R.string.cat_energy, R.string.cat_internet),
    ),
    CategoryTemplate(
        R.string.cat_car, CategoryKind.EXPENSE, color = 2,
        subcategories = listOf(R.string.cat_fuel, R.string.cat_service, R.string.cat_insurance),
    ),
    CategoryTemplate(R.string.cat_kids, CategoryKind.EXPENSE, color = 3),
    CategoryTemplate(R.string.cat_fun, CategoryKind.EXPENSE, color = 4),
    CategoryTemplate(
        R.string.cat_health, CategoryKind.EXPENSE, color = 0,
        subcategories = listOf(R.string.cat_doctor, R.string.cat_medicine),
    ),
    CategoryTemplate(R.string.cat_clothes, CategoryKind.EXPENSE, color = 1),
    CategoryTemplate(R.string.cat_other, CategoryKind.EXPENSE, color = 5),
    CategoryTemplate(R.string.cat_salary, CategoryKind.INCOME, color = 0),
    CategoryTemplate(R.string.cat_other_income, CategoryKind.INCOME, color = 5),
)

enum class OnboardingStep { WELCOME, ACCOUNTS, CATEGORIES }

data class WizardAccount(
    val key: Int,
    val name: String,
    val type: AccountType = AccountType.CHECKING,
    val openingCents: Long = 0,
    val includeInTotal: Boolean = true,
)

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val accounts: List<WizardAccount> = emptyList(),
    /** Odškrtnuté položky: index hlavnej kategórie, pri podkategórii aj jej index. */
    val unchecked: Set<Pair<Int, Int?>> = emptySet(),
    val saving: Boolean = false,
) {
    fun isChecked(main: Int, sub: Int? = null): Boolean = (main to sub) !in unchecked

    val accountsValid: Boolean get() = accounts.isNotEmpty() && accounts.all { it.name.isNotBlank() }

    /** Musí ostať aspoň jedna hlavná kategória z každého druhu. */
    val categoriesValid: Boolean
        get() = CategoryKind.entries.all { kind ->
            DEFAULT_CATEGORIES.withIndex().any { (index, template) -> template.kind == kind && isChecked(index) }
        }
}

class OnboardingViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state
    private var nextKey = 0

    fun ensureFirstAccount(defaultName: String) = _state.update {
        if (it.accounts.isEmpty()) it.copy(accounts = listOf(WizardAccount(nextKey++, defaultName))) else it
    }

    fun go(step: OnboardingStep) = _state.update { it.copy(step = step) }

    fun addAccount() = _state.update { it.copy(accounts = it.accounts + WizardAccount(nextKey++, "")) }

    fun removeAccount(key: Int) = _state.update {
        if (it.accounts.size <= 1) it else it.copy(accounts = it.accounts.filter { account -> account.key != key })
    }

    fun updateAccount(key: Int, transform: (WizardAccount) -> WizardAccount) = _state.update {
        it.copy(accounts = it.accounts.map { account -> if (account.key == key) transform(account) else account })
    }

    fun toggle(main: Int, sub: Int? = null) = _state.update {
        val item = main to sub
        it.copy(unchecked = if (item in it.unchecked) it.unchecked - item else it.unchecked + item)
    }

    /** Zapíše účty a zvolené kategórie naraz; Prehľad sa potom otvorí sám, lebo už existuje účet. */
    fun finish(categories: List<InitialCategory>) {
        val current = _state.value
        if (current.saving || !current.accountsValid || !current.categoriesValid) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                container.setupRepository.createInitialData(
                    accounts = current.accounts.map {
                        InitialAccount(it.name, it.type, it.openingCents, it.includeInTotal)
                    },
                    categories = categories,
                )
            } catch (_: InvariantViolationException) {
                _state.update { it.copy(saving = false) }
            }
        }
    }
}

@Composable
fun OnboardingScreen() {
    val viewModel = grosViewModel { container, _ -> OnboardingViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val defaultAccountName = stringResource(R.string.onboarding_default_account)
    LaunchedEffect(Unit) { viewModel.ensureFirstAccount(defaultAccountName) }

    // Názvy sa prekladajú zo zdrojov až tu, aby sa do databázy zapísali v jazyku appky.
    val categoryNames = DEFAULT_CATEGORIES.map { template ->
        stringResource(template.name) to template.subcategories.map { stringResource(it) }
    }

    BackHandler(enabled = state.step != OnboardingStep.WELCOME) {
        viewModel.go(OnboardingStep.entries[state.step.ordinal - 1])
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        val backup = rememberBackupFlow()
        CenteredContent(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
            Column(modifier = Modifier.fillMaxSize().padding(horizontal = ScreenPadding)) {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = 24.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (state.step) {
                        OnboardingStep.WELCOME -> Intro(R.string.onboarding_welcome_title, R.string.onboarding_welcome_text)
                        OnboardingStep.ACCOUNTS -> AccountsStep(state, viewModel)
                        OnboardingStep.CATEGORIES -> CategoriesStep(state, categoryNames, viewModel::toggle)
                    }
                }
                Column(modifier = Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (state.step) {
                        OnboardingStep.WELCOME -> {
                            PrimaryButton(
                                stringResource(R.string.onboarding_start),
                                onClick = { viewModel.go(OnboardingStep.ACCOUNTS) },
                            )
                            // Obnova na novom telefóne: po importe pribudnú účty a sprievodca sa zavrie sám.
                            SecondaryButton(
                                stringResource(R.string.onboarding_restore),
                                onClick = backup.import,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        OnboardingStep.ACCOUNTS -> PrimaryButton(
                            stringResource(R.string.action_continue),
                            onClick = { viewModel.go(OnboardingStep.CATEGORIES) },
                            enabled = state.accountsValid,
                        )

                        OnboardingStep.CATEGORIES -> PrimaryButton(
                            stringResource(R.string.onboarding_finish),
                            onClick = {
                                viewModel.finish(
                                    DEFAULT_CATEGORIES.mapIndexedNotNull { index, template ->
                                        if (!state.isChecked(index)) return@mapIndexedNotNull null
                                        val (name, subNames) = categoryNames[index]
                                        InitialCategory(
                                            name = name,
                                            kind = template.kind,
                                            color = template.color,
                                            subcategories = subNames.filterIndexed { sub, _ -> state.isChecked(index, sub) },
                                        )
                                    },
                                )
                            },
                            enabled = state.categoriesValid && !state.saving,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.Intro(@StringRes title: Int, @StringRes text: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ColumnScope.AccountsStep(state: OnboardingState, viewModel: OnboardingViewModel) {
    var sheet by rememberSaveable { mutableStateOf<Pair<Boolean, Int>?>(null) }

    Intro(R.string.onboarding_accounts_title, R.string.onboarding_accounts_text)
    state.accounts.forEach { account ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .outlined(MaterialTheme.colorScheme.outline, GrosShapes.cardMedium)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextFieldTile(
                stringResource(R.string.account_name),
                account.name,
                onValueChange = { name -> viewModel.updateAccount(account.key) { it.copy(name = name) } },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FieldTile(
                    label = stringResource(R.string.account_type),
                    value = stringResource(account.type.labelRes()),
                    onClick = { sheet = false to account.key },
                    modifier = Modifier.weight(1f),
                )
                FieldTile(
                    label = stringResource(R.string.account_opening),
                    value = MoneyFormatter.format(account.openingCents),
                    onClick = { sheet = true to account.key },
                    modifier = Modifier.weight(1f),
                )
            }
            SwitchRow(
                stringResource(R.string.accounts_in_total),
                account.includeInTotal,
                onCheckedChange = { include -> viewModel.updateAccount(account.key) { it.copy(includeInTotal = include) } },
            )
            if (state.accounts.size > 1) {
                SecondaryButton(
                    stringResource(R.string.onboarding_remove_account),
                    onClick = { viewModel.removeAccount(account.key) },
                    contentColor = GrosTheme.colors.warning,
                    containerColor = MaterialTheme.colorScheme.background,
                )
            }
        }
    }
    SecondaryButton(
        stringResource(R.string.accounts_add),
        onClick = viewModel::addAccount,
        modifier = Modifier.fillMaxWidth(),
        contentColor = MaterialTheme.colorScheme.primary,
    )

    val open = sheet
    val account = state.accounts.firstOrNull { it.key == open?.second }
    if (open != null && account != null) {
        if (open.first) {
            AmountSheet(
                title = stringResource(R.string.account_opening),
                initialCents = account.openingCents,
                allowNegative = true,
                onConfirm = { cents ->
                    viewModel.updateAccount(account.key) { it.copy(openingCents = cents) }
                    sheet = null
                },
                onDismiss = { sheet = null },
            )
        } else {
            OptionSheet(
                title = stringResource(R.string.account_type),
                options = AccountType.entries.map { SheetOption(it, stringResource(it.labelRes())) },
                selected = account.type,
                onSelect = { type ->
                    viewModel.updateAccount(account.key) { it.copy(type = type) }
                    sheet = null
                },
                onDismiss = { sheet = null },
            )
        }
    }
}

@Composable
private fun ColumnScope.CategoriesStep(
    state: OnboardingState,
    names: List<Pair<String, List<String>>>,
    onToggle: (Int, Int?) -> Unit,
) {
    Intro(R.string.onboarding_categories_title, R.string.onboarding_categories_text)
    CategoryKind.entries.forEach { kind ->
        Text(
            stringResource(if (kind == CategoryKind.EXPENSE) R.string.onboarding_expense else R.string.onboarding_income),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = 6.dp).semantics { heading() },
        )
        DEFAULT_CATEGORIES.forEachIndexed { index, template ->
            if (template.kind != kind) return@forEachIndexed
            val (name, subNames) = names[index]
            val mainChecked = state.isChecked(index)
            Column(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, GrosShapes.cardMedium),
            ) {
                CheckRow(
                    label = name,
                    checked = mainChecked,
                    onToggle = { onToggle(index, null) },
                    leading = { CategoryBadge(name, GrosTheme.colors.category(template.color), Modifier.size(36.dp)) },
                )
                subNames.forEachIndexed { subIndex, subName ->
                    CheckRow(
                        label = subName,
                        checked = mainChecked && state.isChecked(index, subIndex),
                        onToggle = { onToggle(index, subIndex) },
                        enabled = mainChecked,
                        indent = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckRow(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    enabled: Boolean = true,
    indent: Boolean = false,
    leading: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(GrosShapes.cardMedium)
            .toggleable(value = checked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .alpha(if (enabled) 1f else 0.4f)
            .padding(start = if (indent) 60.dp else 12.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Text(
            label,
            style = if (indent) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(GrosShapes.pill)
                .background(if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) GrosIcon(R.drawable.ic_check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
