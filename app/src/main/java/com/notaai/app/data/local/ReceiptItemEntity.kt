package com.notaai.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "receipt_items",
    foreignKeys = [ForeignKey(entity = ReceiptEntity::class, parentColumns = ["id"], childColumns = ["receiptId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("receiptId")]
)
data class ReceiptItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val receiptId: Long,
    val sortOrder: Int,
    val name: String,
    val quantity: Double?,
    val unit: String?,
    val unitPrice: Long?,
    val lineTotal: Long,
    val isCrossedOut: Boolean,
    val needsReview: Boolean,
    val isVerifiedByUser: Boolean,
    val ocrConfidenceNote: String?
)
