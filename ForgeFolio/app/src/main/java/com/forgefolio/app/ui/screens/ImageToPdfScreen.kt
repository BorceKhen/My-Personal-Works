package com.forgefolio.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forgefolio.app.model.ImageScaleMode
import com.forgefolio.app.model.PageOrientation
import com.forgefolio.app.model.PageRangeParser
import com.forgefolio.app.model.PaperSizePreset
import com.forgefolio.app.ui.ConverterUiState
import com.forgefolio.app.ui.ConverterViewModel
import com.forgefolio.app.ui.components.BookcaseShelfPlank
import com.forgefolio.app.ui.components.DocCalloutBanner
import com.forgefolio.app.ui.components.DocSectionCard
import com.forgefolio.app.ui.components.DocSelectableChip
import com.forgefolio.app.ui.theme.ForgeTextStyles
import com.forgefolio.app.ui.theme.LocalForgeColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImageToPdfScreen(
    state: ConverterUiState,
    viewModel: ConverterViewModel
) {
    val colors = LocalForgeColors.current

    val pickImagesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addImages(uris)
    }

    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) viewModel.executeImagesToPdf(uri)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DocSectionCard(
            sectionNumber = "01",
            title = "Image Folio Queue",
            subtitle = "Pick multiple images, reorder plates, and verify EXIF orientation.",
            spineColor = colors.bookSpineEmerald
        ) {
            OutlinedButton(
                onClick = { pickImagesLauncher.launch(arrayOf("image/*")) },
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, colors.accentCobalt),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (state.selectedImages.isEmpty()) "Add Images…" else "Add More Images…",
                    style = ForgeTextStyles.monoBadge,
                    color = colors.accentCobalt
                )
            }

            if (state.selectedImages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.selectedImages.forEachIndexed { idx, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(colors.calloutTint)
                                .border(1.dp, colors.border, RoundedCornerShape(6.dp))
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(colors.surface)
                                    .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = "P.${String.format("%02d", idx + 1)}",
                                    style = ForgeTextStyles.monoBadge,
                                    color = colors.bookSpineEmerald
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            item.thumbnail?.let { bmp ->
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = item.displayName,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.displayName,
                                    style = ForgeTextStyles.monoMeta,
                                    color = colors.primaryInk,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.widthPx} × ${item.heightPx} px • ${PageRangeParser.formatBytes(item.sizeBytes)}",
                                    style = ForgeTextStyles.monoBadge,
                                    color = colors.secondaryInk
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                SmallOrderBtn("↑", enabled = idx > 0) { viewModel.moveImageUp(idx) }
                                SmallOrderBtn("↓", enabled = idx < state.selectedImages.lastIndex) { viewModel.moveImageDown(idx) }
                                SmallOrderBtn("✕", enabled = true, isDanger = true) { viewModel.removeImageAt(idx) }
                            }
                        }
                    }
                }
            }
        }

        DocSectionCard(
            sectionNumber = "02",
            title = "Page Geometry & Scaling",
            subtitle = "Choose archival paper stock, orientation, and framing.",
            spineColor = colors.bookSpineEmerald
        ) {
            Text("PAPER SIZE PRESET", style = ForgeTextStyles.monoBadge, color = colors.secondaryInk)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PaperSizePreset.entries.forEach { preset ->
                    DocSelectableChip(
                        label = preset.label,
                        badge = preset.dimensionsBadge,
                        selected = state.imgToPdfPaper == preset,
                        onClick = { viewModel.updateImgToPdfPaper(preset) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("ORIENTATION", style = ForgeTextStyles.monoBadge, color = colors.secondaryInk)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PageOrientation.entries.forEach { orient ->
                    DocSelectableChip(
                        label = orient.label,
                        selected = state.imgToPdfOrientation == orient,
                        onClick = { viewModel.updateImgToPdfOrientation(orient) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("IMAGE FRAMING", style = ForgeTextStyles.monoBadge, color = colors.secondaryInk)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageScaleMode.entries.forEach { mode ->
                    DocSelectableChip(
                        label = mode.label,
                        selected = state.imgToPdfScaleMode == mode,
                        onClick = { viewModel.updateImgToPdfScaleMode(mode) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { createPdfLauncher.launch("ForgeFolio_Images_${System.currentTimeMillis() % 10000}.pdf") },
                enabled = state.selectedImages.isNotEmpty() && !state.progress.isRunning,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentCobalt,
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text(
                    text = "Export ${state.selectedImages.size} Plate(s) to PDF",
                    style = ForgeTextStyles.monoBadge
                )
            }
        }

        if (state.selectedImages.isEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                DocCalloutBanner(
                    title = "EXIF & MEMORY GUARD",
                    body = "Photos are auto-rotated using ExifInterface and decoded via two-pass inSampleSize sampling to prevent OutOfMemory errors.",
                    modifier = Modifier.padding(horizontal = 10.dp)
                )
                BookcaseShelfPlank(shelfLabel = "CURATOR'S NOTE")
            }
        }
    }
}

@Composable
private fun SmallOrderBtn(
    symbol: String,
    enabled: Boolean,
    isDanger: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LocalForgeColors.current
    val fg = when {
        !enabled -> colors.secondaryInk.copy(alpha = 0.35f)
        isDanger -> colors.bookSpineOxblood
        else -> colors.primaryInk
    }
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = symbol, style = ForgeTextStyles.monoBadge, color = fg)
    }
}
