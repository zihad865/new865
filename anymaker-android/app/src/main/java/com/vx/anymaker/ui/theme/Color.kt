package com.vx.anymaker.ui.theme

import androidx.compose.ui.graphics.Color

internal val BrandTeal = Color(0xFF0F7B74)
internal val BrandTealDark = Color(0xFF4CC3B8)

internal val LightBackground = Color(0xFFF3F6F8)
internal val LightSurface = Color(0xFFFFFFFF)
internal val LightSurfaceVariant = Color(0xFFE7EDF1)
internal val LightOnSurface = Color(0xFF14232E)
internal val LightOnSurfaceVariant = Color(0xFF5A6B78)
internal val LightOutline = Color(0xFFD9E2E8)

internal val DarkBackground = Color(0xFF0E161C)
internal val DarkSurface = Color(0xFF16222B)
internal val DarkSurfaceVariant = Color(0xFF1E2C36)
internal val DarkOnSurface = Color(0xFFE6EEF3)
internal val DarkOnSurfaceVariant = Color(0xFF9DB0BD)
internal val DarkOutline = Color(0xFF26343F)

internal val StampLight = Color(0xFFC2410C)
internal val StampDark = Color(0xFFF08A52)

/** Section colors for tool tiles: a strong tone for the icon and a soft tone for its chip. */
data class SectionColor(val strong: Color, val soft: Color)

data class ToolColors(
    val photo: SectionColor,
    val scan: SectionColor,
    val pdf: SectionColor,
    val good: Color,
    val warn: Color,
)

internal val LightToolColors = ToolColors(
    photo = SectionColor(Color(0xFF2563A8), Color(0xFFE3EDF8)),
    scan = SectionColor(Color(0xFF0F7B74), Color(0xFFDFF1EE)),
    pdf = SectionColor(Color(0xFFB4235A), Color(0xFFFBE6EE)),
    good = Color(0xFF1F7A3D),
    warn = Color(0xFFA15C00),
)

internal val DarkToolColors = ToolColors(
    photo = SectionColor(Color(0xFF7FB0EA), Color(0xFF16283C)),
    scan = SectionColor(Color(0xFF4CC3B8), Color(0xFF13302E)),
    pdf = SectionColor(Color(0xFFEF7EA6), Color(0xFF3A1826)),
    good = Color(0xFF5FCF86),
    warn = Color(0xFFE8A94A),
)
