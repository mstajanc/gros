package io.github.mstajanc.gros.domain.rules

import io.github.mstajanc.gros.domain.Money
import io.github.mstajanc.gros.domain.model.Account
import io.github.mstajanc.gros.domain.model.Category
import io.github.mstajanc.gros.domain.model.CategoryKind
import io.github.mstajanc.gros.domain.model.RecurringRuleDraft
import io.github.mstajanc.gros.domain.model.TransactionDraft
import io.github.mstajanc.gros.domain.model.TransactionType

/** To, čo majú transakcia a opakované pravidlo spoločné: typ, suma, účty a kategória. */
data class Posting(
    val type: TransactionType,
    val amountCents: Long,
    val accountId: Long,
    val toAccountId: Long?,
    val categoryId: Long?,
)

/** Záznamy, na ktoré zápis odkazuje, tak ako ich našiel repozitár (`null` = neexistuje). */
data class PostingRefs(
    val account: Account?,
    val toAccount: Account?,
    val category: Category?,
)

fun TransactionDraft.toPosting() = Posting(type, amountCents, accountId, toAccountId, categoryId)

fun RecurringRuleDraft.toPosting() = Posting(type, amountCents, accountId, toAccountId, categoryId)

object PostingRules {

    /**
     * - suma je > 0 a najviac [Money.MAX_CENTS],
     * - prevod má cieľový účet odlišný od zdrojového a nemá kategóriu,
     * - výdavok a príjem majú kategóriu správneho druhu (hlavnú alebo podkategóriu) a nemajú cieľový účet.
     *
     * Archivovaný účet či kategória tu nevadia: ostávajú na záznamoch, ktoré ich už používajú.
     */
    fun validate(posting: Posting, refs: PostingRefs): Violation? {
        if (posting.amountCents <= 0) return Violation.AMOUNT_NOT_POSITIVE
        if (posting.amountCents > Money.MAX_CENTS) return Violation.AMOUNT_TOO_LARGE
        if (refs.account == null) return Violation.ACCOUNT_NOT_FOUND

        return when (posting.type) {
            TransactionType.TRANSFER -> when {
                posting.toAccountId == null -> Violation.TRANSFER_TARGET_MISSING
                posting.toAccountId == posting.accountId -> Violation.TRANSFER_SAME_ACCOUNT
                refs.toAccount == null -> Violation.TRANSFER_TARGET_NOT_FOUND
                posting.categoryId != null -> Violation.TRANSFER_WITH_CATEGORY
                else -> null
            }

            TransactionType.EXPENSE -> validateCategorized(posting, refs, CategoryKind.EXPENSE)
            TransactionType.INCOME -> validateCategorized(posting, refs, CategoryKind.INCOME)
        }
    }

    private fun validateCategorized(posting: Posting, refs: PostingRefs, expectedKind: CategoryKind): Violation? =
        when {
            posting.toAccountId != null -> Violation.TARGET_ACCOUNT_NOT_ALLOWED
            posting.categoryId == null -> Violation.CATEGORY_MISSING
            refs.category == null -> Violation.CATEGORY_NOT_FOUND
            refs.category.kind != expectedKind -> Violation.CATEGORY_KIND_MISMATCH
            else -> null
        }
}

object RecurringRuleRules {

    /**
     * Pravidlo sa dá uložiť len s názvom, sumou > 0, aktívnym účtom, kategóriou správneho druhu,
     * pri prevode s dvoma rôznymi účtami a s intervalom ≥ 1.
     */
    fun validate(draft: RecurringRuleDraft, refs: PostingRefs): Violation? {
        if (draft.name.isBlank()) return Violation.NAME_BLANK
        PostingRules.validate(draft.toPosting(), refs)?.let { return it }
        if (draft.interval < 1) return Violation.INTERVAL_NOT_POSITIVE
        if (refs.account?.archived == true || refs.toAccount?.archived == true) return Violation.ACCOUNT_ARCHIVED
        return null
    }
}
