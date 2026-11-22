package com.forgefolio.app

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
}
