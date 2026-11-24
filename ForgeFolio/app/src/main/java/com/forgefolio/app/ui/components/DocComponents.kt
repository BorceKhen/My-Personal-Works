package com.forgefolio.app.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.forgefolio.app.model.ConversionProgress
import com.forgefolio.app.model.ConversionResult
import com.forgefolio.app.model.ForgeTab
import com.forgefolio.app.model.ShelfArchiveEntry
import com.forgefolio.app.ui.theme.ForgeTextStyles
import com.forgefolio.app.ui.theme.LocalForgeColors

private val SmoothEase = tween<Float>(durationMillis = 240, easing = FastOutSlowInEasing)

/**
 * Editorial Library Header displaying FORGEFOLIO, [ OFFLINE • ARCHIVAL LIBRARY ],
 * leather spine stack, and an interactive Brass Candlestick toggle:
 * Light Mode = Lit Candle (warm candlelight), Dark Mode = Candle Extinguished.
 */
@Composable
fun ForgeFolioHeader(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalForgeColors.current
    val candleLit = !isDarkTheme
    val flameAlpha by animateFloatAsState(
        targetValue = if (candleLit) 1f else 0f,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "headerCandleFlame"
    )
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        colors.surface,
                        colors.libraryShelfTint
                    )
                )
            )
            .drawBehind {
                if (flameAlpha > 0.01f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.candleGlow.copy(alpha = 0.28f * flameAlpha),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.84f, size.height * 0.5f),
                            radius = size.width * 0.36f
                        ),
                        center = Offset(size.width * 0.84f, size.height * 0.5f),
                        radius = size.width * 0.36f
                    )
                }
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .height(30.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(1.dp, colors.border, RoundedCornerShape(4.dp))
            ) {
                Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(colors.bookSpineOxblood))
                Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(colors.bookSpineEmerald))
                Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(colors.bookSpineNavy))
                Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(colors.brassFoilGold))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "FORGEFOLIO",
                    style = ForgeTextStyles.folioDisplay,
                    color = colors.primaryInk
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(colors.surface.copy(alpha = 0.75f))
                        .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "[ OFFLINE • ARCHIVAL LIBRARY ]",
                        style = ForgeTextStyles.monoBadge,
                        color = colors.brassFoilGold
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(colors.surface.copy(alpha = 0.9f))
                .border(
                    width = 1.dp,
                    color = colors.brassFoilGold.copy(alpha = if (candleLit) 0.78f else 0.42f),
                    shape = RoundedCornerShape(7.dp)
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onToggleTheme
                )
                .padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            LibraryBrassCandle(
                litFraction = flameAlpha,
                brassColor = colors.brassFoilGold,
                darkInk = colors.walnutDark
            )
            Column {
                Text(
                    text = if (candleLit) "CANDLE LIT" else "CANDLE OFF",
                    style = ForgeTextStyles.monoBadge.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = colors.primaryInk
                )
                Text(
                    text = if (candleLit) "Tap to dim" else "Tap to light",
                    style = ForgeTextStyles.monoBadge.copy(fontSize = 9.sp, lineHeight = 10.sp),
                    color = colors.secondaryInk
                )
            }
        }
    }
}

/**
 * Custom Canvas brass taper candlestick: glows with a golden teardrop flame in Light Mode
 * and rests extinguished with a delicate silver smoke curl in Dark Mode.
 */
