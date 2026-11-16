package io.github.mstajanc.gros.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Tvary podľa tokenov: karty 24 / 18 / 14 dp, tlačidlá 16 dp, čipy a okrúhle tlačidlá plne zaoblené. */
object GrosShapes {
    val cardLarge = RoundedCornerShape(24.dp)
    val cardMedium = RoundedCornerShape(18.dp)
    val cardSmall = RoundedCornerShape(14.dp)
    val button = RoundedCornerShape(16.dp)
    val pill = CircleShape
}

internal val MaterialShapes = Shapes(
    extraSmall = GrosShapes.cardSmall,
    small = GrosShapes.cardSmall,
    medium = GrosShapes.cardMedium,
    large = GrosShapes.cardLarge,
    extraLarge = GrosShapes.cardLarge,
)
