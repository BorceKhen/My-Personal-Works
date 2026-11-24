package com.forgefolio.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forgefolio.app.model.ImagePageLayout
import com.forgefolio.app.model.ImagePageMargin
import com.forgefolio.app.model.ImagePdfLayoutEngine
import com.forgefolio.app.model.ImageScaleMode
import com.forgefolio.app.model.ImageSlotAlignment
import com.forgefolio.app.model.PageOrientation
import com.forgefolio.app.model.PageRangeParser
import com.forgefolio.app.model.PaperSizePreset
import com.forgefolio.app.model.SelectedImageItem
import com.forgefolio.app.ui.ConverterUiState
import com.forgefolio.app.ui.ConverterViewModel
import com.forgefolio.app.ui.components.BookcaseShelfPlank
import com.forgefolio.app.ui.components.DocCalloutBanner
import com.forgefolio.app.ui.components.DocSectionCard
import com.forgefolio.app.ui.components.DocSelectableChip
import com.forgefolio.app.ui.theme.ForgeTextStyles
import com.forgefolio.app.ui.theme.LocalForgeColors
import kotlin.math.roundToInt

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

    val pages = ImagePdfLayoutEngine.paginate(state.selectedImages, state.imgToPdfLayout)
    val pageCount = pages.size

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DocSectionCard(
            sectionNumber = "01",
            title = "Image Folio Queue",
            subtitle = "Pick images, reorder plates, or rotate 90° before laying out pages.",
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
                        val (effW, effH) = item.effectiveDimensions()
                        val displayThumb = remember(item.id, item.thumbnail, item.rotationDegrees) {
                            rotatePreviewBitmap(item.thumbnail, item.rotationDegrees)
                        }
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
                            displayThumb?.let { bmp ->
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
                                val rotSuffix = if (item.rotationDegrees != 0) " • ${item.rotationDegrees}°" else ""
                                Text(
                                    text = "$effW × $effH px • ${PageRangeParser.formatBytes(item.sizeBytes)}$rotSuffix",
                                    style = ForgeTextStyles.monoBadge,
                                    color = colors.secondaryInk
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                SmallOrderBtn("↻", enabled = true) { viewModel.rotateImageAt(idx) }
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
            title = "Page Geometry & Layout",
            subtitle = "Set paper stock, grid per page, margins, framing, and alignment.",
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
            Text("IMAGES PER PAGE", style = ForgeTextStyles.monoBadge, color = colors.secondaryInk)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImagePageLayout.entries.forEach { layoutOpt ->
                    DocSelectableChip(
                        label = layoutOpt.label,
                        badge = layoutOpt.badge,
                        selected = state.imgToPdfLayout == layoutOpt,
                        onClick = { viewModel.updateImgToPdfLayout(layoutOpt) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("PAGE MARGIN", style = ForgeTextStyles.monoBadge, color = colors.secondaryInk)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ImagePageMargin.entries.forEach { marginOpt ->
                    DocSelectableChip(
                        label = marginOpt.label,
                        badge = marginOpt.badge,
                        selected = state.imgToPdfMargin == marginOpt,
                        onClick = { viewModel.updateImgToPdfMargin(marginOpt) }
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

            if (state.imgToPdfScaleMode == ImageScaleMode.FIT_INSIDE) {
                Spacer(modifier = Modifier.height(14.dp))
                Text("SLOT ALIGNMENT", style = ForgeTextStyles.monoBadge, color = colors.secondaryInk)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ImageSlotAlignment.entries.forEach { alignOpt ->
                        DocSelectableChip(
                            label = alignOpt.label,
                            selected = state.imgToPdfAlignment == alignOpt,
                            onClick = { viewModel.updateImgToPdfAlignment(alignOpt) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        DocSectionCard(
            sectionNumber = "03",
            title = "Live Layout Preview & Export",
            subtitle = "WYSIWYG PDF sheet preview. Tap any plate on the sheet to rotate 90°.",
            spineColor = colors.bookSpineEmerald
        ) {
            if (state.selectedImages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.libraryShelfTint)
                        .border(1.dp, colors.border, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Add images in VOL.01 to arrange & preview PDF pages",
                        style = ForgeTextStyles.monoMeta,
                        color = colors.secondaryInk
                    )
                }
            } else {
                val safePage = state.imgToPdfPreviewPage.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                val pageItems = pages[safePage]
                val startGlobalIdx = safePage * state.imgToPdfLayout.perPage

                LivePdfLayoutSheetPreview(
                    pageItems = pageItems,
                    startGlobalIndex = startGlobalIdx,
                    paperPreset = state.imgToPdfPaper,
                    orientation = state.imgToPdfOrientation,
                    layout = state.imgToPdfLayout,
                    margin = state.imgToPdfMargin,
                    scaleMode = state.imgToPdfScaleMode,
                    alignment = state.imgToPdfAlignment,
                    onTapRotatePlate = { globalIdx -> viewModel.rotateImageAt(globalIdx) }
                )

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallTextNavBtn("◀ Prev", enabled = safePage > 0) {
                        viewModel.stepImgToPdfPreviewPage(-1)
                    }
                    Text(
                        text = "PDF PAGE ${String.format("%02d", safePage + 1)} / ${String.format("%02d", pageCount)}",
                        style = ForgeTextStyles.monoBadge,
                        fontWeight = FontWeight.Bold,
                        color = colors.accentCobalt
                    )
                    SmallTextNavBtn("Next ▶", enabled = safePage < pageCount - 1) {
                        viewModel.stepImgToPdfPreviewPage(+1)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
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
                val exportLabel = if (state.selectedImages.isEmpty()) {
                    "Export Plates to PDF"
                } else {
                    "Export ${state.selectedImages.size} Plate(s) → $pageCount PDF Page(s)"
                }
                Text(
                    text = exportLabel,
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
private fun LivePdfLayoutSheetPreview(
    pageItems: List<SelectedImageItem>,
    startGlobalIndex: Int,
    paperPreset: PaperSizePreset,
    orientation: PageOrientation,
    layout: ImagePageLayout,
    margin: ImagePageMargin,
    scaleMode: ImageScaleMode,
    alignment: ImageSlotAlignment,
    onTapRotatePlate: (Int) -> Unit
) {
    val colors = LocalForgeColors.current
    val (refW, refH) = pageItems.first().effectiveDimensions()
    val (pageW, pageH) = ImagePdfLayoutEngine.resolvePageDimensionsPt(
        refWidthPx = refW,
        refHeightPx = refH,
        preset = paperPreset,
        orientation = orientation
    )
    val sheetAspect = (pageW.toFloat() / pageH.toFloat()).coerceIn(0.45f, 2.1f)
    val slots = ImagePdfLayoutEngine.computeSlotRects(
        pageW = pageW.toFloat(),
        pageH = pageH.toFloat(),
        marginPt = margin.marginPt.toFloat(),
        gapPt = 12f,
        layout = layout,
        orientation = orientation
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(colors.libraryShelfTint)
            .border(1.dp, colors.border, RoundedCornerShape(6.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "${paperPreset.label.uppercase()} • $pageW × $pageH PT • MARGIN ${margin.badge.uppercase()}",
            style = ForgeTextStyles.monoBadge.copy(fontSize = 10.sp),
            color = colors.secondaryInk
        )
        Spacer(modifier = Modifier.height(10.dp))

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            val density = LocalDensity.current
            val cappedMaxW = if (maxWidth < 260.dp) maxWidth else 260.dp
            val cappedMaxH = 310.dp
            val (sheetWidthDp, sheetHeightDp) = if (cappedMaxW / sheetAspect <= cappedMaxH) {
                cappedMaxW to (cappedMaxW / sheetAspect)
            } else {
                (cappedMaxH * sheetAspect) to cappedMaxH
            }

            val boxW = with(density) { sheetWidthDp.toPx() }
            val boxH = with(density) { sheetHeightDp.toPx() }
            val scaleX = boxW / pageW.toFloat()
            val scaleY = boxH / pageH.toFloat()

            Box(
                modifier = Modifier
                    .size(width = sheetWidthDp, height = sheetHeightDp)
                    .background(Color.White)
                    .border(1.5.dp, colors.brassFoilGold.copy(alpha = 0.85f))
            ) {
                // Dashed architectural page margin & slot blueprint lines
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val dash = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                    slots.forEach { slot ->
                        drawRect(
                            color = Color(0xFF94A3B8).copy(alpha = 0.55f),
                            topLeft = Offset(slot.left * scaleX, slot.top * scaleY),
                            size = Size(slot.width * scaleX, slot.height * scaleY),
                            style = Stroke(width = 1.dp.toPx(), pathEffect = dash)
                        )
                    }
                }

                // Rendered plates placed identically to PdfConversionRepository
                pageItems.forEachIndexed { slotIdx, item ->
                    val slot = slots.getOrNull(slotIdx) ?: return@forEachIndexed
                    val (effW, effH) = item.effectiveDimensions()
                    val dst = ImagePdfLayoutEngine.computeImageRectInSlot(
                        srcW = effW,
                        srcH = effH,
                        slot = slot,
                        scaleMode = scaleMode,
                        alignment = alignment
                    )

                    val rotatedThumb = remember(item.id, item.thumbnail, item.rotationDegrees) {
                        rotatePreviewBitmap(item.thumbnail, item.rotationDegrees)
                    }

                    val slotLeftPx = (slot.left * scaleX).roundToInt()
                    val slotTopPx = (slot.top * scaleY).roundToInt()
                    val slotWidthDp = with(density) { (slot.width * scaleX).toDp() }
                    val slotHeightDp = with(density) { (slot.height * scaleY).toDp() }

                    val relLeftPx = ((dst.left - slot.left) * scaleX).roundToInt()
                    val relTopPx = ((dst.top - slot.top) * scaleY).roundToInt()
                    val drawWidthDp = with(density) { (dst.width * scaleX).toDp() }
                    val drawHeightDp = with(density) { (dst.height * scaleY).toDp() }

                    val globalIdx = startGlobalIndex + slotIdx
                    Box(
                        modifier = Modifier
                            .offset { IntOffset(slotLeftPx, slotTopPx) }
                            .size(width = slotWidthDp, height = slotHeightDp)
                            .clip(RoundedCornerShape(0.dp))
                            .clickable { onTapRotatePlate(globalIdx) }
                    ) {
                        if (rotatedThumb != null) {
                            Image(
                                bitmap = rotatedThumb.asImageBitmap(),
                                contentDescription = item.displayName,
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier
                                    .offset { IntOffset(relLeftPx, relTopPx) }
                                    .size(width = drawWidthDp, height = drawHeightDp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .offset { IntOffset(relLeftPx, relTopPx) }
                                    .size(width = drawWidthDp, height = drawHeightDp)
                                    .background(Color(0xFFE2E8F0))
                            )
                        }

                        // Tiny plate tag in top-left of slot
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(3.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xB30F172A))
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "P.${String.format("%02d", globalIdx + 1)} ↻",
                                style = ForgeTextStyles.monoBadge.copy(fontSize = 9.sp, lineHeight = 11.sp),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun rotatePreviewBitmap(source: Bitmap?, rotationDegrees: Int): Bitmap? {
    if (source == null) return null
    val norm = ((rotationDegrees % 360) + 360) % 360
    if (norm == 0) return source
    val matrix = Matrix().apply { postRotate(norm.toFloat()) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

@Composable
private fun SmallTextNavBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
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
