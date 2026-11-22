package com.forgefolio.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class ForgeColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val border: Color,
    val calloutTint: Color,
    val primaryInk: Color,
    val secondaryInk: Color,
    val accentCobalt: Color,
    // Classic Library Ambience Tokens (leather spines, brass foil, oak alcoves, candlelight)
    val bookSpineOxblood: Color,
    val bookSpineEmerald: Color,
    val bookSpineNavy: Color,
    val brassFoilGold: Color,
    val warmWalnut: Color,
    val walnutDark: Color,
    val libraryShelfTint: Color,
    val alcoveWallTop: Color,
    val alcoveWallBottom: Color,
    val candleGlow: Color
)

private val ArchivalPaperColors = ForgeColors(
    isDark = false,
    // Candlelit Study: Warm antique oak-lit parchment instead of plain white
    background = Color(0xFFE7DAC3),
    surface = Color(0xFFFAF3E3),
    border = Color(0xFFD5C4A6),
    calloutTint = Color(0xFFEFE4CE),
    primaryInk = Color(0xFF231B15),
    secondaryInk = Color(0xFF5D4D3F),
    accentCobalt = Color(0xFF1E4E8C),
    bookSpineOxblood = Color(0xFF74222D),
    bookSpineEmerald = Color(0xFF1B4737),
    bookSpineNavy = Color(0xFF223857),
    brassFoilGold = Color(0xFFC59B38),
    warmWalnut = Color(0xFF6B442E),
    walnutDark = Color(0xFF3E2619),
    libraryShelfTint = Color(0xFFEDE1CB),
    alcoveWallTop = Color(0xFFF3E7CE),
    alcoveWallBottom = Color(0xFFD8C6A8),
    candleGlow = Color(0xFFFFC15E)
)

private val MidnightBlueprintColors = ForgeColors(
    isDark = true,
    // Midnight Study: Extinguished candle, dark smoked walnut alcove & slate ink
    background = Color(0xFF100E0D),
    surface = Color(0xFF191715),
    border = Color(0xFF2E2823),
    calloutTint = Color(0xFF211D1A),
    primaryInk = Color(0xFFF2ECE1),
    secondaryInk = Color(0xFFA3988C),
    accentCobalt = Color(0xFF6FA8EC),
    bookSpineOxblood = Color(0xFF8C2D3A),
    bookSpineEmerald = Color(0xFF225945),
    bookSpineNavy = Color(0xFF2B466D),
    brassFoilGold = Color(0xFFD9B25F),
    warmWalnut = Color(0xFF543625),
    walnutDark = Color(0xFF2D1C12),
    libraryShelfTint = Color(0xFF151210),
    alcoveWallTop = Color(0xFF171412),
    alcoveWallBottom = Color(0xFF0C0A09),
    candleGlow = Color(0x00000000)
)

val LocalForgeColors = staticCompositionLocalOf { ArchivalPaperColors }

@Composable
fun ForgeFolioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val forgeColors = if (darkTheme) MidnightBlueprintColors else ArchivalPaperColors

    val materialScheme = if (darkTheme) {
        darkColorScheme(
            primary = forgeColors.accentCobalt,
            onPrimary = Color(0xFF0A1220),
            background = forgeColors.background,
            onBackground = forgeColors.primaryInk,
            surface = forgeColors.surface,
            onSurface = forgeColors.primaryInk,
            surfaceVariant = forgeColors.calloutTint,
            onSurfaceVariant = forgeColors.secondaryInk,
            outline = forgeColors.border
        )
    } else {
        lightColorScheme(
            primary = forgeColors.accentCobalt,
            onPrimary = Color.White,
            background = forgeColors.background,
            onBackground = forgeColors.primaryInk,
            surface = forgeColors.surface,
            onSurface = forgeColors.primaryInk,
            surfaceVariant = forgeColors.calloutTint,
            onSurfaceVariant = forgeColors.secondaryInk,
            outline = forgeColors.border
        )
    }

    CompositionLocalProvider(LocalForgeColors provides forgeColors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = ForgeTypography,
            content = content
        )
    }
}
