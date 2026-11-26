package io.github.mstajanc.gros.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import io.github.mstajanc.gros.data.settings.TextSize

/**
 * Téma appky. Dynamické farby (Material You) sa zámerne nepoužívajú, aby vzhľad zodpovedal tokenom.
 * Tmavá a svetlá téma sú dve sady tých istých tokenov (H1).
 */
@Composable
fun GrosTheme(dark: Boolean = true, textSize: TextSize = TextSize.STANDARD, content: @Composable () -> Unit) {
    // Voľba veľkosti písma (F4) násobí systémovú veľkosť písma, spolu najviac 200 % (H2).
    val density = LocalDensity.current
    val scaled = Density(density.density, textSize.fontScale(density.fontScale))
    val tokens = if (dark) DarkTokens else LightTokens
    val colors = remember(dark) { tokens.grosColors() }
    val scheme = remember(dark) { tokens.colorScheme(dark) }
    CompositionLocalProvider(LocalGrosColors provides colors, LocalDensity provides scaled) {
        MaterialTheme(
            colorScheme = scheme,
            typography = GrosTypography,
            shapes = MaterialShapes,
            content = content,
        )
    }
}

/** Prístup k farbám mimo Material 3: `GrosTheme.colors.transfer`. */
object GrosTheme {
    val colors: GrosColors
        @Composable
        @ReadOnlyComposable
        get() = LocalGrosColors.current
}
