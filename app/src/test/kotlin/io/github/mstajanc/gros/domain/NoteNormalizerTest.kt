package io.github.mstajanc.gros.domain

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class NoteNormalizerTest {

    @Test
    fun `normalize lowercases and strips diacritics`() {
        assertEquals("alza", NoteNormalizer.normalize("Alza"))
        assertEquals("alza skolska taska", NoteNormalizer.normalize("Alza školská TAŠKA"))
        assertEquals("lsctzyaieuaondrl", NoteNormalizer.normalize("ľščťžýáíéúäôňďŕĺ"))
        assertEquals("lsctzyaieuaondrl", NoteNormalizer.normalize("ĽŠČŤŽÝÁÍÉÚÄÔŇĎŔĹ"))
    }

    @Test
    fun `normalize keeps digits punctuation and inner spaces`() {
        assertEquals("kabel usb-c 2 m", NoteNormalizer.normalize("Kábel USB-C 2 m"))
    }

    @Test
    fun `clean trims and turns blank into null`() {
        assertEquals("Alza", NoteNormalizer.clean("  Alza  "))
        assertNull(NoteNormalizer.clean("   "))
        assertNull(NoteNormalizer.clean(""))
        assertNull(NoteNormalizer.clean(null))
    }

    @Test
    fun `toSearch derives the search column from a note`() {
        assertEquals("alza sluchadla", NoteNormalizer.toSearch("  Alza slúchadlá "))
        assertNull(NoteNormalizer.toSearch("  "))
        assertNull(NoteNormalizer.toSearch(null))
    }

    @Test
    fun `search for alza matches Alza`() {
        val stored = NoteNormalizer.toSearch("Alza kábel USB-C")!!
        val query = NoteNormalizer.normalize("alza")
        assertEquals(true, stored.contains(query))
    }

    @Test
    fun `escapeLike escapes wildcard and escape characters`() {
        assertEquals("100\\%", NoteNormalizer.escapeLike("100%"))
        assertEquals("a\\_b", NoteNormalizer.escapeLike("a_b"))
        assertEquals("a\\\\b", NoteNormalizer.escapeLike("a\\b"))
        assertEquals("alza", NoteNormalizer.escapeLike("alza"))
    }

    @Test
    fun `containsPattern builds an escaped LIKE pattern from user input`() {
        assertEquals("%alza%", NoteNormalizer.containsPattern(" Alza "))
        assertEquals("%50\\% zlava%", NoteNormalizer.containsPattern("50% zľava"))
        assertNull(NoteNormalizer.containsPattern("   "))
    }
}
