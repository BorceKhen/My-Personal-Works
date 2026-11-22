package com.forgefolio.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.forgefolio.app.model.ConversionResult
import com.forgefolio.app.model.ForgeTab
import com.forgefolio.app.ui.ConverterViewModel
import com.forgefolio.app.ui.components.ConversionStatusPanel
import com.forgefolio.app.ui.components.ForgeFolioHeader
import com.forgefolio.app.ui.screens.ImageToPdfScreen
import com.forgefolio.app.ui.screens.PdfToImageScreen
import com.forgefolio.app.ui.screens.PdfToWordScreen
import com.forgefolio.app.ui.screens.WordToPdfScreen
import com.forgefolio.app.ui.theme.ForgeFolioTheme
import com.forgefolio.app.ui.theme.ForgeTextStyles
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

    fun openResult(res: ConversionResult) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            if (res.isFolderTree) {
                setDataAndType(res.uri, "vnd.android.document/directory")
            } else {
                setDataAndType(res.uri, res.mimeType)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching {
            context.startActivity(Intent.createChooser(intent, "Open ${res.displayName}"))
        }.onFailure {
            Toast.makeText(context, "No viewer app found for ${res.displayName}", Toast.LENGTH_SHORT).show()
        }
    }

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

        ScrollableTabRow(
            selectedTabIndex = state.selectedTab.index,
            containerColor = colors.surface,
            contentColor = colors.primaryInk,
            edgePadding = 12.dp,
            divider = { HorizontalDivider(thickness = 1.dp, color = colors.border) },
            indicator = { tabPositions ->
                val pos = tabPositions[state.selectedTab.index]
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(pos),
                    height = 2.dp,
                    color = colors.accentCobalt
                )
            }
        ) {
            ForgeTab.entries.forEach { tab ->
                val selected = state.selectedTab == tab
                val spineDot = when (tab) {
                    ForgeTab.PDF_TO_IMG -> colors.bookSpineOxblood
                    ForgeTab.IMG_TO_PDF -> colors.bookSpineEmerald
                    ForgeTab.DOCX_TO_PDF -> colors.brassFoilGold
                    ForgeTab.PDF_TO_DOCX -> colors.accentCobalt
                }
                Tab(
                    selected = selected,
                    onClick = { viewModel.selectTab(tab) },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(spineDot)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tab.label,
                                style = ForgeTextStyles.monoBadge,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (selected) colors.accentCobalt else colors.secondaryInk
                            )
                        }
                    }
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (state.selectedTab) {
                ForgeTab.PDF_TO_IMG -> PdfToImageScreen(state = state, viewModel = viewModel)
                ForgeTab.IMG_TO_PDF -> ImageToPdfScreen(state = state, viewModel = viewModel)
                ForgeTab.DOCX_TO_PDF -> WordToPdfScreen(state = state, viewModel = viewModel)
                ForgeTab.PDF_TO_DOCX -> PdfToWordScreen(state = state, viewModel = viewModel)
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
