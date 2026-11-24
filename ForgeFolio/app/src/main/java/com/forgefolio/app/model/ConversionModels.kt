package com.forgefolio.app.model

import android.graphics.Bitmap
import android.net.Uri

enum class ForgeTab(val index: Int, val code: String, val label: String) {
    PDF_TO_IMG(0, "01", "01. PDF → IMG"),
    IMG_TO_PDF(1, "02", "02. IMG → PDF"),
    DOCX_TO_PDF(2, "03", "03. DOCX → PDF"),
    PDF_TO_DOCX(3, "04", "04. PDF → DOCX")
}

enum class ImageOutputFormat(
    val label: String,
    val ext: String,
    val mimeType: String
) {
    PNG("PNG", "png", "image/png"),
    JPG("JPG", "jpg", "image/jpeg"),
    WEBP("WEBP", "webp", "image/webp")
}

enum class PdfDpiPreset(val dpi: Int, val badge: String) {
    DPI_72(72, "72 DPI • Draft"),
    DPI_150(150, "150 DPI • Standard"),
    DPI_300(300, "300 DPI • Archival")
}

enum class PdfToImageOutputMode(val label: String) {
    FOLDER("Folder (SAF Tree)"),
    ZIP("ZIP Archive (.zip)")
}

enum class PaperSizePreset(
    val label: String,
    val widthPt: Int,
    val heightPt: Int,
    val dimensionsBadge: String
) {
    FIT_TO_IMAGE("Fit to Image", 0, 0, "Auto • Source Size"),
    SHORT_BOND("Short Bond / Letter", 612, 792, "612 × 792 pt"),
    LONG_BOND("Long Bond / Folio", 612, 936, "612 × 936 pt"),
    A4("A4", 595, 842, "595 × 842 pt"),
    LEGAL("Legal", 612, 1008, "612 × 1008 pt");

    fun orientedSize(orientation: PageOrientation): Pair<Int, Int> {
        if (this == FIT_TO_IMAGE) return 0 to 0
        return if (orientation == PageOrientation.LANDSCAPE) {
            heightPt to widthPt
        } else {
            widthPt to heightPt
        }
    }
}

enum class PageOrientation(val label: String) {
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class ImageScaleMode(val label: String) {
    FIT_INSIDE("Fit Inside"),
    FILL_PAGE("Fill Page")
}

enum class ImagePageLayout(val label: String, val badge: String, val perPage: Int) {
    SINGLE("1 / Page", "Full Folio", 1),
    TWO_UP("2 / Page", "Split Pair", 2),
    FOUR_UP("4 / Page", "2 × 2 Grid", 4)
}

enum class ImagePageMargin(val label: String, val marginPt: Int, val badge: String) {
    NONE("None", 0, "0 pt"),
    NARROW("Narrow", 18, "18 pt"),
    STANDARD("Book", 36, "36 pt"),
    WIDE("Gallery", 54, "54 pt")
}

enum class ImageSlotAlignment(val label: String) {
    START("Top / Left"),
    CENTER("Centered"),
    END("Bottom / Right")
}

enum class MarginPreset(val label: String, val marginPt: Float, val badge: String) {
    NORMAL("Normal (1.0 in)", 72f, "72 pt"),
    NARROW("Narrow (0.5 in)", 36f, "36 pt")
}

enum class PdfToWordMode(val label: String, val description: String) {
    EDITABLE_TEXT(
        "Editable Text (.docx)",
        "Extracts text blocks via PDFBox into editable Word paragraphs & page breaks."
    ),
    EXACT_LAYOUT(
        "Exact Layout Preserved (.docx)",
        "Renders each PDF page cleanly at high DPI and embeds it so tables & graphics stay intact."
    )
}

data class PdfDocumentInfo(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val pageCount: Int,
    val firstPageThumbnail: Bitmap? = null
)

data class DocxDocumentInfo(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val paragraphCount: Int,
    val tableCount: Int
)

data class SelectedImageItem(
    val id: String,
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val widthPx: Int,
    val heightPx: Int,
    val thumbnail: Bitmap?,
    val rotationDegrees: Int = 0
) {
    fun effectiveDimensions(): Pair<Int, Int> =
        if (((rotationDegrees % 360) + 360) % 360 % 180 == 90) {
            heightPx to widthPx
        } else {
            widthPx to heightPx
        }
}

data class ConversionProgress(
    val isRunning: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val message: String = ""
)

data class ConversionResult(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val isFolderTree: Boolean = false,
    val summary: String
)

data class ShelfArchiveEntry(
    val id: String,
    val title: String,
    val meta: String,
    val badge: String,
    val uri: Uri,
    val mimeType: String,
    val isFolderTree: Boolean,
    val sourceTab: ForgeTab,
    val isExportedOutput: Boolean
)

object PageRangeParser {
    /**
     * Parses a 1-based range string like "1-3, 5, 8-10" into a sorted, deduplicated
     * list of valid 1-based page numbers within [1, totalPages].
     * Blank input or "all" returns all pages 1..totalPages.
     */
    fun parse(raw: String, totalPages: Int): List<Int> {
        if (totalPages <= 0) return emptyList()
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed.equals("all", ignoreCase = true)) {
            return (1..totalPages).toList()
        }

