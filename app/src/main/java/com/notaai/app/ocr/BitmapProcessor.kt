package com.notaai.app.ocr

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max
import kotlin.math.min

data class DetectedTextBand(val rect: Rect, val rowIndex: Int)

object BitmapProcessor {
    fun loadNormalizedBitmap(context: Context, uri: Uri, maxDimension: Int = 2400): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null
        val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, maxDimension)
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sampleSize })
        } ?: return null
        val exifRotation = context.contentResolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        return if (exifRotation != 0f) rotateBitmap(decoded, exifRotation).also { if (it !== decoded) decoded.recycle() } else decoded
    }

    fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        if (degrees % 360f == 0f) return source
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun cropNormalized(source: Bitmap, leftRatio: Float, topRatio: Float, rightRatio: Float, bottomRatio: Float): Bitmap {
        val l = (leftRatio.coerceIn(0f, 0.95f) * source.width).toInt()
        val t = (topRatio.coerceIn(0f, 0.95f) * source.height).toInt()
        val r = (rightRatio.coerceIn(0.05f, 1f) * source.width).toInt()
        val b = (bottomRatio.coerceIn(0.05f, 1f) * source.height).toInt()
        val w = max(20, r - l).coerceAtMost(source.width - l)
        val h = max(20, b - t).coerceAtMost(source.height - t)
        return Bitmap.createBitmap(source, l, t, w, h)
    }

    fun upscaleIfTooSmall(source: Bitmap): Bitmap {
        val minDim = min(source.width, source.height)
        val maxDim = max(source.width, source.height)
        if (minDim >= 1200 || maxDim >= 2200) return source
        val scale = min(2.2f, 2200f / maxDim.toFloat())
        if (scale <= 1.05f) return source
        return Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true)
    }

    fun softNormalizeColor(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        val contrast = 1.18f
        val translate = (-0.5f * contrast + 0.5f) * 255f + 12f
        paint.colorFilter = ColorMatrixColorFilter(ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )))
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    fun normalizeBackgroundWhitePaper(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val gray = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            gray[i] = (((c shr 16) and 0xFF) * 30 + ((c shr 8) and 0xFF) * 59 + (c and 0xFF) * 11) / 100
        }

        val stride = w + 1
        val integral = LongArray(stride * (h + 1))
        for (y in 0 until h) {
            var rowSum = 0L
            val rowOff = y * w
            val curRow = (y + 1) * stride
            val prevRow = y * stride
            for (x in 0 until w) {
                rowSum += gray[rowOff + x]
                integral[curRow + x + 1] = integral[prevRow + x + 1] + rowSum
            }
        }

        val radius = max(20, min(w, h) / 22)
        val outPixels = IntArray(w * h)
        for (y in 0 until h) {
            val y0 = max(0, y - radius)
            val y1 = min(h - 1, y + radius)
            val rowOff = y * w
            val intY1 = (y1 + 1) * stride
            val intY0 = y0 * stride
            for (x in 0 until w) {
                val x0 = max(0, x - radius)
                val x1 = min(w - 1, x + radius)
                val count = (x1 - x0 + 1) * (y1 - y0 + 1)
                val sum = integral[intY1 + x1 + 1] - integral[intY0 + x1 + 1] - integral[intY1 + x0] + integral[intY0 + x0]
                val localMean = (sum / count).toInt().coerceAtLeast(1)
                val ratio = (gray[rowOff + x] * 255) / localMean
                val v = if (ratio >= 226) 255 else ((ratio - 48) * 255 / (226 - 48)).coerceIn(0, 255)
                outPixels[rowOff + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(outPixels, 0, w, 0, 0, w, h) }
    }

    fun removeThinHorizontalRules(whitePaper: Bitmap): Bitmap {
        val w = whitePaper.width
        val h = whitePaper.height
        val pixels = IntArray(w * h)
        whitePaper.getPixels(pixels, 0, w, 0, 0, w, h)
        val minRunLen = max(45, (w * 0.24f).toInt())
        val maxStrokeThick = max(12, h / 55)

        val toErase = BooleanArray(w * h)
        for (y in 0 until h) {
            val rowOff = y * w
            var x = 0
            while (x < w) {
                if ((pixels[rowOff + x] and 0xFF) < 190) {
                    val startX = x
                    var gap = 0
                    while (x < w) {
                        if ((pixels[rowOff + x] and 0xFF) < 190) { gap = 0; x++ }
                        else if (gap < 3) { gap++; x++ }
                        else break
                    }
                    val endX = x - gap
                    if (endX - startX >= minRunLen) {
                        for (rx in startX until endX) {
                            var topY = y
                            while (topY > 0 && (pixels[(topY - 1) * w + rx] and 0xFF) < 190 && y - topY <= maxStrokeThick) topY--
                            var botY = y
                            while (botY < h - 1 && (pixels[(botY + 1) * w + rx] and 0xFF) < 190 && botY - y <= maxStrokeThick) botY++
                            if (botY - topY <= maxStrokeThick) for (ry in topY..botY) toErase[ry * w + rx] = true
                        }
                    }
                } else x++
            }
        }
        for (i in pixels.indices) if (toErase[i]) pixels[i] = Color.WHITE
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(pixels, 0, w, 0, 0, w, h) }
    }

    /** Deteksi komponen tinta 2D sehingga baris miring tetap terpisah rapi. */
    fun findTextBoxesOnWhitePaper(whitePaper: Bitmap): List<DetectedTextBand> {
        val w = whitePaper.width
        val h = whitePaper.height
        val scale = max(1, max(w, h) / 900)
        val mw = (w / scale).coerceAtLeast(1)
        val mh = (h / scale).coerceAtLeast(1)
        val small = if (scale > 1) Bitmap.createScaledBitmap(whitePaper, mw, mh, true) else whitePaper
        val pixels = IntArray(mw * mh)
        small.getPixels(pixels, 0, mw, 0, 0, mw, mh)
        if (small !== whitePaper) small.recycle()

        val dark = BooleanArray(mw * mh) { (pixels[it] and 0xFF) < 205 }
        val visited = BooleanArray(mw * mh)
        val queue = IntArray(mw * mh)
        val rawBoxes = mutableListOf<Rect>()

        for (sy in 0 until mh) {
            for (sx in 0 until mw) {
                val startIdx = sy * mw + sx
                if (!dark[startIdx] || visited[startIdx]) continue
                var head = 0
                var tail = 0
                queue[tail++] = startIdx
                visited[startIdx] = true
                var minX = sx; var maxX = sx; var minY = sy; var maxY = sy
                var count = 0
                while (head < tail) {
                    val cur = queue[head++]
                    val cx = cur % mw
                    val cy = cur / mw
                    count++
                    if (cx < minX) minX = cx; if (cx > maxX) maxX = cx
                    if (cy < minY) minY = cy; if (cy > maxY) maxY = cy
                    for (dy in -1..1) {
                        val ny = cy + dy
                        if (ny < 0 || ny >= mh) continue
                        for (dx in -1..1) {
                            val nx = cx + dx
                            if (nx < 0 || nx >= mw) continue
                            val nIdx = ny * mw + nx
                            if (dark[nIdx] && !visited[nIdx]) {
                                visited[nIdx] = true
                                queue[tail++] = nIdx
                            }
                        }
                    }
                }
                if (count >= 4) {
                    rawBoxes.add(Rect(minX * scale, minY * scale, min(w, (maxX + 1) * scale), min(h, (maxY + 1) * scale)))
                }
            }
        }
        if (rawBoxes.isEmpty()) return emptyList()

        // Gabungkan huruf/titik yang berdekatan dalam 1 kata/angka
        val merged = rawBoxes.toMutableList()
        var changed = true
        while (changed) {
            changed = false
            var i = 0
            while (i < merged.size) {
                var j = i + 1
                while (j < merged.size) {
                    if (shouldMergeWordBoxes(merged[i], merged[j], w)) {
                        merged[i].union(merged[j])
                        merged.removeAt(j)
                        changed = true
                    } else j++
                }
                i++
            }
        }

        val cleanBoxes = merged.filter { it.width() >= 14 && it.height() >= 12 }.sortedBy { it.centerY() }
        if (cleanBoxes.isEmpty()) return emptyList()

        // Kelompokkan kotak kiri & kanan menjadi baris visual
        val rows = mutableListOf<MutableList<Rect>>()
        for (box in cleanBoxes) {
            val match = rows.lastOrNull()?.let { row ->
                val avgY = row.map { it.centerY() }.average()
                val avgH = row.map { it.height().coerceAtLeast(16) }.average()
                val rowLeft = row.minOf { it.left }; val rowRight = row.maxOf { it.right }
                val overlap = min(rowRight, box.right) - max(rowLeft, box.left)
                val minW = min(rowRight - rowLeft, box.width()).coerceAtLeast(1)
                val yTol = if (overlap < minW * 0.25f) avgH * 1.15 else avgH * 0.55
                if (kotlin.math.abs(box.centerY() - avgY) <= yTol) row else null
            }
            if (match != null) match.add(box) else rows.add(mutableListOf(box))
        }

        val pad = max(8, min(w, h) / 140)
        val out = mutableListOf<DetectedTextBand>()
        rows.forEachIndexed { rowIdx, row ->
            row.sortedBy { it.left }.forEach { r ->
                out.add(DetectedTextBand(
                    Rect(max(0, r.left - pad), max(0, r.top - pad), min(w, r.right + pad), min(h, r.bottom + pad)),
                    rowIdx
                ))
            }
        }
        return out.take(75)
    }

    private fun shouldMergeWordBoxes(a: Rect, b: Rect, imageWidth: Int): Boolean {
        val vOverlap = min(a.bottom, b.bottom) - max(a.top, b.top)
        val maxH = max(a.height(), b.height()).coerceAtLeast(12)
        val minH = min(a.height(), b.height()).coerceAtLeast(1)
        val smallDotNearLine = minH <= maxH * 0.45f && kotlin.math.abs(a.centerY() - b.centerY()) <= maxH * 0.72f
        val verticallyAligned = vOverlap > 0 || smallDotNearLine
        if (!verticallyAligned) return false
        val hGap = max(a.left, b.left) - min(a.right, b.right)
        val maxWordGap = max(24, min((maxH * 1.15f).toInt(), (imageWidth * 0.075f).toInt()))
        return hGap <= maxWordGap
    }

    fun findBestVerticalSplitX(whitePaper: Bitmap): Int {
        val w = whitePaper.width; val h = whitePaper.height
        val startX = (w * 0.35f).toInt(); val endX = (w * 0.65f).toInt()
        if (endX <= startX + 10) return w / 2
        val pixels = IntArray(w * h); whitePaper.getPixels(pixels, 0, w, 0, 0, w, h)
        var bestX = w / 2; var minInk = Int.MAX_VALUE
        for (x in startX..endX step 2) {
            var ink = 0
            for (y in 0 until h step 3) if ((pixels[y * w + x] and 0xFF) < 185) ink++
            if (ink < minInk) { minInk = ink; bestX = x }
        }
        return bestX
    }

    fun padWithWhite(source: Bitmap, pad: Int = 36): Bitmap {
        val out = Bitmap.createBitmap(source.width + pad * 2, source.height + pad * 2, Bitmap.Config.ARGB_8888)
        Canvas(out).apply { drawColor(Color.WHITE); drawBitmap(source, pad.toFloat(), pad.toFloat(), null) }
        return out
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        val longest = max(width, height)
        while (longest / inSampleSize > maxDim) inSampleSize *= 2
        return min(inSampleSize, 16)
    }
}
