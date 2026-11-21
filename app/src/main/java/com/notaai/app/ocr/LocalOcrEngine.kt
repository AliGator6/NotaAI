package com.notaai.app.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class OcrRecognizedLine(val text: String, val confidence: Float?)
data class OcrResult(val lines: List<OcrRecognizedLine>, val rawText: String)

class LocalOcrEngine {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap, enhanceContrast: Boolean): OcrResult {
        val baseBitmap = BitmapProcessor.upscaleIfTooSmall(bitmap)
        return try {
            // Pass 1: Foto asli berwarna (sering paling akurat untuk ML Kit)
            val pass1 = runSinglePass(baseBitmap)
            if (!enhanceContrast) return pass1

            // Pass 2: Kontras lembut (membantu jika tinta pudar / cahaya redup)
            val enhanced = BitmapProcessor.enhanceContrastForOcr(baseBitmap)
            val pass2 = try {
                runSinglePass(enhanced)
            } finally {
                enhanced.recycle()
            }

            if (scoreResult(pass2) > scoreResult(pass1)) pass2 else pass1
        } finally {
            if (baseBitmap !== bitmap) baseBitmap.recycle()
        }
    }

    private suspend fun runSinglePass(bmp: Bitmap): OcrResult {
        val visionText = recognizer.process(InputImage.fromBitmap(bmp, 0)).await()
        val orderedRows = groupLinesByVisualRow(visionText)
        return OcrResult(lines = orderedRows, rawText = orderedRows.joinToString("\n") { it.text })
    }

    private fun scoreResult(result: OcrResult): Int {
        val priceLikeCount = result.lines.count { Regex("""\d{3,}""").containsMatchIn(it.text) }
        return priceLikeCount * 10 + result.lines.size
    }

    private fun groupLinesByVisualRow(visionText: Text): List<OcrRecognizedLine> {
        data class BoxedLine(val text: String, val box: Rect, val confidence: Float?)
        val allLines = visionText.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val box = line.boundingBox ?: return@mapNotNull null
            val clean = line.text.trim()
            if (clean.isEmpty()) null else BoxedLine(clean, box, line.confidence)
        }.sortedBy { it.box.centerY() }

        if (allLines.isEmpty()) return emptyList()

        val rows = mutableListOf<MutableList<BoxedLine>>()
        for (line in allLines) {
            val matchingRow = rows.lastOrNull()?.let { lastRow ->
                val avgY = lastRow.map { it.box.centerY() }.average()
                val avgH = lastRow.map { it.box.height().coerceAtLeast(14) }.average()
                val rowLeft = lastRow.minOf { it.box.left }
                val rowRight = lastRow.maxOf { it.box.right }
                val overlap = min(rowRight, line.box.right) - max(rowLeft, line.box.left)
                val minW = min(rowRight - rowLeft, line.box.width()).coerceAtLeast(1)
                val isSeparateColumn = overlap < minW * 0.25f
                val yTolerance = if (isSeparateColumn) avgH * 1.15 else avgH * 0.5
                if (abs(line.box.centerY() - avgY) <= yTolerance) lastRow else null
            }
            if (matchingRow != null) matchingRow.add(line) else rows.add(mutableListOf(line))
        }

        return rows.map { row ->
            val sorted = row.sortedBy { it.box.left }
            val confs = sorted.mapNotNull { it.confidence }
            OcrRecognizedLine(
                text = sorted.joinToString(" ") { it.text },
                confidence = if (confs.isEmpty()) null else confs.min()
            )
        }
    }
}
