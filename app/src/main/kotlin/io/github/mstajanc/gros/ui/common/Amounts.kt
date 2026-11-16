package io.github.mstajanc.gros.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.mstajanc.gros.R
import io.github.mstajanc.gros.domain.AmountInput
import io.github.mstajanc.gros.ui.theme.GrosShapes
import io.github.mstajanc.gros.ui.theme.tabularNumbers

/** Suma: tabuľkové číslice a nikdy sa nezalamuje. */
@Composable
fun AmountText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
    textAlign: TextAlign? = null,
) {
    Text(text, modifier = modifier, style = style.tabularNumbers(), color = color, maxLines = 1, softWrap = false, textAlign = textAlign)
}

@Composable
fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
    checkedTrackColor = MaterialTheme.colorScheme.primary,
    uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
    uncheckedTrackColor = MaterialTheme.colorScheme.outline,
    uncheckedBorderColor = MaterialTheme.colorScheme.outline,
)

/** Číselná klávesnica appky: 1–9, čiarka, 0 a mazanie. */
@Composable
fun AmountKeypad(onInput: (AmountInput) -> Unit, input: AmountInput, modifier: Modifier = Modifier) {
    val rows = listOf("123", "456", "789", ",0<")
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { key ->
                    val description = when (key) {
                        ',' -> stringResource(R.string.keypad_comma)
                        '<' -> stringResource(R.string.keypad_backspace)
                        else -> key.toString()
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                            .clip(GrosShapes.cardSmall)
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable(role = Role.Button) {
                                onInput(
                                    when (key) {
                                        ',' -> input.comma()
                                        '<' -> input.backspace()
                                        else -> input.digit(key.digitToInt())
                                    },
                                )
                            }
                            .semantics { contentDescription = description },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (key == '<') {
                            GrosIcon(R.drawable.ic_delete, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(key.toString(), style = MaterialTheme.typography.headlineSmall)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Spodný panel na zadanie sumy mimo Novej transakcie (počiatočný stav, oprava zostatku),
 * s rovnakou klávesnicou appky. Pri [allowNegative] pribudne prepínač „Záporný stav“.
 */
@Composable
fun AmountSheet(
    title: String,
    initialCents: Long,
    allowNegative: Boolean,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(AmountInput.fromCents(initialCents).text) }
    var negative by rememberSaveable { mutableStateOf(initialCents < 0) }
    val input = AmountInput(text)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenPadding).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            AmountText(
                (if (negative && input.cents != 0L) "−" else "") + input.display + " €",
                style = MaterialTheme.typography.displayMedium,
            )
            if (allowNegative) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(GrosShapes.cardSmall)
                        .clickable(role = Role.Switch) { negative = !negative }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.account_negative),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = negative, onCheckedChange = null, colors = switchColors())
                }
            }
            AmountKeypad(input = input, onInput = { text = it.text })
            PrimaryButton(
                stringResource(R.string.action_done),
                onClick = { onConfirm(if (negative) -input.cents else input.cents) },
            )
        }
    }
}
