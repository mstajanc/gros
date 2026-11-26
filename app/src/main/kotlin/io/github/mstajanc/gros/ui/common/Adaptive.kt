package io.github.mstajanc.gros.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * Rozloženia pre úzke okno a veľké písmo (responzivita: 360 dp, písmo 200 %). Pri bežnej veľkosti
 * vyzerajú ako obyčajný riadok; keď sa obsah nezmestí, prvky sa namiesto lámania slov zoradia pod seba.
 */

/**
 * Rovnako široké prvky vedľa seba (dvojica dlaždíc Príjmy / Výdavky). Ak sa text niektorého prvku
 * nezmestí na jeden riadok, prvky sa zoradia pod seba na celú šírku.
 */
@Composable
fun AdaptiveRow(modifier: Modifier = Modifier, spacing: Dp = 8.dp, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val gap = spacing.roundToPx()
        val width = constraints.maxWidth
        val count = measurables.size
        if (count == 0) return@Layout layout(width, 0) {}
        val cell = (width - gap * (count - 1)) / count
        val fits = measurables.all { it.maxIntrinsicWidth(Constraints.Infinity) <= cell }
        if (fits) {
            val height = measurables.maxOf { it.minIntrinsicHeight(cell) }
            val placeables = measurables.map { it.measure(Constraints.fixed(cell, height)) }
            layout(width, height) {
                placeables.forEachIndexed { index, placeable -> placeable.place(index * (cell + gap), 0) }
            }
        } else {
            val placeables = measurables.map { it.measure(Constraints(minWidth = width, maxWidth = width)) }
            val height = placeables.sumOf { it.height } + gap * (count - 1)
            layout(width, height) {
                var y = 0
                placeables.forEach { it.place(0, y); y += it.height + gap }
            }
        }
    }
}

/**
 * Riadok zoznamu: ikona, text a suma vpravo. Keby na text ostalo menej ako [minContentFraction]
 * šírky, suma sa presunie pod text (zarovnaná vpravo), takže sa názvy nelámu po písmenách.
 */
@Composable
fun ListItemLayout(
    leading: @Composable () -> Unit,
    content: @Composable () -> Unit,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    minContentFraction: Float = 0.45f,
) {
    Layout(contents = listOf(leading, content, trailing), modifier = modifier) { (lead, main, trail), constraints ->
        val gap = spacing.roundToPx()
        val width = constraints.maxWidth
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val leadP = lead.map { it.measure(loose) }
        val leadW = leadP.maxOfOrNull { it.width } ?: 0
        val leadH = leadP.maxOfOrNull { it.height } ?: 0
        val trailW = trail.maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0
        val contentX = if (leadW > 0) leadW + gap else 0
        val sideBySide = width - contentX - trailW - gap
        if (sideBySide >= width * minContentFraction) {
            val mainP = main.map { it.measure(Constraints(minWidth = sideBySide, maxWidth = sideBySide)) }
            val trailP = trail.map { it.measure(loose) }
            val height = maxOf(leadH, mainP.maxOfOrNull { it.height } ?: 0, trailP.maxOfOrNull { it.height } ?: 0)
                .coerceAtLeast(constraints.minHeight)
            layout(width, height) {
                leadP.forEach { it.place(0, (height - it.height) / 2) }
                mainP.forEach { it.place(contentX, (height - it.height) / 2) }
                trailP.forEach { it.place(width - it.width, (height - it.height) / 2) }
            }
        } else {
            val contentW = width - contentX
            val mainP = main.map { it.measure(Constraints(minWidth = contentW, maxWidth = contentW)) }
            val trailP = trail.map { it.measure(Constraints(maxWidth = contentW)) }
            val mainH = mainP.maxOfOrNull { it.height } ?: 0
            val height = maxOf(leadH, mainH + (trailP.maxOfOrNull { it.height } ?: 0))
                .coerceAtLeast(constraints.minHeight)
            layout(width, height) {
                leadP.forEach { it.place(0, 0) }
                mainP.forEach { it.place(contentX, 0) }
                trailP.forEach { it.place(width - it.width, mainH) }
            }
        }
    }
}
