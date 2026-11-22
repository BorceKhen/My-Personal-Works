package com.forgefolio.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.forgefolio.app.model.PageRangeParser
import com.forgefolio.app.model.PdfToWordMode
import com.forgefolio.app.ui.ConverterUiState
import com.forgefolio.app.ui.ConverterViewModel
import com.forgefolio.app.ui.components.DocCalloutBanner
import com.forgefolio.app.ui.components.DocSectionCard
import com.forgefolio.app.ui.components.DocSelectableChip
import com.forgefolio.app.ui.components.InteractiveFolioPreview
import com.forgefolio.app.ui.theme.ForgeTextStyles
import com.forgefolio.app.ui.theme.LocalForgeColors

@Composable
fun PdfToWordScreen(
    state: ConverterUiState,
    viewModel: ConverterViewModel
) {
    val colors = LocalForgeColors.current

    val pickPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.loadPdfForDocx(uri)
    }

    val createDocxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )
    ) { uri ->
        if (uri != null) viewModel.executePdfToDocx(uri)
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DocSectionCard(
            sectionNumber = "01",
            title = "Source PDF Volume",
            subtitle = "Choose a PDF and inspect pages before converting to Word (.docx).",
            spineColor = colors.bookSpineNavy
        ) {
            OutlinedButton(
                onClick = { pickPdfLauncher.launch(arrayOf("application/pdf")) },
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, colors.accentCobalt),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (state.pdfToDocxInfo == null) "Select PDF Document…" else "Change PDF Document…",
                    style = ForgeTextStyles.monoBadge,
                    color = colors.accentCobalt
                )
            }

            state.pdfToDocxInfo?.let { doc ->
                Spacer(modifier = Modifier.height(12.dp))
                InteractiveFolioPreview(
                    bitmap = state.pdfToDocxPreviewBitmap ?: doc.firstPageThumbnail,
                    displayName = doc.displayName,
                    sizeLabel = "SIZE: ${PageRangeParser.formatBytes(doc.sizeBytes)} • ${doc.pageCount} PAGES",
                    currentPageZeroBased = state.pdfToDocxPreviewPage,
                    totalPages = doc.pageCount,
                    onPrevPage = { viewModel.stepPdfToDocxPreview(-1) },
                    onNextPage = { viewModel.stepPdfToDocxPreview(+1) }
                )
            }
        }

        DocSectionCard(
            sectionNumber = "02",
            title = "Page Range & Conversion Mode",
            subtitle = "Switch between live PDFBox text extraction or pixel-faithful page plates.",
            spineColor = colors.bookSpineNavy
        ) {
            Text("PAGE RANGE", style = ForgeTextStyles.monoBadge, color = colors.secondaryInk)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DocSelectableChip(
                    label = "All Pages",
                    selected = state.pdfToDocxAllPages,
                    onClick = { viewModel.updatePdfToDocxAllPages(true) },
                    modifier = Modifier.weight(1f)
                )
                DocSelectableChip(
                    label = "Custom Range",
                    selected = !state.pdfToDocxAllPages,
                    onClick = { viewModel.updatePdfToDocxAllPages(false) },
                    modifier = Modifier.weight(1f)
                )
            }

            if (!state.pdfToDocxAllPages) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.pdfToDocxCustomRange,
                    onValueChange = viewModel::updatePdfToDocxCustomRange,
                    label = { Text("Pages (e.g. 1-3, 5)", style = ForgeTextStyles.monoBadge) },
                    singleLine = true,
                    textStyle = ForgeTextStyles.monoMeta,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("CONVERSION MODE", style = ForgeTextStyles.monoBadge, color = colors.secondaryInk)
            Spacer(modifier = Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PdfToWordMode.entries.forEach { mode ->
                    DocSelectableChip(
                        label = mode.label,
                        badge = mode.description,
                        selected = state.pdfToDocxMode == mode,
                        onClick = { viewModel.updatePdfToDocxMode(mode) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (state.pdfToDocxMode == PdfToWordMode.EXACT_LAYOUT) {
                Spacer(modifier = Modifier.height(12.dp))
                Text("EMBED RESOLUTION", style = ForgeTextStyles.monoBadge, color = colors.secondaryInk)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(150, 200).forEach { dpi ->
                        DocSelectableChip(
                            label = "$dpi DPI",
                            badge = if (dpi == 150) "Balanced" else "High Sharpness",
                            selected = state.pdfToDocxExactDpi == dpi,
                            onClick = { viewModel.updatePdfToDocxExactDpi(dpi) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            DocCalloutBanner(
                title = "ACTIVE PIPELINE: ${state.pdfToDocxMode.label.uppercase()}",
                body = state.pdfToDocxMode.description
            )

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val base = state.pdfToDocxInfo?.displayName?.substringBeforeLast(".") ?: "document"
                    createDocxLauncher.launch("$base.docx")
                },
                enabled = state.pdfToDocxInfo != null && !state.progress.isRunning,
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentCobalt,
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("Convert & Export .docx", style = ForgeTextStyles.monoBadge)
            }
        }
    }
}
