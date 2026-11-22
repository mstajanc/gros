package io.github.mstajanc.gros.data.repository

import androidx.room3.withWriteTransaction
import io.github.mstajanc.gros.data.db.CategoryEntity
import io.github.mstajanc.gros.data.db.GrosDatabase
import io.github.mstajanc.gros.data.db.toDomain
import io.github.mstajanc.gros.domain.model.Category
import io.github.mstajanc.gros.domain.model.CategoryKind
import io.github.mstajanc.gros.domain.rules.CategoryRules
import io.github.mstajanc.gros.domain.rules.InvariantViolationException
import io.github.mstajanc.gros.domain.rules.Violation
import io.github.mstajanc.gros.domain.rules.throwIfViolated
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Kategórie v dvoch úrovniach. Stráži: najviac dve úrovne, podkategória má druh aj farbu rodiča,
 * druh sa po vytvorení nemení a vždy ostáva aspoň jedna aktívna hlavná kategória každého druhu.
 * Názvy sú medzi súrodencami jedinečné (G1).
 */
class CategoryRepository(private val db: GrosDatabase) {

    private val categories = db.categoryDao()
    private val transactions = db.transactionDao()
    private val rules = db.recurringRuleDao()
    private val budgets = db.budgetDao()

    /** Všetky kategórie vrátane archivovaných, zoradené podľa `sortOrder` v rámci rodiča. */
    fun observeCategories(): Flow<List<Category>> =
        categories.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun get(id: Long): Category? = categories.getById(id)?.toDomain()

    /** Vytvorí hlavnú kategóriu (`parentId = null`) alebo podkategóriu; tá preberá farbu rodiča. */
    suspend fun create(name: String, kind: CategoryKind, color: Int, parentId: Long? = null): Long =
        db.withWriteTransaction {
            val parent = parentId?.let { categories.getById(it)?.toDomain() }
            CategoryRules.validateNew(name, kind, color, parentId, parent).throwIfViolated()
            CategoryRules.validateName(name, siblings(parentId, kind)).throwIfViolated()
            val sortOrder = if (parentId == null) {
                categories.maxMainSortOrder(kind) + 1
            } else {
                categories.maxChildSortOrder(parentId) + 1
            }
            categories.insert(
                CategoryEntity(
                    parentId = parentId,
                    name = name.trim(),
                    kind = kind,
                    color = CategoryRules.effectiveColor(color, parent),
                    archived = false,
                    sortOrder = sortOrder,
                ),
            )
        }

    suspend fun rename(id: Long, name: String) {
        db.withWriteTransaction {
            val existing = require(id)
            CategoryRules.validateName(name, siblings(existing.parentId, existing.kind), selfId = id).throwIfViolated()
            categories.update(existing.copy(name = name.trim()))
        }
    }

    /** Zmení farbu hlavnej kategórie aj všetkých jej podkategórií. */
    suspend fun setColor(id: Long, color: Int) {
        db.withWriteTransaction {
            val existing = require(id)
            CategoryRules.canSetColor(existing.toDomain(), color).throwIfViolated()
            categories.update(existing.copy(color = color))
            categories.setChildrenColor(parentId = id, color = color)
        }
    }

    /**
     * Archivuje kategóriu. Archivácia hlavnej kategórie skryje aj jej podkategórie,
     * ich vlastný príznak sa však nemení, takže obnovenie vráti pôvodný stav. Aktívne opakované platby
     * v kategórii a jej podkategóriách sa pozastavia (pravidlo 10); vráti ich názvy.
     */
    suspend fun archive(id: Long): List<String> = db.withWriteTransaction {
        val existing = require(id)
        if (existing.archived) return@withWriteTransaction emptyList()
        CategoryRules.canArchive(existing.toDomain(), categories.countActiveMain(existing.kind)).throwIfViolated()
        categories.update(existing.copy(archived = true))
        // Pravidlo 10 platí aj pre pravidlá zaradené v podkategóriách archivovanej hlavnej kategórie.
        db.pauseRules(rules.getUsingCategories(listOf(id) + categories.getChildIds(id)))
    }

    /** Názvy aktívnych opakovaných platieb, ktoré by archivácia kategórie pozastavila. */
    suspend fun activeRulesUsing(id: Long): List<String> =
        rules.getUsingCategories(listOf(id) + categories.getChildIds(id)).activeNames()

    suspend fun restore(id: Long) {
        db.withWriteTransaction {
            val existing = require(id)
            if (existing.archived) categories.update(existing.copy(archived = false))
        }
    }

    /**
     * Natvrdo zmaže kategóriu bez podkategórií, na ktorú neodkazuje žiadna transakcia ani pravidlo.
     * Jej rozpočet sa zmaže s ňou.
     */
    suspend fun delete(id: Long) {
        db.withWriteTransaction {
            val existing = require(id)
            CategoryRules.canDelete(
                category = existing.toDomain(),
                activeMainCountOfKind = categories.countActiveMain(existing.kind),
                hasSubcategories = categories.countChildren(id) > 0,
                referenced = transactions.countByCategory(id) > 0 || rules.countByCategory(id) > 0,
            ).throwIfViolated()
            categories.deleteById(id)
        }
    }

