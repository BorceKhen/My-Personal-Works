package com.forgefolio.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.StyleSpan
import com.forgefolio.app.model.ConversionResult
import com.forgefolio.app.model.DocxDocumentInfo
import com.forgefolio.app.model.MarginPreset
import com.forgefolio.app.model.PageOrientation
import com.forgefolio.app.model.PageRangeParser
import com.forgefolio.app.model.PaperSizePreset
import com.forgefolio.app.model.PdfDocumentInfo
import com.forgefolio.app.model.PdfToWordMode
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.ensureActive
import org.apache.poi.util.Units
import org.apache.poi.xwpf.usermodel.BodyElementType
import org.apache.poi.xwpf.usermodel.ParagraphAlignment
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.apache.poi.xwpf.usermodel.XWPFParagraph
import org.apache.poi.xwpf.usermodel.XWPFTable
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import kotlin.coroutines.coroutineContext
import kotlin.math.min
import kotlin.math.roundToInt

class WordConversionRepository(private val context: Context) {

    fun inspectDocx(uri: Uri): DocxDocumentInfo {
        val (name, size) = SafFileHelper.queryFileMeta(context, uri, "document.docx")
        var paraCount = 0
        var tableCount = 0
        context.contentResolver.openInputStream(uri)?.use { stream ->
            XWPFDocument(stream).use { doc ->
                paraCount = doc.paragraphs.size
                tableCount = doc.tables.size
            }
        } ?: error("Unable to open Word (.docx) file.")

        return DocxDocumentInfo(
            uri = uri,
            displayName = name,
            sizeBytes = size,
            paragraphCount = paraCount,
            tableCount = tableCount
        )
    }

