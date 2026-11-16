package io.github.mstajanc.gros.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.mstajanc.gros.R

// Obe písma sú pribalené ako variabilné súbory (OFL); rezy sa odvodzujú osou „wght“.
private fun variableFont(resId: Int, weight: FontWeight) = Font(
    resId = resId,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Inter – bežný text. */
val InterFamily = FontFamily(
    variableFont(R.font.inter_variable, FontWeight.Normal),
    variableFont(R.font.inter_variable, FontWeight.Medium),
    variableFont(R.font.inter_variable, FontWeight.SemiBold),
    variableFont(R.font.inter_variable, FontWeight.Bold),
)

/** Space Grotesk – nadpisy a veľké sumy. */
val SpaceGroteskFamily = FontFamily(
    variableFont(R.font.space_grotesk_variable, FontWeight.Normal),
    variableFont(R.font.space_grotesk_variable, FontWeight.Medium),
    variableFont(R.font.space_grotesk_variable, FontWeight.SemiBold),
    variableFont(R.font.space_grotesk_variable, FontWeight.Bold),
)

private fun heading(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Medium) = TextStyle(
    fontFamily = SpaceGroteskFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)

private fun text(size: Int, lineHeight: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = InterFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
)

internal val GrosTypography = Typography(
    // Veľké sumy
    displayLarge = heading(46, 54),
    displayMedium = heading(38, 46),
    displaySmall = heading(30, 38),
    // Nadpisy obrazoviek
    headlineLarge = heading(26, 32, FontWeight.SemiBold),
    headlineMedium = heading(22, 28, FontWeight.SemiBold),
    headlineSmall = heading(20, 26, FontWeight.SemiBold),
    // Nadpisy sekcií a riadkov
    titleLarge = text(18, 24, FontWeight.SemiBold),
    titleMedium = text(16, 22, FontWeight.Medium),
    titleSmall = text(14, 20, FontWeight.Medium),
    bodyLarge = text(16, 24),
    bodyMedium = text(14, 20),
    bodySmall = text(13, 18),
    labelLarge = text(15, 20, FontWeight.SemiBold),
    labelMedium = text(13, 18, FontWeight.Medium),
    labelSmall = text(11, 16, FontWeight.Medium),
)

/** Sumy sa sádzajú tabuľkovými číslicami, aby stĺpce čísel lícovali. */
fun TextStyle.tabularNumbers(): TextStyle = copy(fontFeatureSettings = "tnum")
