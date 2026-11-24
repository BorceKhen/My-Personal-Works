package com.forgefolio.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.forgefolio.app.model.ConversionResult
import com.forgefolio.app.model.ImageOutputFormat
import com.forgefolio.app.model.ImagePageLayout
import com.forgefolio.app.model.ImagePageMargin
import com.forgefolio.app.model.ImagePdfLayoutEngine
import com.forgefolio.app.model.ImageScaleMode
import com.forgefolio.app.model.ImageSlotAlignment
import com.forgefolio.app.model.PageOrientation
import com.forgefolio.app.model.PageRangeParser
import com.forgefolio.app.model.PaperSizePreset
import com.forgefolio.app.model.PdfDocumentInfo
import com.forgefolio.app.model.PdfToImageOutputMode
import com.forgefolio.app.model.SelectedImageItem
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class PdfConversionRepository(private val context: Context) {

    fun inspectPdf(uri: Uri): PdfDocumentInfo {
        val (name, size) = SafFileHelper.queryFileMeta(context, uri, "document.pdf")
        var pageCount = 0
        var thumb: Bitmap? = null

        SafFileHelper.withSeekablePdfDescriptor(context, uri) { fd ->
            PdfRenderer(fd).use { renderer ->
                pageCount = renderer.pageCount
                if (pageCount > 0) {
                    renderer.openPage(0).use { page ->
                        val maxThumb = 420f
                        val scale = min(maxThumb / page.width, maxThumb / page.height).coerceAtMost(2f)
                        val w = (page.width * scale).roundToInt().coerceAtLeast(1)
                        val h = (page.height * scale).roundToInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        thumb = bmp
                    }
                }
            }
        }
        return PdfDocumentInfo(
            uri = uri,
            displayName = name,
            sizeBytes = size,
            pageCount = pageCount,
            firstPageThumbnail = thumb
        )
    }

    fun renderPreviewPage(uri: Uri, pageIndex: Int, maxDim: Float = 960f): Bitmap? {
        var result: Bitmap? = null
        SafFileHelper.withSeekablePdfDescriptor(context, uri) { fd ->
            PdfRenderer(fd).use { renderer ->
                if (pageIndex in 0 until renderer.pageCount) {
                    renderer.openPage(pageIndex).use { page ->
                        val scale = min(maxDim / page.width, maxDim / page.height).coerceAtMost(2.5f)
                        val w = (page.width * scale).roundToInt().coerceAtLeast(1)
                        val h = (page.height * scale).roundToInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        Canvas(bmp).drawColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        result = bmp
                    }
                }
            }
        }
        return result
    }

    fun inspectSelectedImage(uri: Uri): SelectedImageItem {
        val (name, size) = SafFileHelper.queryFileMeta(context, uri, "image.jpg")
        val (w, h) = SafFileHelper.readImageBounds(context, uri)
        val thumb = runCatching { SafFileHelper.decodeSampledBitmapWithExif(context, uri, 220) }.getOrNull()
        return SelectedImageItem(
            id = UUID.randomUUID().toString(),
            uri = uri,
            displayName = name,
            sizeBytes = size,
            widthPx = thumb?.width ?: w,
            heightPx = thumb?.height ?: h,
            thumbnail = thumb
        )
    }

    suspend fun convertPdfToImages(
        pdfInfo: PdfDocumentInfo,
        pageRangeRaw: String,
        format: ImageOutputFormat,
        dpi: Int,
        quality: Int,
        outputMode: PdfToImageOutputMode,
        outputUri: Uri,
        onProgress: (current: Int, total: Int, message: String) -> Unit
    ): ConversionResult {
        val baseName = pdfInfo.displayName.substringBeforeLast(".").ifBlank { "document" }
        val compressFmt = SafFileHelper.compressFormatCompat(format.ext)
        val clampedQuality = quality.coerceIn(10, 100)
        val callerContext = coroutineContext
        var exportedCount = 0

        SafFileHelper.withSeekablePdfDescriptor(context, pdfInfo.uri) { fd ->
            PdfRenderer(fd).use { renderer ->
                val targetPages = PageRangeParser.parse(pageRangeRaw, renderer.pageCount)
                require(targetPages.isNotEmpty()) { "No valid pages matched range \"$pageRangeRaw\"." }

                val total = targetPages.size

                if (outputMode == PdfToImageOutputMode.ZIP) {
                    val outStream = context.contentResolver.openOutputStream(outputUri)
                        ?: error("Unable to open ZIP output stream.")
                    ZipOutputStream(outStream).use { zos ->
                        targetPages.forEachIndexed { idx, pageNum ->
                            callerContext.ensureActive()
                            onProgress(idx + 1, total, "[${pad2(idx + 1)}/${pad2(total)}] Rendering page $pageNum @ $dpi DPI...")
                            val bytes = renderPageToBytes(renderer, pageNum - 1, dpi, compressFmt, clampedQuality)
                            val entryName = "${baseName}_page_${String.format("%03d", pageNum)}.${format.ext}"
                            zos.putNextEntry(ZipEntry(entryName))
                            zos.write(bytes)
                            zos.closeEntry()
                            exportedCount++
                        }
                    }
                } else {
                    val treeDir = DocumentFile.fromTreeUri(context, outputUri)
                        ?: error("Unable to access selected output folder.")
                    targetPages.forEachIndexed { idx, pageNum ->
                        callerContext.ensureActive()
                        onProgress(idx + 1, total, "[${pad2(idx + 1)}/${pad2(total)}] Writing page $pageNum.${format.ext}...")
                        val bytes = renderPageToBytes(renderer, pageNum - 1, dpi, compressFmt, clampedQuality)
                        val fileName = "${baseName}_page_${String.format("%03d", pageNum)}.${format.ext}"
                        val docFile = treeDir.createFile(format.mimeType, fileName)
                            ?: error("Failed to create $fileName in destination folder.")
                        context.contentResolver.openOutputStream(docFile.uri)?.use { it.write(bytes) }
                            ?: error("Unable to write $fileName.")
                        exportedCount++
                    }
                }
            }
        }

        val (outName, _) = SafFileHelper.queryFileMeta(
            context,
            outputUri,
            if (outputMode == PdfToImageOutputMode.ZIP) "${baseName}_pages.zip" else "Selected Folder"
        )
        return ConversionResult(
            uri = outputUri,
            displayName = outName,
            mimeType = if (outputMode == PdfToImageOutputMode.ZIP) "application/zip" else "*/*",
            isFolderTree = outputMode == PdfToImageOutputMode.FOLDER,
            summary = "Exported $exportedCount page(s) as ${format.label} @ $dpi DPI ($clampedQuality% quality)."
        )
    }

    suspend fun convertImagesToPdf(
        images: List<SelectedImageItem>,
        paperPreset: PaperSizePreset,
        orientation: PageOrientation,
        scaleMode: ImageScaleMode,
        layout: ImagePageLayout,
        margin: ImagePageMargin,
        alignment: ImageSlotAlignment,
        outputUri: Uri,
        onProgress: (current: Int, total: Int, message: String) -> Unit
    ): ConversionResult {
        require(images.isNotEmpty()) { "Select at least one image to convert." }

        val pages = ImagePdfLayoutEngine.paginate(images, layout)
        val totalPages = pages.size
        val pdfDocument = PdfDocument()
        try {
            pages.forEachIndexed { pageIndex, pageItems ->
                coroutineContext.ensureActive()
                onProgress(
                    pageIndex + 1,
                    totalPages,
                    "[${pad2(pageIndex + 1)}/${pad2(totalPages)}] Laying out PDF page ${pageIndex + 1} (${pageItems.size} plate(s))..."
                )

                val (refW, refH) = pageItems.first().effectiveDimensions()
                val (pageW, pageH) = ImagePdfLayoutEngine.resolvePageDimensionsPt(
                    refWidthPx = refW,
                    refHeightPx = refH,
                    preset = paperPreset,
                    orientation = orientation
                )

                val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, pageIndex + 1).create()
                val page = pdfDocument.startPage(pageInfo)
                val canvas = page.canvas
                canvas.drawColor(Color.WHITE)

                val slotRects = ImagePdfLayoutEngine.computeSlotRects(
                    pageW = pageW.toFloat(),
                    pageH = pageH.toFloat(),
                    marginPt = margin.marginPt.toFloat(),
                    gapPt = 12f,
                    layout = layout,
                    orientation = orientation
                )

                val maxEdge = if (layout == ImagePageLayout.SINGLE) 2480 else 1600
                pageItems.forEachIndexed { slotIdx, item ->
                    coroutineContext.ensureActive()
                    val slot = slotRects[slotIdx]
                    val baseBmp = SafFileHelper.decodeSampledBitmapWithExif(context, item.uri, maxEdge)
                    val orientedBmp = rotateBitmapIfNeeded(baseBmp, item.rotationDegrees)
                    try {
                        val dst = ImagePdfLayoutEngine.computeImageRectInSlot(
                            srcW = orientedBmp.width,
                            srcH = orientedBmp.height,
                            slot = slot,
                            scaleMode = scaleMode,
                            alignment = alignment
                        )
                        canvas.save()
                        canvas.clipRect(slot.left, slot.top, slot.right, slot.bottom)
                        canvas.drawBitmap(
                            orientedBmp,
                            null,
                            RectF(dst.left, dst.top, dst.right, dst.bottom),
                            null
                        )
                        canvas.restore()
                    } finally {
                        if (orientedBmp !== baseBmp) {
                            orientedBmp.recycle()
                        }
                        baseBmp.recycle()
                    }
                }

                pdfDocument.finishPage(page)
            }

            context.contentResolver.openOutputStream(outputUri)?.use { out ->
                pdfDocument.writeTo(out)
            } ?: error("Unable to open PDF output stream.")
        } finally {
            pdfDocument.close()
        }

        val (outName, outSize) = SafFileHelper.queryFileMeta(context, outputUri, "ForgeFolio_Bundle.pdf")
        return ConversionResult(
            uri = outputUri,
            displayName = outName,
            mimeType = "application/pdf",
            summary = "Compiled ${images.size} plate(s) across $totalPages page(s) • ${paperPreset.label} (${layout.label}) • ${PageRangeParser.formatBytes(outSize)}."
        )
    }

    private fun rotateBitmapIfNeeded(source: Bitmap, rotationDegrees: Int): Bitmap {
        val norm = ((rotationDegrees % 360) + 360) % 360
        if (norm == 0) return source
        val matrix = Matrix().apply { postRotate(norm.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun renderPageToBytes(
        renderer: PdfRenderer,
        pageIndex: Int,
        dpi: Int,
        format: Bitmap.CompressFormat,
        quality: Int
    ): ByteArray {
        renderer.openPage(pageIndex).use { page ->
            val rawScale = dpi / 72f
            val maxEdge = 4096f
            val safeScale = min(rawScale, min(maxEdge / page.width, maxEdge / page.height))
            val targetW = (page.width * safeScale).roundToInt().coerceAtLeast(1)
            val targetH = (page.height * safeScale).roundToInt().coerceAtLeast(1)

            val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
            try {
                Canvas(bitmap).drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                val baos = ByteArrayOutputStream()
                bitmap.compress(format, quality, baos)
                return baos.toByteArray()
            } finally {
                bitmap.recycle()
            }
        }
    }

    private fun pad2(v: Int): String = String.format("%02d", v)
}
