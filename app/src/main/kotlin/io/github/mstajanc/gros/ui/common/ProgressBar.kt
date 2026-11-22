package io.github.mstajanc.gros.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mstajanc.gros.ui.theme.GrosShapes

/** Vodorovný pruh (rozpočty, reporty): podklad `outline`, výplň [color] v pomere [fraction]. */
@Composable
fun ProgressBar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 10.dp) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(GrosShapes.pill)
            .background(MaterialTheme.colorScheme.outline),
    ) {
        val value = fraction.coerceIn(0f, 1f)
        if (value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(value)
                    .fillMaxHeight()
                    .clip(GrosShapes.pill)
                    .background(color),
            )
        }
    }
}
