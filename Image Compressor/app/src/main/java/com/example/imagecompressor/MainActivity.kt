package com.example.imagecompressor

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.collectLatest
import java.io.File

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF00B4D8),
                    onPrimary = Color(0xFF03045E),
                    primaryContainer = Color(0xFF0077B6),
                    surface = Color(0xFF0D1B2A),
                    background = Color(0xFF060B14),
                    surfaceVariant = Color(0xFF1B263B),
                    onSurface = Color(0xFFE0E1DD),
                    secondary = Color(0xFF48CAE4),
                    tertiary = Color(0xFF2EC4B6)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PhotoBoothScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoBoothScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedMode by viewModel.selectedMode.collectAsState()
    val targetFormat by viewModel.targetFormat.collectAsState()
    val targetQuality by viewModel.targetQuality.collectAsState()

    var showInstructionsDialog by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.processImageUri(uri)
        }
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            photoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        } else {
            showPermissionRationale = true
        }
    }

    var pendingSaveFile by remember { mutableStateOf<Pair<File, OutputImageFormat>?>(null) }
    val writePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            pendingSaveFile?.let { (file, format) ->
                viewModel.saveToGallery(file, format)
            }
        } else {
            Toast.makeText(context, "Storage permission is required to save photos.", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collectLatest { event ->
            when (event) {
                is UiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is UiEvent.RequestWritePermission -> {
                    pendingSaveFile = Pair(event.fileToSave, event.format)
                    writePermissionLauncher.launch(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            PhotoBoothMarqueeHeader(
                onOpenInstructions = { showInstructionsDialog = true }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF060B14), Color(0xFF0D1B2A), Color(0xFF050910))
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (val state = uiState) {
                    is CompressionUiState.Idle -> {
                        BoothKioskIdleView(
                            selectedMode = selectedMode,
                            targetFormat = targetFormat,
                            targetQuality = targetQuality,
                            onModeSelected = { viewModel.setCompressionMode(it) },
                            onFormatSelected = { viewModel.setTargetFormat(it) },
                            onQualityChanged = { viewModel.setTargetQuality(it) },
                            onOpenPicker = {
                                if (PermissionUtil.hasReadPermission(context)) {
                                    photoPickerLauncher.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                } else {
                                    storagePermissionLauncher.launch(PermissionUtil.getRequiredReadPermission())
                                }
                            },
                            onOpenGuide = { showInstructionsDialog = true }
                        )
                    }

                    is CompressionUiState.Loading -> {
                        BoothDevelopingCurtainView(message = state.message)
                    }

                    is CompressionUiState.Success -> {
                        PhotoPrintCollectionTrayView(
                            state = state,
                            onSave = { viewModel.saveToGallery(state.result.outputFile, state.result.outputFormat) },
                            onShare = { FileUtil.shareImageFile(context, state.result.outputFile, state.result.outputFormat) },
                            onReset = { viewModel.reset() }
                        )
                    }

                    is CompressionUiState.Error -> {
                        BoothErrorView(
                            errorMessage = state.message,
                            onRetry = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    if (showInstructionsDialog) {
        PhotoBoothInstructionsModal(
            onDismiss = { showInstructionsDialog = false }
        )
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text("Storage Access Needed", fontWeight = FontWeight.Bold) },
            text = {
                Text("The Photo Booth needs access to your device's photos to import and optimize your images. Please enable permission in App Settings.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionRationale = false
                        context.startActivity(PermissionUtil.getAppSettingsIntent(context))
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionRationale = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun PhotoBoothMarqueeHeader(onOpenInstructions: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF091424),
        border = BorderStroke(1.dp, Color(0xFF0077B6).copy(alpha = 0.5f)),
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF00B4D8))
                        .shadow(4.dp, CircleShape)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "● PHOTO BOOTH ●",
                        color = Color(0xFFCAF0F8),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "SMART IMAGE COMPRESSOR",
                        color = Color(0xFF90E0EF),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            IconButton(
                onClick = onOpenInstructions,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1B263B))
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Booth Instructions",
                    tint = Color(0xFF48CAE4),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun BoothKioskIdleView(
    selectedMode: CompressionMode,
    targetFormat: TargetFormat,
    targetQuality: Int,
    onModeSelected: (CompressionMode) -> Unit,
    onFormatSelected: (TargetFormat) -> Unit,
    onQualityChanged: (Int) -> Unit,
    onOpenPicker: () -> Unit,
    onOpenGuide: () -> Unit
) {
    Spacer(modifier = Modifier.height(6.dp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
        border = BorderStroke(2.dp, Color(0xFFCBD5E1))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            // Top Status Bar on Kiosk
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEAB308))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "KIOSK STANDBY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569),
                        fontFamily = FontFamily.Monospace
                    )
                }

                Surface(
                    onClick = onOpenGuide,
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFE2E8F0)
                ) {
                    Text(
                        text = "HOW IT WORKS ⓘ",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF070D19)),
                border = BorderStroke(1.5.dp, Color(0xFF1E293B))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {

                    // Mode Selection Tabs
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF0F172A)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            BoothModeTab(
                                title = "Visually Lossless",
                                subtitle = "30% - 70% savings",
                                isSelected = selectedMode == CompressionMode.VISUALLY_LOSSLESS,
                                modifier = Modifier.weight(1f),
                                onClick = { onModeSelected(CompressionMode.VISUALLY_LOSSLESS) }
                            )
                            BoothModeTab(
                                title = "Pure Lossless",
                                subtitle = "0% quality change",
                                isSelected = selectedMode == CompressionMode.PURE_LOSSLESS,
                                modifier = Modifier.weight(1f),
                                onClick = { onModeSelected(CompressionMode.PURE_LOSSLESS) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. Output Format Selector
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF0D1B2A))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "DESIRED OUTPUT FORMAT",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TargetFormat.values().forEach { format ->
                                val isSelected = (targetFormat == format)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onFormatSelected(format) },
                                    label = {
                                        Text(
                                            text = format.displayName,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFF0284C7),
                                        selectedLabelColor = Color.White,
                                        containerColor = Color(0xFF1E293B),
                                        labelColor = Color(0xFFCBD5E1)
                                    ),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isSelected) Color(0xFF38BDF8) else Color(0xFF334155)
                                    )
                                )
                            }
                        }

                        if (targetFormat == TargetFormat.JPEG) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "💡 Note: Transparent areas in PNGs will be placed onto clean solid white when saving as JPEG.",
                                fontSize = 10.sp,
                                color = Color(0xFF7DD3FC),
                                lineHeight = 13.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // 2. Dynamic Quality Slider
                    if (selectedMode == CompressionMode.VISUALLY_LOSSLESS) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0D1B2A))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TARGET QUALITY",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF94A3B8),
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "$targetQuality% (Recommended: 60%)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                            Slider(
                                value = targetQuality.toFloat(),
                                onValueChange = { onQualityChanged(it.toInt()) },
                                valueRange = 40f..90f,
                                steps = 9,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF38BDF8),
                                    activeTrackColor = Color(0xFF0284C7),
                                    inactiveTrackColor = Color(0xFF1E293B)
                                )
                            )
                            Text(
                                text = "Dynamic for all formats: applies color quantization for PNG and 4:2:0 subsampling for JPEG/WebP.",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B),
                                lineHeight = 14.sp
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF0D1B2A))
                                .padding(12.dp)
                        ) {
                            Text(
                                text = "PURE MATHEMATICAL LOSSLESS",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4ADE80),
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "100% pixel-identical DEFLATE / Huffman encoding. Zero pixel change.",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8),
                                lineHeight = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Insert Photo Button
                    Button(
                        onClick = onOpenPicker,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Insert Photo",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "INSERT PHOTO INTO BOOTH",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BoothModeTab(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) Color(0xFF0284C7) else Color.Transparent
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = if (isSelected) Color.White else Color(0xFF94A3B8)
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = if (isSelected) Color(0xFFE0F2FE) else Color(0xFF475569)
            )
        }
    }
}

