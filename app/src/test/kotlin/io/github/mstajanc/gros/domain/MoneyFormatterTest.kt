package io.github.mstajanc.gros.domain

import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.junit.Test

class MoneyFormatterTest {

    private val nbsp = ' '
    private val minus = '−'

    @Test
    fun `formats thousands with non-breaking spaces and decimal comma`() {
        assertEquals("1${nbsp}234,56${nbsp}€", MoneyFormatter.format(123_456))
        assertEquals("8${nbsp}525,50${nbsp}€", MoneyFormatter.format(852_550))
        assertEquals("1${nbsp}000${nbsp}000,00${nbsp}€", MoneyFormatter.format(100_000_000))
    }

    @Test
    fun `formats small amounts with leading zero and two decimals`() {
        assertEquals("0,00${nbsp}€", MoneyFormatter.format(0))
        assertEquals("0,05${nbsp}€", MoneyFormatter.format(5))
        assertEquals("0,50${nbsp}€", MoneyFormatter.format(50))
        assertEquals("85,00${nbsp}€", MoneyFormatter.format(8_500))
        assertEquals("999,99${nbsp}€", MoneyFormatter.format(99_999))
    }

    @Test
    fun `formats the maximum enterable amount`() {
        assertEquals("9${nbsp}999${nbsp}999,99${nbsp}€", MoneyFormatter.format(Money.MAX_CENTS))
    }

    @Test
    fun `negative amounts use the real minus sign`() {
        assertEquals("${minus}42,30${nbsp}€", MoneyFormatter.format(-4_230))
        assertEquals("${minus}1${nbsp}312,40${nbsp}€", MoneyFormatter.format(-131_240))
        assertEquals("${minus}0,01${nbsp}€", MoneyFormatter.format(-1))
    }

    @Test
    fun `sign ALWAYS adds plus to positive amounts and no sign to zero`() {
        assertEquals("+2${nbsp}900,00${nbsp}€", MoneyFormatter.format(290_000, SignDisplay.ALWAYS))
        assertEquals("${minus}61,20${nbsp}€", MoneyFormatter.format(-6_120, SignDisplay.ALWAYS))
        assertEquals("0,00${nbsp}€", MoneyFormatter.format(0, SignDisplay.ALWAYS))
    }

    @Test
    fun `sign NEVER shows the absolute value`() {
        assertEquals("300,00${nbsp}€", MoneyFormatter.format(-30_000, SignDisplay.NEVER))
        assertEquals("300,00${nbsp}€", MoneyFormatter.format(30_000, SignDisplay.NEVER))
    }

    @Test
    fun `output never contains ASCII hyphen or a breaking space`() {
        val formatted = MoneyFormatter.format(-123_456_789)
        assertFalse(formatted.contains('-'))
        assertFalse(formatted.contains(' '))
    }

    @Test
    fun `does not depend on the device locale`() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("${minus}1${nbsp}234,56${nbsp}€", MoneyFormatter.format(-123_456))
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            assertEquals("${minus}1${nbsp}234,56${nbsp}€", MoneyFormatter.format(-123_456))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun `extreme values do not overflow`() {
        assertEquals(
            "92${nbsp}233${nbsp}720${nbsp}368${nbsp}547${nbsp}758,07${nbsp}€",
            MoneyFormatter.format(Long.MAX_VALUE),
        )
        assertEquals(
            "${minus}92${nbsp}233${nbsp}720${nbsp}368${nbsp}547${nbsp}758,08${nbsp}€",
            MoneyFormatter.format(Long.MIN_VALUE),
        )
    }
}
