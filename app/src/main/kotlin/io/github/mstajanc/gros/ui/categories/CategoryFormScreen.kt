package io.github.mstajanc.gros.ui.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.mstajanc.gros.AppContainer
import io.github.mstajanc.gros.R
import io.github.mstajanc.gros.data.repository.CategoryUsage
import io.github.mstajanc.gros.domain.model.Category
import io.github.mstajanc.gros.domain.model.CategoryKind
import io.github.mstajanc.gros.domain.rules.CategoryRules
import io.github.mstajanc.gros.domain.rules.InvariantViolationException
import io.github.mstajanc.gros.domain.rules.Violation
import io.github.mstajanc.gros.ui.common.CenteredContent
import io.github.mstajanc.gros.ui.common.ConfirmDialog
import io.github.mstajanc.gros.ui.common.GrosIcon
import io.github.mstajanc.gros.ui.common.OptionSheet
import io.github.mstajanc.gros.ui.common.PrimaryButton
import io.github.mstajanc.gros.ui.common.ReorderableColumn
import io.github.mstajanc.gros.ui.common.ScreenHeader
import io.github.mstajanc.gros.ui.common.ScreenPadding
import io.github.mstajanc.gros.ui.common.SecondaryButton
import io.github.mstajanc.gros.ui.common.SheetOption
import io.github.mstajanc.gros.ui.common.TextFieldTile
import io.github.mstajanc.gros.ui.common.grosViewModel
import io.github.mstajanc.gros.ui.theme.GrosShapes
import io.github.mstajanc.gros.ui.theme.GrosTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryFormState(
    val loaded: Boolean = false,
    val existing: Category? = null,
    val parentId: Long? = null,
    val kind: CategoryKind = CategoryKind.EXPENSE,
    val name: String = "",
    val color: Int = 0,
    val categories: List<Category> = emptyList(),
    val usage: CategoryUsage? = null,
    val error: Violation? = null,
) {
    val isNew: Boolean get() = existing == null
    val isMain: Boolean get() = parentId == null
    val parent: Category? get() = categories.firstOrNull { it.id == parentId }
    val canSave: Boolean get() = name.isNotBlank()

    private val sorted = compareBy<Category>({ it.sortOrder }, { it.id })
    private fun activeMains(): List<Category> =
        categories.filter { it.isMain && it.kind == kind && !it.archived }.sortedWith(sorted)

    fun children(archived: Boolean): List<Category> =
        existing?.let { self -> categories.filter { it.parentId == self.id && it.archived == archived }.sortedWith(sorted) }
            .orEmpty()

    private val hasChildren: Boolean get() = existing != null && categories.any { it.parentId == existing.id }

    /** Kam sa dá kategória presunúť: pod inú aktívnu hlavnú kategóriu, podkategória aj medzi hlavné. */
    val moveTargets: List<Category?>
        get() {
            val self = existing ?: return emptyList()
            if (self.isMain && hasChildren) return emptyList()
            val mains = activeMains().filter { it.id != self.id && it.id != self.parentId }
            return if (self.isMain) mains else listOf<Category?>(null) + mains
        }

    /** Ciele zlúčenia: aktívne hlavné kategórie druhu a ich podkategórie, podľa pravidiel G2. */
    val mergeTargets: List<Category>
        get() {
            val self = existing ?: return emptyList()
            return activeMains().flatMap { main ->
                listOf(main) + categories.filter { it.parentId == main.id && !it.archived }.sortedWith(sorted)
            }.filter { target ->
                val targetParent = target.parentId?.let { id -> categories.firstOrNull { it.id == id } }
                CategoryRules.canMerge(self, target, targetParent, hasChildren) == null
            }
        }

    /** Zmazať sa dá len nepoužitá kategória bez podkategórií (inak ostáva archivácia a zlúčenie). */
    val canDelete: Boolean get() = existing != null && usage != null && !usage.isUsed && usage.subcategories == 0
}

@OptIn(ExperimentalCoroutinesApi::class)
class CategoryFormViewModel(private val container: AppContainer, private val handle: SavedStateHandle) : ViewModel() {

