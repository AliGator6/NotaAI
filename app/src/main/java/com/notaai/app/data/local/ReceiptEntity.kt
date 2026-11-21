package com.notaai.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "receipts")
data class ReceiptEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val createdAt: Long,
    val updatedAt: Long,
    val title: String,
    val imagePath: String,
    val writtenTotal: Long?,
    val calculatedTotal: Long,
    val rawOcrText: String,
    val isVerified: Boolean
)
