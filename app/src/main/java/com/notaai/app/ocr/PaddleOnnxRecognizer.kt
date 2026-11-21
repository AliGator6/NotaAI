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

class PaddleOnnxRecognizer(private val context: Context) {
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var dict: List<String> = emptyList()
    private var numericAllowedIndices: BooleanArray? = null
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
            env = ortEnv
            session = ortEnv.createSession(modelBytes, OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) })
            dict = dictLines
            buildNumericMask(dictLines)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun buildNumericMask(dictLines: List<String>) {
        val allowedChars = setOf('0','1','2','3','4','5','6','7','8','9','.',',','-','/','x','X','=',' ')
        val mask = BooleanArray(dictLines.size + 3)
        mask[0] = true // CTC blank selalu diizinkan
        dictLines.forEachIndexed { idx, tok ->
            if (tok.length == 1 && tok[0] in allowedChars) mask[idx + 1] = true
        }
        if (dictLines.size + 1 < mask.size) mask[dictLines.size + 1] = true // spasi akhir
        numericAllowedIndices = mask
    }

    fun isAvailable(): Boolean = ensureInitialized()

    fun recognizeCrop(crop: Bitmap, preferDigits: Boolean = false): PaddleLineResult? {
        if (!ensureInitialized()) return null
        val ortEnv = env ?: return null
        val ortSession = session ?: return null

        val inputInfo = ortSession.inputInfo.values.firstOrNull()?.info as? TensorInfo ?: return null
        val imgH = if (inputInfo.shape.size >= 4 && inputInfo.shape[2] > 0) inputInfo.shape[2].toInt() else 48
        val fixedW = if (inputInfo.shape.size >= 4 && inputInfo.shape[3] > 0) inputInfo.shape[3].toInt() else -1
        val maxW = if (fixedW > 0) fixedW else 960

        val ratio = crop.width.toFloat() / crop.height.coerceAtLeast(1).toFloat()
        val resizedW = min(ceil(imgH * ratio).toInt().coerceAtLeast(8), maxW)
        val targetW = if (fixedW > 0) fixedW else max(320, ((resizedW + 31) / 32) * 32)

        val resized = Bitmap.createScaledBitmap(crop, resizedW, imgH, true)
        val pixels = IntArray(resizedW * imgH)
        resized.getPixels(pixels, 0, resizedW, 0, 0, resizedW, imgH)
        if (resized !== crop) resized.recycle()

        val channelSize = imgH * targetW
        val tensorData = FloatArray(3 * channelSize)
        for (y in 0 until imgH) {
            val srcRow = y * resizedW
            val dstRow = y * targetW
            for (x in 0 until resizedW) {
                val c = pixels[srcRow + x]
                tensorData[dstRow + x] = ((c and 0xFF) / 255f - 0.5f) / 0.5f
                tensorData[channelSize + dstRow + x] = (((c shr 8) and 0xFF) / 255f - 0.5f) / 0.5f
                tensorData[2 * channelSize + dstRow + x] = (((c shr 16) and 0xFF) / 255f - 0.5f) / 0.5f
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
                val rawOut = FloatArray(seqLen * numClasses)
                outTensor.floatBuffer.get(rawOut)

                val normal = decodeCtc(rawOut, seqLen, numClasses, numericOnly = false)
                if (!preferDigits) return normal

                val looksLikePrice = normal.text.count { it.isDigit() } >= 2 &&
                    !Regex("""[a-zA-Z]{4,}""").containsMatchIn(normal.text)
                if (!looksLikePrice) return normal

                val constrained = decodeCtc(rawOut, seqLen, numClasses, numericOnly = true)
                return if (constrained.text.count { it.isDigit() } >= normal.text.count { it.isDigit() } &&
                    constrained.text.isNotBlank() && constrained.confidence >= 0.45f) {
                    constrained
                } else normal
            }
        }
    }

    private fun decodeCtc(data: FloatArray, seqLen: Int, numClasses: Int, numericOnly: Boolean): PaddleLineResult {
        val sb = StringBuilder()
        var prevIdx = 0
        var confSum = 0f
        var charCount = 0
        val mask = if (numericOnly) numericAllowedIndices else null

        for (t in 0 until seqLen) {
            val base = t * numClasses
            var rawMax = data[base]
            var rawMin = rawMax
            var rawSum = 0f
            for (c in 0 until numClasses) {
                val v = data[base + c]
                rawSum += v
                if (v > rawMax) rawMax = v
                if (v < rawMin) rawMin = v
            }
            val alreadyProb = rawMin >= 0f && rawSum in 0.97f..1.03f
            val expDenom = if (alreadyProb) 1f else {
                var s = 0f
                for (c in 0 until numClasses) s += exp((data[base + c] - rawMax).toDouble()).toFloat()
                max(s, 1e-6f)
            }

            var bestIdx = 0
            var bestScore = Float.NEGATIVE_INFINITY
            for (c in 0 until numClasses) {
                if (mask != null && c < mask.size && !mask[c]) continue
                if (data[base + c] > bestScore) { bestScore = data[base + c]; bestIdx = c }
            }

            val prob = if (alreadyProb) bestScore else exp((bestScore - rawMax).toDouble()).toFloat() / expDenom
            if (bestIdx != 0 && bestIdx != prevIdx) {
                val token = when {
                    bestIdx - 1 in dict.indices -> dict[bestIdx - 1]
                    bestIdx == dict.size + 1 -> " "
                    else -> ""
                }
                if (token.isNotEmpty()) {
                    sb.append(token)
                    confSum += prob
                    charCount++
                }
            }
            prevIdx = bestIdx
        }
        return PaddleLineResult(sb.toString().trim(), if (charCount > 0) confSum / charCount else 0f)
    }
}