    private val categoryId: Long? = handle.get<Long>(ARG_ID)?.takeIf { it > 0 }
    private val newParentId: Long? = handle.get<Long>(ARG_PARENT)?.takeIf { it > 0 }
    private val newKind: CategoryKind =
        handle.get<String>(ARG_KIND)?.let { arg -> CategoryKind.entries.firstOrNull { it.name == arg } } ?: CategoryKind.EXPENSE
    private val repository = container.categoryRepository

    private val name = handle.getStateFlow(KEY_NAME, "")
    private val color = handle.getStateFlow(KEY_COLOR, 0)
    private val loaded = handle.getStateFlow(KEY_LOADED, categoryId == null)
    private val error = MutableStateFlow<Violation?>(null)

    /** Názvy aktívnych opakovaných platieb, ktoré archivácia pozastaví; `null` = dialóg nie je otvorený. */
    private val _archiveConfirm = MutableStateFlow<List<String>?>(null)
    val archiveConfirm: StateFlow<List<String>?> = _archiveConfirm

    private val _close = Channel<Unit>(Channel.BUFFERED)
    val close = _close.receiveAsFlow()

    private val data = repository.observeCategories().mapLatest { list -> list to categoryId?.let { repository.usage(it) } }

    val state: StateFlow<CategoryFormState> = combine(
        combine(name, color, loaded) { n, c, l -> Triple(n, c, l) },
        data,
        error,
    ) { (nameValue, colorValue, isLoaded), (categories, usage), errorValue ->
        val existing = categories.firstOrNull { it.id == categoryId }
        val parentId = if (categoryId != null) existing?.parentId else newParentId
        CategoryFormState(
            loaded = isLoaded && (categoryId == null || existing != null),
            existing = existing,
            parentId = parentId,
            kind = existing?.kind ?: categories.firstOrNull { it.id == newParentId }?.kind ?: newKind,
            name = nameValue,
            color = existing?.takeIf { !it.isMain }?.color
                ?: categories.firstOrNull { it.id == newParentId }?.color
                ?: colorValue,
            categories = categories,
            usage = usage,
            error = errorValue,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoryFormState())

    init {
        viewModelScope.launch {
            if (!loaded.value && categoryId != null) {
                repository.get(categoryId)?.let {
                    handle[KEY_NAME] = it.name
                    handle[KEY_COLOR] = it.color
                }
                handle[KEY_LOADED] = true
            }
        }
    }

    fun onName(value: String) {
        handle[KEY_NAME] = value
        error.value = null
    }

    fun onColor(value: Int) {
        handle[KEY_COLOR] = value
    }

    fun save() {
        val current = state.value
        if (!current.canSave) return
        launchSafely {
            val existing = current.existing
            if (existing == null) {
                repository.create(current.name, current.kind, current.color, current.parentId)
            } else {
                if (current.name.trim() != existing.name) repository.rename(existing.id, current.name)
                if (existing.isMain && current.color != existing.color) repository.setColor(existing.id, current.color)
            }
            _close.send(Unit)
        }
    }

    fun move(newParentId: Long?) {
        val id = categoryId ?: return
        launchSafely { repository.move(id, newParentId) }
    }

    fun merge(targetId: Long) {
        val id = categoryId ?: return
        launchSafely {
            repository.merge(id, targetId)
            _close.send(Unit)
        }
    }

    /** Pred archiváciou zistí, ktoré opakované platby sa pozastavia, a otvorí potvrdenie (D1). */
    fun requestArchive() {
        val id = categoryId ?: return
        viewModelScope.launch { _archiveConfirm.value = repository.activeRulesUsing(id) }
    }

    fun dismissArchive() {
        _archiveConfirm.value = null
    }

    fun archive() {
        val id = categoryId ?: return
        _archiveConfirm.value = null
        launchSafely { repository.archive(id) }
    }

    fun restore() {
        val id = categoryId ?: return
        launchSafely { repository.restore(id) }
    }

    fun delete() {
        val id = categoryId ?: return
        launchSafely {
            repository.delete(id)
            _close.send(Unit)
        }
    }

    fun reorderChildren(activeIds: List<Long>) {
        val archivedIds = state.value.children(archived = true).map { it.id }
        viewModelScope.launch { repository.reorder(activeIds + archivedIds) }
    }

    /** Porušenie pravidla sa ukáže pod názvom; dáta ostávajú nezmenené. */
    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: InvariantViolationException) {
                error.value = e.violation
            }
        }
    }

    companion object {
        const val ARG_ID = "id"
        const val ARG_PARENT = "parent"
        const val ARG_KIND = "kind"
        private const val KEY_NAME = "category_name"
        private const val KEY_COLOR = "category_color"
        private const val KEY_LOADED = "category_loaded"
    }
}

