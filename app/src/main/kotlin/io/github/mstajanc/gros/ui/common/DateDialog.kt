package io.github.mstajanc.gros.ui.common

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.mstajanc.gros.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private fun LocalDate.toPickerMillis(): Long = atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

/** Výber dátumu; [minDate] a [maxDate] (vrátane) obmedzia, čo sa dá zvoliť. */
@Composable
fun DateDialog(
    date: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    minDate: LocalDate? = null,
    maxDate: LocalDate? = null,
) {
    val minMillis = minDate?.toPickerMillis()
    val maxMillis = maxDate?.toPickerMillis()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = date.toPickerMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                (minMillis == null || utcTimeMillis >= minMillis) && (maxMillis == null || utcTimeMillis <= maxMillis)

            override fun isSelectableYear(year: Int): Boolean =
                (minDate == null || year >= minDate.year) && (maxDate == null || year <= maxDate.year)
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                },
            ) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = MaterialTheme.colorScheme.onSurface)
            }
        },
    ) {
        DatePicker(state = pickerState)
    }
}