@Composable
fun BoothDevelopingCurtainView(message: String) {
    Spacer(modifier = Modifier.height(60.dp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(16.dp, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B192C)),
        border = BorderStroke(2.dp, Color(0xFF0077B6))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(68.dp),
                strokeWidth = 6.dp,
                color = Color(0xFF00B4D8),
                trackColor = Color(0xFF1E3E62)
            )

            Spacer(modifier = Modifier.height(26.dp))

            Text(
                text = "● IN THE BOOTH ●",
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp,
                color = Color(0xFF90E0EF),
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFE0E1DD),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Developing scanlines, recalculating optimal Huffman codes & stripping metadata...",
                fontSize = 12.sp,
                color = Color(0xFF64748B),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PhotoPrintCollectionTrayView(
    state: CompressionUiState.Success,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onReset: () -> Unit
) {
    val result = state.result
    val bitmap = remember(result.outputFile) {
        BitmapFactory.decodeFile(result.outputFile.absolutePath)?.asImageBitmap()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = BorderStroke(2.dp, Color(0xFFCBD5E1))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            // Tray Slotted Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF334155))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "▼ COLLECT YOUR PHOTO HERE ▼",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFF8FAFC),
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "READY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4ADE80),
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // White Glossy Photo Print Frame
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Photo Print Preview",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF0F172A)),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "PHOTO BOOTH PRINT • ${result.outputFormat.displayName} (.${result.outputFormat.extension})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Printed Print Receipt / Compression Stats
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PRINT SPECIFICATIONS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF94A3B8),
                            fontFamily = FontFamily.Monospace
                        )
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF14532D)
                        ) {
                            Text(
                                text = "-${String.format(java.util.Locale.US, "%.1f", result.reductionPercentage)}% SAVED",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF4ADE80)
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color(0xFF1E293B))

                    // Format Conversion Row
                    val formatLabel = if (result.originalFormat == result.outputFormat) {
                        "${result.originalFormat.displayName} (Retained)"
                    } else {
                        "${result.originalFormat.displayName} → ${result.outputFormat.displayName}"
                    }
                    BoothStatLine("Format Conversion", formatLabel, Color(0xFF38BDF8))
                    Spacer(modifier = Modifier.height(6.dp))

                    BoothStatLine("Original File Size", FileUtil.formatFileSize(result.originalSizeBytes))
                    Spacer(modifier = Modifier.height(6.dp))
                    BoothStatLine("Optimized Print Size", FileUtil.formatFileSize(result.compressedSizeBytes))
                    Spacer(modifier = Modifier.height(6.dp))
                    BoothStatLine("Storage Reclaimed", FileUtil.formatFileSize(result.savedBytes), Color(0xFF4ADE80))
                    Spacer(modifier = Modifier.height(6.dp))

                    val qualityDesc = if (state.mode == CompressionMode.PURE_LOSSLESS) {
                        "Pure Lossless (100%)"
                    } else {
                        "${result.usedQuality}% (Quantized / Subsampled)"
                    }
                    BoothStatLine("Compression Level", qualityDesc)
                    Spacer(modifier = Modifier.height(6.dp))
                    BoothStatLine("Processing Speed", "${result.elapsedMillis} ms")

                    if (result.convertedAlphaToWhite) {
                        Spacer(modifier = Modifier.height(6.dp))
                        BoothStatLine("Alpha Background", "Solid White Matte", Color(0xFFFDE047))
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = onSave,
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                ) {
                    Text("Print / Save to Gallery", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                FilledTonalButton(
                    onClick = onShare,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFF334155))
                ) {
                    Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedButton(
                onClick = onReset,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.5.dp, Color(0xFF94A3B8))
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "New Photo", modifier = Modifier.size(16.dp), tint = Color(0xFF334155))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Take / Insert Another Photo", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF334155))
            }
        }
    }
}

