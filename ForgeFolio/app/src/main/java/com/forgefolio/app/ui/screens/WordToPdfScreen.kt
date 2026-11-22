package com.forgefolio.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.forgefolio.app.model.MarginPreset
import com.forgefolio.app.model.PageRangeParser
import com.forgefolio.app.model.PaperSizePreset
import com.forgefolio.app.ui.ConverterUiState
import com.forgefolio.app.ui.ConverterViewModel
import com.forgefolio.app.ui.components.DocCalloutBanner
import com.forgefolio.app.ui.components.DocSectionCard
import com.forgefolio.app.ui.components.DocSelectableChip
import com.forgefolio.app.ui.theme.ForgeTextStyles
import com.forgefolio.app.ui.theme.LocalForgeColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordToPdfScreen(
    state: ConverterUiState,
    viewModel: ConverterViewModel
) {
    val colors = LocalForgeColors.current

    val pickDocxLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.loadDocxForPdf(uri)
    }

    val createPdfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) viewModel.executeDocxToPdf(uri)
    }

    val docxPaperOptions = listOf(
        PaperSizePreset.SHORT_BOND,
        PaperSizePreset.LONG_BOND,
        PaperSizePreset.A4,
        PaperSizePreset.LEGAL
    )

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        DocSectionCard(
            sectionNumber = "01",
            title = "Source Word Manuscript (.docx)",
            subtitle = "Parse paragraphs, headings, bold/italic runs, and tables offline via Apache POI.",
            spineColor = colors.brassFoilGold
        ) {
            OutlinedButton(
                onClick = {
                    pickDocxLauncher.launch(
                        arrayOf(
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "application/octet-stream"
                        )
                    )
                },
                shape = RoundedCornerShapeFixed,
                border = BorderStroke(1.dp, colors.accentCobalt),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (state.docxToPdfInfo == null) "Select .docx Document…" else "Change .docx Document…",
                    style = ForgeTextStyles.monoBadge,
                    color = colors.accentCobalt
                )
            }

            state.docxToPdfInfo?.let { info ->
                Spacer(modifier = Modifier.height(12.dp))
                DocCalloutBanner(
                    title = info.displayName,
                    body = "SIZE: ${PageRangeParser.formatBytes(info.sizeBytes)} • PARAGRAPHS: ${info.paragraphCount} • TABLES: ${info.tableCount}",
                    accentColor = colors.brassFoilGold
                )
            }
        }

        DocSectionCard(
            sectionNumber = "02",
            title = "Page Setup & Typesetting",
            subtitle = "Paginated with Android StaticLayout onto native PdfDocument canvases.",
            spineColor = colors.brassFoilGold
        ) {
            Text("PAPER STOCK", style = ForgeTextStyles.monoBadge, color = colors.secondaryInk)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                docxPaperOptions.forEach { preset ->
                    DocSelectableChip(
                        label = preset.label,
                        badge = preset.dimensionsBadge,
                        selected = state.docxToPdfPaper == preset,
                        onClick = { viewModel.updateDocxToPdfPaper(preset) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("MARGIN PRESET", style = ForgeTextStyles.monoBadge, color = colors.secondaryInk)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MarginPreset.entries.forEach { margin ->
                    DocSelectableChip(
                        label = margin.label,
                        badge = margin.badge,
                        selected = state.docxToPdfMargin == margin,
                        onClick = { viewModel.updateDocxToPdfMargin(margin) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val base = state.docxToPdfInfo?.displayName?.substringBeforeLast(".") ?: "manuscript"
                    createPdfLauncher.launch("$base.pdf")
                },
                enabled = state.docxToPdfInfo != null && !state.progress.isRunning,
                shape = RoundedCornerShapeFixed,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentCobalt,
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth().height(46.dp)
            ) {
                Text("Typeset & Export PDF", style = ForgeTextStyles.monoBadge)
            }
        }
    }
}

private val RoundedCornerShapeFixed = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
