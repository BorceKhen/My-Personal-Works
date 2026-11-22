package com.forgefolio.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.forgefolio.app.data.PdfConversionRepository
import com.forgefolio.app.data.WordConversionRepository
import com.forgefolio.app.model.ConversionProgress
import com.forgefolio.app.model.ConversionResult
import com.forgefolio.app.model.DocxDocumentInfo
import com.forgefolio.app.model.ForgeTab
import com.forgefolio.app.model.ImageOutputFormat
import com.forgefolio.app.model.ImageScaleMode
import com.forgefolio.app.model.MarginPreset
import com.forgefolio.app.model.PageOrientation
import com.forgefolio.app.model.PageRangeParser
import com.forgefolio.app.model.PaperSizePreset
import com.forgefolio.app.model.PdfDocumentInfo
import com.forgefolio.app.model.PdfDpiPreset
import com.forgefolio.app.model.PdfToImageOutputMode
import com.forgefolio.app.model.PdfToWordMode
import com.forgefolio.app.model.SelectedImageItem
import com.forgefolio.app.model.ShelfArchiveEntry
import kotlinx.coroutines.Dispatchers
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ConverterUiState(
    val selectedTab: ForgeTab = ForgeTab.PDF_TO_IMG,
    val progress: ConversionProgress = ConversionProgress(),
    val result: ConversionResult? = null,
    val errorMessage: String? = null,

    // Tab 1: PDF -> IMG
    val pdfToImgDoc: PdfDocumentInfo? = null,
    val pdfToImgPreviewPage: Int = 0,
    val pdfToImgPreviewBitmap: android.graphics.Bitmap? = null,
    val pdfToImgAllPages: Boolean = true,
    val pdfToImgCustomRange: String = "1",
    val pdfToImgFormat: ImageOutputFormat = ImageOutputFormat.PNG,
    val pdfToImgDpi: PdfDpiPreset = PdfDpiPreset.DPI_150,
    val pdfToImgQuality: Int = 92,
    val pdfToImgOutputMode: PdfToImageOutputMode = PdfToImageOutputMode.ZIP,

    // Tab 2: IMG -> PDF
    val selectedImages: List<SelectedImageItem> = emptyList(),
    val imgToPdfPaper: PaperSizePreset = PaperSizePreset.SHORT_BOND,
    val imgToPdfOrientation: PageOrientation = PageOrientation.PORTRAIT,
    val imgToPdfScaleMode: ImageScaleMode = ImageScaleMode.FIT_INSIDE,

    // Tab 3: DOCX -> PDF
    val docxToPdfInfo: DocxDocumentInfo? = null,
    val docxToPdfPaper: PaperSizePreset = PaperSizePreset.SHORT_BOND,
    val docxToPdfMargin: MarginPreset = MarginPreset.NORMAL,

    // Tab 4: PDF -> DOCX
    val pdfToDocxInfo: PdfDocumentInfo? = null,
    val pdfToDocxPreviewPage: Int = 0,
    val pdfToDocxPreviewBitmap: android.graphics.Bitmap? = null,
    val pdfToDocxAllPages: Boolean = true,
    val pdfToDocxCustomRange: String = "1",
    val pdfToDocxMode: PdfToWordMode = PdfToWordMode.EDITABLE_TEXT,
    val pdfToDocxExactDpi: Int = 150,

    // Interactive Library Shelf History
    val shelfHistory: List<ShelfArchiveEntry> = emptyList()
)

class ConverterViewModel(application: Application) : AndroidViewModel(application) {

    private val pdfRepo = PdfConversionRepository(application.applicationContext)
    private val wordRepo = WordConversionRepository(application.applicationContext)

    private val _uiState = MutableStateFlow(ConverterUiState())
    val uiState: StateFlow<ConverterUiState> = _uiState.asStateFlow()

    fun selectTab(tab: ForgeTab) {
        _uiState.update { it.copy(selectedTab = tab, errorMessage = null, result = null) }
    }

