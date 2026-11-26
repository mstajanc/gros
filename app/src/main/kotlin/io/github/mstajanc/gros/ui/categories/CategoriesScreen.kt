package io.github.mstajanc.gros.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.mstajanc.gros.AppContainer
import io.github.mstajanc.gros.R
import io.github.mstajanc.gros.domain.model.Category
import io.github.mstajanc.gros.domain.model.CategoryKind
import io.github.mstajanc.gros.ui.common.CategoryBadge
import io.github.mstajanc.gros.ui.common.CenteredContent
import io.github.mstajanc.gros.ui.common.GrosIcon
import io.github.mstajanc.gros.ui.common.ReorderableColumn
import io.github.mstajanc.gros.ui.common.ScreenHeader
import io.github.mstajanc.gros.ui.common.ScreenPadding
import io.github.mstajanc.gros.ui.common.SecondaryButton
import io.github.mstajanc.gros.ui.common.SegmentedControl
import io.github.mstajanc.gros.ui.common.grosViewModel
import io.github.mstajanc.gros.ui.theme.GrosShapes
import io.github.mstajanc.gros.ui.theme.GrosTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoriesState(val loaded: Boolean = false, val categories: List<Category> = emptyList()) {
    fun mains(kind: CategoryKind, archived: Boolean): List<Category> =
        categories.filter { it.isMain && it.kind == kind && it.archived == archived }
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))

    fun children(parentId: Long): List<Category> =
        categories.filter { it.parentId == parentId }.sortedWith(compareBy({ it.sortOrder }, { it.id }))
}

class CategoriesViewModel(container: AppContainer) : ViewModel() {

    private val repository = container.categoryRepository

    val state: StateFlow<CategoriesState> = repository.observeCategories()
        .map { CategoriesState(loaded = true, categories = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesState())

    /** Uloží nové poradie aktívnych hlavných kategórií druhu; archivované ostávajú za nimi. */
    fun reorder(kind: CategoryKind, activeIds: List<Long>) {
        val archivedIds = state.value.mains(kind, archived = true).map { it.id }
        viewModelScope.launch { repository.reorder(activeIds + archivedIds) }
    }

    fun restore(id: Long) {
        viewModelScope.launch { repository.restore(id) }
    }
}

/**
 * Nastavenia → Kategórie: hlavné kategórie druhu, ich poradie (potiahnutím) a archivované.
 * Podkategórie sa spravujú v detaile hlavnej kategórie.
 */
@Composable
fun CategoriesScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit,
    onAdd: (CategoryKind) -> Unit,
) {
    val viewModel = grosViewModel { container, _ -> CategoriesViewModel(container) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    var kindIndex by rememberSaveable { mutableIntStateOf(0) }
    val kind = if (kindIndex == 0) CategoryKind.EXPENSE else CategoryKind.INCOME

    CenteredContent(modifier = Modifier.padding(contentPadding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(stringResource(R.string.categories_title), onBack = onBack)
            SegmentedControl(
                options = listOf(stringResource(R.string.onboarding_expense), stringResource(R.string.onboarding_income)),
                selectedIndex = kindIndex,
                onSelect = { kindIndex = it },
            )
            if (!state.loaded) return@Column

            ReorderableColumn(
                items = state.mains(kind, archived = false),
                key = { it.id },
                label = { it.name },
                onReorder = { viewModel.reorder(kind, it) },
            ) { category, handle, modifier ->
                CategoryRow(
                    category = category,
                    subtitle = subcategoryText(state.children(category.id).filter { !it.archived }),
                    onClick = { onOpen(category.id) },
                    modifier = modifier,
                    leading = handle,
                )
            }

            SecondaryButton(
                stringResource(R.string.categories_add),
                onClick = { onAdd(kind) },
                modifier = Modifier.fillMaxWidth(),
                contentColor = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.categories_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            val archived = state.mains(kind, archived = true)
            if (archived.isNotEmpty()) {
                Text(
                    stringResource(R.string.accounts_archived),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp).semantics { heading() },
                )
                archived.forEach { category ->
                    CategoryRow(
                        category = category,
                        subtitle = null,
                        onClick = { onOpen(category.id) },
                        dimmed = true,
                        trailing = {
                            SecondaryButton(
                                stringResource(R.string.accounts_restore),
                                onClick = { viewModel.restore(category.id) },
                                contentColor = MaterialTheme.colorScheme.primary,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun subcategoryText(children: List<Category>): String =
    if (children.isEmpty()) stringResource(R.string.categories_no_subcategories) else children.joinToString(", ") { it.name }

/** Riadok kategórie: ikona v jej farbe, názov, podnadpis a šípka (alebo vlastná akcia vpravo). */
@Composable
fun CategoryRow(
    category: Category,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    dimmed: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(GrosShapes.cardMedium)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = if (leading != null) 4.dp else 14.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        CategoryBadge(category.name, GrosTheme.colors.category(category.color))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                category.name,
                style = MaterialTheme.typography.titleMedium,
                color = if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            trailing()
        } else {
            GrosIcon(R.drawable.ic_chevron_right, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
