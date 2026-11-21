package com.notaai.app.data.repository

import com.notaai.app.data.local.*
import com.notaai.app.domain.Receipt
import com.notaai.app.domain.ReceiptCalculator
import com.notaai.app.util.ImageStorageManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ReceiptRepository(
    private val dao: ReceiptDao,
    private val imageStorage: ImageStorageManager
) {
    fun observeAllReceipts(): Flow<List<Receipt>> =
        dao.observeAllReceipts().map { list -> list.map { it.toDomain() } }

    suspend fun getReceipt(id: Long): Receipt? = dao.getReceiptById(id)?.toDomain()

    suspend fun getAllReceiptsOnce(): List<Receipt> = dao.getAllReceiptsOnce().map { it.toDomain() }

    suspend fun saveReceipt(receipt: Receipt): Long {
        val summary = ReceiptCalculator.summarize(receipt.items, receipt.writtenTotal)
        val now = System.currentTimeMillis()
        val entity = ReceiptEntity(
            id = receipt.id,
            createdAt = if (receipt.id == 0L) now else receipt.createdAt,
            updatedAt = now,
            title = receipt.title.ifBlank { "Nota Belanja" },
            imagePath = receipt.imagePath,
            writtenTotal = receipt.writtenTotal,
            calculatedTotal = summary.calculatedTotal,
            rawOcrText = receipt.rawOcrText,
            isVerified = summary.unverifiedActiveCount == 0 && summary.writtenTotal != null && summary.difference == 0L
        )
        val itemEntities = receipt.items.mapIndexed { idx, item ->
            ReceiptItemEntity(
                id = item.id, receiptId = receipt.id, sortOrder = idx, name = item.name,
                quantity = item.quantity, unit = item.unit, unitPrice = item.unitPrice,
                lineTotal = item.lineTotal, isCrossedOut = item.isCrossedOut,
                needsReview = item.needsReview, isVerifiedByUser = item.isVerifiedByUser,
                ocrConfidenceNote = item.ocrConfidenceNote
            )
        }
        return dao.saveReceiptWithItems(entity, itemEntities)
    }

    suspend fun deleteReceipt(receiptId: Long) {
        dao.getReceiptById(receiptId)?.let {
            if (it.receipt.imagePath.isNotBlank()) imageStorage.deleteFileIfExists(it.receipt.imagePath)
        }
        dao.deleteReceiptById(receiptId)
    }

    suspend fun deleteAllReceipts() {
        dao.getAllReceiptsOnce().forEach {
            if (it.receipt.imagePath.isNotBlank()) imageStorage.deleteFileIfExists(it.receipt.imagePath)
        }
        dao.deleteAllReceipts()
    }
}
