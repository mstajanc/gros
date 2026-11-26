package io.github.mstajanc.gros.data.settings

/**
 * Veľkosť písma v appke (F4). Faktor sa násobí systémovou veľkosťou písma, takže zväčšenie
 * nastavené v Androide ostáva zachované.
 */
enum class TextSize(val factor: Float) {
    SMALL(0.9f),
    STANDARD(1.0f),
    LARGE(1.15f),
    ;

    /**
     * Výsledná veľkosť písma je najviac 200 % (H2). Systémovú veľkosť nad 200 % appka nezmenšuje,
     * len ju už ďalej nezväčší.
     */
    fun fontScale(systemFontScale: Float): Float =
        minOf(systemFontScale * factor, maxOf(systemFontScale, MAX_FONT_SCALE))

    companion object {
        const val MAX_FONT_SCALE = 2.0f
    }
}
