package com.forgefolio.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.forgefolio.app.model.ImageOutputFormat
import com.forgefolio.app.model.PageRangeParser
import com.forgefolio.app.model.PdfDpiPreset
import com.forgefolio.app.model.PdfToImageOutputMode
import com.forgefolio.app.ui.ConverterUiState
import com.forgefolio.app.ui.ConverterViewModel
import com.forgefolio.app.ui.components.DocCalloutBanner
import com.forgefolio.app.ui.components.DocSectionCard
import com.forgefolio.app.ui.components.DocSelectableChip
import com.forgefolio.app.ui.theme.ForgeTextStyles
import com.forgefolio.app.ui.theme.LocalForgeColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PdfToImageScreen(
    state: ConverterUiState,
    viewModel: ConverterViewModel
) {
    val colors = LocalForgeColors.current

    val pickPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.loadPdfForImages(uri)
    }

    val pickFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) viewModel.executePdfToImages(uri)
    }

    val createZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        if (uri != null) viewModel.executePdfToImages(uri)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DocSectionCard(
            sectionNumber = "01",
            title = "Source PDF Folio",
            subtitle = "Select a local PDF volume to rasterize into archival image plates.",
            spineColor = colors.bookSpineOxblood
        ) {
            OutlinedButton(
                onClick = { pickPdfLauncher.launch(arrayOf("application/pdf")) },
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, colors.accentCobalt),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (state.pdfToImgDoc == null) "Select PDF Document…" else "Change PDF Document…",
                    style = ForgeTextStyles.monoBadge,
                    color = colors.accentCobalt
                )
            }

            state.pdfToImgDoc?.let { doc ->
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    doc.firstPageThumbnail?.let { thumb ->
                        Image(
                            bitmap = thumb.asImageBitmap(),
                            contentDescription = "Page 1 thumbnail",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(width = 76.dp, height = 100.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, colors.border, RoundedCornerShape(4.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = doc.displayName,
                            style = ForgeTextStyles.sectionHeading,
                            color = colors.primaryInk
                        )
                        Text(
                            text = "SIZE: ${PageRangeParser.formatBytes(doc.sizeBytes)}",
                            style = ForgeTextStyles.monoMeta,
                            color = colors.secondaryInk
                        )
                        Text(
                            text = "PAGES: ${doc.pageCount} • PAGE 1 PREVIEW",
                            style = ForgeTextStyles.monoMeta,
                            color = colors.accentCobalt
                        )
                    }
                }
            }
        }

        DocSectionCard(
            sectionNumber = "02",
            title = "Extraction Parameters",
            subtitle = "Configure page range, raster DPI on white archival canvas, and container.",
            spineColor = colors.bookSpineOxblood
        ) {
            Text("PAGE RANGE", style = ForgeTextStyles.monoBadge, color = colors.secondaryInk)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DocSelectableChip(
                    label = "All Pages",
                    selected = state.pdfToImgAllPages,
                    onClick = { viewModel.updatePdfToImgAllPages(true) },
                    modifier = Modifier.weight(1f)
                )
                DocSelectableChip(
                    label = "Custom Range",
                    selected = !state.pdfToImgAllPages,
                    onClick = { viewModel.updatePdfToImgAllPages(false) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (!state.pdfToImgAllPages) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.pdfToImgCustomRange,
                    onValueChange = viewModel::updatePdfToImgCustomRange,
                    label = { Text("Pages (e.g. 1-3, 5)", style = ForgeTextStyles.monoBadge) },
                    singleLine = true,
                    textStyle = ForgeTextStyles.monoMeta,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("IMAGE FORMAT", style = ForgeTextStyles.monoBadge, color = colors.secondaryInk)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ImageOutputFormat.entries.forEach { fmt ->
                    DocSelectableChip(
                        label = fmt.label,
                        badge = ".${fmt.ext}",
                        selected = state.pdfToImgFormat == fmt,
                        onClick = { viewModel.updatePdfToImgFormat(fmt) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("RESOLUTION", style = ForgeTextStyles.monoBadge, color = colors.secondaryInk)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PdfDpiPreset.entries.forEach { preset ->
                    DocSelectableChip(
                        label = "${preset.dpi} DPI",
                        badge = preset.badge.substringAfter("• "),
                        selected = state.pdfToImgDpi == preset,
                        onClick = { viewModel.updatePdfToImgDpi(preset) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("COMPRESSION QUALITY", style = ForgeTextStyles.monoBadge, color = colors.secondaryInk)
                Text("${state.pdfToImgQuality}%", style = ForgeTextStyles.monoMeta, color = colors.accentCobalt)
            }
            Slider(
                value = state.pdfToImgQuality.toFloat(),
                onValueChange = { viewModel.updatePdfToImgQuality(it.toInt()) },
                valueRange = 10f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = colors.accentCobalt,
                    activeTrackColor = colors.accentCobalt
                )
            )

            Spacer(modifier = Modifier.height(10.dp))
            Text("OUTPUT TARGET", style = ForgeTextStyles.monoBadge, color = colors.secondaryInk)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PdfToImageOutputMode.entries.forEach { mode ->
                    DocSelectableChip(
                        label = mode.label,
                        selected = state.pdfToImgOutputMode == mode,
                        onClick = { viewModel.updatePdfToImgOutputMode(mode) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val doc = state.pdfToImgDoc ?: return@Button
                    val base = doc.displayName.substringBeforeLast(".").ifBlank { "document" }
                    if (state.pdfToImgOutputMode == PdfToImageOutputMode.FOLDER) {
                        pickFolderLauncher.launch(null)
                    } else {
                        createZipLauncher.launch("${base}_pages.zip")
                    }
                },
                enabled = state.pdfToImgDoc != null && !state.progress.isRunning,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentCobalt,
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text(
                    text = if (state.pdfToImgOutputMode == PdfToImageOutputMode.FOLDER) {
                        "Choose Output Folder & Export"
                    } else {
                        "Export Pages as ZIP Archive"
                    },
                    style = ForgeTextStyles.monoBadge
                )
            }
        }

        if (state.pdfToImgDoc == null) {
            DocCalloutBanner(
                title = "ARCHIVAL NOTE • WHITE CANVAS RENDER",
                body = "Transparent PDF pages are automatically composited onto pure white (#FFFFFF) so JPG and PNG exports remain legible."
            )
        }
    }
}
