package io.github.mstajanc.gros.data.repository

import androidx.room3.withWriteTransaction
import io.github.mstajanc.gros.data.db.AccountEntity
import io.github.mstajanc.gros.data.db.GrosDatabase
import io.github.mstajanc.gros.data.db.toDomain
import io.github.mstajanc.gros.domain.AccountBalance
import io.github.mstajanc.gros.domain.BalanceCalculator
import io.github.mstajanc.gros.domain.TotalBalance
import io.github.mstajanc.gros.domain.model.Account
import io.github.mstajanc.gros.domain.model.AccountType
import io.github.mstajanc.gros.domain.rules.AccountRules
import io.github.mstajanc.gros.domain.rules.InvariantViolationException
import io.github.mstajanc.gros.domain.rules.Violation
import io.github.mstajanc.gros.domain.rules.throwIfViolated
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/**
 * Účty a ich zostatky. Zostatok sa nikde neukladá: počíta sa z počiatočného stavu a transakcií.
 * Každá zmena prebehne v jednej DB transakcii a pri porušení invariantu vyhodí
 * [InvariantViolationException] bez zmeny dát.
 */
class AccountRepository(private val db: GrosDatabase) {

    private val accounts = db.accountDao()
    private val transactions = db.transactionDao()
    private val rules = db.recurringRuleDao()

    fun observeAccounts(): Flow<List<Account>> = accounts.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeAccountCount(): Flow<Int> = accounts.observeCount()

    /** Zostatky všetkých účtov (aj archivovaných) v poradí `sortOrder`. */
    fun observeBalances(): Flow<List<AccountBalance>> =
        combine(accounts.observeAll(), transactions.observeAccountFlows()) { accountRows, flowRows ->
            BalanceCalculator.balances(
                accounts = accountRows.map { it.toDomain() },
                flows = flowRows.associate { it.accountId to it.toDomain() },
            )
        }

    /** „Spolu“ a „N z M účtov“. */
    fun observeTotal(): Flow<TotalBalance> = observeBalances().map(BalanceCalculator::total)

    suspend fun get(id: Long): Account? = accounts.getById(id)?.toDomain()

    suspend fun create(
        name: String,
        type: AccountType,
        openingBalanceCents: Long,
        includeInTotal: Boolean = true,
    ): Long = db.withWriteTransaction {
        AccountRules.validateName(name).throwIfViolated()
        AccountRules.validateEnteredBalance(openingBalanceCents).throwIfViolated()
        accounts.insert(
            AccountEntity(
                name = name.trim(),
                type = type,
                openingBalanceCents = openingBalanceCents,
                includeInTotal = includeInTotal,
                archived = false,
                sortOrder = accounts.maxSortOrder() + 1,
            ),
        )
    }

    /** Úprava názvu, typu a prepínača „V súčte“. Počiatočný stav mení len [correctBalance]. */
    suspend fun update(id: Long, name: String, type: AccountType, includeInTotal: Boolean) {
        db.withWriteTransaction {
            val existing = require(id)
            AccountRules.validateName(name).throwIfViolated()
            accounts.update(existing.copy(name = name.trim(), type = type, includeInTotal = includeInTotal))
        }
    }

    /**
     * Oprava zostatku: posunie počiatočný stav tak, aby zostatok vyšiel na [actualBalanceCents].
     * Nevzniká transakcia, takže reporty ani rozpočty sa nemenia.
     */
    suspend fun correctBalance(id: Long, actualBalanceCents: Long) {
        db.withWriteTransaction {
            val existing = require(id)
            AccountRules.validateEnteredBalance(actualBalanceCents).throwIfViolated()
            val flows = transactions.getAccountFlows().firstOrNull { it.accountId == id }?.toDomain()
            val currentBalance = BalanceCalculator.balances(
                accounts = listOf(existing.toDomain()),
                flows = if (flows == null) emptyMap() else mapOf(id to flows),
            ).single().balanceCents
            val newOpening = BalanceCalculator.openingBalanceForCorrection(
                currentOpeningCents = existing.openingBalanceCents,
                currentBalanceCents = currentBalance,
                actualBalanceCents = actualBalanceCents,
            )
            accounts.update(existing.copy(openingBalanceCents = newOpening))
        }
    }

    /** Archivuje účet; posledný aktívny účet sa archivovať nedá. */
    suspend fun archive(id: Long) {
        db.withWriteTransaction {
            val existing = require(id)
            if (existing.archived) return@withWriteTransaction
            AccountRules.canArchive(existing.toDomain(), accounts.countActive()).throwIfViolated()
            accounts.update(existing.copy(archived = true))
        }
    }

    suspend fun restore(id: Long) {
        db.withWriteTransaction {
            val existing = require(id)
            if (existing.archived) accounts.update(existing.copy(archived = false))
        }
    }

    /** Natvrdo zmaže účet, na ktorý neodkazuje žiadna transakcia ani pravidlo. */
    suspend fun delete(id: Long) {
        db.withWriteTransaction {
            val existing = require(id)
            val referenced = transactions.countByAccount(id) > 0 || rules.countByAccount(id) > 0
            AccountRules.canDelete(existing.toDomain(), accounts.countActive(), referenced).throwIfViolated()
            accounts.deleteById(id)
        }
    }

    /** Nastaví poradie účtov podľa zoznamu identifikátorov. */
    suspend fun reorder(orderedIds: List<Long>) {
        db.withWriteTransaction {
            orderedIds.forEachIndexed { index, id -> accounts.setSortOrder(id, index) }
        }
    }

    private suspend fun require(id: Long): AccountEntity =
        accounts.getById(id) ?: throw InvariantViolationException(Violation.NOT_FOUND)
}
