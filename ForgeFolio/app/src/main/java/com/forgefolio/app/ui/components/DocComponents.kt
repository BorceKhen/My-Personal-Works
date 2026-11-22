package com.forgefolio.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forgefolio.app.model.ConversionProgress
import com.forgefolio.app.model.ConversionResult
import com.forgefolio.app.ui.theme.ForgeTextStyles
import com.forgefolio.app.ui.theme.LocalForgeColors

/**
 * Editorial Library Header displaying FORGEFOLIO, [ OFFLINE • LOCAL ENGINE ],
 * book-spine folio color strips, and a Light/Dark reading-room theme switch.
 */
@Composable
fun ForgeFolioHeader(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalForgeColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Classic leather & brass book-spine emblem inspired by archival library volumes
                Row(
                    modifier = Modifier
                        .height(26.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                ) {
                    Box(modifier = Modifier.width(5.dp).fillMaxHeight().background(colors.bookSpineOxblood))
                    Box(modifier = Modifier.width(4.dp).fillMaxHeight().background(colors.bookSpineEmerald))
                    Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(colors.brassFoilGold))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "FORGEFOLIO",
                        style = ForgeTextStyles.folioDisplay,
                        color = colors.primaryInk
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.libraryShelfTint)
                                .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "[ OFFLINE • LOCAL ENGINE ]",
                                style = ForgeTextStyles.monoBadge,
                                color = colors.brassFoilGold
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.calloutTint)
                    .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                    .clickable(onClick = onToggleTheme)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (isDarkTheme) "ARCHIVAL LIGHT" else "MIDNIGHT STUDY",
                    style = ForgeTextStyles.monoBadge,
                    color = colors.secondaryInk
                )
            }
        }
    }
}

/**
 * Numbered documentation section block with 1.dp hairline border, library spine strip,
 * and clean flat layered surface.
 */
@Composable
fun DocSectionCard(
    sectionNumber: String,
    title: String,
    subtitle: String? = null,
    spineColor: Color? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalForgeColors.current
    val effectiveSpine = spineColor ?: colors.brassFoilGold

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Subtle gilded/leather volume accent bar along top edge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(effectiveSpine.copy(alpha = 0.65f))
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
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

                if (!subtitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = subtitle,
                        style = ForgeTextStyles.monoMeta,
                        color = colors.secondaryInk
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                content()
            }
        }
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(6.dp))
            .background(colors.calloutTint)
            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(barColor)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 10.dp)
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
    val bg = if (selected) colors.accentCobalt.copy(alpha = 0.12f) else colors.calloutTint
    val borderColor = if (selected) colors.accentCobalt else colors.border
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