@Composable
private fun LibraryBrassCandle(
    litFraction: Float,
    brassColor: Color,
    darkInk: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(width = 20.dp, height = 28.dp)) {
        val w = size.width
        val h = size.height
        val cx = w * 0.48f

        // Warm candlelight aura behind flame
        if (litFraction > 0.02f) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xFFFFCA62).copy(alpha = 0.65f * litFraction),
                        Color(0xFFFF9F1C).copy(alpha = 0.2f * litFraction),
                        Color.Transparent
                    ),
                    center = Offset(cx, h * 0.24f),
                    radius = w * 0.62f
                ),
                center = Offset(cx, h * 0.24f),
                radius = w * 0.62f
            )
        }

        // Brass saucer & ring handle
        drawRoundRect(
            color = brassColor,
            topLeft = Offset(w * 0.12f, h * 0.84f),
            size = Size(w * 0.72f, h * 0.11f)
        )
        drawCircle(
            color = brassColor,
            radius = w * 0.12f,
            center = Offset(w * 0.84f, h * 0.85f),
            style = Stroke(width = 1.6.dp.toPx())
        )

        // Brass collar cup
        drawRoundRect(
            color = brassColor.copy(alpha = 0.9f),
            topLeft = Offset(cx - w * 0.18f, h * 0.76f),
            size = Size(w * 0.36f, h * 0.09f)
        )

        // Cream beeswax candle shaft
        val waxTop = h * 0.42f
        val waxW = w * 0.24f
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFFE7DEC8),
                    Color(0xFFFDF8EA),
                    Color(0xFFD5C8AD)
                ),
                startX = cx - waxW / 2f,
                endX = cx + waxW / 2f
            ),
            topLeft = Offset(cx - waxW / 2f, waxTop),
            size = Size(waxW, h * 0.77f - waxTop)
        )

        // Charred wick
        drawLine(
            color = darkInk,
            start = Offset(cx, waxTop),
            end = Offset(cx, h * 0.33f),
            strokeWidth = 1.4.dp.toPx(),
            cap = StrokeCap.Round
        )

        // Flame (Light Mode) vs Smoke Curl (Dark Mode)
        if (litFraction > 0.05f) {
            val flamePath = Path().apply {
                moveTo(cx, h * 0.08f)
                quadraticTo(cx + w * 0.18f, h * 0.24f, cx, h * 0.35f)
                quadraticTo(cx - w * 0.18f, h * 0.24f, cx, h * 0.08f)
                close()
            }
            drawPath(
                path = flamePath,
                color = Color(0xFFFF9F1C).copy(alpha = litFraction)
            )
            val corePath = Path().apply {
                moveTo(cx, h * 0.16f)
                quadraticTo(cx + w * 0.08f, h * 0.26f, cx, h * 0.34f)
                quadraticTo(cx - w * 0.08f, h * 0.26f, cx, h * 0.16f)
                close()
            }
            drawPath(
                path = corePath,
                color = Color(0xFFFFF8D6).copy(alpha = litFraction)
            )
        }

        val smokeAlpha = (1f - litFraction).coerceIn(0f, 1f)
        if (smokeAlpha > 0.05f) {
            val smokePath = Path().apply {
                moveTo(cx, h * 0.31f)
                cubicTo(
                    cx - w * 0.12f, h * 0.23f,
                    cx + w * 0.14f, h * 0.15f,
                    cx - w * 0.03f, h * 0.06f
                )
            }
            drawPath(
                path = smokePath,
                color = Color(0xFFA8A095).copy(alpha = 0.55f * smokeAlpha),
                style = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * Architectural Library Bookcase Bay enclosing the scrollable body:
 * renders textured wainscot alcove backdrop, warm candlelight glow in Light Mode,
 * and tall left/right carved walnut bookcase stiles framing every shelf below.
 */
@Composable
fun LibraryBookcaseBody(
    isDarkTheme: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val colors = LocalForgeColors.current
    val candleGlowFraction by animateFloatAsState(
        targetValue = if (!isDarkTheme) 1f else 0f,
        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing),
        label = "alcoveCandleGlow"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // Warm antique parchment alcove (Light) or deep mahogany study bay (Dark)
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(colors.alcoveWallTop, colors.alcoveWallBottom)
                    )
                )

                // Subtle vertical wainscot wood-panel grooves + recessed library crown arch behind shelves
                val stileW = 10.dp.toPx()
                val innerW = (size.width - stileW * 2f).coerceAtLeast(1f)
                for (i in 1..3) {
                    val x = stileW + innerW * (i / 4f)
                    drawLine(
                        color = colors.walnutDark.copy(alpha = if (isDarkTheme) 0.35f else 0.09f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Soft arched wooden alcove molding on the rear backplate
                val archPath = Path().apply {
                    moveTo(stileW + 6.dp.toPx(), 120.dp.toPx())
                    quadraticTo(size.width * 0.5f, -18.dp.toPx(), size.width - stileW - 6.dp.toPx(), 120.dp.toPx())
                }
                drawPath(
                    path = archPath,
                    color = colors.warmWalnut.copy(alpha = if (isDarkTheme) 0.25f else 0.14f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                )

                // Recessed rear-wall brass sconce candles behind the shelves (subtle atmospheric silhouette)
                val sconcePositions = listOf(
                    Offset(size.width * 0.5f, 44.dp.toPx()),
                    Offset(size.width * 0.5f, size.height * 0.54f)
                )
                sconcePositions.forEach { sconceCenter ->
                    if (candleGlowFraction > 0.01f) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    colors.candleGlow.copy(alpha = 0.28f * candleGlowFraction),
                                    colors.candleGlow.copy(alpha = 0.11f * candleGlowFraction),
                                    Color.Transparent
                                ),
                                center = sconceCenter,
                                radius = size.width * 0.62f
                            ),
                            center = sconceCenter,
                            radius = size.width * 0.62f
                        )
                    }

                    // Soft sconce bracket & unlit/lit beeswax pillar behind the vellum bays
                    val sx = sconceCenter.x
                    val sy = sconceCenter.y
                    drawRoundRect(
                        color = colors.brassFoilGold.copy(alpha = if (isDarkTheme) 0.14f else 0.18f),
                        topLeft = Offset(sx - 10.dp.toPx(), sy + 14.dp.toPx()),
                        size = Size(20.dp.toPx(), 4.dp.toPx())
                    )
                    drawRoundRect(
                        color = Color(0xFFF6ECD5).copy(alpha = if (isDarkTheme) 0.11f else 0.26f),
                        topLeft = Offset(sx - 5.dp.toPx(), sy - 10.dp.toPx()),
                        size = Size(10.dp.toPx(), 24.dp.toPx())
                    )
                    if (candleGlowFraction > 0.05f) {
                        val backFlame = Path().apply {
                            moveTo(sx, sy - 26.dp.toPx())
                            quadraticTo(sx + 7.dp.toPx(), sy - 15.dp.toPx(), sx, sy - 10.dp.toPx())
                            quadraticTo(sx - 7.dp.toPx(), sy - 15.dp.toPx(), sx, sy - 26.dp.toPx())
                            close()
                        }
                        drawPath(
                            path = backFlame,
                            color = Color(0xFFFFB240).copy(alpha = 0.34f * candleGlowFraction)
                        )
                    }
                }

                // Warm radial candlelight aura cascading down from the top-right header candle
                if (candleGlowFraction > 0.01f) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.candleGlow.copy(alpha = 0.24f * candleGlowFraction),
                                colors.candleGlow.copy(alpha = 0.08f * candleGlowFraction),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.85f, size.height * 0.04f),
                            radius = size.width * 0.95f
                        )
                    )
                }

                // Left carved walnut bookcase stile + inner alcove shadow
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(colors.walnutDark, colors.warmWalnut, colors.walnutDark),
                        startX = 0f,
                        endX = stileW
                    ),
                    topLeft = Offset.Zero,
                    size = Size(stileW, size.height)
                )
                drawLine(
                    color = colors.brassFoilGold.copy(alpha = 0.35f),
                    start = Offset(stileW - 1.dp.toPx(), 0f),
                    end = Offset(stileW - 1.dp.toPx(), size.height),
                    strokeWidth = 1.dp.toPx()
                )
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(colors.walnutDark.copy(alpha = 0.28f), Color.Transparent),
                        startX = stileW,
                        endX = stileW + 10.dp.toPx()
                    ),
                    topLeft = Offset(stileW, 0f),
                    size = Size(10.dp.toPx(), size.height)
                )

                // Right carved walnut bookcase stile + inner alcove shadow
                val rightX = size.width - stileW
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(colors.walnutDark, colors.warmWalnut, colors.walnutDark),
                        startX = rightX,
                        endX = size.width
                    ),
                    topLeft = Offset(rightX, 0f),
                    size = Size(stileW, size.height)
                )
                drawLine(
                    color = colors.brassFoilGold.copy(alpha = 0.35f),
                    start = Offset(rightX + 1.dp.toPx(), 0f),
                    end = Offset(rightX + 1.dp.toPx(), size.height),
                    strokeWidth = 1.dp.toPx()
                )
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, colors.walnutDark.copy(alpha = 0.28f)),
                        startX = rightX - 10.dp.toPx(),
                        endX = rightX
                    ),
                    topLeft = Offset(rightX - 10.dp.toPx(), 0f),
                    size = Size(10.dp.toPx(), size.height)
                )
            }
            .padding(horizontal = 10.dp)
    ) {
        content()
    }
}

