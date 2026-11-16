package io.github.mstajanc.gros.domain

import java.text.Normalizer
import java.util.Locale

/** Poznámka transakcie a jej podoba na hľadanie bez ohľadu na veľkosť písmen a diakritiku. */
object NoteNormalizer {

    /** Znak, ktorým sa v SQL `LIKE ... ESCAPE` ruší význam `%` a `_`. */
    const val LIKE_ESCAPE: Char = '\\'

    private val combiningMarks = Regex("\\p{Mn}+")

    /** Malé písmená bez diakritiky („Školská TAŠKA“ → „skolska taska“). */
    fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(combiningMarks, "")
            .lowercase(Locale.ROOT)

    /** Poznámka tak, ako sa ukladá: bez okrajových medzier, prázdna je `null`. */
    fun clean(note: String?): String? = note?.trim()?.takeIf { it.isNotEmpty() }

    /** Hodnota stĺpca `noteSearch` pre danú poznámku. */
    fun toSearch(note: String?): String? = clean(note)?.let(::normalize)

    /** Zruší význam zástupných znakov `LIKE` vo vstupe používateľa. */
    fun escapeLike(text: String): String = buildString(text.length + 4) {
        for (char in text) {
            if (char == LIKE_ESCAPE || char == '%' || char == '_') append(LIKE_ESCAPE)
            append(char)
        }
    }

    /** Vzor pre `noteSearch LIKE :pattern ESCAPE '\'`; pri prázdnom vstupe `null`. */
    fun containsPattern(query: String): String? {
        val normalized = toSearch(query) ?: return null
        return "%${escapeLike(normalized)}%"
    }
}
