package com.notaai.app.data.local

import androidx.room.Embedded
import androidx.room.Relation
import com.notaai.app.domain.Receipt
import com.notaai.app.domain.ReceiptItem

data class ReceiptWithItems(
    @Embedded val receipt: ReceiptEntity,
    @Relation(parentColumn = "id", entityColumn = "receiptId")
    val items: List<ReceiptItemEntity>
) {
    fun toDomain(): Receipt = Receipt(
        id = receipt.id,
        createdAt = receipt.createdAt,
        updatedAt = receipt.updatedAt,
        title = receipt.title,
        imagePath = receipt.imagePath,
        writtenTotal = receipt.writtenTotal,
        calculatedTotal = receipt.calculatedTotal,
        rawOcrText = receipt.rawOcrText,
        isVerified = receipt.isVerified,
        items = items.sortedBy { it.sortOrder }.map {
            ReceiptItem(
                id = it.id, receiptId = it.receiptId, sortOrder = it.sortOrder,
                name = it.name, quantity = it.quantity, unit = it.unit,
                unitPrice = it.unitPrice, lineTotal = it.lineTotal,
                isCrossedOut = it.isCrossedOut, needsReview = it.needsReview,
                isVerifiedByUser = it.isVerifiedByUser, ocrConfidenceNote = it.ocrConfidenceNote
            )
        }
    )
}
