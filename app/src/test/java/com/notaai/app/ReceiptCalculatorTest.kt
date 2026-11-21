package com.notaai.app

import com.notaai.app.domain.*
import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptCalculatorTest {
    @Test fun `penjumlahan harga item aktif berhasil`() {
        val items = listOf(ReceiptItem(name = "Beras", lineTotal = 75_000L), ReceiptItem(name = "Gula", lineTotal = 28_000L), ReceiptItem(name = "Minyak", lineTotal = 45_000L))
        assertEquals(148_000L, ReceiptCalculator.calculateActiveTotal(items))
    }
    @Test fun `perhitungan qty kali harga satuan akurat`() {
        assertEquals(75_000L, ReceiptCalculator.calculateLineTotal(5.0, 15_000L))
        assertEquals(22_500L, ReceiptCalculator.calculateLineTotal(1.5, 15_000L))
        assertEquals(3_000L, ReceiptCalculator.calculateLineTotal(0.1, 30_000L))
    }
    @Test fun `item dicoret tidak dihitung dan aktifkan kembali dihitung`() {
        val list = listOf(ReceiptItem(name = "Beras", lineTotal = 75_000L), ReceiptItem(name = "Gula", lineTotal = 28_000L, isCrossedOut = true))
        assertEquals(75_000L, ReceiptCalculator.calculateActiveTotal(list))
        assertEquals(103_000L, ReceiptCalculator.calculateActiveTotal(list.map { it.copy(isCrossedOut = false) }))
    }
    @Test fun `edit dan hapus item memperbarui total`() {
        val items = mutableListOf(ReceiptItem(name = "Beras", lineTotal = 75_000L), ReceiptItem(name = "Gula", lineTotal = 28_000L))
        items[1] = items[1].copy(lineTotal = 30_000L)
        assertEquals(105_000L, ReceiptCalculator.calculateActiveTotal(items))
        items.removeAt(0)
        assertEquals(30_000L, ReceiptCalculator.calculateActiveTotal(items))
    }
    @Test fun `perhitungan selisih dan status verifikasi`() {
        val ok = listOf(ReceiptItem(name = "Beras", lineTotal = 75_000L, isVerifiedByUser = true))
        assertEquals(TotalMatchStatus.MATCH, ReceiptCalculator.summarize(ok, 75_000L).matchStatus)
        assertEquals(-5_000L, ReceiptCalculator.summarize(ok, 80_000L).difference)
        val unverified = ok + ReceiptItem(name = "Gula", lineTotal = 28_000L, needsReview = true, isVerifiedByUser = false)
        assertEquals(TotalMatchStatus.UNVERIFIED_ITEMS, ReceiptCalculator.summarize(unverified, 103_000L).matchStatus)
    }
}
