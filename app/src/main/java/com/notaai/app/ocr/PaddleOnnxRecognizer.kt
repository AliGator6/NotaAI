package com.notaai.app.ocr

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import java.nio.FloatBuffer
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

data class PaddleLineResult(val text: String, val confidence: Float)

/**
 * Mesin pengenalan teks tulisan tangan offline memakai PaddleOCR v5 (ONNX Runtime).
 * 100% lokal di HP tanpa koneksi internet.
 */
class PaddleOnnxRecognizer(private val context: Context) {
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var dict: List<String> = emptyList()
    private var initialized = false

    @Synchronized
    private fun ensureInitialized(): Boolean {
        if (initialized) return session != null
        initialized = true
        return try {
            val modelBytes = context.assets.open("ppocr_rec.onnx").use { it.readBytes() }
            val dictLines = context.assets.open("ppocr_dict.txt").bufferedReader(Charsets.UTF_8).use { br ->
                br.readLines().map { it.trimEnd('\r', '\n') }
            }
            val ortEnv = OrtEnvironment.getEnvironment()
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
            }
            env = ortEnv
            session = ortEnv.createSession(modelBytes, opts)
            dict = dictLines
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun isAvailable(): Boolean = ensureInitialized()

    fun recognizeCrop(crop: Bitmap): PaddleLineResult? {
        if (!ensureInitialized()) return null
        val ortEnv = env ?: return null
        val ortSession = session ?: return null

        val inputInfo = ortSession.inputInfo.values.firstOrNull()?.info as? TensorInfo ?: return null
        val imgH = if (inputInfo.shape.size >= 4 && inputInfo.shape[2] > 0) inputInfo.shape[2].toInt() else 48
        val fixedW = if (inputInfo.shape.size >= 4 && inputInfo.shape[3] > 0) inputInfo.shape[3].toInt() else -1
        val maxW = if (fixedW > 0) fixedW else 320

        val ratio = crop.width.toFloat() / crop.height.coerceAtLeast(1).toFloat()
        val resizedW = min(ceil(imgH * ratio).toInt().coerceAtLeast(8), maxW)
        val targetW = if (fixedW > 0) fixedW else max(320, ((resizedW + 31) / 32) * 32)

        val resized = Bitmap.createScaledBitmap(crop, resizedW, imgH, true)
        val pixels = IntArray(resizedW * imgH)
        resized.getPixels(pixels, 0, resizedW, 0, 0, resizedW, imgH)
        if (resized !== crop) resized.recycle()

        // PaddleOCR RecResizeImg: [1, 3, H, W], (x/255 - 0.5)/0.5, area padding = 0.0f
        val channelSize = imgH * targetW
        val tensorData = FloatArray(3 * channelSize)
        for (y in 0 until imgH) {
            val srcRow = y * resizedW
            val dstRow = y * targetW
            for (x in 0 until resizedW) {
                val c = pixels[srcRow + x]
                val r = (((c shr 16) and 0xFF) / 255f - 0.5f) / 0.5f
                val g = (((c shr 8) and 0xFF) / 255f - 0.5f) / 0.5f
                val b = ((c and 0xFF) / 255f - 0.5f) / 0.5f
                tensorData[dstRow + x] = b
                tensorData[channelSize + dstRow + x] = g
                tensorData[2 * channelSize + dstRow + x] = r
            }
        }

        val inputName = ortSession.inputNames.first()
        OnnxTensor.createTensor(ortEnv, FloatBuffer.wrap(tensorData), longArrayOf(1L, 3L, imgH.toLong(), targetW.toLong())).use { inputTensor ->
            ortSession.run(mapOf(inputName to inputTensor)).use { result ->
                val outTensor = result[0] as? OnnxTensor ?: return null
                val outShape = outTensor.info.shape
                if (outShape.size < 3) return null
                val seqLen = outShape[1].toInt()
                val numClasses = outShape[2].toInt()
                val fb = outTensor.floatBuffer
                val rawOut = FloatArray(seqLen * numClasses)
                fb.get(rawOut)
                return decodeCtc(rawOut, seqLen, numClasses)
            }
        }
    }

    private fun decodeCtc(data: FloatArray, seqLen: Int, numClasses: Int): PaddleLineResult {
        val sb = StringBuilder()
        var prevIdx = 0
        var confSum = 0f
        var charCount = 0

        for (t in 0 until seqLen) {
            val base = t * numClasses
            var maxIdx = 0
            var maxVal = data[base]
            var minVal = maxVal
            var sumVal = 0f
            for (c in 0 until numClasses) {
                val v = data[base + c]
                sumVal += v
                if (v > maxVal) { maxVal = v; maxIdx = c }
                if (v < minVal) minVal = v
            }
            val prob = if (minVal >= 0f && sumVal in 0.97f..1.03f) {
                maxVal
            } else {
                var expSum = 0f
                for (c in 0 until numClasses) expSum += exp((data[base + c] - maxVal).toDouble()).toFloat()
                1f / max(expSum, 1e-6f)
            }

            if (maxIdx != 0 && maxIdx != prevIdx) {
                val token = when {
                    maxIdx - 1 in dict.indices -> dict[maxIdx - 1]
                    maxIdx == dict.size + 1 -> " "
                    else -> ""
                }
                if (token.isNotEmpty()) {
                    sb.append(token)
                    confSum += prob
                    charCount++
                }
            }
            prevIdx = maxIdx
        }

        val text = sb.toString().trim()
        val avgConf = if (charCount > 0) confSum / charCount else 0f
        return PaddleLineResult(text, avgConf)
    }
}