/**
 * Wall-to-wall carved wooden shelf plank spanning smoothly between the left and right
 * bookcase pillars, with brass edge trim, corner brackets, and a centered catalog plaque.
 */
@Composable
fun BookcaseShelfPlank(
    shelfLabel: String? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalForgeColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        // Carved oak/walnut shelf board
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.warmWalnut,
                            colors.walnutDark
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (!shelfLabel.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.brassFoilGold.copy(alpha = 0.9f))
                        .padding(horizontal = 8.dp, vertical = 0.5.dp)
                ) {
                    Text(
                        text = shelfLabel,
                        style = ForgeTextStyles.monoBadge.copy(fontSize = 8.sp, lineHeight = 10.sp),
                        fontWeight = FontWeight.Bold,
                        color = colors.walnutDark,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        // Brass shelf front lip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(colors.brassFoilGold.copy(alpha = 0.65f))
        )
        // Corner brass shelf brackets & under-shelf cast shadow
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(5.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.walnutDark.copy(alpha = 0.28f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(width = 14.dp, height = 4.dp)
                    .background(colors.brassFoilGold.copy(alpha = 0.5f))
            )
            Box(
                modifier = Modifier
                    .size(width = 14.dp, height = 4.dp)
                    .background(colors.brassFoilGold.copy(alpha = 0.5f))
            )
        }
    }
}

