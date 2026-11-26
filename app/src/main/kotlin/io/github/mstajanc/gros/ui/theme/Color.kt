package io.github.mstajanc.gros.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Jedna sada dizajnových tokenov (docs/SPEC.md, sekcia Dizajnové tokeny). */
internal class Tokens(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val outline: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val primary: Color,
    val onPrimary: Color,
    val transfer: Color,
    val transferContainer: Color,
    val warning: Color,
    val expense: Color,
    /** Poradie: zelená, fialová, oranžová, modrá, ružová, sivá (index = `Category.color`). */
    val categoryPalette: List<CategoryColor>,
)

internal val DarkTokens = Tokens(
    background = Color(0xFF0E1311),
    surface = Color(0xFF171E1B),
    surfaceVariant = Color(0xFF202925),
    outline = Color(0xFF2A3530),
    onSurface = Color(0xFFEDF2EF),
    onSurfaceVariant = Color(0xFFA3B0A9),
    primary = Color(0xFF6EE7B0),
    onPrimary = Color(0xFF0E1311),
    transfer = Color(0xFF9CC0FF),
    transferContainer = Color(0xFF24304A),
    warning = Color(0xFFFFB36B),
    expense = Color(0xFFFF8A8A),
    categoryPalette = listOf(
        CategoryColor(container = Color(0xFF2B3A33), content = Color(0xFF6EE7B0)),
        CategoryColor(container = Color(0xFF2E2B40), content = Color(0xFFC5B8FF)),
        CategoryColor(container = Color(0xFF3A3226), content = Color(0xFFFFB36B)),
        CategoryColor(container = Color(0xFF24304A), content = Color(0xFF9CC0FF)),
        CategoryColor(container = Color(0xFF3A2834), content = Color(0xFFF5A3C7)),
        CategoryColor(container = Color(0xFF2A3530), content = Color(0xFFC9D4CE)),
    ),
)

/** Svetlá téma (H1): kontrast každého textu voči každému pozadiu ≥ 5 : 1. */
internal val LightTokens = Tokens(
    background = Color(0xFFF3F6F4),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE8EEEA),
    outline = Color(0xFFC5D1CA),
    onSurface = Color(0xFF111915),
    onSurfaceVariant = Color(0xFF4B5851),
    primary = Color(0xFF0A7148),
    onPrimary = Color(0xFFFFFFFF),
    transfer = Color(0xFF2A5BB5),
    transferContainer = Color(0xFFDCE6FA),
    warning = Color(0xFF9A4A00),
    expense = Color(0xFFBA2D36),
    categoryPalette = listOf(
        CategoryColor(container = Color(0xFFD8F0E4), content = Color(0xFF0A7148)),
        CategoryColor(container = Color(0xFFE6E1FA), content = Color(0xFF5841B5)),
        CategoryColor(container = Color(0xFFFBE6CF), content = Color(0xFF8F4700)),
        CategoryColor(container = Color(0xFFDCE6FA), content = Color(0xFF2A5BB5)),
        CategoryColor(container = Color(0xFFF8DDE9), content = Color(0xFFA12F63)),
        CategoryColor(container = Color(0xFFE3E9E6), content = Color(0xFF4B5851)),
    ),
)

/** Dvojica farieb kategórie: pozadie ikony a farba písmena či pruhu. */
@Immutable
data class CategoryColor(val container: Color, val content: Color)

/** Farby, ktoré Material 3 nepozná: prevody, výdavky, varovanie a paleta kategórií. */
@Immutable
data class GrosColors(
    val transfer: Color,
    val transferContainer: Color,
    val warning: Color,
    /** Sumy výdavkov (rozhodnutie F2). */
    val expense: Color,
    val categoryPalette: List<CategoryColor>,
) {
    /** `Category.color` je index do palety; neznámy index padne na poslednú (sivú) farbu. */
    fun category(index: Int): CategoryColor = categoryPalette.getOrElse(index) { categoryPalette.last() }
}

internal fun Tokens.grosColors() = GrosColors(
    transfer = transfer,
    transferContainer = transferContainer,
    warning = warning,
    expense = expense,
    categoryPalette = categoryPalette,
)

/**
 * Material 3 schéma zložená výhradne z tokenov, aby nikde nepresvitali predvolené farby knižnice.
 * Chybové stavy používajú `warning` (rozhodnutie B14).
 */
internal fun Tokens.colorScheme(dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = categoryPalette[0].container,
        onPrimaryContainer = primary,
        inversePrimary = categoryPalette[0].container,
        secondary = primary,
        onSecondary = onPrimary,
        secondaryContainer = surfaceVariant,
        onSecondaryContainer = onSurface,
        tertiary = transfer,
        onTertiary = background,
        tertiaryContainer = transferContainer,
        onTertiaryContainer = transfer,
        background = background,
        onBackground = onSurface,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceTint = Color.Transparent,
        inverseSurface = onSurface,
        inverseOnSurface = background,
        error = warning,
        onError = background,
        errorContainer = categoryPalette[2].container,
        onErrorContainer = warning,
        outline = outline,
        outlineVariant = outline,
        scrim = Color.Black,
        surfaceBright = if (dark) surfaceVariant else surface,
        surfaceDim = if (dark) background else surfaceVariant,
        surfaceContainerLowest = if (dark) background else surface,
        surfaceContainerLow = surface,
        surfaceContainer = surface,
        surfaceContainerHigh = if (dark) surfaceVariant else surface,
        surfaceContainerHighest = surfaceVariant,
    )
}

internal val LocalGrosColors = staticCompositionLocalOf { DarkTokens.grosColors() }
