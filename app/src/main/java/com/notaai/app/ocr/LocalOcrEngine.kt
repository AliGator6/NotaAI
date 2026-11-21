package com.notaai.app.ocr

import android.content.Context
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

private data class BoxedLine(val text: String, val box: Rect, val confidence: Float?)

class LocalOcrEngine(context: Context) {
    private val mlKitRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val paddleRecognizer = PaddleOnnxRecognizer(context.applicationContext)

    suspend fun recognize(bitmap: Bitmap, enhanceContrast: Boolean): OcrResult {
        val baseBitmap = BitmapProcessor.upscaleIfTooSmall(bitmap)
        val candidates = mutableListOf<OcrResult>()
        try {
            val softColor = BitmapProcessor.softNormalizeColor(baseBitmap)
            val whiteRaw = BitmapProcessor.normalizeBackgroundWhitePaper(baseBitmap)
            val cleanWhite = BitmapProcessor.removeThinHorizontalRules(whiteRaw)
            whiteRaw.recycle()

            try {
                if (paddleRecognizer.isAvailable()) {
                    candidates.add(runDualImagePaddlePass(cleanWhite, softColor))
                }
                candidates.add(runMlKitFullPass(baseBitmap))
                if (enhanceContrast) {
                    candidates.add(runMlKitFullPass(cleanWhite))
                }
            } finally {
                cleanWhite.recycle()
                softColor.recycle()
            }
            return candidates.maxByOrNull { scoreResult(it) } ?: candidates.first()
        } finally {
            if (baseBitmap !== bitmap) baseBitmap.recycle()
        }
    }

    private fun runDualImagePaddlePass(cleanWhite: Bitmap, softColor: Bitmap): OcrResult {
        val bands = BitmapProcessor.findTextBoxesOnWhitePaper(cleanWhite)
        if (bands.isEmpty()) return OcrResult(emptyList(), "")

        val w = cleanWhite.width
        val maxRowIdx = bands.maxOf { it.rowIndex }
        val boxesByRow = bands.groupBy { it.rowIndex }

        val rows = mutableListOf<OcrRecognizedLine>()
        for (rowIdx in 0..maxRowIdx) {
            val rowBands = boxesByRow[rowIdx]?.sortedBy { it.rect.left } ?: continue
            val recognizedParts = mutableListOf<PaddleLineResult>()

            rowBands.forEachIndexed { idx, band ->
                val r = band.rect
                val isPriceCol = (rowBands.size >= 2 && idx == rowBands.lastIndex && r.left >= w * 0.42f) ||
                    (rowBands.size == 1 && idx == 0 && r.left >= w * 0.48f)

                val recA = recognizeBox(cleanWhite, r, isPriceCol)
                val recB = recognizeBox(softColor, r, isPriceCol)
                val best = pickBetterBoxResult(recA, recB, isPriceCol)
                if (best != null && best.text.isNotBlank()) recognizedParts.add(best)
            }

            if (recognizedParts.isNotEmpty()) {
                rows.add(OcrRecognizedLine(
                    text = recognizedParts.joinToString(" ") { it.text },
                    confidence = recognizedParts.minOf { it.confidence }
                ))
            }
        }
        return OcrResult(rows, rows.joinToString("\n") { it.text })
    }

    private fun recognizeBox(source: Bitmap, r: Rect, preferDigits: Boolean): PaddleLineResult? {
        val crop = Bitmap.createBitmap(source, r.left, r.top, r.width(), r.height())
        val padded = BitmapProcessor.padWithWhite(crop, 20)
        crop.recycle()
        return try {
            paddleRecognizer.recognizeCrop(padded, preferDigits)
        } finally {
            padded.recycle()
        }
    }

    private fun pickBetterBoxResult(a: PaddleLineResult?, b: PaddleLineResult?, isPriceCol: Boolean): PaddleLineResult? {
        if (a == null) return b
        if (b == null) return a
        if (isPriceCol) {
            val scoreA = priceBoxQuality(a)
            val scoreB = priceBoxQuality(b)
            return if (scoreB > scoreA) b else a
        }
        return if (b.confidence > a.confidence) b else a
    }

    private fun priceBoxQuality(res: PaddleLineResult): Float {
        val validPriceBonus = if (Regex("""^\d{1,3}[.,\-]\d{3}$""").matches(res.text.trim())) 2.0f else 0f
        val digitRatio = res.text.count { it.isDigit() }.toFloat() / res.text.length.coerceAtLeast(1)
        return validPriceBonus + digitRatio + res.confidence
    }

    private suspend fun runMlKitFullPass(bmp: Bitmap): OcrResult {
        val padded = BitmapProcessor.padWithWhite(bmp, 36)
        return try {
            val visionText = mlKitRecognizer.process(InputImage.fromBitmap(padded, 0)).await()
            val boxes = visionText.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                val rawBox = line.boundingBox ?: return@mapNotNull null
                val clean = line.text.trim()
                if (clean.isEmpty()) null else BoxedLine(clean, Rect(rawBox.left - 36, rawBox.top - 36, rawBox.right - 36, rawBox.bottom - 36), line.confidence)
            }
            val rows = groupBoxesByVisualRow(boxes)
            OcrResult(rows, rows.joinToString("\n") { it.text })
        } finally {
            padded.recycle()
        }
    }

    private fun scoreResult(result: OcrResult): Int {
        val validPrices = result.lines.count { Regex("""\d{1,3}[.,\- :;']\d{3}""").containsMatchIn(it.text) || Regex("""\d{4,}""").containsMatchIn(it.text) }
        val digitCount = result.rawText.count { it.isDigit() }
        val badLettersInDigits = Regex("""[oOlIsSD]\d|\d[oOlIsSD]""").findAll(result.rawText).count()
        return validPrices * 30 + digitCount * 2 - badLettersInDigits * 10 + result.lines.size
    }

    private fun groupBoxesByVisualRow(input: List<BoxedLine>): List<OcrRecognizedLine> {
        val allLines = input.sortedBy { it.box.centerY() }
        if (allLines.isEmpty()) return emptyList()
        val rows = mutableListOf<MutableList<BoxedLine>>()
        for (line in allLines) {
            val matchingRow = rows.lastOrNull()?.let { lastRow ->
                val avgY = lastRow.map { it.box.centerY() }.average()
                val avgH = lastRow.map { it.box.height().coerceAtLeast(14) }.average()
                val rowLeft = lastRow.minOf { it.box.left }; val rowRight = lastRow.maxOf { it.box.right }
                val overlap = min(rowRight, line.box.right) - max(rowLeft, line.box.left)
                val minW = min(rowRight - rowLeft, line.box.width()).coerceAtLeast(1)
                val yTol = if (overlap < minW * 0.25f) avgH * 1.25 else avgH * 0.5
                if (abs(line.box.centerY() - avgY) <= yTol) lastRow else null
            }
            if (matchingRow != null) matchingRow.add(line) else rows.add(mutableListOf(line))
        }
        return rows.map { row ->
            val sorted = row.sortedBy { it.box.left }
            val confs = sorted.mapNotNull { it.confidence }
            OcrRecognizedLine(sorted.joinToString(" ") { it.text }, if (confs.isEmpty()) null else confs.min())
        }
    }
}