/**
 * Rich oak/walnut library alcove shelf with 4 leather-bound volume books,
 * decorative flanking spines, gold-leaf spine tooling, and a smooth GPU-only
 * pull-out lift (zero layout re-measure/vibration).
 */
@Composable
fun LibraryBookshelfBar(
    selectedTab: ForgeTab,
    loadedBadges: Map<ForgeTab, String?>,
    onSelectTab: (ForgeTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalForgeColors.current
    val shelfCandleGlow by animateFloatAsState(
        targetValue = if (!colors.isDark) 1f else 0f,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "shelfCandleGlow"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(colors.walnutDark.copy(alpha = 0.22f), colors.libraryShelfTint)
                )
            )
            .drawBehind {
                if (shelfCandleGlow > 0.01f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                colors.candleGlow.copy(alpha = 0.28f * shelfCandleGlow),
                                Color.Transparent
                            ),
                            center = Offset(size.width * 0.86f, 0f),
                            radius = size.width * 0.65f
                        ),
                        center = Offset(size.width * 0.86f, 0f),
                        radius = size.width * 0.65f
                    )
                }
            }
    ) {
        // Subtle library alcove cornice molding
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(colors.walnutDark.copy(alpha = 0.65f))
        )

        // Fixed-height bookshelf stage so layout below never jumps or vibrates
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(94.dp)
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Left decorative bookend & filler archival volumes
            DecorativeFillerSpine(widthDp = 12, heightDp = 64, color = colors.walnutDark, foil = colors.brassFoilGold)
            DecorativeFillerSpine(widthDp = 9, heightDp = 56, color = colors.bookSpineNavy.copy(alpha = 0.7f), foil = colors.brassFoilGold)

            ForgeTab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                val spineBase = when (tab) {
                    ForgeTab.PDF_TO_IMG -> colors.bookSpineOxblood
                    ForgeTab.IMG_TO_PDF -> colors.bookSpineEmerald
                    ForgeTab.DOCX_TO_PDF -> colors.warmWalnut
                    ForgeTab.PDF_TO_DOCX -> colors.bookSpineNavy
                }

                // Pure GPU translation & scale — zero layout shift / vibration!
                val densityScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.03f else 1.0f,
                    animationSpec = SmoothEase,
                    label = "bookScale"
                )
                val liftFraction by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0f,
                    animationSpec = SmoothEase,
                    label = "bookLift"
                )
                val borderTint by animateColorAsState(
                    targetValue = if (isSelected) colors.brassFoilGold else colors.walnutDark.copy(alpha = 0.75f),
                    animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing),
                    label = "bookBorder"
                )

                val naturalHeight = when (tab) {
                    ForgeTab.PDF_TO_IMG -> 76.dp
                    ForgeTab.IMG_TO_PDF -> 72.dp
                    ForgeTab.DOCX_TO_PDF -> 78.dp
                    ForgeTab.PDF_TO_DOCX -> 74.dp
                }

                val shortLabel = when (tab) {
                    ForgeTab.PDF_TO_IMG -> "PDF\n→IMG"
                    ForgeTab.IMG_TO_PDF -> "IMG\n→PDF"
                    ForgeTab.DOCX_TO_PDF -> "DOCX\n→PDF"
                    ForgeTab.PDF_TO_DOCX -> "PDF\n→DOCX"
                }
                val loadedHint = loadedBadges[tab]
                val interactionSource = remember { MutableInteractionSource() }

                // Cylindrical leather spine shading
                val spineBrush = Brush.horizontalGradient(
                    colors = listOf(
                        spineBase.copy(alpha = 0.85f),
                        spineBase,
                        spineBase.copy(alpha = 0.78f)
                    )
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(naturalHeight)
                        .graphicsLayer {
                            translationY = -14f * liftFraction
                            scaleX = densityScale
                            scaleY = densityScale
                        }
                        .clip(RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                        .background(spineBrush)
                        .border(
                            width = if (isSelected) 1.5.dp else 1.dp,
                            color = borderTint,
                            shape = RoundedCornerShape(topStart = 5.dp, topEnd = 5.dp, bottomStart = 2.dp, bottomEnd = 2.dp)
                        )
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = { onSelectTab(tab) }
                        )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 5.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top double gilt spine ribs
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.5.dp)
                                    .background(colors.brassFoilGold.copy(alpha = if (isSelected) 1f else 0.55f))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(colors.brassFoilGold.copy(alpha = if (isSelected) 0.75f else 0.35f))
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = tab.code,
                                    style = ForgeTextStyles.monoBadge.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = colors.brassFoilGold
                                )
                                if (!loadedHint.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF4ADE80))
                                    )
                                }
                            }
                            Text(
                                text = shortLabel,
                                style = ForgeTextStyles.monoBadge.copy(fontSize = 10.sp, lineHeight = 12.sp),
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = Color(0xFFFBF9F5),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "◆",
                                fontSize = 7.sp,
                                color = colors.brassFoilGold.copy(alpha = if (isSelected) 0.95f else 0.45f)
                            )
                        }

                        // Bottom double gilt spine ribs
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(colors.brassFoilGold.copy(alpha = if (isSelected) 0.75f else 0.35f))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.5.dp)
                                    .background(colors.brassFoilGold.copy(alpha = if (isSelected) 1f else 0.55f))
                            )
                        }
                    }
                }
            }

            // Right decorative filler volumes & bookend
            DecorativeFillerSpine(widthDp = 9, heightDp = 60, color = colors.bookSpineOxblood.copy(alpha = 0.72f), foil = colors.brassFoilGold)
            DecorativeFillerSpine(widthDp = 12, heightDp = 66, color = colors.walnutDark, foil = colors.brassFoilGold)
        }

        // Carved Mahogany/Oak Shelf Plank + Brass Catalog Plaque Strip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(colors.warmWalnut, colors.walnutDark)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            // Centered brass catalog plaque
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.brassFoilGold.copy(alpha = 0.88f))
                    .padding(horizontal = 10.dp, vertical = 0.5.dp)
            ) {
                Text(
                    text = "SHELF ${selectedTab.label}",
                    style = ForgeTextStyles.monoBadge.copy(fontSize = 9.sp, lineHeight = 11.sp),
                    fontWeight = FontWeight.Bold,
                    color = colors.walnutDark
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(colors.brassFoilGold.copy(alpha = 0.75f))
        )
    }
}

