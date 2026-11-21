package com.notaai.app.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notaai.app.NotaApplication
import com.notaai.app.data.preferences.UserSettings
import com.notaai.app.domain.Receipt
import com.notaai.app.domain.ReceiptCalculator
import com.notaai.app.ocr.ReceiptParser
import com.notaai.app.util.CsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NotaApplication
    private val repository = app.repository
    private val preferences = app.preferences

    val receipts: StateFlow<List<Receipt>> = repository.observeAllReceipts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val settings: StateFlow<UserSettings> = preferences.settings

    fun setEnhanceContrast(enabled: Boolean) = preferences.setEnhanceContrast(enabled)
    fun setFlagLowConfidence(enabled: Boolean) = preferences.setFlagLowConfidence(enabled)
    fun deleteReceipt(id: Long) { viewModelScope.launch { repository.deleteReceipt(id) } }
    fun deleteAllReceipts() { viewModelScope.launch { repository.deleteAllReceipts() } }

    fun exportAllReceiptsCsv(onReady: (File) -> Unit, onEmpty: () -> Unit) {
        viewModelScope.launch {
            val all = withContext(Dispatchers.IO) { repository.getAllReceiptsOnce() }
            if (all.isEmpty()) onEmpty()
            else onReady(withContext(Dispatchers.IO) { CsvExporter.exportAllReceiptsToCsv(app, all) })
        }
    }

    suspend fun processCroppedBitmapAndCreateReceipt(croppedBitmap: Bitmap): Long = withContext(Dispatchers.IO) {
        val savedPath = app.imageStorage.savePermanentReceiptBitmap(croppedBitmap)
        val cur = preferences.settings.value
        val ocr = app.ocrEngine.recognize(croppedBitmap, cur.enhanceContrastForOcr)
        val parsed = ReceiptParser.parse(ocr.lines, cur.flagLowConfidenceOcr)
        val calc = ReceiptCalculator.calculateActiveTotal(parsed.items)
        repository.saveReceipt(Receipt(
            title = "Nota Belanja", imagePath = savedPath,
            writtenTotal = parsed.detectedWrittenTotal, calculatedTotal = calc,
            rawOcrText = ocr.rawText, items = parsed.items
        ))
    }
}
