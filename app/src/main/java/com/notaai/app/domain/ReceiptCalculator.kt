package com.notaai.app.domain

import java.math.BigDecimal
import java.math.RoundingMode

object ReceiptCalculator {
    fun calculateLineTotal(quantity: Double, unitPrice: Long): Long {
        if (quantity <= 0.0 || unitPrice <= 0L) return 0L
        return BigDecimal.valueOf(quantity)
            .multiply(BigDecimal.valueOf(unitPrice))
            .setScale(0, RoundingMode.HALF_UP)
            .toLong()
    }

    fun calculateActiveTotal(items: List<ReceiptItem>): Long {
        var total = 0L
        for (item in items) {
            if (!item.isCrossedOut) total += item.lineTotal
        }
        return total
    }

    fun summarize(items: List<ReceiptItem>, writtenTotal: Long?): ReceiptSummary {
        val activeItems = items.filter { !it.isCrossedOut }
        val crossedOutCount = items.count { it.isCrossedOut }
        val unverifiedActiveCount = activeItems.count { it.needsReview && !it.isVerifiedByUser }
        val calculatedTotal = calculateActiveTotal(items)
        val difference = writtenTotal?.let { calculatedTotal - it }
        val matchStatus = when {
            unverifiedActiveCount > 0 -> TotalMatchStatus.UNVERIFIED_ITEMS
            writtenTotal == null -> TotalMatchStatus.WAITING_INPUT
            difference == 0L -> TotalMatchStatus.MATCH
            else -> TotalMatchStatus.MISMATCH
        }
        return ReceiptSummary(
            activeItemCount = activeItems.size,
            crossedOutItemCount = crossedOutCount,
            unverifiedActiveCount = unverifiedActiveCount,
            calculatedTotal = calculatedTotal,
            writtenTotal = writtenTotal,
            difference = difference,
            matchStatus = matchStatus
        )
    }
}
