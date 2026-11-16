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
 *
 * Presun, povýšenie a zlúčenie kategórií pribudnú v M5.
 */
class CategoryRepository(private val db: GrosDatabase) {

    private val categories = db.categoryDao()
    private val transactions = db.transactionDao()
    private val rules = db.recurringRuleDao()

    /** Všetky kategórie vrátane archivovaných, zoradené podľa `sortOrder` v rámci rodiča. */
    fun observeCategories(): Flow<List<Category>> =
        categories.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun get(id: Long): Category? = categories.getById(id)?.toDomain()

    /** Vytvorí hlavnú kategóriu (`parentId = null`) alebo podkategóriu; tá preberá farbu rodiča. */
    suspend fun create(name: String, kind: CategoryKind, color: Int, parentId: Long? = null): Long =
        db.withWriteTransaction {
            val parent = parentId?.let { categories.getById(it)?.toDomain() }
            CategoryRules.validateNew(name, kind, color, parentId, parent).throwIfViolated()
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
            CategoryRules.validateName(name).throwIfViolated()
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
     * ich vlastný príznak sa však nemení, takže obnovenie vráti pôvodný stav.
     */
    suspend fun archive(id: Long) {
        db.withWriteTransaction {
            val existing = require(id)
            if (existing.archived) return@withWriteTransaction
            CategoryRules.canArchive(existing.toDomain(), categories.countActiveMain(existing.kind)).throwIfViolated()
            categories.update(existing.copy(archived = true))
        }
    }

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

    private suspend fun require(id: Long): CategoryEntity =
        categories.getById(id) ?: throw InvariantViolationException(Violation.NOT_FOUND)
}
