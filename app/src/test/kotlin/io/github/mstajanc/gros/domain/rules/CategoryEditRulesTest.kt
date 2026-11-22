package io.github.mstajanc.gros.domain.rules

import io.github.mstajanc.gros.domain.model.Category
import io.github.mstajanc.gros.domain.model.CategoryKind
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

/** Názov, presun a zlúčenie kategórií (M5, rozhodnutia G1–G3). */
class CategoryEditRulesTest {

    private val car = Category(id = 1, name = "Auto", kind = CategoryKind.EXPENSE, color = 2)
    private val fuel = Category(id = 2, parentId = 1, name = "Tankovanie", kind = CategoryKind.EXPENSE, color = 2)
    private val service = Category(id = 3, parentId = 1, name = "Servis", kind = CategoryKind.EXPENSE, color = 2)
    private val food = Category(id = 4, name = "Potraviny", kind = CategoryKind.EXPENSE, color = 0)
    private val kids = Category(id = 5, name = "Deti", kind = CategoryKind.EXPENSE, color = 1)
    private val salary = Category(id = 6, name = "Výplata", kind = CategoryKind.INCOME, color = 0)
    private val archivedMain = Category(id = 7, name = "Staré", kind = CategoryKind.EXPENSE, color = 5, archived = true)
    private val subOfArchived = Category(id = 8, parentId = 7, name = "Niečo", kind = CategoryKind.EXPENSE, color = 5)
    private val all = listOf(car, fuel, service, food, kids, salary, archivedMain, subOfArchived)

    @Test
    fun `name is required and at most 40 characters after trimming`() {
        assertNull(CategoryRules.validateName("Deti"))
        assertEquals(Violation.NAME_BLANK, CategoryRules.validateName("  "))
        assertNull(CategoryRules.validateName("  " + "a".repeat(40) + "  "))
        assertEquals(Violation.NAME_TOO_LONG, CategoryRules.validateName("a".repeat(41)))
    }

    @Test
    fun `siblings are subcategories of the same parent or main categories of the same kind`() {
        assertEquals(listOf(fuel, service), CategoryRules.siblings(all, parentId = car.id, kind = CategoryKind.EXPENSE))
        assertEquals(
            listOf(car, food, kids, archivedMain),
            CategoryRules.siblings(all, parentId = null, kind = CategoryKind.EXPENSE),
        )
        assertEquals(listOf(salary), CategoryRules.siblings(all, parentId = null, kind = CategoryKind.INCOME))
    }

    @Test
    fun `name must be unique among siblings ignoring case`() {
        val mains = CategoryRules.siblings(all, parentId = null, kind = CategoryKind.EXPENSE)
        assertEquals(Violation.NAME_DUPLICATE, CategoryRules.validateName(" potraviny ", mains))
        // Aj archivovaná kategória blokuje názov, inak by obnovenie vytvorilo duplicitu.
        assertEquals(Violation.NAME_DUPLICATE, CategoryRules.validateName("STARÉ", mains))
        assertNull(CategoryRules.validateName("Tankovanie", mains))
        // Premenovanie na vlastný názov (napr. iná veľkosť písmen) nie je duplicita.
        assertNull(CategoryRules.validateName("POTRAVINY", mains, selfId = food.id))
        // Rovnaký názov pod iným rodičom je v poriadku.
        assertNull(CategoryRules.validateName("Servis", listOf(fuel)))
    }

    @Test
    fun `subcategory can move under another active main category of the same kind`() {
        assertNull(CategoryRules.canMove(fuel, newParent = kids, hasSubcategories = false))
        assertEquals(Violation.PARENT_KIND_MISMATCH, CategoryRules.canMove(fuel, newParent = salary, hasSubcategories = false))
        assertEquals(Violation.TARGET_ARCHIVED, CategoryRules.canMove(fuel, newParent = archivedMain, hasSubcategories = false))
        assertEquals(Violation.PARENT_NOT_MAIN, CategoryRules.canMove(fuel, newParent = service, hasSubcategories = false))
    }

    @Test
    fun `subcategory can be promoted to a main category`() {
        assertNull(CategoryRules.canMove(fuel, newParent = null, hasSubcategories = false))
    }

    @Test
    fun `main category without subcategories can move under another main category`() {
        assertNull(CategoryRules.canMove(food, newParent = kids, hasSubcategories = false))
        assertEquals(
            Violation.CATEGORY_HAS_SUBCATEGORIES,
            CategoryRules.canMove(car, newParent = kids, hasSubcategories = true),
        )
        assertEquals(Violation.SAME_CATEGORY, CategoryRules.canMove(food, newParent = food, hasSubcategories = false))
    }

    @Test
    fun `merge needs two different categories of the same kind`() {
        assertNull(CategoryRules.canMerge(food, target = kids, targetParent = null, sourceHasSubcategories = false))
        assertEquals(
            Violation.SAME_CATEGORY,
            CategoryRules.canMerge(food, target = food, targetParent = null, sourceHasSubcategories = false),
        )
        assertEquals(
            Violation.CATEGORY_KIND_MISMATCH,
            CategoryRules.canMerge(food, target = salary, targetParent = null, sourceHasSubcategories = false),
        )
    }

    @Test
    fun `subcategory can merge into its own main category or another subcategory`() {
        assertNull(CategoryRules.canMerge(fuel, target = car, targetParent = null, sourceHasSubcategories = false))
        assertNull(CategoryRules.canMerge(fuel, target = service, targetParent = car, sourceHasSubcategories = false))
    }

    @Test
    fun `main category with subcategories merges only into another main category`() {
        assertNull(CategoryRules.canMerge(car, target = kids, targetParent = null, sourceHasSubcategories = true))
        assertEquals(
            Violation.MERGE_TARGET_NOT_MAIN,
            CategoryRules.canMerge(kids, target = fuel, targetParent = car, sourceHasSubcategories = true),
        )
        // Bez podkategórií sa hlavná kategória smie zlúčiť aj do podkategórie inej hlavnej.
        assertNull(CategoryRules.canMerge(food, target = fuel, targetParent = car, sourceHasSubcategories = false))
    }

    @Test
    fun `main category cannot merge into its own subcategory`() {
        assertEquals(
            Violation.MERGE_INTO_OWN_SUBCATEGORY,
            CategoryRules.canMerge(car, target = fuel, targetParent = car, sourceHasSubcategories = false),
        )
    }

    @Test
    fun `nothing merges into an archived category or into a subcategory of an archived main`() {
        assertEquals(
            Violation.TARGET_ARCHIVED,
            CategoryRules.canMerge(food, target = archivedMain, targetParent = null, sourceHasSubcategories = false),
        )
        assertEquals(
            Violation.TARGET_ARCHIVED,
            CategoryRules.canMerge(food, target = subOfArchived, targetParent = archivedMain, sourceHasSubcategories = false),
        )
    }
}
