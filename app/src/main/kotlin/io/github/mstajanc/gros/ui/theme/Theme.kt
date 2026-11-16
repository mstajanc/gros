package io.github.mstajanc.gros.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Téma appky. Dynamické farby (Material You) sa zámerne nepoužívajú, aby vzhľad zodpovedal tokenom.
 * Do M6 existuje len tmavá téma; svetlá pribudne ako druhá sada tých istých tokenov.
 */
@Composable
fun GrosTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalGrosColors provides DarkGrosColors) {
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
