package com.forgefolio.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.forgefolio.app.model.ConversionResult
import com.forgefolio.app.model.ImageOutputFormat
import com.forgefolio.app.model.ImageScaleMode
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
        outputUri: Uri,
        onProgress: (current: Int, total: Int, message: String) -> Unit
    ): ConversionResult {
        require(images.isNotEmpty()) { "Select at least one image to convert." }

        val pdfDocument = PdfDocument()
        try {
            val total = images.size
            images.forEachIndexed { index, item ->
                coroutineContext.ensureActive()
                onProgress(
                    index + 1,
                    total,
                    "[${pad2(index + 1)}/${pad2(total)}] Embedding ${item.displayName}..."
                )

                val bitmap = SafFileHelper.decodeSampledBitmapWithExif(context, item.uri, 2480)
                try {
                    val (pageW, pageH) = resolvePageSize(bitmap, paperPreset, orientation)
                    val pageInfo = PdfDocument.PageInfo.Builder(pageW, pageH, index + 1).create()
                    val page = pdfDocument.startPage(pageInfo)
                    val canvas = page.canvas
                    canvas.drawColor(Color.WHITE)

                    val dstRect = computeImageRect(
                        srcW = bitmap.width,
                        srcH = bitmap.height,
                        dstW = pageW,
                        dstH = pageH,
                        scaleMode = scaleMode
                    )
                    canvas.drawBitmap(bitmap, null, dstRect, null)
                    pdfDocument.finishPage(page)
                } finally {
                    bitmap.recycle()
                }
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
            summary = "Compiled ${images.size} image(s) into ${paperPreset.label} (${orientation.label}) • ${PageRangeParser.formatBytes(outSize)}."
        )
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

    private fun resolvePageSize(
        bitmap: Bitmap,
        preset: PaperSizePreset,
        orientation: PageOrientation
    ): Pair<Int, Int> {
        if (preset != PaperSizePreset.FIT_TO_IMAGE) {
            return preset.orientedSize(orientation)
        }
        val maxPt = 1440f
        val scale = min(1f, min(maxPt / bitmap.width, maxPt / bitmap.height))
        var w = (bitmap.width * scale).roundToInt().coerceAtLeast(72)
        var h = (bitmap.height * scale).roundToInt().coerceAtLeast(72)
        if (orientation == PageOrientation.LANDSCAPE && h > w) {
            val tmp = w; w = h; h = tmp
        } else if (orientation == PageOrientation.PORTRAIT && w > h) {
            val tmp = w; w = h; h = tmp
        }
        return w to h
    }

    private fun computeImageRect(
        srcW: Int,
        srcH: Int,
        dstW: Int,
        dstH: Int,
        scaleMode: ImageScaleMode
    ): Rect {
        val scale = if (scaleMode == ImageScaleMode.FIT_INSIDE) {
            min(dstW.toFloat() / srcW, dstH.toFloat() / srcH)
        } else {
            max(dstW.toFloat() / srcW, dstH.toFloat() / srcH)
        }
        val drawW = (srcW * scale).roundToInt()
        val drawH = (srcH * scale).roundToInt()
        val left = (dstW - drawW) / 2
        val top = (dstH - drawH) / 2
        return Rect(left, top, left + drawW, top + drawH)
    }

    private fun pad2(v: Int): String = String.format("%02d", v)
}