        val pages = sortedSetOf<Int>()
        for (token in trimmed.split(",")) {
            val part = token.trim()
            if (part.isEmpty()) continue
            if ("-" in part) {
                val bounds = part.split("-", limit = 2)
                val start = bounds[0].trim().toIntOrNull()
                val end = bounds.getOrNull(1)?.trim()?.toIntOrNull()
                if (start != null && end != null) {
                    val lo = minOf(start, end).coerceAtLeast(1)
                    val hi = maxOf(start, end).coerceAtMost(totalPages)
                    if (lo <= hi) {
                        for (p in lo..hi) pages.add(p)
                    }
                }
            } else {
                val single = part.toIntOrNull()
                if (single != null && single in 1..totalPages) {
                    pages.add(single)
                }
            }
        }
        return pages.toList()
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024L) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        return String.format("%.2f MB", mb)
    }
}

data class LayoutRectF(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
}

/**
 * Pure geometry engine shared identically between the interactive Compose layout preview
 * and PdfConversionRepository's PdfDocument canvas export.
 */
object ImagePdfLayoutEngine {

    fun <T> paginate(items: List<T>, layout: ImagePageLayout): List<List<T>> {
        if (items.isEmpty()) return emptyList()
        return items.chunked(layout.perPage.coerceAtLeast(1))
    }

    fun resolvePageDimensionsPt(
        refWidthPx: Int,
        refHeightPx: Int,
        preset: PaperSizePreset,
        orientation: PageOrientation
    ): Pair<Int, Int> {
        if (preset != PaperSizePreset.FIT_TO_IMAGE) {
            return preset.orientedSize(orientation)
        }
        val safeW = refWidthPx.coerceAtLeast(72)
        val safeH = refHeightPx.coerceAtLeast(72)
        val maxPt = 1440f
        val scale = minOf(1f, minOf(maxPt / safeW, maxPt / safeH))
        var w = (safeW * scale).toInt().coerceAtLeast(72)
        var h = (safeH * scale).toInt().coerceAtLeast(72)
        if (orientation == PageOrientation.LANDSCAPE && h > w) {
            val tmp = w; w = h; h = tmp
        } else if (orientation == PageOrientation.PORTRAIT && w > h) {
            val tmp = w; w = h; h = tmp
        }
        return w to h
    }

    fun computeSlotRects(
        pageW: Float,
        pageH: Float,
        marginPt: Float,
        gapPt: Float,
        layout: ImagePageLayout,
        orientation: PageOrientation
    ): List<LayoutRectF> {
        val safeMargin = marginPt.coerceIn(0f, minOf(pageW, pageH) * 0.25f)
        val availW = (pageW - safeMargin * 2f).coerceAtLeast(1f)
        val availH = (pageH - safeMargin * 2f).coerceAtLeast(1f)
        val left = safeMargin
        val top = safeMargin

        return when (layout) {
            ImagePageLayout.SINGLE -> listOf(
                LayoutRectF(left, top, left + availW, top + availH)
            )
            ImagePageLayout.TWO_UP -> {
                val safeGap = gapPt.coerceIn(0f, minOf(availW, availH) * 0.2f)
                if (orientation == PageOrientation.LANDSCAPE && availW >= availH) {
                    val colW = (availW - safeGap) / 2f
                    listOf(
                        LayoutRectF(left, top, left + colW, top + availH),
                        LayoutRectF(left + colW + safeGap, top, left + availW, top + availH)
                    )
                } else {
                    val rowH = (availH - safeGap) / 2f
                    listOf(
                        LayoutRectF(left, top, left + availW, top + rowH),
                        LayoutRectF(left, top + rowH + safeGap, left + availW, top + availH)
                    )
                }
            }
            ImagePageLayout.FOUR_UP -> {
                val safeGap = gapPt.coerceIn(0f, minOf(availW, availH) * 0.2f)
                val colW = (availW - safeGap) / 2f
                val rowH = (availH - safeGap) / 2f
                listOf(
                    LayoutRectF(left, top, left + colW, top + rowH),
                    LayoutRectF(left + colW + safeGap, top, left + availW, top + rowH),
                    LayoutRectF(left, top + rowH + safeGap, left + colW, top + availH),
                    LayoutRectF(left + colW + safeGap, top + rowH + safeGap, left + availW, top + availH)
                )
            }
        }
    }

    fun computeImageRectInSlot(
        srcW: Int,
        srcH: Int,
        slot: LayoutRectF,
        scaleMode: ImageScaleMode,
        alignment: ImageSlotAlignment
    ): LayoutRectF {
        val safeSrcW = srcW.coerceAtLeast(1).toFloat()
        val safeSrcH = srcH.coerceAtLeast(1).toFloat()
        val slotW = slot.width.coerceAtLeast(1f)
        val slotH = slot.height.coerceAtLeast(1f)

        val scale = if (scaleMode == ImageScaleMode.FIT_INSIDE) {
            minOf(slotW / safeSrcW, slotH / safeSrcH)
        } else {
            maxOf(slotW / safeSrcW, slotH / safeSrcH)
        }

        val drawW = safeSrcW * scale
        val drawH = safeSrcH * scale
        val extraX = slotW - drawW
        val extraY = slotH - drawH

        val dstLeft = when {
            scaleMode == ImageScaleMode.FILL_PAGE -> slot.left + extraX / 2f
            alignment == ImageSlotAlignment.START -> slot.left
            alignment == ImageSlotAlignment.END -> slot.left + extraX
            else -> slot.left + extraX / 2f
        }
        val dstTop = when {
            scaleMode == ImageScaleMode.FILL_PAGE -> slot.top + extraY / 2f
            alignment == ImageSlotAlignment.START -> slot.top
            alignment == ImageSlotAlignment.END -> slot.top + extraY
            else -> slot.top + extraY / 2f
        }

        return LayoutRectF(dstLeft, dstTop, dstLeft + drawW, dstTop + drawH)
    }
}
