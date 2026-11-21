package com.notaai.app.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

class ImageStorageManager(private val context: Context) {
    private val receiptsDir: File get() = File(context.filesDir, "receipts").apply { if (!exists()) mkdirs() }
    private val tempDir: File get() = File(context.cacheDir, "temp_images").apply { if (!exists()) mkdirs() }

    fun createTempCameraFile(): File = File(tempDir, "cam_${System.currentTimeMillis()}.jpg")

    fun savePermanentReceiptBitmap(bitmap: Bitmap): String {
        val outFile = File(receiptsDir, "nota_${System.currentTimeMillis()}.jpg")
        FileOutputStream(outFile).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        return outFile.absolutePath
    }

    fun copyUriToTempFile(uri: Uri): File? {
        val outFile = File(tempDir, "pick_${System.currentTimeMillis()}.jpg")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
            outFile
        } catch (_: Exception) { null }
    }

    fun deleteFileIfExists(path: String) {
        val file = File(path)
        if (file.exists()) file.delete()
    }
}