@Composable
private fun DecorativeFillerSpine(
    widthDp: Int,
    heightDp: Int,
    color: Color,
    foil: Color
) {
    Column(
        modifier = Modifier
            .width(widthDp.dp)
            .height(heightDp.dp)
            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
            .background(color)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(foil.copy(alpha = 0.4f)))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(foil.copy(alpha = 0.4f)))
    }
}

/**
 * Interactive lower Reading Room Shelf displaying loaded & exported documents as
 * tactile mini volumes sitting on a wooden plank.
 */
@Composable
fun RecentShelfStrip(
    entries: List<ShelfArchiveEntry>,
    onEntryClick: (ShelfArchiveEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalForgeColors.current
    if (entries.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(colors.libraryShelfTint.copy(alpha = 0.92f))
                .border(1.dp, colors.border, RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "READING DESK SHELF",
                    style = ForgeTextStyles.monoBadge,
                    fontWeight = FontWeight.Bold,
                    color = colors.brassFoilGold
                )
                Text(
                    text = "${entries.size} FOLIO(S)",
                    style = ForgeTextStyles.monoBadge,
                    color = colors.secondaryInk
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                entries.forEach { item ->
                    val spineColor = if (item.isExportedOutput) {
                        colors.bookSpineEmerald
                    } else {
                        colors.bookSpineOxblood
                    }
                    Row(
                        modifier = Modifier
                            .width(174.dp)
                            .height(IntrinsicSize.Min)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.border, RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .clickable { onEntryClick(item) }
                    ) {
                        Box(
                            modifier = Modifier
                                .width(6.dp)
                                .fillMaxHeight()
                                .background(spineColor)
                        )
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp)) {
                            Text(
                                text = if (item.isExportedOutput) "EXPORTED • ${item.badge}" else "OPENED • ${item.badge}",
                                style = ForgeTextStyles.monoBadge.copy(fontSize = 10.sp),
                                color = spineColor,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.title,
                                style = ForgeTextStyles.monoMeta.copy(fontSize = 11.sp),
                                fontWeight = FontWeight.SemiBold,
                                color = colors.primaryInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = item.meta,
                                style = ForgeTextStyles.monoBadge.copy(fontSize = 10.sp),
                                color = colors.secondaryInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        BookcaseShelfPlank(shelfLabel = "READING DESK • ${entries.size} FOLIO(S)")
    }
}

/**
 * Smooth collapsible archival book-folio section card resting directly inside a wooden
 * bookcase bay plank, complete with flanking brass bookends and bound leather spine.
 */
@Composable
fun DocSectionCard(
    sectionNumber: String,
    title: String,
    subtitle: String? = null,
    spineColor: Color? = null,
    initiallyExpanded: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalForgeColors.current
    val effectiveSpine = spineColor ?: colors.brassFoilGold
    var expanded by rememberSaveable(sectionNumber, title) { mutableStateOf(initiallyExpanded) }
    val interactionSource = remember { MutableInteractionSource() }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 3.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Left resting bookcase bookend resting on the shelf edge
            Box(
                modifier = Modifier
                    .padding(end = 3.dp)
                    .size(width = 4.dp, height = 28.dp)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(effectiveSpine.copy(alpha = 0.72f))
            )

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 2.dp, bottomEnd = 2.dp),
                color = colors.surface.copy(alpha = if (colors.isDark) 0.94f else 0.91f),
                border = BorderStroke(1.dp, colors.border),
                shadowElevation = 0.dp
            ) {
                // Draw the left leather spine & gilt ribs via drawBehind instead of IntrinsicSize.Min
                // so multi-line FlowRow blocks never get height-clipped at the bottom.
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            val spineW = 7.dp.toPx()
                            drawRect(
                                color = effectiveSpine,
                                topLeft = Offset.Zero,
                                size = Size(spineW, size.height)
                            )
                            val ribH = 2.dp.toPx()
                            val ribOffset = 12.dp.toPx()
                            drawRect(
                                color = colors.brassFoilGold,
                                topLeft = Offset(0f, ribOffset),
                                size = Size(spineW, ribH)
                            )
                            drawRect(
                                color = colors.brassFoilGold,
                                topLeft = Offset(0f, (size.height - ribOffset - ribH).coerceAtLeast(ribOffset + ribH)),
                                size = Size(spineW, ribH)
                            )
                        }
                        .padding(start = 21.dp, top = 14.dp, end = 14.dp, bottom = 14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null,
                                onClick = { expanded = !expanded }
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.libraryShelfTint)
                                    .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "VOL.$sectionNumber",
                                    style = ForgeTextStyles.monoBadge,
                                    color = effectiveSpine
                                )
                            }
                            Text(
                                text = title,
                                style = ForgeTextStyles.sectionHeading,
                                color = colors.primaryInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = if (expanded) "▾" else "▸",
                            style = ForgeTextStyles.monoMeta,
                            color = colors.brassFoilGold
                        )
                    }

                    if (!subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = subtitle,
                            style = ForgeTextStyles.monoMeta,
                            color = colors.secondaryInk
                        )
                    }

                    AnimatedVisibility(
                        visible = expanded,
                        enter = fadeIn(tween(180, easing = FastOutSlowInEasing)),
                        exit = fadeOut(tween(140, easing = FastOutSlowInEasing))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            content()
                        }
                    }
                }
            }

            // Right resting bookcase bookend
            Box(
                modifier = Modifier
                    .padding(start = 3.dp)
                    .size(width = 4.dp, height = 24.dp)
                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                    .background(colors.walnutDark.copy(alpha = 0.72f))
            )
        }

        BookcaseShelfPlank(shelfLabel = "BAY $sectionNumber • ${title.uppercase()}")
    }
}

