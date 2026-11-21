package com.notaai.app.domain

data class ReceiptItem(
    val id: Long = 0L,
    val receiptId: Long = 0L,
    val sortOrder: Int = 0,
    val name: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val unitPrice: Long? = null,
    val lineTotal: Long,
    val isCrossedOut: Boolean = false,
    val needsReview: Boolean = false,
    val isVerifiedByUser: Boolean = false,
    val ocrConfidenceNote: String? = null
)

data class Receipt(
    val id: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val title: String = "Nota Belanja",
    val imagePath: String = "",
    val writtenTotal: Long? = null,
    val calculatedTotal: Long = 0L,
    val rawOcrText: String = "",
    val isVerified: Boolean = false,
    val items: List<ReceiptItem> = emptyList()
)

enum class TotalMatchStatus {
    MATCH, MISMATCH, UNVERIFIED_ITEMS, WAITING_INPUT
}

data class ReceiptSummary(
    val activeItemCount: Int,
    val crossedOutItemCount: Int,
    val unverifiedActiveCount: Int,
    val calculatedTotal: Long,
    val writtenTotal: Long?,
    val difference: Long?,
    val matchStatus: TotalMatchStatus
)
