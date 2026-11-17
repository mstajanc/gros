package io.github.mstajanc.gros.ui.plan

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.mstajanc.gros.R
import io.github.mstajanc.gros.domain.PluralForm
import io.github.mstajanc.gros.domain.SlovakDates
import io.github.mstajanc.gros.domain.SlovakText
import io.github.mstajanc.gros.domain.model.Frequency
import java.time.LocalDate

/** „Mesačne“, „Každé 3 mesiace“, „Každých 6 týždňov“, „Ročne“. */
@Composable
fun repeatShort(frequency: Frequency, interval: Int): String {
    if (interval <= 1) {
        return stringResource(
            when (frequency) {
                Frequency.WEEKLY -> R.string.repeat_weekly
                Frequency.MONTHLY -> R.string.repeat_monthly
                Frequency.YEARLY -> R.string.repeat_yearly
            },
        )
    }
    val few = SlovakText.pluralForm(interval) == PluralForm.FEW
    val res = when (frequency) {
        Frequency.WEEKLY -> if (few) R.string.repeat_weeks_few else R.string.repeat_weeks_many
        Frequency.MONTHLY -> if (few) R.string.repeat_months_few else R.string.repeat_months_many
        Frequency.YEARLY -> if (few) R.string.repeat_years_few else R.string.repeat_years_many
    }
    return stringResource(res, interval)
}

/**
 * Popis opakovania odvodený z „Ďalšej platby“ (rozhodnutie A11):
 * „Mesačne, 1. deň“, „Týždenne, pondelok“, „Ročne, 3. 3.“.
 */
@Composable
fun repeatLong(frequency: Frequency, interval: Int, nextDate: LocalDate): String {
    val detail = when (frequency) {
        Frequency.WEEKLY -> SlovakDates.weekdayName(nextDate)
        Frequency.MONTHLY -> stringResource(R.string.repeat_day_of_month, nextDate.dayOfMonth)
        Frequency.YEARLY -> "${nextDate.dayOfMonth}. ${nextDate.monthValue}."
    }
    return stringResource(R.string.repeat_with_detail, repeatShort(frequency, interval), detail)
}