/**
 * Interactive PDF Folio Page Flipper + Tap-to-Zoom Lightbox Dialog.
 */
@Composable
fun InteractiveFolioPreview(
    bitmap: Bitmap?,
    displayName: String,
    sizeLabel: String,
    currentPageZeroBased: Int,
    totalPages: Int,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalForgeColors.current
    var showZoomDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(colors.libraryShelfTint)
            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (bitmap != null) {
                Box {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Folio page preview",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(width = 84.dp, height = 112.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                            .border(1.dp, colors.brassFoilGold.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                            .clickable { showZoomDialog = true }
                    )
                    // Corner bookmark ribbon on preview plate
                    Canvas(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 6.dp)
                            .size(width = 10.dp, height = 18.dp)
                    ) {
                        val path = Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width, size.height)
                            lineTo(size.width / 2f, size.height * 0.72f)
                            lineTo(0f, size.height)
                            close()
                        }
                        drawPath(path, color = colors.bookSpineOxblood)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = displayName,
                    style = ForgeTextStyles.sectionHeading,
                    color = colors.primaryInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = sizeLabel,
                    style = ForgeTextStyles.monoMeta,
                    color = colors.secondaryInk
                )
                Text(
                    text = "PLATE ${String.format("%02d", currentPageZeroBased + 1)} / ${String.format("%02d", totalPages)} • TAP TO INSPECT",
                    style = ForgeTextStyles.monoBadge,
                    color = colors.accentCobalt
                )

                if (totalPages > 1) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        PageFlipBtn("◀ Prev", enabled = currentPageZeroBased > 0, onClick = onPrevPage)
                        PageFlipBtn("Next ▶", enabled = currentPageZeroBased < totalPages - 1, onClick = onNextPage)
                    }
                }
            }
        }
    }

    if (showZoomDialog && bitmap != null) {
        Dialog(onDismissRequest = { showZoomDialog = false }) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.brassFoilGold)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PLATE ${currentPageZeroBased + 1} / $totalPages",
                            style = ForgeTextStyles.monoBadge,
                            color = colors.brassFoilGold
                        )
                        Text(
                            text = "CLOSE ✕",
                            style = ForgeTextStyles.monoBadge,
                            color = colors.bookSpineOxblood,
                            modifier = Modifier
                                .clickable { showZoomDialog = false }
                                .padding(4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Zoomed page",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White)
                            .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                    )
                    if (totalPages > 1) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PageFlipBtn("◀ Prev Page", enabled = currentPageZeroBased > 0, onClick = onPrevPage)
                            PageFlipBtn("Next Page ▶", enabled = currentPageZeroBased < totalPages - 1, onClick = onNextPage)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PageFlipBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalForgeColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(if (enabled) colors.surface else colors.calloutTint)
            .border(1.dp, if (enabled) colors.accentCobalt else colors.border, RoundedCornerShape(5.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            style = ForgeTextStyles.monoBadge,
            color = if (enabled) colors.accentCobalt else colors.secondaryInk
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocQualitySlider(
    quality: Int,
    isLosslessPng: Boolean,
    onQualityChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalForgeColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("COMPRESSION QUALITY", style = ForgeTextStyles.monoBadge, color = colors.secondaryInk)
            Text(
                text = if (isLosslessPng) "100% (LOSSLESS PNG)" else "$quality%",
                style = ForgeTextStyles.monoMeta,
                color = colors.accentCobalt
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = if (isLosslessPng) 100f else quality.toFloat(),
            onValueChange = { if (!isLosslessPng) onQualityChanged(it.toInt()) },
            enabled = !isLosslessPng,
            valueRange = 10f..100f,
            thumb = {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (isLosslessPng) colors.border else colors.accentCobalt)
                        .border(2.dp, colors.surface, CircleShape)
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(5.dp),
                    colors = SliderDefaults.colors(
                        activeTrackColor = colors.accentCobalt,
                        inactiveTrackColor = colors.border
                    )
                )
            }
        )
    }
}

