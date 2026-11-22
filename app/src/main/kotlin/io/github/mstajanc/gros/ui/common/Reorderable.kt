package io.github.mstajanc.gros.ui.common

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.github.mstajanc.gros.R

/**
 * Stĺpec, ktorého poradie sa mení potiahnutím za úchyt (B16). Vlastná implementácia bez knižnice;
 * pre TalkBack má úchyt akcie „posunúť vyššie“ a „posunúť nižšie“. Riadok dostane úchyt
 * a modifikátor, ktorý ho pri ťahaní posúva; [onReorder] dostane nové poradie kľúčov.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    key: (T) -> Long,
    label: (T) -> String,
    onReorder: (List<Long>) -> Unit,
    spacing: Dp = 10.dp,
    itemContent: @Composable (item: T, handle: @Composable () -> Unit, modifier: Modifier) -> Unit,
) {
    val byKey = items.associateBy(key)
    val storedOrder = items.map(key)
    // Počas ťahania platí miestne poradie; po uložení ho nahradí poradie z databázy.
    var order by remember(storedOrder) { mutableStateOf(storedOrder) }
    var draggedId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var itemHeight by remember { mutableIntStateOf(0) }
    val spacingPx = with(LocalDensity.current) { spacing.toPx() }
    val currentOrder by rememberUpdatedState(order)

    fun move(id: Long, delta: Int): Boolean {
        val from = order.indexOf(id)
        val to = from + delta
        if (from < 0 || to !in order.indices) return false
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        return true
    }

    Column(verticalArrangement = Arrangement.spacedBy(spacing)) {
        order.forEach { id ->
            val item = byKey[id] ?: return@forEach
            key(id) {
                val isDragged = draggedId == id
                val moveUp = stringResource(R.string.reorder_move_up)
                val moveDown = stringResource(R.string.reorder_move_down)
                val dragDescription = stringResource(R.string.reorder_drag, label(item))
                itemContent(
                    item,
                    {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .semantics {
                                    contentDescription = dragDescription
                                    customActions = listOf(
                                        CustomAccessibilityAction(moveUp) {
                                            move(id, -1).also { moved -> if (moved) onReorder(order) }
                                        },
                                        CustomAccessibilityAction(moveDown) {
                                            move(id, 1).also { moved -> if (moved) onReorder(order) }
                                        },
                                    )
                                }
                                .pointerInput(id) {
                                    detectDragGestures(
                                        onDragStart = {
                                            draggedId = id
                                            dragOffset = 0f
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            dragOffset += amount.y
                                            val step = itemHeight + spacingPx
                                            if (step > 0f) {
                                                // Po prekročení polovice susedného riadka si riadky vymenia miesto.
                                                if (dragOffset > step / 2 && move(id, 1)) dragOffset -= step
                                                if (dragOffset < -step / 2 && move(id, -1)) dragOffset += step
                                            }
                                        },
                                        onDragEnd = {
                                            draggedId = null
                                            dragOffset = 0f
                                            onReorder(currentOrder)
                                        },
                                        onDragCancel = {
                                            draggedId = null
                                            dragOffset = 0f
                                        },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            GrosIcon(R.drawable.ic_grip_vertical, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    Modifier
                        .zIndex(if (isDragged) 1f else 0f)
                        .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
                        .onSizeChanged { itemHeight = it.height },
                )
            }
        }
    }
}
