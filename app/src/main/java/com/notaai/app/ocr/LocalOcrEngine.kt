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
            val whiteRaw = BitmapProcessor.normalizeBackgroundWhitePaper(baseBitmap)
            val cleanWhite = BitmapProcessor.removeThinHorizontalRules(whiteRaw)
            whiteRaw.recycle()

            try {
                if (paddleRecognizer.isAvailable()) {
                    candidates.add(runPaddlePass(cleanWhite))
                }
                candidates.add(runMlKitFullPass(baseBitmap))
                if (enhanceContrast) {
                    candidates.add(runMlKitFullPass(cleanWhite))
                    candidates.add(runMlKitColumnSplitPass(cleanWhite))
                }
            } finally {
                cleanWhite.recycle()
            }

            return candidates.maxByOrNull { scoreResult(it) } ?: candidates.first()
        } finally {
            if (baseBitmap !== bitmap) baseBitmap.recycle()
        }
    }

    private fun runPaddlePass(cleanWhite: Bitmap): OcrResult {
        val bands = BitmapProcessor.findTextBoxesOnWhitePaper(cleanWhite)
        if (bands.isEmpty()) return OcrResult(emptyList(), "")

        val grouped = linkedMapOf<Int, MutableList<Pair<Rect, PaddleLineResult>>>()
        for (band in bands) {
            val r = band.rect
            val crop = Bitmap.createBitmap(cleanWhite, r.left, r.top, r.width(), r.height())
            val padded = BitmapProcessor.padWithWhite(crop, 14)
            crop.recycle()
            val rec = try {
                paddleRecognizer.recognizeCrop(padded)
            } finally {
                padded.recycle()
            }
            if (rec != null && rec.text.isNotBlank()) {
                grouped.getOrPut(band.rowIndex) { mutableListOf() }.add(r to rec)
            }
        }

        val rows = grouped.values.map { rowItems ->
            val sorted = rowItems.sortedBy { it.first.left }
            val text = sorted.joinToString(" ") { it.second.text }
            val minConf = sorted.minOf { it.second.confidence }
            OcrRecognizedLine(text, minConf)
        }
        return OcrResult(rows, rows.joinToString("\n") { it.text })
    }

    private suspend fun runMlKitFullPass(bmp: Bitmap): OcrResult {
        val padded = BitmapProcessor.padWithWhite(bmp, 36)
        return try {
            val visionText = mlKitRecognizer.process(InputImage.fromBitmap(padded, 0)).await()
            val boxes = extractBoxes(visionText, -36, -36)
            val rows = groupBoxesByVisualRow(boxes)
            OcrResult(rows, rows.joinToString("\n") { it.text })
        } finally {
            padded.recycle()
        }
    }

    private suspend fun runMlKitColumnSplitPass(whitePaper: Bitmap): OcrResult {
        val splitX = BitmapProcessor.findBestVerticalSplitX(whitePaper)
        val h = whitePaper.height
        val w = whitePaper.width
        if (splitX < 40 || w - splitX < 40) return runMlKitFullPass(whitePaper)

        val pad = 48
        val leftCrop = Bitmap.createBitmap(whitePaper, 0, 0, splitX, h)
        val rightCrop = Bitmap.createBitmap(whitePaper, splitX, 0, w - splitX, h)
        val leftPadded = BitmapProcessor.padWithWhite(leftCrop, pad)
        val rightPadded = BitmapProcessor.padWithWhite(rightCrop, pad)

        return try {
            val leftText = mlKitRecognizer.process(InputImage.fromBitmap(leftPadded, 0)).await()
            val rightText = mlKitRecognizer.process(InputImage.fromBitmap(rightPadded, 0)).await()
            val allBoxes = extractBoxes(leftText, -pad, -pad) + extractBoxes(rightText, splitX - pad, -pad)
            val rows = groupBoxesByVisualRow(allBoxes)
            OcrResult(rows, rows.joinToString("\n") { it.text })
        } finally {
            leftCrop.recycle(); rightCrop.recycle(); leftPadded.recycle(); rightPadded.recycle()
        }
    }

    private fun extractBoxes(visionText: Text, offsetX: Int, offsetY: Int): List<BoxedLine> {
        return visionText.textBlocks.flatMap { it.lines }.mapNotNull { line ->
            val rawBox = line.boundingBox ?: return@mapNotNull null
            val clean = line.text.trim()
            if (clean.isEmpty()) return@mapNotNull null
            BoxedLine(clean, Rect(rawBox.left + offsetX, rawBox.top + offsetY, rawBox.right + offsetX, rawBox.bottom + offsetY), line.confidence)
        }
    }

    private fun scoreResult(result: OcrResult): Int {
        val validPrices = result.lines.count { Regex("""\d{1,3}[.,\- ]\d{3}""").containsMatchIn(it.text) || Regex("""\d{4,}""").containsMatchIn(it.text) }
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
                val rowLeft = lastRow.minOf { it.box.left }
                val rowRight = lastRow.maxOf { it.box.right }
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
