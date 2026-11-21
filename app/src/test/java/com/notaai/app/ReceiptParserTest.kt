package com.notaai.app

import com.notaai.app.ocr.OcrRecognizedLine
import com.notaai.app.ocr.ReceiptParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptParserTest {
    @Test fun `parse format sederhana dan format kuantitas`() {
        val parsed = ReceiptParser.parse(listOf(
            OcrRecognizedLine("Beras 5 kg x 15.000 = 75.000", 0.92f),
            OcrRecognizedLine("Gula 28.000", 0.90f),
            OcrRecognizedLine("Total 103.000", 0.95f)
        ))
        assertEquals(2, parsed.items.size)
        assertEquals(75_000L, parsed.items[0].lineTotal)
        assertEquals(28_000L, parsed.items[1].lineTotal)
        assertEquals(103_000L, parsed.detectedWrittenTotal)
    }
    @Test fun `ocr meragukan ditandai perlu diperiksa tanpa mengarang angka`() {
        val parsed = ReceiptParser.parse(listOf(
            OcrRecognizedLine("Telur 32.000", 0.45f),
            OcrRecognizedLine("Kopi 1O.5O0", 0.88f)
        ), flagLowConfidence = true)
        assertEquals(2, parsed.items.size)
        assertTrue(parsed.items[0].needsReview)
        assertTrue(parsed.items[1].needsReview)
        assertEquals(0L, parsed.items[1].lineTotal)
    }
}
