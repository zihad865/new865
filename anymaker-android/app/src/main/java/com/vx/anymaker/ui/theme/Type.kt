package com.vx.anymaker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.vx.anymaker.R

/** Plus Jakarta Sans for Latin text; other scripts fall back to the system Noto fonts. */
val Jakarta = FontFamily(
    Font(R.font.plusjakartasans_regular, FontWeight.Normal),
    Font(R.font.plusjakartasans_medium, FontWeight.Medium),
    Font(R.font.plusjakartasans_semibold, FontWeight.SemiBold),
    Font(R.font.plusjakartasans_bold, FontWeight.Bold),
)

private val base = Typography()

val AnymakerTypography = Typography(
    displaySmall = base.displaySmall.copy(fontFamily = Jakarta, fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Jakarta, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Jakarta, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Jakarta, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = Jakarta, fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Jakarta),
    bodyMedium = base.bodyMedium.copy(fontFamily = Jakarta),
    bodySmall = base.bodySmall.copy(fontFamily = Jakarta),
    labelLarge = base.labelLarge.copy(fontFamily = Jakarta, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = Jakarta, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontFamily = Jakarta, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp),
)
