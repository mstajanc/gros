package io.github.mstajanc.gros.ui.transactions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.mstajanc.gros.AppContainer
import io.github.mstajanc.gros.R
import io.github.mstajanc.gros.domain.DayGroup
import io.github.mstajanc.gros.domain.ListSummary
import io.github.mstajanc.gros.domain.Period
import io.github.mstajanc.gros.domain.TransactionFilter
import io.github.mstajanc.gros.domain.TransactionList
import io.github.mstajanc.gros.domain.model.Transaction
import io.github.mstajanc.gros.ui.common.Lookup
import io.github.mstajanc.gros.ui.common.SnackbarEvent
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TransactionsState(
    val loaded: Boolean = false,
    val filter: TransactionFilter,
    val searchActive: Boolean = false,
    val today: LocalDate,
    val lookup: Lookup = Lookup(),
    val transactions: List<Transaction> = emptyList(),
    val groups: List<DayGroup> = emptyList(),
    val summary: ListSummary = ListSummary(0, 0, 0),
    /** Aktuálny zostatok účtu, keď je zvolený filter jedného účtu. */
    val accountBalanceCents: Long? = null,
)

private data class Controls(val filter: TransactionFilter, val searchActive: Boolean, val periodBeforeSearch: Period?)

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionsViewModel(private val container: AppContainer, handle: SavedStateHandle) : ViewModel() {

    private val initialAccountId: Long? = handle.get<Long>(ARG_ACCOUNT_ID)?.takeIf { it > 0 }

    private val controls = MutableStateFlow(
        Controls(
            filter = TransactionFilter(
                // Predvolený je aktuálny mesiac; ťuknutie na účet otvára Celé obdobie (rozhodnutie A4).
                period = if (initialAccountId != null) Period.All else Period.Month(YearMonth.from(container.today())),
                accountId = initialAccountId,
            ),
            searchActive = false,
            periodBeforeSearch = null,
        ),
    )

    private val categories = container.categoryRepository.observeCategories()

    private val list = combine(controls, categories) { controls, categories ->
        val ids = controls.filter.categoryId?.let { TransactionFilter.categoryIds(it, categories) }
        Triple(controls.filter, ids, categories)
    }.flatMapLatest { (filter, ids, categories) ->
        container.transactionRepository.observeFiltered(filter, ids, categories)
    }

    val state: StateFlow<TransactionsState> = combine(
        controls,
        list,
        container.accountRepository.observeBalances(),
        categories,
    ) { controls, transactions, balances, categories ->
        TransactionsState(
            loaded = true,
            filter = controls.filter,
            searchActive = controls.searchActive,
            today = container.today(),
            lookup = Lookup.of(balances.map { it.account }, categories),
            transactions = transactions,
            groups = TransactionList.groupByDay(transactions),
            summary = TransactionList.summarize(transactions),
            accountBalanceCents = balances.firstOrNull { it.account.id == controls.filter.accountId }?.balanceCents,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        TransactionsState(filter = controls.value.filter, today = container.today()),
    )

    private fun updateFilter(transform: (TransactionFilter) -> TransactionFilter) =
        controls.update { it.copy(filter = transform(it.filter)) }

    fun setPeriod(period: Period) = updateFilter { it.copy(period = period) }
    fun previousPeriod() = updateFilter { it.copy(period = it.period.previous()) }
    fun nextPeriod() = updateFilter { it.copy(period = it.period.next()) }
    fun setAccount(accountId: Long?) = updateFilter { it.copy(accountId = accountId) }
    fun setCategory(categoryId: Long?) = updateFilter { it.copy(categoryId = categoryId) }
    fun setQuery(query: String) = updateFilter { it.copy(query = query) }

    /** Hľadanie prepne obdobie na Celé obdobie; po zatvorení sa vráti pôvodné. */
    fun openSearch() = controls.update {
        if (it.searchActive) it else it.copy(
            searchActive = true,
            periodBeforeSearch = it.filter.period,
            filter = it.filter.copy(period = Period.All),
        )
    }

    fun closeSearch() = controls.update {
        it.copy(
            searchActive = false,
            periodBeforeSearch = null,
            filter = it.filter.copy(query = "", period = it.periodBeforeSearch ?: it.filter.period),
        )
    }

    /** Zmazanie sa vykoná hneď; Späť vloží transakciu späť s pôvodným `id`. */
    fun delete(transaction: Transaction) {
        viewModelScope.launch {
            val deleted = container.transactionRepository.delete(transaction.id) ?: return@launch
            container.snackbarBus.show(
                SnackbarEvent(R.string.snackbar_deleted) { container.transactionRepository.restore(deleted) },
            )
        }
    }

    companion object {
        const val ARG_ACCOUNT_ID = "accountId"
    }
}
