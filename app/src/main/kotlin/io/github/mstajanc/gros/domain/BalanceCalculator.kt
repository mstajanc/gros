package io.github.mstajanc.gros.domain

import io.github.mstajanc.gros.domain.model.Account
import io.github.mstajanc.gros.domain.model.Transaction
import io.github.mstajanc.gros.domain.model.TransactionType

/** Súčty pohybov jedného účtu; všetky hodnoty sú nezáporné. */
data class AccountFlows(
    val incomeCents: Long = 0,
    val expenseCents: Long = 0,
    val transferInCents: Long = 0,
    val transferOutCents: Long = 0,
)

data class AccountBalance(val account: Account, val balanceCents: Long)

/** „Spolu“ na Prehľade: súčet a popis „N z M účtov“. */
data class TotalBalance(
    val totalCents: Long,
    /** N: nearchivované účty zahrnuté v súčte. */
    val includedCount: Int,
    /** M: všetky nearchivované účty. */
    val activeCount: Int,
)

/**
 * Zostatky sa nikdy neukladajú, vždy sa počítajú z počiatočného stavu a transakcií.
 * Záporný zostatok je povolený.
 */
object BalanceCalculator {

    /** Zostatok = počiatočný stav + príjmy − výdavky + prevody na účet − prevody z účtu. */
    fun balance(openingBalanceCents: Long, flows: AccountFlows): Long =
        openingBalanceCents + flows.incomeCents - flows.expenseCents +
            flows.transferInCents - flows.transferOutCents

    /** Rozdelí transakcie na pohyby účtov. Prevod nie je príjem ani výdavok, mení len oba zostatky. */
    fun flowsByAccount(transactions: Iterable<Transaction>): Map<Long, AccountFlows> {
        val result = HashMap<Long, AccountFlows>()
        fun update(accountId: Long, change: (AccountFlows) -> AccountFlows) {
            result[accountId] = change(result[accountId] ?: AccountFlows())
        }
        for (tx in transactions) {
            when (tx.type) {
                TransactionType.INCOME ->
                    update(tx.accountId) { it.copy(incomeCents = it.incomeCents + tx.amountCents) }

                TransactionType.EXPENSE ->
                    update(tx.accountId) { it.copy(expenseCents = it.expenseCents + tx.amountCents) }

                TransactionType.TRANSFER -> {
                    update(tx.accountId) { it.copy(transferOutCents = it.transferOutCents + tx.amountCents) }
                    tx.toAccountId?.let { target ->
                        update(target) { it.copy(transferInCents = it.transferInCents + tx.amountCents) }
                    }
                }
            }
        }
        return result
    }

    /** Zostatky účtov v poradí, v akom prišli. */
    fun balances(accounts: List<Account>, flows: Map<Long, AccountFlows>): List<AccountBalance> =
        accounts.map { account ->
            AccountBalance(account, balance(account.openingBalanceCents, flows[account.id] ?: AccountFlows()))
        }

    /** „Spolu“ = súčet zostatkov nearchivovaných účtov s `includeInTotal`. */
    fun total(balances: List<AccountBalance>): TotalBalance {
        val active = balances.filter { !it.account.archived }
        val included = active.filter { it.account.includeInTotal }
        return TotalBalance(
            totalCents = included.sumOf { it.balanceCents },
            includedCount = included.size,
            activeCount = active.size,
        )
    }

    /**
     * Oprava zostatku: používateľ zadá skutočný stav a počiatočný stav sa posunie o rozdiel.
     * Nevzniká transakcia, takže reporty ani rozpočty sa nemenia.
     */
    fun openingBalanceForCorrection(
        currentOpeningCents: Long,
        currentBalanceCents: Long,
        actualBalanceCents: Long,
    ): Long = currentOpeningCents + (actualBalanceCents - currentBalanceCents)
}