@Composable
fun BoothStatLine(label: String, value: String, valueColor: Color = Color(0xFFE2E8F0)) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color(0xFF94A3B8), fontSize = 13.sp)
        Text(text = value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun PhotoBoothInstructionsModal(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "● PHOTO BOOTH GUIDE ●",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0284C7),
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "How Image Optimization Works",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                GuideCard(
                    title = "Target Quality & PNG Quantization",
                    body = "• Changing the Target Quality dial dynamically shrinks both JPEGs and PNGs.\n• For PNGs, intelligent color palette quantization reduces color depth while protecting transparent pixels.\n• Lower % (e.g. 60%): Massive 40%–70% savings with virtually imperceptible visual difference.\n• Higher % (e.g. 90%): Larger file sizes with ultra-fine fidelity."
                )

                GuideCard(
                    title = "Output Format Selector",
                    body = "• Keep Original: Keeps the original file extension.\n• JPEG (.jpg): Optimal compression for photos (transparent backgrounds are filled with clean white).\n• PNG (.png): Preserves transparent layers and crisp graphics.\n• WebP (.webp): Next-gen format combining alpha transparency with small file sizes."
                )

                GuideCard(
                    title = "Privacy & Metadata Stripping",
                    body = "The booth automatically strips hidden camera tags, GPS coordinates, and thumbnail caches for privacy and reduced file size."
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
            ) {
                Text("Step Into The Booth", fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFFF8FAFC)
    )
}

@Composable
fun GuideCard(title: String, body: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = body, fontSize = 12.sp, color = Color(0xFF334155), lineHeight = 16.sp)
        }
    }
}

@Composable
fun BoothErrorView(errorMessage: String, onRetry: () -> Unit) {
    Spacer(modifier = Modifier.height(40.dp))

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E)),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Booth Jam / Error",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = errorMessage,
                fontSize = 13.sp,
                color = Color.LightGray,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onRetry,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Try Another Photo")
            }
        }
    }
}
