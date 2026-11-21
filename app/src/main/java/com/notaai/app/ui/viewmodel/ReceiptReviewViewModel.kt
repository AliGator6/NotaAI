package com.notaai.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.notaai.app.NotaApplication
import com.notaai.app.domain.*
import com.notaai.app.util.CsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ReceiptReviewUiState(
    val isLoading: Boolean = true,
    val receipt: Receipt? = null,
    val writtenTotalInput: String = "",
    val summary: ReceiptSummary? = null,
    val isSavedFeedbackVisible: Boolean = false
)

class ReceiptReviewViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as NotaApplication
    private val repository = app.repository
    private val _uiState = MutableStateFlow(ReceiptReviewUiState())
    val uiState: StateFlow<ReceiptReviewUiState> = _uiState.asStateFlow()

    fun loadReceipt(receiptId: Long) {
        viewModelScope.launch {
            val loaded = repository.getReceipt(receiptId)
            _uiState.value = if (loaded != null) {
                ReceiptReviewUiState(false, loaded, loaded.writtenTotal?.toString() ?: "", ReceiptCalculator.summarize(loaded.items, loaded.writtenTotal))
            } else ReceiptReviewUiState(isLoading = false, receipt = null)
        }
    }

    fun updateTitle(newTitle: String) {
        val cur = _uiState.value.receipt ?: return
        updateAndPersist(cur.copy(title = newTitle))
    }

    fun updateWrittenTotalInput(raw: String) {
        val digits = raw.filter { it.isDigit() }
        val parsed = RupiahFormatter.parseRupiahInput(digits)
        val cur = _uiState.value.receipt ?: return
        val updated = cur.copy(writtenTotal = parsed)
        _uiState.value = _uiState.value.copy(writtenTotalInput = digits, receipt = updated, summary = ReceiptCalculator.summarize(updated.items, updated.writtenTotal))
        viewModelScope.launch { repository.saveReceipt(updated) }
    }

    fun addOrUpdateItem(existingIndex: Int?, name: String, quantity: Double?, unit: String?, unitPrice: Long?, manualLineTotal: Long) {
        val cur = _uiState.value.receipt ?: return
        val computedTotal = if (quantity != null && unitPrice != null && quantity > 0.0 && unitPrice > 0L) ReceiptCalculator.calculateLineTotal(quantity, unitPrice) else manualLineTotal
        val mutable = cur.items.toMutableList()
        if (existingIndex == null) {
            mutable.add(ReceiptItem(receiptId = cur.id, sortOrder = mutable.size, name = name.trim(), quantity = quantity, unit = unit?.trim()?.takeIf { it.isNotEmpty() }, unitPrice = unitPrice, lineTotal = computedTotal, isCrossedOut = false, needsReview = false, isVerifiedByUser = true))
        } else if (existingIndex in mutable.indices) {
            mutable[existingIndex] = mutable[existingIndex].copy(name = name.trim(), quantity = quantity, unit = unit?.trim()?.takeIf { it.isNotEmpty() }, unitPrice = unitPrice, lineTotal = computedTotal, needsReview = false, isVerifiedByUser = true, ocrConfidenceNote = null)
        }
        updateAndPersist(cur.copy(items = mutable))
    }

    fun toggleCrossedOut(index: Int) {
        val cur = _uiState.value.receipt ?: return
        if (index !in cur.items.indices) return
        val mutable = cur.items.toMutableList()
        mutable[index] = mutable[index].copy(isCrossedOut = !mutable[index].isCrossedOut)
        updateAndPersist(cur.copy(items = mutable))
    }

    fun markItemVerified(index: Int) {
        val cur = _uiState.value.receipt ?: return
        if (index !in cur.items.indices) return
        val mutable = cur.items.toMutableList()
        mutable[index] = mutable[index].copy(needsReview = false, isVerifiedByUser = true, ocrConfidenceNote = null)
        updateAndPersist(cur.copy(items = mutable))
    }

    fun deleteItem(index: Int) {
        val cur = _uiState.value.receipt ?: return
        if (index !in cur.items.indices) return
        val mutable = cur.items.toMutableList()
        mutable.removeAt(index)
        updateAndPersist(cur.copy(items = mutable))
    }

    fun saveExplicitly() {
        val cur = _uiState.value.receipt ?: return
        viewModelScope.launch {
            repository.saveReceipt(cur)
            _uiState.value = _uiState.value.copy(isSavedFeedbackVisible = true)
        }
    }

    fun clearSavedFeedback() { _uiState.value = _uiState.value.copy(isSavedFeedbackVisible = false) }

    fun exportCsv(onReady: (File) -> Unit) {
        val cur = _uiState.value.receipt ?: return
        viewModelScope.launch { onReady(withContext(Dispatchers.IO) { CsvExporter.exportSingleReceiptToCsv(app, cur) }) }
    }

    private fun updateAndPersist(updated: Receipt) {
        val summary = ReceiptCalculator.summarize(updated.items, updated.writtenTotal)
        val recalculated = updated.copy(calculatedTotal = summary.calculatedTotal)
        _uiState.value = _uiState.value.copy(receipt = recalculated, summary = summary)
        viewModelScope.launch { repository.saveReceipt(recalculated) }
    }
}
