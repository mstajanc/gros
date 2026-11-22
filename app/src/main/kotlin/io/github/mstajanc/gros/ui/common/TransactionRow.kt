package io.github.mstajanc.gros.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.mstajanc.gros.R
import io.github.mstajanc.gros.domain.MoneyFormatter
import io.github.mstajanc.gros.domain.RelativeDay
import io.github.mstajanc.gros.domain.SlovakDates
import io.github.mstajanc.gros.domain.TransactionList
import io.github.mstajanc.gros.domain.model.Account
import io.github.mstajanc.gros.domain.model.Category
import io.github.mstajanc.gros.domain.model.Transaction
import io.github.mstajanc.gros.domain.model.TransactionType
import io.github.mstajanc.gros.ui.theme.CategoryColor
import io.github.mstajanc.gros.ui.theme.GrosTheme
import java.time.LocalDate

/** Názvy účtov a kategórií potrebné na vykreslenie transakcií (vrátane archivovaných). */
@Immutable
data class Lookup(
    val accounts: Map<Long, Account> = emptyMap(),
    val categories: Map<Long, Category> = emptyMap(),
) {
    fun accountName(id: Long?): String = accounts[id]?.name.orEmpty()

    /** Hlavná kategória danej kategórie; určuje písmeno a farbu ikony. */
    fun mainCategory(id: Long?): Category? {
        val category = categories[id] ?: return null
        return category.parentId?.let { categories[it] } ?: category
    }

    /** „Auto · Tankovanie“ pre podkategóriu, „Auto“ pre hlavnú kategóriu. */
    fun categoryTitle(id: Long?): String {
        val category = categories[id] ?: return ""
        val parent = category.parentId?.let { categories[it] }
        return if (parent == null) category.name else "${parent.name} · ${category.name}"
    }

    companion object {
        fun of(accounts: List<Account>, categories: List<Category>) =
            Lookup(accounts.associateBy { it.id }, categories.associateBy { it.id })
    }
}

enum class RowStyle {
    /** Prehľad: „Dnes · Bežný účet“. */
    OVERVIEW,

    /** Zoznam po dňoch: „Bežný účet · poznámka · opakovaná“. */
    LIST,

    /** Hľadanie: titulkom je poznámka, pod ňou „8. 11. 2026 · Ostatné · Bežný účet“. */
    SEARCH,
}

/** „Dnes“, „Včera“ alebo krátky dátum. */
@Composable
fun dayLabel(date: LocalDate, today: LocalDate): String = when (SlovakDates.relativeDay(date, today)) {
    RelativeDay.TODAY -> stringResource(R.string.day_today)
    RelativeDay.YESTERDAY -> stringResource(R.string.day_yesterday)
    RelativeDay.OTHER -> SlovakDates.shortDate(date, today)
}

@Composable
fun categoryColor(lookup: Lookup, categoryId: Long?): CategoryColor =
    GrosTheme.colors.category(lookup.mainCategory(categoryId)?.color ?: -1)

@Composable
fun TransactionRow(
    transaction: Transaction,
    lookup: Lookup,
    style: RowStyle,
    today: LocalDate,
    accountFilterId: Long?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isTransfer = transaction.type == TransactionType.TRANSFER
    val categoryTitle = if (isTransfer) stringResource(R.string.transfer) else lookup.categoryTitle(transaction.categoryId)
    val accountText = if (isTransfer) {
        "${lookup.accountName(transaction.accountId)} → ${lookup.accountName(transaction.toAccountId)}"
    } else {
        lookup.accountName(transaction.accountId)
    }
    val separator = " · "

    val title = if (style == RowStyle.SEARCH) transaction.note ?: categoryTitle else categoryTitle
    val subtitle = when (style) {
        RowStyle.OVERVIEW -> dayLabel(transaction.date, today) + separator + accountText
        RowStyle.LIST -> listOfNotNull(
            accountText,
            transaction.note,
            if (transaction.recurringRuleId != null) stringResource(R.string.label_recurring) else null,
        ).joinToString(separator)

        RowStyle.SEARCH -> listOf(SlovakDates.fullDate(transaction.date), categoryTitle, accountText).joinToString(separator)
    }

    val amount = TransactionList.displayAmount(transaction, accountFilterId)
    val amountColor = when (transaction.type) {
        TransactionType.INCOME -> MaterialTheme.colorScheme.primary
        TransactionType.TRANSFER -> GrosTheme.colors.transfer
        TransactionType.EXPENSE -> GrosTheme.colors.expense
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = ScreenPadding, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isTransfer) {
            TransferBadge()
        } else {
            CategoryBadge(
                name = lookup.mainCategory(transaction.categoryId)?.name.orEmpty(),
                color = categoryColor(lookup, transaction.categoryId),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AmountText(MoneyFormatter.format(amount.cents, amount.sign), color = amountColor)
    }
}
