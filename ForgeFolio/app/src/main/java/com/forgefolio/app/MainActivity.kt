package com.forgefolio.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forgefolio.app.model.ConversionResult
import com.forgefolio.app.model.ForgeTab
import com.forgefolio.app.ui.ConverterViewModel
import com.forgefolio.app.ui.components.ConversionStatusPanel
import com.forgefolio.app.ui.components.ForgeFolioHeader
import com.forgefolio.app.ui.components.LibraryBookshelfBar
import com.forgefolio.app.ui.components.RecentShelfStrip
import com.forgefolio.app.ui.screens.ImageToPdfScreen
import com.forgefolio.app.ui.screens.PdfToImageScreen
import com.forgefolio.app.ui.screens.PdfToWordScreen
import com.forgefolio.app.ui.screens.WordToPdfScreen
import com.forgefolio.app.ui.theme.ForgeFolioTheme
import com.forgefolio.app.ui.theme.LocalForgeColors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val systemDark = isSystemInDarkTheme()
            var darkMode by rememberSaveable { mutableStateOf(systemDark) }

            ForgeFolioTheme(darkTheme = darkMode) {
                ForgeFolioAppScreen(
                    isDarkTheme = darkMode,
                    onToggleTheme = { darkMode = !darkMode }
                )
            }
        }
    }
}

@Composable
fun ForgeFolioAppScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    viewModel: ConverterViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = LocalForgeColors.current
    val context = LocalContext.current

    fun openUri(uri: android.net.Uri, displayName: String, mimeType: String, isFolderTree: Boolean) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            if (isFolderTree) {
                setDataAndType(uri, "vnd.android.document/directory")
            } else {
                setDataAndType(uri, mimeType)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Open $displayName"))
        }.onFailure {
            Toast.makeText(context, "No viewer app found for $displayName", Toast.LENGTH_SHORT).show()
        }
    }

    fun openResult(res: ConversionResult) =
        openUri(res.uri, res.displayName, res.mimeType, res.isFolderTree)

    fun shareResult(res: ConversionResult) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = res.mimeType
            putExtra(Intent.EXTRA_STREAM, res.uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Share ${res.displayName}"))
        }.onFailure {
            Toast.makeText(context, "Unable to share ${res.displayName}", Toast.LENGTH_SHORT).show()
        }
    }

    val loadedBadges = mapOf(
        ForgeTab.PDF_TO_IMG to state.pdfToImgDoc?.displayName,
        ForgeTab.IMG_TO_PDF to if (state.selectedImages.isNotEmpty()) "${state.selectedImages.size}" else null,
        ForgeTab.DOCX_TO_PDF to state.docxToPdfInfo?.displayName,
        ForgeTab.PDF_TO_DOCX to state.pdfToDocxInfo?.displayName
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
    ) {
        ForgeFolioHeader(
            isDarkTheme = isDarkTheme,
            onToggleTheme = onToggleTheme
        )

        HorizontalDivider(thickness = 1.dp, color = colors.border)

        LibraryBookshelfBar(
            selectedTab = state.selectedTab,
            loadedBadges = loadedBadges,
            onSelectTab = viewModel::selectTab
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            RecentShelfStrip(
                entries = state.shelfHistory,
                onEntryClick = { entry ->
                    if (entry.isExportedOutput) {
                        openUri(entry.uri, entry.title, entry.mimeType, entry.isFolderTree)
                    } else {
                        viewModel.selectTab(entry.sourceTab)
                    }
                }
            )

            Crossfade(
                targetState = state.selectedTab,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                label = "folioTabCrossfade"
            ) { activeTab ->
                when (activeTab) {
                    ForgeTab.PDF_TO_IMG -> PdfToImageScreen(state = state, viewModel = viewModel)
                    ForgeTab.IMG_TO_PDF -> ImageToPdfScreen(state = state, viewModel = viewModel)
                    ForgeTab.DOCX_TO_PDF -> WordToPdfScreen(state = state, viewModel = viewModel)
                    ForgeTab.PDF_TO_DOCX -> PdfToWordScreen(state = state, viewModel = viewModel)
                }
            }

            ConversionStatusPanel(
                progress = state.progress,
                result = state.result,
                errorMessage = state.errorMessage,
                onOpenResult = ::openResult,
                onShareResult = ::shareResult
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
