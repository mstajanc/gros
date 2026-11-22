package io.github.mstajanc.gros.domain.rules

import io.github.mstajanc.gros.domain.Money
import io.github.mstajanc.gros.domain.model.Account
import io.github.mstajanc.gros.domain.model.Category
import io.github.mstajanc.gros.domain.model.CategoryKind

object AccountRules {

    /** Najdlhší povolený názov kategórie po orezaní medzier (G1). */
    const val MAX_NAME_LENGTH = 40

    /**
     * Názov je povinný, najviac [MAX_NAME_LENGTH] znakov a medzi súrodencami jedinečný bez ohľadu
     * na veľkosť písmen (G1). Kategória [selfId] sa pri premenovaní s vlastným názvom neporovnáva.
     */
    fun validateName(name: String, siblings: List<Category> = emptyList(), selfId: Long = 0): Violation? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> Violation.NAME_BLANK
            trimmed.length > MAX_NAME_LENGTH -> Violation.NAME_TOO_LONG
            siblings.any { it.id != selfId && it.name.trim().equals(trimmed, ignoreCase = true) } -> Violation.NAME_DUPLICATE
            else -> null
        }
    }

    /** Súrodenci: podkategórie toho istého rodiča, alebo hlavné kategórie rovnakého druhu. */
    fun siblings(all: List<Category>, parentId: Long?, kind: CategoryKind): List<Category> =
        all.filter { it.parentId == parentId && (parentId != null || it.kind == kind) }

    /**
     * Presun pod iného rodiča ([newParent]) alebo povýšenie na hlavnú (`newParent = null`).
     * Pod iného rodiča smie ísť podkategória a hlavná kategória bez podkategórií, a to len pod
     * aktívnu hlavnú kategóriu rovnakého druhu (G2).
     */
    fun canMove(category: Category, newParent: Category?, hasSubcategories: Boolean): Violation? = when {
        newParent == null -> null
        newParent.id == category.id -> Violation.SAME_CATEGORY
        !newParent.isMain -> Violation.PARENT_NOT_MAIN
        newParent.kind != category.kind -> Violation.PARENT_KIND_MISMATCH
        newParent.archived -> Violation.TARGET_ARCHIVED
        category.isMain && hasSubcategories -> Violation.CATEGORY_HAS_SUBCATEGORIES
        else -> null
    }

    /**
     * Zlúčenie [source] do [target] rovnakého druhu. Cieľ nesmie byť archivovaný (ani jeho rodič)
     * a hlavná kategória s podkategóriami sa zlučuje len do inej hlavnej kategórie (G2).
     */
    fun canMerge(source: Category, target: Category, targetParent: Category?, sourceHasSubcategories: Boolean): Violation? =
        when {
            source.id == target.id -> Violation.SAME_CATEGORY
            source.kind != target.kind -> Violation.CATEGORY_KIND_MISMATCH
            target.archived || targetParent?.archived == true -> Violation.TARGET_ARCHIVED
            target.parentId == source.id -> Violation.MERGE_INTO_OWN_SUBCATEGORY
            sourceHasSubcategories && !target.isMain -> Violation.MERGE_TARGET_NOT_MAIN
            else -> null
        }

    /** Počiatočný stav a oprava zostatku môžu byť záporné, v absolútnej hodnote najviac [Money.MAX_CENTS]. */
    fun validateEnteredBalance(cents: Long): Violation? =
        if (cents > Money.MAX_CENTS || cents < -Money.MAX_CENTS) Violation.AMOUNT_TOO_LARGE else null

    /** Vždy musí ostať aspoň jeden aktívny účet. */
    fun canArchive(account: Account, activeAccountCount: Int): Violation? =
        if (!account.archived && activeAccountCount <= 1) Violation.LAST_ACTIVE_ACCOUNT else null

    /** Natvrdo sa dá zmazať len účet, na ktorý neodkazuje žiadna transakcia ani pravidlo. */
    fun canDelete(account: Account, activeAccountCount: Int, referenced: Boolean): Violation? = when {
        referenced -> Violation.ACCOUNT_IN_USE
        !account.archived && activeAccountCount <= 1 -> Violation.LAST_ACTIVE_ACCOUNT
        else -> null
    }
}

object CategoryRules {

    /** Počet farieb v palete kategórií; `Category.color` je index 0 až [COLOR_COUNT] − 1. */
    const val COLOR_COUNT = 6

    /**
     * Nová kategória: názov, farba z palety (pri hlavnej) a najviac dve úrovne –
     * rodič podkategórie musí existovať, byť hlavnou kategóriou a mať rovnaký druh.
     */
    fun validateNew(name: String, kind: CategoryKind, color: Int, parentId: Long?, parent: Category?): Violation? =
        when {
            name.isBlank() -> Violation.NAME_BLANK
            parentId == null -> validateColor(color)
            parent == null -> Violation.PARENT_NOT_FOUND
            !parent.isMain -> Violation.PARENT_NOT_MAIN
            parent.kind != kind -> Violation.PARENT_KIND_MISMATCH
            else -> null
        }

    /** Najdlhší povolený názov kategórie po orezaní medzier (G1). */
    const val MAX_NAME_LENGTH = 40

