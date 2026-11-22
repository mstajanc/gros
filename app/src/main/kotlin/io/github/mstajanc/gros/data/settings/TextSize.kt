package io.github.mstajanc.gros.data.settings

/**
 * Veľkosť písma v appke (F4). Faktor sa násobí systémovou veľkosťou písma, takže zväčšenie
 * nastavené v Androide ostáva zachované.
 */
enum class TextSize(val factor: Float) {
    SMALL(0.9f),
    STANDARD(1.0f),
    LARGE(1.15f),
}
