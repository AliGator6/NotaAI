package com.notaai.app.ocr

import com.notaai.app.domain.ReceiptCalculator
import com.notaai.app.domain.ReceiptItem
import kotlin.math.abs

data class ParsedReceiptResult(val items: List<ReceiptItem>, val detectedWrittenTotal: Long?)

object ReceiptParser {
    private val totalKeywords = listOf("grand total", "total belanja", "total bayar", "total", "jumlah", "jml", "subtotal", "sub total")
    private val nonItemKeywords = listOf("tunai", "cash", "kembali", "kembalian", "bayar", "uang", "terima kasih", "makasih", "tanggal", "tgl", "kasir", "no.", "nota", "bon", "struk", "rp", "harga")
    private val qtyPriceRegex = Regex("""^(.*?)\s+(\d+(?:[.,]\d+)?)\s*(kg|g|gr|gram|ons|l|ltr|liter|ml|pcs|pc|bh|buah|bks|bungkus|pak|pack|dus|box|btl|botol|kaleng|ikat|siung|ekor|meter|m|x)?\s*[xX×*]\s*(?:rp\.?\s*)?([0-9.,]+)(?:\s*=\s*(?:rp\.?\s*)?([0-9.,]+))?$""", RegexOption.IGNORE_CASE)
    private val simpleItemPriceRegex = Regex("""^(.+?)\s+(?:rp\.?\s*)?([0-9][0-9.,]{1,12})$""", RegexOption.IGNORE_CASE)
    private val ambiguousNumberTokenRegex = Regex("""[0-9]+[oOlIsSBb][0-9.,]*|[oOlI][0-9]{2,}""")

    fun parse(lines: List<OcrRecognizedLine>, flagLowConfidence: Boolean = true, lowConfidenceThreshold: Float = 0.72f): ParsedReceiptResult {
        val items = mutableListOf<ReceiptItem>()
        var detectedTotal: Long? = null
        lines.forEach { line ->
            val raw = line.text.replace(Regex("""\s+"""), " ").trim()
            if (raw.length < 2) return@forEach
            val lower = raw.lowercase()
            if (totalKeywords.any { kw -> lower.startsWith(kw) || Regex("""\b$kw\b""").containsMatchIn(lower) }) {
                Regex("""([0-9][0-9.,]{1,14})\s*$""").find(raw)?.let { m ->
                    parseIndonesianRupiahToken(m.groupValues[1])?.takeIf { it > 0L }?.let { detectedTotal = it }
                }
                return@forEach
            }
            if (nonItemKeywords.any { lower == it || lower.startsWith("$it ") || lower.startsWith("$it:") }) return@forEach
            val lowConf = flagLowConfidence && line.confidence != null && line.confidence < lowConfidenceThreshold
            val hasAmbig = ambiguousNumberTokenRegex.containsMatchIn(raw)
            val qtyMatch = qtyPriceRegex.find(raw)
            if (qtyMatch != null) {
                val name = cleanItemName(qtyMatch.groupValues[1])
                val qty = qtyMatch.groupValues[2].replace(',', '.').toDoubleOrNull()
                val unit = qtyMatch.groupValues[3].takeIf { it.isNotBlank() && !it.equals("x", true) }
                val unitPrice = parseIndonesianRupiahToken(qtyMatch.groupValues[4])
                val parsedTotal = qtyMatch.groupValues[5].takeIf { it.isNotBlank() }?.let { parseIndonesianRupiahToken(it) }
                if (name.isNotBlank() && qty != null && unitPrice != null) {
                    val computed = ReceiptCalculator.calculateLineTotal(qty, unitPrice)
                    val mismatch = parsedTotal != null && abs(computed - parsedTotal) > 1L
                    val small = unitPrice < 100L || (parsedTotal != null && parsedTotal < 100L)
                    val needsReview = lowConf || hasAmbig || mismatch || small
                    items.add(ReceiptItem(
                        sortOrder = items.size, name = name, quantity = qty, unit = unit, unitPrice = unitPrice,
                        lineTotal = parsedTotal ?: computed, isCrossedOut = looksCrossedOut(raw),
                        needsReview = needsReview, isVerifiedByUser = !needsReview,
                        ocrConfidenceNote = buildNote(lowConf, hasAmbig, if (mismatch) "Hasil qty × harga berbeda" else if (small) "Nominal terlalu kecil" else null)
                    ))
                    return@forEach
                }
            }
            val simpleMatch = simpleItemPriceRegex.find(raw)
            if (simpleMatch != null) {
                val name = cleanItemName(simpleMatch.groupValues[1])
                val price = parseIndonesianRupiahToken(simpleMatch.groupValues[2])
                if (name.any { it.isLetter() } && price != null) {
                    val small = price < 100L
                    val needsReview = lowConf || hasAmbig || small
                    items.add(ReceiptItem(
                        sortOrder = items.size, name = name, lineTotal = price,
                        isCrossedOut = looksCrossedOut(raw), needsReview = needsReview,
                        isVerifiedByUser = !needsReview,
                        ocrConfidenceNote = buildNote(lowConf, hasAmbig, if (small) "Nominal perlu diperiksa" else null)
                    ))
                    return@forEach
                }
            }
            if (raw.any { it.isLetter() } && (raw.any { it.isDigit() } || hasAmbig)) {
                val cleaned = cleanItemName(raw)
                if (cleaned.length >= 2) {
                    items.add(ReceiptItem(
                        sortOrder = items.size, name = cleaned, lineTotal = 0L,
                        isCrossedOut = looksCrossedOut(raw), needsReview = true, isVerifiedByUser = false,
                        ocrConfidenceNote = "Harga tidak terbaca jelas — isi manual"
                    ))
                }
            }
        }
        return ParsedReceiptResult(items, detectedTotal)
    }

    internal fun parseIndonesianRupiahToken(token: String): Long? {
        val trimmed = token.trim().trimEnd('.', ',')
        if (trimmed.isEmpty()) return null
        val noDec = if (Regex(""",\d{2}$""").containsMatchIn(trimmed)) trimmed.substringBeforeLast(',')
        else if (Regex("""\.\d{2}$""").containsMatchIn(trimmed) && trimmed.count { it == '.' } == 1 && trimmed.length <= 8) trimmed.substringBeforeLast('.')
        else trimmed
        val digits = noDec.filter { it.isDigit() }
        return if (digits.isEmpty()) null else digits.toLongOrNull()
    }

    private fun looksCrossedOut(raw: String) = raw.contains("~~") || raw.contains("--") || raw.contains("[coret]", true)
    private fun cleanItemName(raw: String) = raw.trim().removePrefix("-").removePrefix("•").removePrefix("*").replace("~~", "").trim().trimEnd(':', '-', '=').trim()
    private fun buildNote(low: Boolean, ambig: Boolean, extra: String?): String? {
        val p = mutableListOf<String>()
        if (low) p.add("Keyakinan OCR rendah")
        if (ambig) p.add("Karakter angka mirip huruf (O/l/S)")
        if (extra != null) p.add(extra)
        return p.takeIf { it.isNotEmpty() }?.joinToString(" • ")
    }
}
