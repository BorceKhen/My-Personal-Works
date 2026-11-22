package com.forgefolio.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Editorial & Documentation Typography:
 * - Library Serif for Folio titles & volume headings
 * - Clean Sans-Serif for controls & descriptions
 * - Monospace for technical metadata (dimensions, DPI, byte counts, progress)
 */
object ForgeTextStyles {
    val folioDisplay = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.4.sp
    )

    val sectionHeading = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp
    )

    val monoBadge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.3.sp
    )

    val monoMeta = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp
    )
}

val ForgeTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)
