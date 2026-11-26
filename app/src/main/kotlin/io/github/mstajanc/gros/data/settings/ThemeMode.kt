package io.github.mstajanc.gros.data.settings

/** Voľba témy v Nastaveniach (H1). */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        SYSTEM -> systemDark
        LIGHT -> false
        DARK -> true
    }

    companion object {
        /** Tmavá, aby sa vzhľad po aktualizácii nezmenil sám (H1). */
        val DEFAULT = DARK
    }
}
