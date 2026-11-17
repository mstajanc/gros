package io.github.mstajanc.gros.data.repository

import androidx.room3.withWriteTransaction
import io.github.mstajanc.gros.data.db.GrosDatabase
import io.github.mstajanc.gros.data.db.TransactionEntity
import io.github.mstajanc.gros.data.db.toDomain
import io.github.mstajanc.gros.data.db.toEntity
import io.github.mstajanc.gros.domain.NoteNormalizer
import io.github.mstajanc.gros.domain.NoteSuggestions
import io.github.mstajanc.gros.domain.TransactionFilter
import io.github.mstajanc.gros.domain.model.Category
import io.github.mstajanc.gros.domain.model.Transaction
import io.github.mstajanc.gros.domain.model.TransactionDraft
import io.github.mstajanc.gros.domain.rules.InvariantViolationException
import io.github.mstajanc.gros.domain.rules.Posting
import io.github.mstajanc.gros.domain.rules.PostingRefs
import io.github.mstajanc.gros.domain.rules.PostingRules
import io.github.mstajanc.gros.domain.rules.Violation
import io.github.mstajanc.gros.domain.rules.throwIfViolated
import io.github.mstajanc.gros.domain.rules.toPosting
import java.time.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Načíta záznamy, na ktoré zápis odkazuje, aby ich mohli overiť čisté pravidlá v `domain`. */
internal suspend fun GrosDatabase.loadPostingRefs(posting: Posting): PostingRefs = PostingRefs(
    account = accountDao().getById(posting.accountId)?.toDomain(),
    toAccount = posting.toAccountId?.let { accountDao().getById(it)?.toDomain() },
    category = posting.categoryId?.let { categoryDao().getById(it)?.toDomain() },
)

/**
 * Výdavky, príjmy a prevody. Stráži invarianty zápisu (suma > 0, kategória správneho druhu,
 * prevod medzi dvoma rôznymi účtami bez kategórie) a dopĺňa odvodené polia `noteSearch`,
 * `createdAt` a `updatedAt`.
 *
 * Zákaz budúceho dátumu je pravidlo formulára, nie invariant (rozhodnutie A9).
 */
class TransactionRepository(private val db: GrosDatabase, private val clock: Clock) {

    private val transactions = db.transactionDao()
    private val rules = db.recurringRuleDao()

    suspend fun get(id: Long): Transaction? = transactions.getById(id)?.toDomain()

    /**
     * Zoznam od najnovšej transakcie. [categoryIds] je už rozvinutý výber kategórie
     * (hlavná + jej podkategórie), `null` = bez filtra kategórie. Hľadaný text sa porovná s poznámkou
     * a s názvami kategórií [categories] (rozhodnutie C1).
     */
    fun observeFiltered(
        filter: TransactionFilter,
        categoryIds: List<Long>?,
        categories: List<Category> = emptyList(),
    ): Flow<List<Transaction>> =
        transactions.observeFiltered(
            from = filter.period.from,
            to = filter.period.to,
            accountId = filter.accountId,
            filterCategories = categoryIds != null,
            categoryIds = categoryIds.orEmpty(),
            notePattern = NoteNormalizer.containsPattern(filter.query),
            queryCategoryIds = TransactionFilter.categoryIdsMatching(filter.query, categories),
        ).map { list -> list.map { it.toDomain() } }

    fun observeRecent(limit: Int): Flow<List<Transaction>> =
        transactions.observeRecent(limit).map { list -> list.map { it.toDomain() } }

    /** Návrhy poznámky: od 2 znakov najviac 5 predošlých poznámok obsahujúcich zadaný text. */
    suspend fun suggestNotes(input: String): List<String> {
        val pattern = NoteSuggestions.pattern(input) ?: return emptyList()
        return transactions.suggestNotes(pattern, NoteSuggestions.LIMIT)
    }

    /** [recurringRuleId] nastavuje len generátor opakovaných platieb. */
    suspend fun create(draft: TransactionDraft, recurringRuleId: Long? = null): Long = db.withWriteTransaction {
        validate(draft.toPosting())
        val now = clock.millis()
        val note = NoteNormalizer.clean(draft.note)
        transactions.insert(
            TransactionEntity(
                type = draft.type,
                amountCents = draft.amountCents,
                date = draft.date,
                accountId = draft.accountId,
                toAccountId = draft.toAccountId,
                categoryId = draft.categoryId,
                note = note,
                noteSearch = NoteNormalizer.toSearch(note),
                recurringRuleId = recurringRuleId,
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    /** Upraviť sa dajú všetky polia vrátane typu; `createdAt` a väzba na pravidlo ostávajú. */
    suspend fun update(id: Long, draft: TransactionDraft) {
        db.withWriteTransaction {
            val existing = transactions.getById(id) ?: throw InvariantViolationException(Violation.NOT_FOUND)
            validate(draft.toPosting())
            val note = NoteNormalizer.clean(draft.note)
            transactions.update(
                existing.copy(
                    type = draft.type,
                    amountCents = draft.amountCents,
                    date = draft.date,
                    accountId = draft.accountId,
                    toAccountId = draft.toAccountId,
                    categoryId = draft.categoryId,
                    note = note,
                    noteSearch = NoteNormalizer.toSearch(note),
                    updatedAt = clock.millis(),
                ),
            )
        }
    }

    /** Zmaže transakciu hneď a vráti jej podobu pre akciu Späť ([restore]); `null`, ak neexistuje. */
    suspend fun delete(id: Long): Transaction? = db.withWriteTransaction {
        val existing = transactions.getById(id) ?: return@withWriteTransaction null
        transactions.deleteById(id)
        existing.toDomain()
    }

    /**
     * Akcia Späť: vráti transakciu do podoby [snapshot] s pôvodným `id` a časmi –
     * zmazanú vloží späť, upravenej vráti pôvodné hodnoty.
     */
    suspend fun restore(snapshot: Transaction) {
        db.withWriteTransaction {
            validate(
                Posting(
                    type = snapshot.type,
                    amountCents = snapshot.amountCents,
                    accountId = snapshot.accountId,
                    toAccountId = snapshot.toAccountId,
                    categoryId = snapshot.categoryId,
                ),
            )
            val note = NoteNormalizer.clean(snapshot.note)
            // Pravidlo mohlo byť medzitým zmazané; transakcia potom ostáva bez väzby.
            val ruleId = snapshot.recurringRuleId?.takeIf { rules.getById(it) != null }
            val entity = snapshot.copy(
                note = note,
                noteSearch = NoteNormalizer.toSearch(note),
                recurringRuleId = ruleId,
            ).toEntity()
            if (transactions.getById(snapshot.id) == null) transactions.insert(entity) else transactions.update(entity)
        }
    }

    private suspend fun validate(posting: Posting) {
        PostingRules.validate(posting, db.loadPostingRefs(posting)).throwIfViolated()
    }
}