    /** Nastaví poradie súrodencov (hlavných kategórií jedného druhu alebo podkategórií jedného rodiča). */
    suspend fun reorder(orderedIds: List<Long>) {
        db.withWriteTransaction {
            orderedIds.forEachIndexed { index, id -> categories.setSortOrder(id, index) }
        }
    }

    /** Koľko záznamov kategóriu používa; podľa toho UI ponúkne Zmazať alebo len Archivovať a Zlúčiť. */
    suspend fun usage(id: Long): CategoryUsage = CategoryUsage(
        transactions = transactions.countByCategory(id),
        rules = rules.countByCategory(id),
        subcategories = categories.countChildren(id),
    )

    /**
     * Presunie kategóriu pod hlavnú kategóriu [newParentId] alebo ju povýši na hlavnú (`null`).
     * Zaradí sa na koniec nového rodiča (G3) a ako podkategória preberie jeho farbu. Rozpočet
     * presúvanej hlavnej kategórie prejde na nového rodiča, ak ten žiadny nemá; inak zanikne (G11).
     */
    suspend fun move(id: Long, newParentId: Long?) {
        db.withWriteTransaction {
            val existing = require(id)
            if (existing.parentId == newParentId) return@withWriteTransaction
            val newParent = newParentId?.let { require(it) }
            CategoryRules.canMove(existing.toDomain(), newParent?.toDomain(), categories.countChildren(id) > 0)
                .throwIfViolated()
            CategoryRules.validateName(existing.name, siblings(newParentId, existing.kind), selfId = id).throwIfViolated()
            if (newParent != null && existing.parentId == null) handOverBudget(fromId = id, toMainId = newParent.id)
            categories.update(
                existing.copy(
                    parentId = newParentId,
                    color = newParent?.color ?: existing.color,
                    sortOrder = nextSortOrder(newParentId, existing.kind),
                ),
            )
        }
    }

    /**
     * Zlúči [sourceId] do [targetId]: transakcie a pravidlá prejdú na cieľ, podkategórie zdroja sa
     * presunú pod cieľ (rovnomenné sa zlúčia do existujúcich, G11) a zdroj sa zmaže. Rozpočet cieľa
     * ostáva; ak cieľ (pri podkategórii jej hlavná kategória) rozpočet nemá, prevezme rozpočet zdroja.
     */
    suspend fun merge(sourceId: Long, targetId: Long) {
        db.withWriteTransaction {
            val source = require(sourceId)
            val target = require(targetId)
            val targetParent = target.parentId?.let { require(it) }
            val children = categories.getChildren(sourceId)
            CategoryRules.canMerge(source.toDomain(), target.toDomain(), targetParent?.toDomain(), children.isNotEmpty())
                .throwIfViolated()

            moveReferences(sourceId, targetId)
            val targetChildren = categories.getChildren(targetId)
            for (child in children) {
                val sameName = targetChildren.firstOrNull { it.name.trim().equals(child.name.trim(), ignoreCase = true) }
                if (sameName != null) {
                    moveReferences(child.id, sameName.id)
                    categories.deleteById(child.id)
                } else {
                    categories.update(
                        child.copy(
                            parentId = targetId,
                            color = target.color,
                            sortOrder = categories.maxChildSortOrder(targetId) + 1,
                        ),
                    )
                }
            }
            if (source.parentId == null) handOverBudget(fromId = sourceId, toMainId = target.parentId ?: targetId)
            categories.deleteById(sourceId)
        }
    }

    private suspend fun moveReferences(fromId: Long, toId: Long) {
        transactions.reassignCategory(fromId, toId)
        rules.reassignCategory(fromId, toId)
    }

    /** Rozpočet prejde na hlavnú kategóriu [toMainId], ak tá žiadny nemá; inak sa zmaže. */
    private suspend fun handOverBudget(fromId: Long, toMainId: Long) {
        val budget = budgets.getByCategory(fromId) ?: return
        if (budgets.getByCategory(toMainId) == null) {
            budgets.update(budget.copy(categoryId = toMainId))
        } else {
            budgets.deleteById(budget.id)
        }
    }

    private suspend fun nextSortOrder(parentId: Long?, kind: CategoryKind): Int =
        if (parentId == null) categories.maxMainSortOrder(kind) + 1 else categories.maxChildSortOrder(parentId) + 1

    private suspend fun siblings(parentId: Long?, kind: CategoryKind): List<Category> =
        CategoryRules.siblings(categories.getAll().map { it.toDomain() }, parentId, kind)

    private suspend fun require(id: Long): CategoryEntity =
        categories.getById(id) ?: throw InvariantViolationException(Violation.NOT_FOUND)
}

/** Počty záznamov, ktoré kategóriu používajú. */
data class CategoryUsage(val transactions: Int, val rules: Int, val subcategories: Int) {
    val isUsed: Boolean get() = transactions > 0 || rules > 0
}
