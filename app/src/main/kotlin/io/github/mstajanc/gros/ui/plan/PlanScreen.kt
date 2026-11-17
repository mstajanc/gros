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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.mstajanc.gros.AppContainer
import io.github.mstajanc.gros.R
import io.github.mstajanc.gros.domain.MonthlySummary
import io.github.mstajanc.gros.domain.MoneyFormatter
import io.github.mstajanc.gros.domain.RuleState
import io.github.mstajanc.gros.domain.RuleStatus
import io.github.mstajanc.gros.domain.SignDisplay
import io.github.mstajanc.gros.domain.SlovakDates
import io.github.mstajanc.gros.domain.model.RecurringRule
import io.github.mstajanc.gros.domain.model.TransactionType
import io.github.mstajanc.gros.ui.common.AmountText
import io.github.mstajanc.gros.ui.common.CategoryBadge
import io.github.mstajanc.gros.ui.common.CenteredContent
import io.github.mstajanc.gros.ui.common.Lookup
import io.github.mstajanc.gros.ui.common.ScreenHeader
import io.github.mstajanc.gros.ui.common.ScreenPadding
import io.github.mstajanc.gros.ui.common.SecondaryButton
import io.github.mstajanc.gros.ui.common.SegmentedControl
import io.github.mstajanc.gros.ui.common.SummaryTile
import io.github.mstajanc.gros.ui.common.TransferBadge
import io.github.mstajanc.gros.ui.common.categoryColor
import io.github.mstajanc.gros.ui.common.grosViewModel
import io.github.mstajanc.gros.ui.theme.GrosShapes
import io.github.mstajanc.gros.ui.theme.GrosTheme
import java.time.LocalDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class PlanState(
    val loaded: Boolean = false,
    val today: LocalDate,
    /** Skupiny Aktívne / Pozastavené / Ukončené, v skupine podľa ďalšieho dátumu (B10). */
    val groups: Map<RuleStatus, List<RecurringRule>> = emptyMap(),
    val summary: MonthlySummary = MonthlySummary(0, 0),
    val lookup: Lookup = Lookup(),
) {
    val isEmpty: Boolean get() = groups.values.all { it.isEmpty() }
}

class PlanViewModel(container: AppContainer) : ViewModel() {

    val state: StateFlow<PlanState> = combine(
        container.recurringRuleRepository.observeRules(),
        container.accountRepository.observeAccounts(),
        container.categoryRepository.observeCategories(),
    ) { rules, accounts, categories ->
        PlanState(
            loaded = true,
            today = container.today(),
            groups = RuleStatus.entries.associateWith { status ->
                rules.filter { RuleState.status(it) == status }.sortedWith(compareBy({ it.nextDate }, { it.id }))
            },
            summary = RuleState.monthlySummary(rules),
            lookup = Lookup.of(accounts, categories),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanState(today = container.today()))
}

@Composable
fun PlanScreen(contentPadding: PaddingValues, onAddRule: () -> Unit, onEditRule: (Long) -> Unit) {
    val viewModel = grosViewModel { container, _ -> PlanViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    CenteredContent(modifier = Modifier.padding(contentPadding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(stringResource(R.string.nav_plan))
            SegmentedControl(
                options = listOf(stringResource(R.string.plan_tab_recurring), stringResource(R.string.plan_tab_budgets)),
                selectedIndex = tab,
                onSelect = { tab = it },
            )
            if (tab == 1) {
                Text(
                    stringResource(R.string.placeholder_budgets),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                return@Column
            }
            if (!state.loaded) return@Column

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Znamienka podľa mockupu 06: výdavky bez mínusu, príjmy v akcentovej farbe (A12).
                SummaryTile(
                    label = stringResource(R.string.plan_expenses_month),
                    amount = MoneyFormatter.format(state.summary.expenseCents),
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.surface,
                )
                SummaryTile(
                    label = stringResource(R.string.plan_income_month),
                    amount = MoneyFormatter.format(state.summary.incomeCents),
                    modifier = Modifier.weight(1f),
                    amountColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surface,
                )
            }

            if (state.isEmpty) {
                Text(
                    stringResource(R.string.plan_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )
            }

            val onlyActive = state.groups.filterValues { it.isNotEmpty() }.keys == setOf(RuleStatus.ACTIVE)
            RuleStatus.entries.forEach { status ->
                val rules = state.groups[status].orEmpty()
                if (rules.isEmpty()) return@forEach
                // Pri samých aktívnych pravidlách je zoznam bez nadpisu, ako v mockupe 06.
                if (!onlyActive) {
                    Text(
                        stringResource(
                            when (status) {
                                RuleStatus.ACTIVE -> R.string.plan_group_active
                                RuleStatus.PAUSED -> R.string.plan_group_paused
                                RuleStatus.FINISHED -> R.string.plan_group_finished
                            },
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                    )
                }
                rules.forEach { rule -> RuleCard(rule, status, state, onClick = { onEditRule(rule.id) }) }
            }

            SecondaryButton(
                stringResource(R.string.plan_add_rule),
                onClick = onAddRule,
                modifier = Modifier.fillMaxWidth(),
                contentColor = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun RuleCard(rule: RecurringRule, status: RuleStatus, state: PlanState, onClick: () -> Unit) {
    val lookup = state.lookup
    val isTransfer = rule.type == TransactionType.TRANSFER
    val accountText = if (isTransfer) {
        "${lookup.accountName(rule.accountId)} → ${lookup.accountName(rule.toAccountId)}"
    } else {
        lookup.accountName(rule.accountId)
    }
    val amount = when (rule.type) {
        TransactionType.EXPENSE -> MoneyFormatter.format(-rule.amountCents)
        TransactionType.INCOME -> MoneyFormatter.format(rule.amountCents, SignDisplay.ALWAYS)
        TransactionType.TRANSFER -> MoneyFormatter.format(rule.amountCents, SignDisplay.NEVER)
    }
    val amountColor = when (rule.type) {
        TransactionType.INCOME -> MaterialTheme.colorScheme.primary
        TransactionType.TRANSFER -> GrosTheme.colors.transfer
        TransactionType.EXPENSE -> MaterialTheme.colorScheme.onSurface
    }
    val dateText = when (status) {
        RuleStatus.ACTIVE -> stringResource(R.string.plan_next, SlovakDates.shortDate(rule.nextDate, state.today))
        RuleStatus.PAUSED -> stringResource(R.string.plan_paused)
        RuleStatus.FINISHED -> stringResource(R.string.plan_finished)
    }
    val dimmed = status != RuleStatus.ACTIVE

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(GrosShapes.cardMedium)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isTransfer) {
            TransferBadge()
        } else {
            CategoryBadge(lookup.mainCategory(rule.categoryId)?.name.orEmpty(), categoryColor(lookup, rule.categoryId))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                rule.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${repeatShort(rule.frequency, rule.interval)} · $accountText",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            AmountText(amount, color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else amountColor)
            Text(dateText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
