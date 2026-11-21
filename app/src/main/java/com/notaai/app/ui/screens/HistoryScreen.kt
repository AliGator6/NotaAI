package com.notaai.app.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notaai.app.domain.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(receipts: List<Receipt>, onOpenReceipt: (Long) -> Unit, onDeleteReceipt: (Long) -> Unit, onBack: () -> Unit) {
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Riwayat Nota (${receipts.size})") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") }
        })
    }) { padding ->
        if (receipts.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("Belum ada riwayat nota tersimpan.") }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(receipts, key = { it.id }) { receipt ->
                    val s = ReceiptCalculator.summarize(receipt.items, receipt.writtenTotal)
                    val badge = when (s.matchStatus) {
                        TotalMatchStatus.MATCH -> "Cocok ✓" to Color(0xFF15803D)
                        TotalMatchStatus.MISMATCH -> "Selisih ${RupiahFormatter.formatSignedDifference(s.difference ?: 0L)}" to Color(0xFFDC2626)
                        TotalMatchStatus.UNVERIFIED_ITEMS -> "Perlu Diperiksa" to Color(0xFFB45309)
                        TotalMatchStatus.WAITING_INPUT -> "Belum Dicek Total Nota" to Color(0xFF0369A1)
                    }
                    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().clickable { onOpenReceipt(receipt.id) }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            val thumb = remember(receipt.imagePath) { File(receipt.imagePath).takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.absolutePath) } }
                            if (thumb != null) Image(thumb.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.size(60.dp).clip(RoundedCornerShape(10.dp)))
                            else Box(Modifier.size(60.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ReceiptLong, null) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(receipt.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                Text(dateFormat.format(Date(receipt.createdAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(RupiahFormatter.format(s.calculatedTotal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                Text(badge.first, style = MaterialTheme.typography.labelMedium, color = badge.second, fontWeight = FontWeight.SemiBold)
                            }
                            IconButton(onClick = { pendingDeleteId = receipt.id }) { Icon(Icons.Rounded.Delete, "Hapus", tint = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
        }
    }
    if (pendingDeleteId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Hapus Riwayat Nota?") },
            text = { Text("Foto nota dan hasil hitungannya akan dihapus permanen dari HP.") },
            confirmButton = { TextButton(onClick = { onDeleteReceipt(pendingDeleteId!!); pendingDeleteId = null }) { Text("Hapus", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("Batal") } }
        )
    }
}
