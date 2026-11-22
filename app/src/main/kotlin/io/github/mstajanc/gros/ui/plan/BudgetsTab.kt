package io.github.mstajanc.gros.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.mstajanc.gros.AppContainer
import io.github.mstajanc.gros.R
import io.github.mstajanc.gros.domain.BudgetCalculator
import io.github.mstajanc.gros.domain.BudgetLine
import io.github.mstajanc.gros.domain.BudgetSummary
import io.github.mstajanc.gros.domain.MoneyFormatter
import io.github.mstajanc.gros.domain.Period
import io.github.mstajanc.gros.domain.PluralForm
import io.github.mstajanc.gros.domain.SlovakDates
import io.github.mstajanc.gros.domain.SlovakText
import io.github.mstajanc.gros.ui.common.AmountText
import io.github.mstajanc.gros.ui.common.ProgressBar
import io.github.mstajanc.gros.ui.common.SecondaryButton
import io.github.mstajanc.gros.ui.common.grosViewModel
import io.github.mstajanc.gros.ui.theme.GrosShapes
import io.github.mstajanc.gros.ui.theme.GrosTheme
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class BudgetsState(
    val loaded: Boolean = false,
    val today: LocalDate,
    val lines: List<BudgetLine> = emptyList(),
    val summary: BudgetSummary = BudgetSummary(0, 0),
)

/** Rozpočty aktuálneho mesiaca: čerpanie zo všetkých účtov, bez prevodov, vrátane podkategórií. */
class BudgetsViewModel(container: AppContainer) : ViewModel() {

    private val today = container.today()

    val state: StateFlow<BudgetsState> = combine(
        container.budgetRepository.observeBudgets(),
        container.categoryRepository.observeCategories(),
        container.reportRepository.observeExpenseByCategory(Period.Month(YearMonth.from(today))),
    ) { budgets, categories, expenses ->
        val lines = BudgetCalculator.lines(budgets, categories, expenses)
        BudgetsState(loaded = true, today = today, lines = lines, summary = BudgetCalculator.summary(lines))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsState(today = today))
}

/** Obsah prepínača Plán → Rozpočty (mockup 08). */
@Composable
fun ColumnScope.BudgetsTab(onAdd: () -> Unit, onEdit: (Long) -> Unit) {
    val viewModel = grosViewModel { container, _ -> BudgetsViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.loaded) return

    if (state.lines.isNotEmpty()) SummaryCard(state)
    if (state.lines.isEmpty()) {
        Text(
            stringResource(R.string.budget_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        )
    }
    state.lines.forEach { line -> BudgetCard(line, onClick = { onEdit(line.budget.id) }) }
    SecondaryButton(
        stringResource(R.string.budget_add),
        onClick = onAdd,
        modifier = Modifier.fillMaxWidth(),
        contentColor = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun daysLeftText(today: LocalDate): String {
    val days = BudgetCalculator.daysLeft(today)
    if (days == 0) return stringResource(R.string.budget_last_day)
    return stringResource(
        when (SlovakText.pluralForm(days)) {
            PluralForm.ONE -> R.string.budget_days_one
            PluralForm.FEW -> R.string.budget_days_few
            PluralForm.MANY -> R.string.budget_days_many
        },
        days,
    )
}

/** „November · ešte 15 dní“, zostatok zo súčtu limitov a pruh; pri prečerpaní oranžový text a pruh. */
@Composable
private fun SummaryCard(state: BudgetsState) {
    val summary = state.summary
    val warning = GrosTheme.colors.warning
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, GrosShapes.cardLarge)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val month = SlovakDates.monthName(YearMonth.from(state.today)).replaceFirstChar { it.uppercaseChar() }
        Text(
            stringResource(R.string.budget_summary_period, month, daysLeftText(state.today)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (summary.isOver) {
            Text(
                stringResource(R.string.budget_over, MoneyFormatter.format(-summary.remainingCents)),
                style = MaterialTheme.typography.headlineMedium,
                color = warning,
            )
        } else {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AmountText(MoneyFormatter.format(summary.remainingCents), style = MaterialTheme.typography.displaySmall)
                Text(
                    stringResource(R.string.budget_remaining_of, MoneyFormatter.format(summary.limitCents)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        ProgressBar(
            fraction = summary.progress,
            color = if (summary.isOver) warning else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * „386,20 / 450,00 €“, pruh a „Zostáva X €“; nad limitom oranžové čerpanie, pruh aj text
 * „Prekročené o X €“. Stav sa nikdy neodlišuje len farbou (A6).
 */
@Composable
private fun BudgetCard(line: BudgetLine, onClick: () -> Unit) {
    val warning = GrosTheme.colors.warning
    val spent = MoneyFormatter.format(line.spentCents).removeSuffix(" €")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GrosShapes.cardMedium)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(line.category.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            val amounts = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        fontWeight = FontWeight.SemiBold,
                        color = if (line.isOver) warning else MaterialTheme.colorScheme.onSurface,
                    ),
                ) { append(spent) }
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    append(" / ")
                    append(MoneyFormatter.format(line.limitCents))
                }
            }
            Text(
                amounts,
                style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                maxLines = 1,
                softWrap = false,
            )
        }
        ProgressBar(fraction = line.progress, color = if (line.isOver) warning else MaterialTheme.colorScheme.primary)
        if (line.isOver) {
            Text(
                stringResource(R.string.budget_over, MoneyFormatter.format(-line.remainingCents)),
                style = MaterialTheme.typography.labelLarge,
                color = warning,
            )
        } else {
            Text(
                stringResource(R.string.budget_remaining, MoneyFormatter.format(line.remainingCents)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