    suspend fun convertDocxToPdf(
        docxInfo: DocxDocumentInfo,
        paperPreset: PaperSizePreset,
        marginPreset: MarginPreset,
        outputUri: Uri,
        onProgress: (current: Int, total: Int, message: String) -> Unit
    ): ConversionResult {
        val safePaper = if (paperPreset == PaperSizePreset.FIT_TO_IMAGE) {
            PaperSizePreset.SHORT_BOND
        } else {
            paperPreset
        }
        val (pageW, pageH) = safePaper.orientedSize(PageOrientation.PORTRAIT)
        val margin = marginPreset.marginPt
        val contentW = (pageW - margin * 2).toInt().coerceAtLeast(72)
        val bottomY = pageH - margin

        val pdfDoc = PdfDocument()
        var pageIndex = 1
        var currentPage = pdfDoc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageIndex).create())
        var canvas = currentPage.canvas
        canvas.drawColor(Color.WHITE)
        var cursorY = margin

        val basePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#18181B")
            textSize = 11f
            typeface = Typeface.SERIF
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#D4D4D8")
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }

        fun startNewPage() {
            pdfDoc.finishPage(currentPage)
            pageIndex++
            currentPage = pdfDoc.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageIndex).create())
            canvas = currentPage.canvas
            canvas.drawColor(Color.WHITE)
            cursorY = margin
        }

        try {
            context.contentResolver.openInputStream(docxInfo.uri)?.use { input ->
                XWPFDocument(input).use { xwpf ->
                    val elements = xwpf.bodyElements
                    val total = elements.size.coerceAtLeast(1)

                    elements.forEachIndexed { idx, element ->
                        coroutineContext.ensureActive()
                        onProgress(
                            idx + 1,
                            total,
                            "[${pad2(idx + 1)}/${pad2(total)}] Typesetting block ${idx + 1}..."
                        )

                        when (element.elementType) {
                            BodyElementType.PARAGRAPH -> {
                                val para = element as XWPFParagraph
                                val spannable = buildParagraphSpannable(para)
                                if (spannable.isEmpty()) {
                                    cursorY += 10f
                                    if (cursorY > bottomY) startNewPage()
                                    return@forEachIndexed
                                }

                                val alignment = when (para.alignment) {
                                    ParagraphAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
                                    ParagraphAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
                                    else -> Layout.Alignment.ALIGN_NORMAL
                                }
                                val layout = StaticLayout.Builder.obtain(
                                    spannable,
                                    0,
                                    spannable.length,
                                    basePaint,
                                    contentW
                                )
                                    .setAlignment(alignment)
                                    .setLineSpacing(2f, 1.15f)
                                    .setIncludePad(false)
                                    .build()

                                for (line in 0 until layout.lineCount) {
                                    val lineTop = layout.getLineTop(line)
                                    val lineBottom = layout.getLineBottom(line)
                                    val lineHeight = (lineBottom - lineTop).toFloat()
                                    if (cursorY + lineHeight > bottomY) {
                                        startNewPage()
                                    }
                                    canvas.save()
                                    canvas.translate(margin, cursorY - lineTop)
                                    canvas.clipRect(0, lineTop, contentW, lineBottom)
                                    layout.draw(canvas)
                                    canvas.restore()
                                    cursorY += lineHeight
                                }
                                cursorY += 6f
                            }

                            BodyElementType.TABLE -> {
                                val table = element as XWPFTable
                                for (row in table.rows) {
                                    val cells = row.tableCells
                                    if (cells.isEmpty()) continue
                                    val colW = (contentW / cells.size).coerceAtLeast(36)
                                    val cellPadding = 6f
                                    val cellLayouts = cells.map { cell ->
                                        val txt = cell.text ?: ""
                                        StaticLayout.Builder.obtain(
                                            txt,
                                            0,
                                            txt.length,
                                            basePaint,
                                            (colW - cellPadding * 2).toInt().coerceAtLeast(16)
                                        ).build()
                                    }
                                    val rowHeight = (cellLayouts.maxOfOrNull { it.height } ?: 14) + cellPadding * 2
                                    if (cursorY + rowHeight > bottomY) {
                                        startNewPage()
                                    }
                                    cells.forEachIndexed { colIdx, _ ->
                                        val cellLeft = margin + colIdx * colW
                                        canvas.drawRect(
                                            cellLeft,
                                            cursorY,
                                            cellLeft + colW,
                                            cursorY + rowHeight,
                                            borderPaint
                                        )
                                        canvas.save()
                                        canvas.translate(cellLeft + cellPadding, cursorY + cellPadding)
                                        cellLayouts[colIdx].draw(canvas)
                                        canvas.restore()
                                    }
                                    cursorY += rowHeight
                                }
                                cursorY += 8f
                            }

                            else -> Unit
                        }
                    }
                }
            } ?: error("Unable to open Word (.docx) file.")

            pdfDoc.finishPage(currentPage)
            context.contentResolver.openOutputStream(outputUri)?.use { out ->
                pdfDoc.writeTo(out)
            } ?: error("Unable to open output PDF stream.")
        } finally {
            pdfDoc.close()
        }

        val (outName, outSize) = SafFileHelper.queryFileMeta(context, outputUri, "ForgeFolio_Doc.pdf")
        return ConversionResult(
            uri = outputUri,
            displayName = outName,
            mimeType = "application/pdf",
            summary = "Rendered $pageIndex PDF page(s) on ${safePaper.label} (${marginPreset.label}) • ${PageRangeParser.formatBytes(outSize)}."
        )
    }

    suspend fun convertPdfToDocx(
        pdfInfo: PdfDocumentInfo,
        pageRangeRaw: String,
        mode: PdfToWordMode,
        renderDpi: Int,
        outputUri: Uri,
        onProgress: (current: Int, total: Int, message: String) -> Unit
    ): ConversionResult {
        val targetPages = PageRangeParser.parse(pageRangeRaw, pdfInfo.pageCount)
        require(targetPages.isNotEmpty()) { "No valid pages matched range \"$pageRangeRaw\"." }

        val outDoc = XWPFDocument()
        try {
            if (mode == PdfToWordMode.EDITABLE_TEXT) {
                context.contentResolver.openInputStream(pdfInfo.uri)?.use { stream ->
                    PDDocument.load(stream).use { pdDoc ->
                        val stripper = PDFTextStripper()
                        val total = targetPages.size
                        targetPages.forEachIndexed { idx, pageNum ->
                            coroutineContext.ensureActive()
                            onProgress(
                                idx + 1,
                                total,
                                "[${pad2(idx + 1)}/${pad2(total)}] Extracting text from page $pageNum..."
                            )
                            stripper.startPage = pageNum
                            stripper.endPage = pageNum
                            val rawText = stripper.getText(pdDoc).orEmpty()
                            val lines = rawText.replace("\r\n", "\n").split("\n")

                            if (lines.all { it.isBlank() }) {
                                outDoc.createParagraph().createRun().setText("[Page $pageNum — No extractable text]")
                            } else {
                                for (line in lines) {
                                    val p = outDoc.createParagraph()
                                    val run = p.createRun()
                                    run.fontSize = 11
                                    run.setText(line)
                                }
                            }
                            if (idx < total - 1) {
                                outDoc.createParagraph().isPageBreak = true
                            }
                        }
                    }
                } ?: error("Unable to open PDF input stream.")
            } else {
                // Exact Layout Preserved: render each PDF page to high-DPI PNG and embed into Word
                val sectPr = outDoc.document.body.addNewSectPr()
                val pgMar = sectPr.addNewPgMar()
                val marginTwips = BigInteger.valueOf(720L) // 0.5 in margin
                pgMar.top = marginTwips
                pgMar.bottom = marginTwips
                pgMar.left = marginTwips
                pgMar.right = marginTwips

                val maxImgWidthPt = 540.0
                val maxImgHeightPt = 720.0

                val callerContext = coroutineContext
                SafFileHelper.withSeekablePdfDescriptor(context, pdfInfo.uri) { fd ->
                    PdfRenderer(fd).use { renderer ->
                        val total = targetPages.size
                        targetPages.forEachIndexed { idx, pageNum ->
                            callerContext.ensureActive()
                            onProgress(
                                idx + 1,
                                total,
                                "[${pad2(idx + 1)}/${pad2(total)}] Embedding exact layout page $pageNum @ $renderDpi DPI..."
                            )
                            renderer.openPage(pageNum - 1).use { page ->
                                val scale = min(renderDpi / 72f, min(3200f / page.width, 3200f / page.height))
                                val bmpW = (page.width * scale).roundToInt().coerceAtLeast(1)
                                val bmpH = (page.height * scale).roundToInt().coerceAtLeast(1)
                                val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                                val pngBytes = try {
                                    Canvas(bitmap).drawColor(Color.WHITE)
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                                    ByteArrayOutputStream().also { baos ->
                                        bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
                                    }.toByteArray()
                                } finally {
                                    bitmap.recycle()
                                }

                                val fitScale = min(maxImgWidthPt / page.width, maxImgHeightPt / page.height)
                                val drawW = page.width * fitScale
                                val drawH = page.height * fitScale

                                val para = outDoc.createParagraph().apply {
                                    alignment = ParagraphAlignment.CENTER
                                }
                                val run = para.createRun()
                                run.addPicture(
                                    ByteArrayInputStream(pngBytes),
                                    XWPFDocument.PICTURE_TYPE_PNG,
                                    "page_$pageNum.png",
                                    Units.toEMU(drawW),
                                    Units.toEMU(drawH)
                                )
                                if (idx < total - 1) {
                                    outDoc.createParagraph().isPageBreak = true
                                }
                            }
                        }
                    }
                }
            }

            context.contentResolver.openOutputStream(outputUri)?.use { out ->
                outDoc.write(out)
            } ?: error("Unable to open DOCX output stream.")
        } finally {
            outDoc.close()
        }

        val (outName, outSize) = SafFileHelper.queryFileMeta(context, outputUri, "ForgeFolio_Export.docx")
        return ConversionResult(
            uri = outputUri,
            displayName = outName,
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            summary = "Converted ${targetPages.size} page(s) (${mode.label}) • ${PageRangeParser.formatBytes(outSize)}."
        )
    }

    private fun buildParagraphSpannable(paragraph: XWPFParagraph): SpannableStringBuilder {
        val builder = SpannableStringBuilder()
        val styleName = paragraph.style.orEmpty()
        val defaultSizePt = when {
            styleName.contains("Title", ignoreCase = true) -> 22
            styleName.contains("Heading1", ignoreCase = true) || styleName == "1" -> 18
            styleName.contains("Heading2", ignoreCase = true) || styleName == "2" -> 15
            styleName.contains("Heading3", ignoreCase = true) || styleName == "3" -> 13
            else -> 11
        }
        val headingBold = defaultSizePt > 11

        for (run in paragraph.runs) {
            val text = run.text() ?: continue
            if (text.isEmpty()) continue
            val start = builder.length
            builder.append(text)
            val end = builder.length

            val sizePt = run.fontSizeAsDouble?.roundToInt()?.takeIf { it in 6..72 } ?: defaultSizePt
            builder.setSpan(
                AbsoluteSizeSpan(sizePt, true),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )

            val bold = run.isBold || headingBold
            val italic = run.isItalic
            val style = when {
                bold && italic -> Typeface.BOLD_ITALIC
                bold -> Typeface.BOLD
                italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            if (style != Typeface.NORMAL) {
                builder.setSpan(
                    StyleSpan(style),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }
        return builder
    }

    private fun pad2(v: Int): String = String.format("%02d", v)
}