private enum class CategoryDialog { MOVE, MERGE, DELETE }

/**
 * Nová kategória alebo detail existujúcej: názov, farba (len hlavná), podkategórie a akcie
 * Presunúť, Zlúčiť, Archivovať / Obnoviť a Zmazať.
 */
@Composable
fun CategoryFormScreen(
    contentPadding: PaddingValues,
    onClose: () -> Unit,
    onOpen: (Long) -> Unit,
    onAddSubcategory: (Long) -> Unit,
) {
    val viewModel = grosViewModel { container, handle -> CategoryFormViewModel(container, handle) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val archiveRules by viewModel.archiveConfirm.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf<CategoryDialog?>(null) }
    var mergeTarget by rememberSaveable { mutableStateOf<Long?>(null) }

    LaunchedEffect(viewModel) { viewModel.close.collect { onClose() } }

    val title = when {
        !state.isNew -> stringResource(R.string.category_edit)
        state.isMain -> stringResource(R.string.category_new)
        else -> stringResource(R.string.category_new_sub)
    }

    CenteredContent(modifier = Modifier.padding(contentPadding)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ScreenPadding)
                .padding(top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ScreenHeader(title, onBack = onClose)
            if (!state.loaded) return@Column

            state.parent?.let { parent ->
                Text(
                    stringResource(R.string.category_parent, parent.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextFieldTile(
                label = stringResource(R.string.category_name),
                value = state.name,
                onValueChange = viewModel::onName,
                maxLength = CategoryRules.MAX_NAME_LENGTH,
            )
            state.error?.let { ErrorText(it) }

            if (state.isMain) {
                Text(
                    stringResource(R.string.category_color),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ColorPicker(selected = state.color, letter = state.name, onSelect = viewModel::onColor)
            }
            PrimaryButton(stringResource(R.string.action_save), onClick = viewModel::save, enabled = state.canSave)

            val existing = state.existing ?: return@Column

            if (existing.isMain) {
                SectionTitle(stringResource(R.string.category_subcategories))
                ReorderableColumn(
                    items = state.children(archived = false),
                    key = { it.id },
                    label = { it.name },
                    onReorder = viewModel::reorderChildren,
                ) { child, handle, modifier ->
                    CategoryRow(category = child, subtitle = null, onClick = { onOpen(child.id) }, modifier = modifier, leading = handle)
                }
                state.children(archived = true).forEach { child ->
                    CategoryRow(
                        category = child,
                        subtitle = stringResource(R.string.label_archived),
                        onClick = { onOpen(child.id) },
                        dimmed = true,
                    )
                }
                SecondaryButton(
                    stringResource(R.string.category_add_sub),
                    onClick = { onAddSubcategory(existing.id) },
                    modifier = Modifier.fillMaxWidth(),
                    contentColor = MaterialTheme.colorScheme.primary,
                )
            }

            SectionTitle(stringResource(R.string.category_actions))
            if (state.moveTargets.isNotEmpty()) {
                SecondaryButton(
                    stringResource(R.string.category_move),
                    onClick = { dialog = CategoryDialog.MOVE },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (state.mergeTargets.isNotEmpty()) {
                SecondaryButton(
                    stringResource(R.string.category_merge),
                    onClick = { dialog = CategoryDialog.MERGE },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (existing.archived) {
                SecondaryButton(
                    stringResource(R.string.accounts_restore),
                    onClick = viewModel::restore,
                    modifier = Modifier.fillMaxWidth(),
                    contentColor = MaterialTheme.colorScheme.primary,
                )
            } else {
                SecondaryButton(
                    stringResource(R.string.category_archive),
                    onClick = viewModel::requestArchive,
                    modifier = Modifier.fillMaxWidth(),
                    contentColor = GrosTheme.colors.warning,
                )
            }
            if (state.canDelete) {
                SecondaryButton(
                    stringResource(R.string.action_delete),
                    onClick = { dialog = CategoryDialog.DELETE },
                    modifier = Modifier.fillMaxWidth(),
                    contentColor = GrosTheme.colors.warning,
                )
            }
            Text(
                stringResource(R.string.category_actions_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    val existing = state.existing
    when (dialog) {
        CategoryDialog.MOVE -> OptionSheet(
            title = stringResource(R.string.category_move_title),
            options = state.moveTargets.map { target ->
                SheetOption(target?.id ?: NO_PARENT, target?.name ?: stringResource(R.string.category_move_to_main))
            },
            selected = null,
            onSelect = {
                dialog = null
                viewModel.move(it.takeIf { id -> id != NO_PARENT })
            },
            onDismiss = { dialog = null },
        )

        CategoryDialog.MERGE -> OptionSheet(
            title = stringResource(R.string.category_merge_title),
            options = state.mergeTargets.map { SheetOption(it.id, it.name, indent = !it.isMain) },
            selected = null,
            onSelect = {
                dialog = null
                mergeTarget = it
            },
            onDismiss = { dialog = null },
        )

        CategoryDialog.DELETE -> ConfirmDialog(
            title = stringResource(R.string.category_delete_title),
            text = stringResource(R.string.category_delete_text),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                dialog = null
                viewModel.delete()
            },
            onDismiss = { dialog = null },
        )

        null -> Unit
    }

    val target = mergeTarget?.let { id -> state.categories.firstOrNull { it.id == id } }
    if (target != null && existing != null) {
        val usage = state.usage
        ConfirmDialog(
            title = stringResource(R.string.category_merge_confirm_title, target.name),
            text = stringResource(
                R.string.category_merge_confirm_text,
                usage?.transactions ?: 0,
                usage?.rules ?: 0,
                target.name,
                existing.name,
            ) + if ((usage?.subcategories ?: 0) > 0) "\n\n" + stringResource(R.string.category_merge_confirm_subs) else "",
            confirmLabel = stringResource(R.string.category_merge_confirm),
            onConfirm = {
                mergeTarget = null
                viewModel.merge(target.id)
            },
            onDismiss = { mergeTarget = null },
        )
    }

    archiveRules?.let { names ->
        ConfirmDialog(
            title = stringResource(R.string.category_archive_title),
            text = if (names.isEmpty()) {
                stringResource(R.string.category_archive_text)
            } else {
                stringResource(R.string.category_archive_text_rules, names.joinToString(", "))
            },
            confirmLabel = stringResource(R.string.category_archive),
            onConfirm = viewModel::archive,
            onDismiss = viewModel::dismissArchive,
        )
    }
}

/** Hodnota voľby „hlavná kategória“ v paneli presunu (OptionSheet nepozná `null` ako voľbu). */
private const val NO_PARENT = -1L

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(top = 8.dp).semantics { heading() },
    )
}

@Composable
private fun ErrorText(violation: Violation) {
    val res = when (violation) {
        Violation.NAME_DUPLICATE -> R.string.category_error_duplicate
        Violation.NAME_TOO_LONG -> R.string.category_error_too_long
        Violation.LAST_ACTIVE_MAIN_CATEGORY -> R.string.category_error_last_main
        else -> R.string.category_error_generic
    }
    Text(stringResource(res), style = MaterialTheme.typography.bodyMedium, color = GrosTheme.colors.warning)
}

/** Výber farby hlavnej kategórie zo 6 farieb palety; ukážka s prvým písmenom názvu. */
@Composable
private fun ColorPicker(selected: Int, letter: String, onSelect: (Int) -> Unit) {
    val names = listOf(
        R.string.color_green,
        R.string.color_purple,
        R.string.color_orange,
        R.string.color_blue,
        R.string.color_pink,
        R.string.color_grey,
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        repeat(CategoryRules.COLOR_COUNT) { index ->
            val color = GrosTheme.colors.category(index)
            val isSelected = index == selected
            val description = stringResource(names[index])
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(GrosShapes.cardSmall)
                    .background(color.container)
                    .then(if (isSelected) Modifier.border(2.dp, color.content, GrosShapes.cardSmall) else Modifier)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(index) }
                    .semantics { contentDescription = description },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    GrosIcon(R.drawable.ic_check, null, tint = color.content)
                } else {
                    Text(letter.trim().take(1).uppercase(), style = MaterialTheme.typography.titleMedium, color = color.content)
                }
            }
        }
    }
}
