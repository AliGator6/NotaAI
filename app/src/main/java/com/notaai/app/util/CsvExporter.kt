package com.notaai.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.notaai.app.domain.Receipt
import com.notaai.app.domain.ReceiptCalculator
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale("id", "ID"))

    fun exportSingleReceiptToCsv(context: Context, receipt: Receipt): File {
        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val file = File(exportDir, "nota_${receipt.id}_${System.currentTimeMillis()}.csv")
        val summary = ReceiptCalculator.summarize(receipt.items, receipt.writtenTotal)
        val sb = StringBuilder().append('\uFEFF')
        sb.appendLine("Judul Nota,Tanggal,No,Nama Barang,Kuantitas,Satuan,Harga Satuan (Rp),Total Baris (Rp),Status Item,Total Hitungan (Rp),Total Tertulis Nota (Rp),Selisih (Rp)")
        val dateStr = dateFormat.format(Date(receipt.createdAt))
        val writtenStr = summary.writtenTotal?.toString() ?: ""
        val diffStr = summary.difference?.toString() ?: ""
        if (receipt.items.isEmpty()) {
            sb.appendLine(listOf(csvEscape(receipt.title), csvEscape(dateStr), "0", csvEscape("(Tidak ada item)"), "", "", "", "0", "", summary.calculatedTotal.toString(), writtenStr, diffStr).joinToString(","))
        } else {
            receipt.items.forEachIndexed { i, item ->
                sb.appendLine(listOf(
                    csvEscape(receipt.title), csvEscape(dateStr), (i + 1).toString(), csvEscape(item.name),
                    item.quantity?.toString() ?: "", csvEscape(item.unit ?: ""), item.unitPrice?.toString() ?: "",
                    item.lineTotal.toString(), if (item.isCrossedOut) "Dicoret" else "Aktif",
                    summary.calculatedTotal.toString(), writtenStr, diffStr
                ).joinToString(","))
            }
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    fun exportAllReceiptsToCsv(context: Context, receipts: List<Receipt>): File {
        val exportDir = File(context.cacheDir, "exports").apply { if (!exists()) mkdirs() }
        val file = File(exportDir, "riwayat_nota_${System.currentTimeMillis()}.csv")
        val sb = StringBuilder().append('\uFEFF')
        sb.appendLine("ID Nota,Tanggal,Judul Nota,Jumlah Item Aktif,Total Hitungan (Rp),Total Tertulis (Rp),Selisih (Rp),Status")
        receipts.forEach { receipt ->
            val s = ReceiptCalculator.summarize(receipt.items, receipt.writtenTotal)
            val status = when {
                s.unverifiedActiveCount > 0 -> "Belum Terverifikasi"
                s.writtenTotal == null -> "Total Nota Kosong"
                s.difference == 0L -> "Cocok"
                else -> "Tidak Cocok"
            }
            sb.appendLine(listOf(
                receipt.id.toString(), csvEscape(dateFormat.format(Date(receipt.createdAt))),
                csvEscape(receipt.title), s.activeItemCount.toString(), s.calculatedTotal.toString(),
                s.writtenTotal?.toString() ?: "", s.difference?.toString() ?: "", csvEscape(status)
            ).joinToString(","))
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    fun shareCsvFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Ekspor / Buka CSV dengan..."))
    }

    private fun csvEscape(v: String) = "\"${v.replace("\"", "\"\"")}\""
}
