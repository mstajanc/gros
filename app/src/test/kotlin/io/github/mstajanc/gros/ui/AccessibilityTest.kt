package io.github.mstajanc.gros.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import io.github.mstajanc.gros.R
import io.github.mstajanc.gros.data.settings.TextSize
import io.github.mstajanc.gros.ui.common.CircleIconButton
import io.github.mstajanc.gros.ui.common.FieldTile
import io.github.mstajanc.gros.ui.common.FilterChip
import io.github.mstajanc.gros.ui.common.PrimaryButton
import io.github.mstajanc.gros.ui.common.ReorderableColumn
import io.github.mstajanc.gros.ui.common.ScreenHeader
import io.github.mstajanc.gros.ui.common.SecondaryButton
import io.github.mstajanc.gros.ui.common.SegmentedControl
import io.github.mstajanc.gros.ui.common.SwitchRow
import io.github.mstajanc.gros.ui.theme.GrosTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * Spoločné ovládacie prvky (z nich sú zložené všetky obrazovky): každý klikateľný prvok má aspoň
 * 48 × 48 dp a TalkBack má čo prečítať, v oboch témach a aj pri písme 200 %.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AccessibilityTest {

    @get:Rule
    val compose = createComposeRule()

    @Composable
    private fun Controls() {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            ScreenHeader("Nadpis", onBack = {})
            CircleIconButton(R.drawable.ic_search, "Hľadať", onClick = {})
            SegmentedControl(options = listOf("Systém", "Svetlá", "Tmavá"), selectedIndex = 0, onSelect = {})
            FilterChip("Všetky účty", selected = false, onClick = {})
            FieldTile(label = "Dátum", value = "Dnes", onClick = {})
            PrimaryButton("Uložiť", onClick = {})
            SecondaryButton("Zrušiť", onClick = {})
            SwitchRow(label = "Zámok", checked = true, onCheckedChange = {})
            ReorderableColumn(
                items = listOf(1L, 2L),
                key = { it },
                label = { "Účet $it" },
                onReorder = {},
            ) { item, handle, modifier ->
                Column(modifier) {
                    Text("Účet $item")
                    handle()
                }
            }
        }
    }

    private fun check(dark: Boolean, fontScale: Float) {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                GrosTheme(dark = dark, textSize = TextSize.STANDARD) { Controls() }
            }
        }
        val clickable = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick), useUnmergedTree = false)
            .fetchSemanticsNodes()
        assertTrue("žiadne klikateľné prvky", clickable.size >= 9)
        val density = compose.density
        clickable.forEach { node ->
            val width = with(density) { node.size.width.toDp() }
            val height = with(density) { node.size.height.toDp() }
            val name = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString()
                ?: node.config.getOrNull(SemanticsProperties.Text)?.joinToString()
            assertTrue("prvok „$name“ má $width × $height", width.value >= 47.5f && height.value >= 47.5f)
            assertTrue("klikateľný prvok bez popisu pre TalkBack", !name.isNullOrBlank())
        }
    }

    @Test
    fun `tmavá téma`() = check(dark = true, fontScale = 1f)

    @Test
    fun `svetlá téma`() = check(dark = false, fontScale = 1f)

    @Test
    fun `písmo 200 percent`() = check(dark = false, fontScale = 2f)
}