    // ---------- Tab 1: PDF -> IMG ----------
    fun loadPdfForImages(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { pdfRepo.inspectPdf(uri) }
                .onSuccess { info ->
                    val entry = ShelfArchiveEntry(
                        id = "src-pdf-img-${info.uri}",
                        title = info.displayName,
                        meta = "${info.pageCount}p • ${PageRangeParser.formatBytes(info.sizeBytes)}",
                        badge = "PDF",
                        uri = info.uri,
                        mimeType = "application/pdf",
                        isFolderTree = false,
                        sourceTab = ForgeTab.PDF_TO_IMG,
                        isExportedOutput = false
                    )
                    _uiState.update {
                        it.copy(
                            pdfToImgDoc = info,
                            pdfToImgPreviewPage = 0,
                            pdfToImgPreviewBitmap = info.firstPageThumbnail,
                            pdfToImgCustomRange = if (info.pageCount > 1) "1-${minOf(3, info.pageCount)}" else "1",
                            shelfHistory = prependShelfEntry(it.shelfHistory, entry),
                            errorMessage = null,
                            result = null
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Failed to read PDF.") }
                }
        }
    }

    fun stepPdfToImgPreview(delta: Int) {
        val doc = _uiState.value.pdfToImgDoc ?: return
        val next = (_uiState.value.pdfToImgPreviewPage + delta).coerceIn(0, (doc.pageCount - 1).coerceAtLeast(0))
        if (next == _uiState.value.pdfToImgPreviewPage) return
        viewModelScope.launch(Dispatchers.IO) {
            val bmp = runCatching { pdfRepo.renderPreviewPage(doc.uri, next) }.getOrNull()
            if (bmp != null) {
                _uiState.update { it.copy(pdfToImgPreviewPage = next, pdfToImgPreviewBitmap = bmp) }
            }
        }
    }

    fun updatePdfToImgAllPages(all: Boolean) = _uiState.update { it.copy(pdfToImgAllPages = all) }
    fun updatePdfToImgCustomRange(range: String) = _uiState.update { it.copy(pdfToImgCustomRange = range) }
    fun updatePdfToImgFormat(format: ImageOutputFormat) = _uiState.update { it.copy(pdfToImgFormat = format) }
    fun updatePdfToImgDpi(dpi: PdfDpiPreset) = _uiState.update { it.copy(pdfToImgDpi = dpi) }
    fun updatePdfToImgQuality(quality: Int) = _uiState.update { it.copy(pdfToImgQuality = quality.coerceIn(10, 100)) }
    fun updatePdfToImgOutputMode(mode: PdfToImageOutputMode) = _uiState.update { it.copy(pdfToImgOutputMode = mode) }

    fun executePdfToImages(outputUri: Uri) {
        val snapshot = _uiState.value
        val doc = snapshot.pdfToImgDoc ?: return
        val range = if (snapshot.pdfToImgAllPages) "all" else snapshot.pdfToImgCustomRange

        launchConversion {
            pdfRepo.convertPdfToImages(
                pdfInfo = doc,
                pageRangeRaw = range,
                format = snapshot.pdfToImgFormat,
                dpi = snapshot.pdfToImgDpi.dpi,
                quality = snapshot.pdfToImgQuality,
                outputMode = snapshot.pdfToImgOutputMode,
                outputUri = outputUri,
                onProgress = ::emitProgress
            )
        }
    }

    // ---------- Tab 2: IMG -> PDF ----------
    fun addImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            val loaded = uris.mapNotNull { uri ->
                runCatching { pdfRepo.inspectSelectedImage(uri) }.getOrNull()
            }
            _uiState.update {
                it.copy(
                    selectedImages = it.selectedImages + loaded,
                    errorMessage = null,
                    result = null
                )
            }
        }
    }

    fun moveImageUp(index: Int) {
        _uiState.update { state ->
            if (index <= 0 || index >= state.selectedImages.size) return@update state
            val mutable = state.selectedImages.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(index - 1, item)
            state.copy(selectedImages = mutable)
        }
    }

    fun moveImageDown(index: Int) {
        _uiState.update { state ->
            if (index < 0 || index >= state.selectedImages.lastIndex) return@update state
            val mutable = state.selectedImages.toMutableList()
            val item = mutable.removeAt(index)
            mutable.add(index + 1, item)
            state.copy(selectedImages = mutable)
        }
    }

    fun removeImageAt(index: Int) {
        _uiState.update { state ->
            if (index !in state.selectedImages.indices) return@update state
            val mutable = state.selectedImages.toMutableList()
            mutable.removeAt(index)
            state.copy(selectedImages = mutable)
        }
    }

    fun updateImgToPdfPaper(paper: PaperSizePreset) = _uiState.update { it.copy(imgToPdfPaper = paper) }
    fun updateImgToPdfOrientation(orientation: PageOrientation) = _uiState.update { it.copy(imgToPdfOrientation = orientation) }
    fun updateImgToPdfScaleMode(mode: ImageScaleMode) = _uiState.update { it.copy(imgToPdfScaleMode = mode) }

    fun executeImagesToPdf(outputUri: Uri) {
        val snapshot = _uiState.value
        if (snapshot.selectedImages.isEmpty()) return

        launchConversion {
            pdfRepo.convertImagesToPdf(
                images = snapshot.selectedImages,
                paperPreset = snapshot.imgToPdfPaper,
                orientation = snapshot.imgToPdfOrientation,
                scaleMode = snapshot.imgToPdfScaleMode,
                outputUri = outputUri,
                onProgress = ::emitProgress
            )
        }
    }

    // ---------- Tab 3: DOCX -> PDF ----------
    fun loadDocxForPdf(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { wordRepo.inspectDocx(uri) }
                .onSuccess { info ->
                    val entry = ShelfArchiveEntry(
                        id = "src-docx-${info.uri}",
                        title = info.displayName,
                        meta = "${info.paragraphCount}¶ • ${PageRangeParser.formatBytes(info.sizeBytes)}",
                        badge = "DOCX",
                        uri = info.uri,
                        mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        isFolderTree = false,
                        sourceTab = ForgeTab.DOCX_TO_PDF,
                        isExportedOutput = false
                    )
                    _uiState.update {
                        it.copy(
                            docxToPdfInfo = info,
                            shelfHistory = prependShelfEntry(it.shelfHistory, entry),
                            errorMessage = null,
                            result = null
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Failed to parse .docx file.") }
                }
        }
    }

    fun updateDocxToPdfPaper(paper: PaperSizePreset) = _uiState.update { it.copy(docxToPdfPaper = paper) }
    fun updateDocxToPdfMargin(margin: MarginPreset) = _uiState.update { it.copy(docxToPdfMargin = margin) }

    fun executeDocxToPdf(outputUri: Uri) {
        val snapshot = _uiState.value
        val doc = snapshot.docxToPdfInfo ?: return

        launchConversion {
            wordRepo.convertDocxToPdf(
                docxInfo = doc,
                paperPreset = snapshot.docxToPdfPaper,
                marginPreset = snapshot.docxToPdfMargin,
                outputUri = outputUri,
                onProgress = ::emitProgress
            )
        }
    }

    // ---------- Tab 4: PDF -> DOCX ----------
    fun loadPdfForDocx(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { pdfRepo.inspectPdf(uri) }
                .onSuccess { info ->
                    val entry = ShelfArchiveEntry(
                        id = "src-pdf-docx-${info.uri}",
                        title = info.displayName,
                        meta = "${info.pageCount}p • ${PageRangeParser.formatBytes(info.sizeBytes)}",
                        badge = "PDF",
                        uri = info.uri,
                        mimeType = "application/pdf",
                        isFolderTree = false,
                        sourceTab = ForgeTab.PDF_TO_DOCX,
                        isExportedOutput = false
                    )
                    _uiState.update {
                        it.copy(
                            pdfToDocxInfo = info,
                            pdfToDocxPreviewPage = 0,
                            pdfToDocxPreviewBitmap = info.firstPageThumbnail,
                            pdfToDocxCustomRange = if (info.pageCount > 1) "1-${minOf(3, info.pageCount)}" else "1",
                            shelfHistory = prependShelfEntry(it.shelfHistory, entry),
                            errorMessage = null,
                            result = null
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update { it.copy(errorMessage = err.message ?: "Failed to read PDF.") }
                }
        }
    }

    fun stepPdfToDocxPreview(delta: Int) {
        val doc = _uiState.value.pdfToDocxInfo ?: return
        val next = (_uiState.value.pdfToDocxPreviewPage + delta).coerceIn(0, (doc.pageCount - 1).coerceAtLeast(0))
        if (next == _uiState.value.pdfToDocxPreviewPage) return
        viewModelScope.launch(Dispatchers.IO) {
            val bmp = runCatching { pdfRepo.renderPreviewPage(doc.uri, next) }.getOrNull()
            if (bmp != null) {
                _uiState.update { it.copy(pdfToDocxPreviewPage = next, pdfToDocxPreviewBitmap = bmp) }
            }
        }
    }

    fun updatePdfToDocxAllPages(all: Boolean) = _uiState.update { it.copy(pdfToDocxAllPages = all) }
    fun updatePdfToDocxCustomRange(range: String) = _uiState.update { it.copy(pdfToDocxCustomRange = range) }
    fun updatePdfToDocxMode(mode: PdfToWordMode) = _uiState.update { it.copy(pdfToDocxMode = mode) }
    fun updatePdfToDocxExactDpi(dpi: Int) = _uiState.update { it.copy(pdfToDocxExactDpi = dpi) }

    fun executePdfToDocx(outputUri: Uri) {
        val snapshot = _uiState.value
        val doc = snapshot.pdfToDocxInfo ?: return
        val range = if (snapshot.pdfToDocxAllPages) "all" else snapshot.pdfToDocxCustomRange

        launchConversion {
            wordRepo.convertPdfToDocx(
                pdfInfo = doc,
                pageRangeRaw = range,
                mode = snapshot.pdfToDocxMode,
                renderDpi = snapshot.pdfToDocxExactDpi,
                outputUri = outputUri,
                onProgress = ::emitProgress
            )
        }
    }

    private fun emitProgress(current: Int, total: Int, message: String) {
        _uiState.update {
            it.copy(progress = ConversionProgress(isRunning = true, current = current, total = total, message = message))
        }
    }

    private fun launchConversion(block: suspend () -> ConversionResult) {
        if (_uiState.value.progress.isRunning) return
        val activeTab = _uiState.value.selectedTab
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update {
                it.copy(
                    progress = ConversionProgress(isRunning = true, current = 0, total = 0, message = "[00/00] Initializing local engine..."),
                    errorMessage = null,
                    result = null
                )
            }
            runCatching { block() }
                .onSuccess { res ->
                    val exportedEntry = ShelfArchiveEntry(
                        id = UUID.randomUUID().toString(),
                        title = res.displayName,
                        meta = res.summary,
                        badge = "EXPORT",
                        uri = res.uri,
                        mimeType = res.mimeType,
                        isFolderTree = res.isFolderTree,
                        sourceTab = activeTab,
                        isExportedOutput = true
                    )
                    _uiState.update {
                        it.copy(
                            progress = ConversionProgress(isRunning = false),
                            result = res,
                            shelfHistory = prependShelfEntry(it.shelfHistory, exportedEntry)
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            progress = ConversionProgress(isRunning = false),
                            errorMessage = err.message ?: "Conversion failed."
                        )
                    }
                }
        }
    }

    private fun prependShelfEntry(
        current: List<ShelfArchiveEntry>,
        entry: ShelfArchiveEntry
    ): List<ShelfArchiveEntry> {
        val filtered = current.filterNot { it.id == entry.id }
        return (listOf(entry) + filtered).take(8)
    }
}
