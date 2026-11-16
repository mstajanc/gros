package io.github.mstajanc.gros.domain

import kotlin.test.assertEquals
import org.junit.Test

class AmountInputTest {

    private fun type(keys: String, start: AmountInput = AmountInput()): AmountInput =
        keys.fold(start) { input, key ->
            when (key) {
                ',' -> input.comma()
                '<' -> input.backspace()
                else -> input.digit(key.digitToInt())
            }
        }

    @Test
    fun `digits build a whole amount`() {
        assertEquals(4_200, type("42").cents)
        assertEquals("42", type("42").display)
    }

    @Test
    fun `comma starts the fraction with at most two digits`() {
        assertEquals(4_230, type("42,3").cents)
        assertEquals("42,3", type("42,3").display)
        assertEquals(4_230, type("42,30").cents)
        assertEquals("42,30", type("42,30").display)
        assertEquals(4_235, type("42,359").cents)
        assertEquals("42,35", type("42,359").display)
    }

    @Test
    fun `second comma is ignored`() {
        assertEquals("42,3", type("42,,3,").display)
    }

    @Test
    fun `comma on empty input gives zero`() {
        assertEquals("0,", type(",").display)
        assertEquals(5, type(",05").cents)
    }

    @Test
    fun `leading zeros are not kept`() {
        assertEquals("0", type("00").display)
        assertEquals("7", type("007").display)
        assertEquals(700, type("007").cents)
    }

    @Test
    fun `empty input is zero`() {
        assertEquals(0, AmountInput().cents)
        assertEquals("0", AmountInput().display)
    }

    @Test
    fun `whole part is limited to the maximum amount`() {
        val max = type("99999999,999")
        assertEquals(Money.MAX_CENTS, max.cents)
        assertEquals("9 999 999,99", max.display)
    }

    @Test
    fun `display groups thousands with a non-breaking space`() {
        assertEquals("1 234", type("1234").display)
        assertEquals("1 234,5", type("1234,5").display)
    }

    @Test
    fun `backspace removes the last key`() {
        assertEquals("42", type("42,<").display)
        assertEquals("4", type("42<").display)
        assertEquals("0", type("4<<<").display)
        assertEquals(0, type("4<<<").cents)
    }

    @Test
    fun `fromCents prefills the editor`() {
        assertEquals("42,30", AmountInput.fromCents(4_230).display)
        assertEquals("520", AmountInput.fromCents(52_000).display)
        assertEquals("0,05", AmountInput.fromCents(5).display)
        assertEquals("0", AmountInput.fromCents(0).display)
        assertEquals("85", AmountInput.fromCents(-8_500).display)
        assertEquals(4_230, AmountInput.fromCents(4_230).cents)
        assertEquals(4_231, type("<1", AmountInput.fromCents(4_230)).cents)
    }
}
