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
    val thumbnail: Bitmap?
)

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
