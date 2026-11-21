package com.notaai.app.ocr

import android.content.Context
import android.graphics.*
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlin.math.max
import kotlin.math.min

object BitmapProcessor {
    fun loadNormalizedBitmap(context: Context, uri: Uri, maxDimension: Int = 2200): Bitmap? {
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

    fun enhanceContrastForOcr(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        val grayscale = ColorMatrix().apply { setSaturation(0f) }
        val contrast = 1.45f
        val translate = (-0.5f * contrast + 0.5f) * 255f
        grayscale.postConcat(ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )))
        paint.colorFilter = ColorMatrixColorFilter(grayscale)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return output
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        val longest = max(width, height)
        while (longest / inSampleSize > maxDim) inSampleSize *= 2
        return min(inSampleSize, 16)
    }
}
