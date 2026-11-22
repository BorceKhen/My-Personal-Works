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
    // Classic Library Ambience Tokens (leather spines, brass foil, emerald cloth, oak, navy)
    val bookSpineOxblood: Color,
    val bookSpineEmerald: Color,
    val bookSpineNavy: Color,
    val brassFoilGold: Color,
    val warmWalnut: Color,
    val walnutDark: Color,
    val libraryShelfTint: Color
)

private val ArchivalPaperColors = ForgeColors(
    isDark = false,
    background = Color(0xFFFBF9F5),
    surface = Color(0xFFFFFFFF),
    border = Color(0xFFE3DDD3),
    calloutTint = Color(0xFFF4F0E8),
    primaryInk = Color(0xFF18181B),
    secondaryInk = Color(0xFF52525B),
    accentCobalt = Color(0xFF2563EB),
    bookSpineOxblood = Color(0xFF74222D),
    bookSpineEmerald = Color(0xFF1B4737),
    bookSpineNavy = Color(0xFF223857),
    brassFoilGold = Color(0xFFC59B38),
    warmWalnut = Color(0xFF63402C),
    walnutDark = Color(0xFF3E2619),
    libraryShelfTint = Color(0xFFF3ECE0)
)

private val MidnightBlueprintColors = ForgeColors(
    isDark = true,
    background = Color(0xFF121214),
    surface = Color(0xFF1A1A1E),
    border = Color(0xFF2C2C32),
    calloutTint = Color(0xFF222227),
    primaryInk = Color(0xFFF4F4F5),
    secondaryInk = Color(0xFFA1A1AA),
    accentCobalt = Color(0xFF60A5FA),
    bookSpineOxblood = Color(0xFF8C2D3A),
    bookSpineEmerald = Color(0xFF225945),
    bookSpineNavy = Color(0xFF2B466D),
    brassFoilGold = Color(0xFFD9B25F),
    warmWalnut = Color(0xFF543625),
    walnutDark = Color(0xFF332015),
    libraryShelfTint = Color(0xFF191716)
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
