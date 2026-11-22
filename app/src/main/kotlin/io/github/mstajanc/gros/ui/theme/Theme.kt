package io.github.mstajanc.gros.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Téma appky. Dynamické farby (Material You) sa zámerne nepoužívajú, aby vzhľad zodpovedal tokenom.
 * Do M6 existuje len tmavá téma; svetlá pribudne ako druhá sada tých istých tokenov.
 */
@Composable
fun GrosTheme(textScale: Float = 1f, content: @Composable () -> Unit) {
    // Voľba veľkosti písma (F4) násobí systémovú veľkosť písma; rozmery v dp sa nemenia.
    val density = LocalDensity.current
    val scaled = Density(density.density, density.fontScale * textScale)
    CompositionLocalProvider(LocalGrosColors provides DarkGrosColors, LocalDensity provides scaled) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
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
