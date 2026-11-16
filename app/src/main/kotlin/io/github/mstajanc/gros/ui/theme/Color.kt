package io.github.mstajanc.gros.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Dizajnové tokeny tmavej témy (docs/SPEC.md, sekcia Dizajnové tokeny). */
internal object DarkTokens {
    val background = Color(0xFF0E1311)
    val surface = Color(0xFF171E1B)
    val surfaceVariant = Color(0xFF202925)
    val outline = Color(0xFF2A3530)
    val onSurface = Color(0xFFEDF2EF)
    val onSurfaceVariant = Color(0xFFA3B0A9)
    val primary = Color(0xFF6EE7B0)
    val onPrimary = Color(0xFF0E1311)
    val transfer = Color(0xFF9CC0FF)
    val transferContainer = Color(0xFF24304A)
    val warning = Color(0xFFFFB36B)

    val categoryPalette = listOf(
        CategoryColor(container = Color(0xFF2B3A33), content = Color(0xFF6EE7B0)), // zelená
        CategoryColor(container = Color(0xFF2E2B40), content = Color(0xFFC5B8FF)), // fialová
        CategoryColor(container = Color(0xFF3A3226), content = Color(0xFFFFB36B)), // oranžová
        CategoryColor(container = Color(0xFF24304A), content = Color(0xFF9CC0FF)), // modrá
        CategoryColor(container = Color(0xFF3A2834), content = Color(0xFFF5A3C7)), // ružová
        CategoryColor(container = Color(0xFF2A3530), content = Color(0xFFC9D4CE)), // sivá
    )
}

/** Dvojica farieb kategórie: pozadie ikony a farba písmena či pruhu. */
@Immutable
data class CategoryColor(val container: Color, val content: Color)

/** Farby, ktoré Material 3 nepozná: prevody, varovanie a paleta kategórií. */
@Immutable
data class GrosColors(
    val transfer: Color,
    val transferContainer: Color,
    val warning: Color,
    val categoryPalette: List<CategoryColor>,
) {
    /** `Category.color` je index do palety; neznámy index padne na poslednú (sivú) farbu. */
    fun category(index: Int): CategoryColor = categoryPalette.getOrElse(index) { categoryPalette.last() }
}

internal val DarkGrosColors = GrosColors(
    transfer = DarkTokens.transfer,
    transferContainer = DarkTokens.transferContainer,
    warning = DarkTokens.warning,
    categoryPalette = DarkTokens.categoryPalette,
)

/**
 * Material 3 schéma zložená výhradne z tokenov, aby nikde nepresvitali predvolené farby knižnice.
 * Chybové stavy používajú `warning` (rozhodnutie B14).
 */
internal val DarkColorScheme: ColorScheme = darkColorScheme(
    primary = DarkTokens.primary,
    onPrimary = DarkTokens.onPrimary,
    primaryContainer = DarkTokens.categoryPalette[0].container,
    onPrimaryContainer = DarkTokens.primary,
    inversePrimary = DarkTokens.categoryPalette[0].container,
    secondary = DarkTokens.primary,
    onSecondary = DarkTokens.onPrimary,
    secondaryContainer = DarkTokens.surfaceVariant,
    onSecondaryContainer = DarkTokens.onSurface,
    tertiary = DarkTokens.transfer,
    onTertiary = DarkTokens.background,
    tertiaryContainer = DarkTokens.transferContainer,
    onTertiaryContainer = DarkTokens.transfer,
    background = DarkTokens.background,
    onBackground = DarkTokens.onSurface,
    surface = DarkTokens.surface,
    onSurface = DarkTokens.onSurface,
    surfaceVariant = DarkTokens.surfaceVariant,
    onSurfaceVariant = DarkTokens.onSurfaceVariant,
    surfaceTint = Color.Transparent,
    inverseSurface = DarkTokens.onSurface,
    inverseOnSurface = DarkTokens.background,
    error = DarkTokens.warning,
    onError = DarkTokens.background,
    errorContainer = DarkTokens.categoryPalette[2].container,
    onErrorContainer = DarkTokens.warning,
    outline = DarkTokens.outline,
    outlineVariant = DarkTokens.outline,
    scrim = Color.Black,
    surfaceBright = DarkTokens.surfaceVariant,
    surfaceDim = DarkTokens.background,
    surfaceContainerLowest = DarkTokens.background,
    surfaceContainerLow = DarkTokens.surface,
    surfaceContainer = DarkTokens.surface,
    surfaceContainerHigh = DarkTokens.surfaceVariant,
    surfaceContainerHighest = DarkTokens.surfaceVariant,
)

internal val LocalGrosColors = staticCompositionLocalOf { DarkGrosColors }
