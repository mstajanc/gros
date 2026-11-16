package io.github.mstajanc.gros.ui.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mstajanc.gros.R
import io.github.mstajanc.gros.data.db.GrosDatabase
import io.github.mstajanc.gros.domain.MoneyFormatter
import io.github.mstajanc.gros.domain.SignDisplay
import io.github.mstajanc.gros.ui.theme.GrosShapes
import io.github.mstajanc.gros.ui.theme.GrosTheme
import io.github.mstajanc.gros.ui.theme.tabularNumbers

/**
 * Dočasná obrazovka míľnika M1: vzorka témy (farby, písma, tvary, formát súm) a stav databázy,
 * aby sa dalo podpísané APK overiť na telefóne. V M2 ju nahradí Prehľad.
 *
 * @param accountCount počet účtov v databáze; `null`, kým sa databáza otvára.
 */
@Composable
fun ThemePreviewScreen(accountCount: Int?) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Header(accountCount)
                TotalCard()
                AmountSamples()
                Section(stringResource(R.string.preview_section_colors)) { ColorTokens() }
                Section(stringResource(R.string.preview_section_categories)) { CategoryPalette() }
                Section(stringResource(R.string.preview_section_type)) { TypeSamples() }
                Button(
                    onClick = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    shape = GrosShapes.button,
                ) {
                    Text(stringResource(R.string.preview_button), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun Header(accountCount: Int?) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineLarge)
        Text(
            stringResource(R.string.preview_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (accountCount == null) {
                stringResource(R.string.preview_database_loading)
            } else {
                stringResource(R.string.preview_database, GrosDatabase.VERSION, accountCount)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TotalCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, GrosShapes.cardLarge)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.preview_total_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            MoneyFormatter.format(852_550),
            style = MaterialTheme.typography.displayMedium.tabularNumbers(),
            maxLines = 1,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SummaryTile(
                label = stringResource(R.string.preview_income),
                amount = MoneyFormatter.format(290_000, SignDisplay.ALWAYS),
                amountColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            SummaryTile(
                label = stringResource(R.string.preview_expenses),
                amount = MoneyFormatter.format(-131_240),
                amountColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SummaryTile(label: String, amount: String, amountColor: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, GrosShapes.cardSmall)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(amount, style = MaterialTheme.typography.titleMedium.tabularNumbers(), color = amountColor, maxLines = 1)
    }
}

@Composable
private fun AmountSamples() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, GrosShapes.cardMedium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.preview_transfer), style = MaterialTheme.typography.titleMedium)
            Text(
                MoneyFormatter.format(30_000, SignDisplay.NEVER),
                style = MaterialTheme.typography.titleMedium.tabularNumbers(),
                color = GrosTheme.colors.transfer,
                maxLines = 1,
            )
        }
        Text(
            stringResource(R.string.preview_over_budget, MoneyFormatter.format(400)),
            style = MaterialTheme.typography.labelMedium,
            color = GrosTheme.colors.warning,
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun ColorTokens() {
    val scheme = MaterialTheme.colorScheme
    val tokens = listOf(
        "background" to scheme.background,
        "surface" to scheme.surface,
        "surfaceVariant" to scheme.surfaceVariant,
        "outline" to scheme.outline,
        "onSurface" to scheme.onSurface,
        "onSurfaceVariant" to scheme.onSurfaceVariant,
        "primary" to scheme.primary,
        "transfer" to GrosTheme.colors.transfer,
        "transferContainer" to GrosTheme.colors.transferContainer,
        "warning" to GrosTheme.colors.warning,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tokens.forEach { (name, color) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(color, GrosShapes.pill)
                        .border(1.dp, scheme.outline, GrosShapes.pill),
                )
                // Názvy tokenov sú technické identifikátory, neprekladajú sa.
                Text(name, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun CategoryPalette() {
    val letters = listOf("P", "B", "A", "D", "Z", "O")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GrosTheme.colors.categoryPalette.forEachIndexed { index, color ->
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(color.container, GrosShapes.cardSmall),
                contentAlignment = Alignment.Center,
            ) {
                Text(letters[index % letters.size], style = MaterialTheme.typography.titleMedium, color = color.content)
            }
        }
    }
}

@Composable
private fun TypeSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(R.string.preview_type_heading), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.preview_type_body), style = MaterialTheme.typography.bodyLarge)
        Text(
            stringResource(R.string.preview_type_secondary),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
