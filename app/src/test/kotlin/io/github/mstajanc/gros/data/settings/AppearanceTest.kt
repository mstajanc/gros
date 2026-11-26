package io.github.mstajanc.gros.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceTest {

    @Test
    fun `voľba písma násobí systémovú veľkosť`() {
        assertEquals(1.15f, TextSize.LARGE.fontScale(1.0f), 0.0001f)
        assertEquals(0.9f, TextSize.SMALL.fontScale(1.0f), 0.0001f)
        assertEquals(1.5f, TextSize.STANDARD.fontScale(1.5f), 0.0001f)
    }

    @Test
    fun `výsledná veľkosť písma je najviac 200 percent (H2)`() {
        assertEquals(2.0f, TextSize.LARGE.fontScale(2.0f), 0.0001f)
        assertEquals(2.0f, TextSize.LARGE.fontScale(1.8f), 0.0001f)
        assertEquals(1.8f, TextSize.SMALL.fontScale(2.0f), 0.0001f)
    }

    @Test
    fun `väčšie systémové písmo appka nezmenšuje`() {
        assertEquals(2.2f, TextSize.STANDARD.fontScale(2.2f), 0.0001f)
        assertEquals(2.2f, TextSize.LARGE.fontScale(2.2f), 0.0001f)
    }

    @Test
    fun `téma podľa voľby a systému (H1)`() {
        assertEquals(true, ThemeMode.DARK.isDark(systemDark = false))
        assertEquals(false, ThemeMode.LIGHT.isDark(systemDark = true))
        assertEquals(true, ThemeMode.SYSTEM.isDark(systemDark = true))
        assertEquals(false, ThemeMode.SYSTEM.isDark(systemDark = false))
    }

    @Test
    fun `predvolená téma je tmavá`() {
        assertEquals(ThemeMode.DARK, ThemeMode.DEFAULT)
    }
}