    /**
     * Názov je povinný, najviac [MAX_NAME_LENGTH] znakov a medzi súrodencami jedinečný bez ohľadu
     * na veľkosť písmen (G1). Kategória [selfId] sa pri premenovaní s vlastným názvom neporovnáva.
     */
    fun validateName(name: String, siblings: List<Category> = emptyList(), selfId: Long = 0): Violation? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> Violation.NAME_BLANK
            trimmed.length > MAX_NAME_LENGTH -> Violation.NAME_TOO_LONG
            siblings.any { it.id != selfId && it.name.trim().equals(trimmed, ignoreCase = true) } -> Violation.NAME_DUPLICATE
            else -> null
        }
    }

    /** Súrodenci: podkategórie toho istého rodiča, alebo hlavné kategórie rovnakého druhu. */
    fun siblings(all: List<Category>, parentId: Long?, kind: CategoryKind): List<Category> =
        all.filter { it.parentId == parentId && (parentId != null || it.kind == kind) }

    /**
     * Presun pod iného rodiča ([newParent]) alebo povýšenie na hlavnú (`newParent = null`).
     * Pod iného rodiča smie ísť podkategória a hlavná kategória bez podkategórií, a to len pod
     * aktívnu hlavnú kategóriu rovnakého druhu (G2).
     */
    fun canMove(category: Category, newParent: Category?, hasSubcategories: Boolean): Violation? = when {
        newParent == null -> null
        newParent.id == category.id -> Violation.SAME_CATEGORY
        !newParent.isMain -> Violation.PARENT_NOT_MAIN
        newParent.kind != category.kind -> Violation.PARENT_KIND_MISMATCH
        newParent.archived -> Violation.TARGET_ARCHIVED
        category.isMain && hasSubcategories -> Violation.CATEGORY_HAS_SUBCATEGORIES
        else -> null
    }

    /**
     * Zlúčenie [source] do [target] rovnakého druhu. Cieľ nesmie byť archivovaný (ani jeho rodič)
     * a hlavná kategória s podkategóriami sa zlučuje len do inej hlavnej kategórie (G2).
     */
    fun canMerge(source: Category, target: Category, targetParent: Category?, sourceHasSubcategories: Boolean): Violation? =
        when {
            source.id == target.id -> Violation.SAME_CATEGORY
            source.kind != target.kind -> Violation.CATEGORY_KIND_MISMATCH
            target.archived || targetParent?.archived == true -> Violation.TARGET_ARCHIVED
            target.parentId == source.id -> Violation.MERGE_INTO_OWN_SUBCATEGORY
            sourceHasSubcategories && !target.isMain -> Violation.MERGE_TARGET_NOT_MAIN
            else -> null
        }

    /** Podkategória preberá farbu rodiča. */
    fun effectiveColor(requested: Int, parent: Category?): Int = parent?.color ?: requested

    fun canSetColor(category: Category, color: Int): Violation? =
        if (!category.isMain) Violation.SUBCATEGORY_COLOR_INHERITED else validateColor(color)

    /** Posledná aktívna hlavná kategória daného druhu sa nedá archivovať; podkategória vždy. */
    fun canArchive(category: Category, activeMainCountOfKind: Int): Violation? =
        if (isLastActiveMain(category, activeMainCountOfKind)) Violation.LAST_ACTIVE_MAIN_CATEGORY else null

    /**
     * Natvrdo sa dá zmazať len kategória bez podkategórií, na ktorú neodkazuje
     * žiadna transakcia ani pravidlo.
     */
    fun canDelete(
        category: Category,
        activeMainCountOfKind: Int,
        hasSubcategories: Boolean,
        referenced: Boolean,
    ): Violation? = when {
        hasSubcategories -> Violation.CATEGORY_HAS_SUBCATEGORIES
        referenced -> Violation.CATEGORY_IN_USE
        isLastActiveMain(category, activeMainCountOfKind) -> Violation.LAST_ACTIVE_MAIN_CATEGORY
        else -> null
    }

    private fun validateColor(color: Int): Violation? =
        if (color !in 0 until COLOR_COUNT) Violation.COLOR_OUT_OF_RANGE else null

    private fun isLastActiveMain(category: Category, activeMainCountOfKind: Int): Boolean =
        category.isMain && !category.archived && activeMainCountOfKind <= 1
}

object BudgetRules {

    fun validateLimit(limitCents: Long): Violation? = when {
        limitCents <= 0 -> Violation.AMOUNT_NOT_POSITIVE
        limitCents > Money.MAX_CENTS -> Violation.AMOUNT_TOO_LARGE
        else -> null
    }

    /** Rozpočet patrí len hlavnej výdavkovej kategórii a každá ho má najviac jeden. */
    fun validate(category: Category?, limitCents: Long, categoryHasBudget: Boolean): Violation? = when {
        category == null -> Violation.CATEGORY_NOT_FOUND
        !category.isMain -> Violation.BUDGET_CATEGORY_NOT_MAIN
        category.kind != CategoryKind.EXPENSE -> Violation.BUDGET_CATEGORY_NOT_EXPENSE
        categoryHasBudget -> Violation.BUDGET_ALREADY_EXISTS
        else -> validateLimit(limitCents)
    }
}
