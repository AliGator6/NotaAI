package com.notaai.app.domain

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import kotlin.math.abs

object RupiahFormatter {
    private val indonesianSymbols = DecimalFormatSymbols().apply {
        groupingSeparator = '.'
        decimalSeparator = ','
    }
    private val integerFormatter = DecimalFormat("#,##0", indonesianSymbols)

    fun format(amount: Long): String {
        val prefix = if (amount < 0L) "-Rp " else "Rp "
        return prefix + integerFormatter.format(abs(amount))
    }

    fun formatSignedDifference(difference: Long): String = when {
        difference > 0L -> "+${format(difference)}"
        difference < 0L -> format(difference)
        else -> "Rp 0"
    }

    fun formatQuantity(quantity: Double): String {
        return if (quantity % 1.0 == 0.0) quantity.toLong().toString()
        else DecimalFormat("0.##", indonesianSymbols).format(quantity)
    }

    fun parseRupiahInput(raw: String): Long? {
        val digitsOnly = raw.filter { it.isDigit() }
        return if (digitsOnly.isEmpty()) null else digitsOnly.toLongOrNull()
    }
}