/**
 * Technical callout box with a 3.dp left cobalt (or custom) accent bar + tinted surface.
 */
@Composable
fun DocCalloutBanner(
    title: String,
    body: String,
    accentColor: Color? = null,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null
) {
    val colors = LocalForgeColors.current
    val barColor = accentColor ?: colors.accentCobalt

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(colors.calloutTint)
            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
            .drawBehind {
                drawRect(
                    color = barColor,
                    topLeft = Offset.Zero,
                    size = Size(3.dp.toPx(), size.height)
                )
            }
            .padding(start = 15.dp, top = 10.dp, end = 12.dp, bottom = 10.dp)
    ) {
        Text(
            text = title,
            style = ForgeTextStyles.monoBadge,
            fontWeight = FontWeight.Bold,
            color = barColor
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = body,
            style = ForgeTextStyles.monoMeta,
            color = colors.primaryInk
        )
        if (trailingContent != null) {
            Spacer(modifier = Modifier.height(10.dp))
            trailingContent()
        }
    }
}

@Composable
fun DocSelectableChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    val colors = LocalForgeColors.current
    val bg by animateColorAsState(
        targetValue = if (selected) colors.accentCobalt.copy(alpha = 0.12f) else colors.calloutTint,
        animationSpec = tween(160, easing = FastOutSlowInEasing),
        label = "chipBg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) colors.accentCobalt else colors.border,
        animationSpec = tween(160, easing = FastOutSlowInEasing),
        label = "chipBorder"
    )
    val textColor = if (selected) colors.accentCobalt else colors.primaryInk

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = ForgeTextStyles.monoBadge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!badge.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = badge,
                style = ForgeTextStyles.monoBadge,
                color = colors.secondaryInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun ConversionStatusPanel(
    progress: ConversionProgress,
    result: ConversionResult?,
    errorMessage: String?,
    onOpenResult: (ConversionResult) -> Unit,
    onShareResult: (ConversionResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalForgeColors.current

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (progress.isRunning) {
            DocCalloutBanner(
                title = "PROCESSING ARCHIVE",
                body = progress.message.ifBlank { "[00/00] Working locally..." },
                accentColor = colors.accentCobalt
            ) {
                if (progress.total > 0) {
                    LinearProgressIndicator(
                        progress = { (progress.current.toFloat() / progress.total.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = colors.accentCobalt,
                        trackColor = colors.border
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = colors.accentCobalt,
                        trackColor = colors.border
                    )
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            DocCalloutBanner(
                title = "CONVERSION ERROR",
                body = errorMessage,
                accentColor = colors.bookSpineOxblood
            )
        }

        if (result != null && !progress.isRunning) {
            DocCalloutBanner(
                title = "EXPORT COMPLETE • ${result.displayName}",
                body = result.summary,
                accentColor = colors.bookSpineEmerald
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onOpenResult(result) },
                        shape = RoundedCornerShape(6.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.accentCobalt,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Open", style = ForgeTextStyles.monoBadge)
                    }
                    if (!result.isFolderTree) {
                        OutlinedButton(
                            onClick = { onShareResult(result) },
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, colors.border)
                        ) {
                            Text("Share", style = ForgeTextStyles.monoBadge, color = colors.primaryInk)
                        }
                    }
                }
            }
        }
    }
}
