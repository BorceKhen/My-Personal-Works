package com.forgefolio.app

import com.forgefolio.app.model.ImagePageLayout
import com.forgefolio.app.model.ImagePdfLayoutEngine
import com.forgefolio.app.model.ImageScaleMode
import com.forgefolio.app.model.ImageSlotAlignment
import com.forgefolio.app.model.PageOrientation
import com.forgefolio.app.model.PageRangeParser
import com.forgefolio.app.model.PaperSizePreset
import org.apache.poi.xwpf.usermodel.XWPFDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class ConversionModelsTest {

    @Test
    fun pageRangeParserHandlesAllAndCustomRanges() {
        assertEquals(listOf(1, 2, 3, 4, 5), PageRangeParser.parse("", 5))
        assertEquals(listOf(1, 2, 3, 4, 5), PageRangeParser.parse("all", 5))
        assertEquals(listOf(1, 2, 3, 5, 8, 9), PageRangeParser.parse("1-3, 5, 9-8, 99", 10))
    }

    @Test
    fun paperSizePresetOrientsCorrectly() {
        assertEquals(612 to 792, PaperSizePreset.SHORT_BOND.orientedSize(PageOrientation.PORTRAIT))
        assertEquals(792 to 612, PaperSizePreset.SHORT_BOND.orientedSize(PageOrientation.LANDSCAPE))
        assertEquals(612 to 936, PaperSizePreset.LONG_BOND.orientedSize(PageOrientation.PORTRAIT))
        assertEquals(595 to 842, PaperSizePreset.A4.orientedSize(PageOrientation.PORTRAIT))
        assertEquals(612 to 1008, PaperSizePreset.LEGAL.orientedSize(PageOrientation.PORTRAIT))
    }

    @Test
    fun apachePoiXwpfRoundTripsOffline() {
        val baos = ByteArrayOutputStream()
        XWPFDocument().use { doc ->
            val p = doc.createParagraph()
            p.createRun().apply {
                isBold = true
                fontSize = 14
                setText("ForgeFolio Archival Test")
            }
            doc.write(baos)
        }

        XWPFDocument(ByteArrayInputStream(baos.toByteArray())).use { readBack ->
            assertEquals(1, readBack.paragraphs.size)
            assertTrue(readBack.paragraphs[0].text.contains("ForgeFolio Archival Test"))
        }
    }

    @Test
    fun imagePdfLayoutEnginePaginatesAndComputesAlignedSlots() {
        val pagesTwoUp = ImagePdfLayoutEngine.paginate(listOf(1, 2, 3, 4, 5), ImagePageLayout.TWO_UP)
        assertEquals(listOf(listOf(1, 2), listOf(3, 4), listOf(5)), pagesTwoUp)

        val portraitSlots = ImagePdfLayoutEngine.computeSlotRects(
            pageW = 600f,
            pageH = 800f,
            marginPt = 20f,
            gapPt = 20f,
            layout = ImagePageLayout.TWO_UP,
            orientation = PageOrientation.PORTRAIT
        )
        assertEquals(2, portraitSlots.size)
        assertEquals(20f, portraitSlots[0].left, 0.01f)
        assertEquals(390f, portraitSlots[0].bottom, 0.01f)
        assertEquals(410f, portraitSlots[1].top, 0.01f)

        val fourSlots = ImagePdfLayoutEngine.computeSlotRects(
            pageW = 600f,
            pageH = 800f,
            marginPt = 0f,
            gapPt = 0f,
            layout = ImagePageLayout.FOUR_UP,
            orientation = PageOrientation.PORTRAIT
        )
        assertEquals(4, fourSlots.size)
        assertEquals(300f, fourSlots[0].width, 0.01f)
        assertEquals(400f, fourSlots[0].height, 0.01f)

        val topAligned = ImagePdfLayoutEngine.computeImageRectInSlot(
            srcW = 1000,
            srcH = 500,
            slot = fourSlots[0],
            scaleMode = ImageScaleMode.FIT_INSIDE,
            alignment = ImageSlotAlignment.START
        )
        assertEquals(0f, topAligned.top, 0.01f)
        assertEquals(150f, topAligned.height, 0.01f)
    }
}
