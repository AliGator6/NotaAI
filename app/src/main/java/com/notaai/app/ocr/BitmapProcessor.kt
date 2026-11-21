package com.notaai.app.ocr

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max
import kotlin.math.min

data class DetectedTextBand(
    val rect: Rect,
    val rowIndex: Int
)

object BitmapProcessor {
    fun loadNormalizedBitmap(context: Context, uri: Uri, maxDimension: Int = 2400): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, boundsOpts) }
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null
        val sampleSize = calculateInSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, maxDimension)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, decodeOpts) } ?: return null
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
        if (minDim >= 1100 || maxDim >= 2200) return source
        val scale = min(2.0f, 2200f / maxDim.toFloat())
        if (scale <= 1.05f) return source
        return Bitmap.createScaledBitmap(source, (source.width * scale).toInt(), (source.height * scale).toInt(), true)
    }

    fun normalizeBackgroundWhitePaper(source: Bitmap): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        val gray = IntArray(w * h)
        for (i in pixels.indices) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            gray[i] = (r * 30 + g * 59 + b * 11) / 100
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

        val radius = max(18, min(w, h) / 24)
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
                val v = if (ratio >= 222) 255 else ((ratio - 50) * 255 / (222 - 50)).coerceIn(0, 255)
                outPixels[rowOff + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(outPixels, 0, w, 0, 0, w, h) }
    }

    /** Menghapus garis pemisah horizontal panjang (seperti ______+) agar tidak mengganggu pembacaan angka total di bawahnya. */
    fun removeThinHorizontalRules(whitePaper: Bitmap): Bitmap {
        val w = whitePaper.width
        val h = whitePaper.height
        val pixels = IntArray(w * h)
        whitePaper.getPixels(pixels, 0, w, 0, 0, w, h)
        val minRunLen = max(40, (w * 0.22f).toInt())
        val maxStrokeThick = max(12, h / 55)

        val toErase = BooleanArray(w * h)
        for (y in 0 until h) {
            val rowOff = y * w
            var x = 0
            while (x < w) {
                if ((pixels[rowOff + x] and 0xFF) < 185) {
                    val startX = x
                    var gap = 0
                    while (x < w) {
                        if ((pixels[rowOff + x] and 0xFF) < 185) { gap = 0; x++ }
                        else if (gap < 3) { gap++; x++ }
                        else break
                    }
                    val endX = x - gap
                    if (endX - startX >= minRunLen) {
                        for (rx in startX until endX) {
                            var topY = y
                            while (topY > 0 && (pixels[(topY - 1) * w + rx] and 0xFF) < 185 && y - topY <= maxStrokeThick) topY--
                            var botY = y
                            while (botY < h - 1 && (pixels[(botY + 1) * w + rx] and 0xFF) < 185 && botY - y <= maxStrokeThick) botY++
                            if (botY - topY <= maxStrokeThick) {
                                for (ry in topY..botY) toErase[ry * w + rx] = true
                            }
                        }
                    }
                } else {
                    x++
                }
            }
        }
        for (i in pixels.indices) {
            if (toErase[i]) pixels[i] = Color.WHITE
        }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setPixels(pixels, 0, w, 0, 0, w, h) }
    }

    /** Memotong setiap baris & memisahkan kolom kiri (barang) dengan kanan (harga) secara murni Kotlin. */
    fun findTextBoxesOnWhitePaper(whitePaper: Bitmap): List<DetectedTextBand> {
        val w = whitePaper.width
        val h = whitePaper.height
        val pixels = IntArray(w * h)
        whitePaper.getPixels(pixels, 0, w, 0, 0, w, h)

        val rowHasInk = BooleanArray(h)
        val minRowPixels = max(4, w / 220)
        for (y in 0 until h) {
            var count = 0
            val off = y * w
            for (x in 0 until w) {
                if ((pixels[off + x] and 0xFF) < 185) count++
            }
            rowHasInk[y] = count >= minRowPixels
        }

        // Gabungkan celah vertikal kecil (<= 10px) agar titik i / tanda baca tetap 1 baris
        val rowGapMerge = max(8, h / 110)
        val bands = mutableListOf<Pair<Int, Int>>()
        var y = 0
        while (y < h) {
            if (rowHasInk[y]) {
                val startY = y
                var lastInkY = y
                y++
                while (y < h) {
                    if (rowHasInk[y]) { lastInkY = y; y++ }
                    else if (y - lastInkY <= rowGapMerge) y++
                    else break
                }
                if (lastInkY - startY >= 14) bands.add(startY to lastInkY)
            } else y++
        }

        val boxes = mutableListOf<DetectedTextBand>()
        bands.forEachIndexed { rowIdx, (sy, ey) ->
            val bandH = ey - sy + 1
            val colHasInk = BooleanArray(w)
            for (x in 0 until w) {
                var cnt = 0
                for (ry in sy..ey) {
                    if ((pixels[ry * w + x] and 0xFF) < 185) cnt++
                }
                colHasInk[x] = cnt >= 2
            }

            // Pecah kolom hanya jika celah kosong lebar (antara nama barang & kolom harga)
            val colGapMerge = max(24, min((bandH * 1.15f).toInt(), (w * 0.08f).toInt()))
            var x = 0
            while (x < w) {
                if (colHasInk[x]) {
                    val startX = x
                    var lastInkX = x
                    x++
                    while (x < w) {
                        if (colHasInk[x]) { lastInkX = x; x++ }
                        else if (x - lastInkX <= colGapMerge) x++
                        else break
                    }
                    if (lastInkX - startX >= 14) {
                        // Ketatkan bounding box vertikal tepat di area tinta segmen ini
                        var tightTop = ey
                        var tightBot = sy
                        for (ry in sy..ey) {
                            var has = false
                            for (rx in startX..lastInkX) {
                                if ((pixels[ry * w + rx] and 0xFF) < 185) { has = true; break }
                            }
                            if (has) {
                                if (ry < tightTop) tightTop = ry
                                if (ry > tightBot) tightBot = ry
                            }
                        }
                        if (tightBot - tightTop >= 12) {
                            boxes.add(DetectedTextBand(Rect(startX, tightTop, lastInkX + 1, tightBot + 1), rowIdx))
                        }
                    }
                } else x++
            }
        }
        return boxes.take(70)
    }

    fun findBestVerticalSplitX(whitePaper: Bitmap): Int {
        val w = whitePaper.width
        val h = whitePaper.height
        val startX = (w * 0.35f).toInt()
        val endX = (w * 0.65f).toInt()
        if (endX <= startX + 10) return w / 2
        val pixels = IntArray(w * h)
        whitePaper.getPixels(pixels, 0, w, 0, 0, w, h)
        var bestX = w / 2
        var minInk = Int.MAX_VALUE
        for (x in startX..endX step 2) {
            var ink = 0
            for (y in 0 until h step 3) if ((pixels[y * w + x] and 0xFF) < 185) ink++
            if (ink < minInk) { minInk = ink; bestX = x }
        }
        return bestX
    }

    fun padWithWhite(source: Bitmap, pad: Int = 36): Bitmap {
        val out = Bitmap.createBitmap(source.width + pad * 2, source.height + pad * 2, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(Color.WHITE)
            drawBitmap(source, pad.toFloat(), pad.toFloat(), null)
        }
        return out
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        val longest = max(width, height)
        while (longest / inSampleSize > maxDim) inSampleSize *= 2
        return min(inSampleSize, 16)
    }
}
