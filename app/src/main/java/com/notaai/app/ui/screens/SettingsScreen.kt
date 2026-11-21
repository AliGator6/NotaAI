package com.notaai.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.notaai.app.data.preferences.UserSettings
import com.notaai.app.util.CsvExporter
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    onToggleEnhanceContrast: (Boolean) -> Unit,
    onToggleFlagLowConfidence: (Boolean) -> Unit,
    onExportAllCsv: (onReady: (File) -> Unit, onEmpty: () -> Unit) -> Unit,
    onDeleteAllHistory: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var showConfirmDeleteAll by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Pengaturan") }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Pembacaan Nota (OCR Offline)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Tingkatkan Kontras Foto", fontWeight = FontWeight.Medium)
                            Text("Membantu membaca tulisan pensil/bolpoin pudar.", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = settings.enhanceContrastForOcr, onCheckedChange = onToggleEnhanceContrast)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(end = 8.dp)) {
                            Text("Tandai Tulisan Meragukan", fontWeight = FontWeight.Medium)
                            Text("Memberi peringatan \"Perlu Diperiksa\" saat OCR ragu.", style = MaterialTheme.typography.bodySmall)
                        }
                        Switch(checked = settings.flagLowConfidenceOcr, onCheckedChange = onToggleFlagLowConfidence)
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Data & Ekspor Excel (CSV)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    OutlinedButton(onClick = {
                        onExportAllCsv({ CsvExporter.shareCsvFile(context, it) }, { Toast.makeText(context, "Belum ada riwayat nota.", Toast.LENGTH_SHORT).show() })
                    }, modifier = Modifier.fillMaxWidth()) { Text("Ekspor Semua Riwayat ke CSV") }
                    Button(onClick = { showConfirmDeleteAll = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) {
                        Text("Hapus Semua Riwayat")
                    }
                }
            }
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Privasi, Biaya & Keterbatasan OCR", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("• 100% Gratis & Offline: Tanpa iklan, tanpa langganan, tanpa server, dan tanpa izin internet.", style = MaterialTheme.typography.bodySmall)
                    Text("• Keterbatasan Tulisan Tangan: Tulisan sambung miring atau coretan tebal bisa salah terbaca. Gunakan Potong Nota, Perbesar Foto, dan Edit Manual agar 100% akurat.", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
    if (showConfirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { showConfirmDeleteAll = false },
            title = { Text("Hapus Semua Riwayat?") },
            text = { Text("Semua foto dan daftar nota yang tersimpan di HP akan dihapus.") },
            confirmButton = { TextButton(onClick = { onDeleteAllHistory(); showConfirmDeleteAll = false; Toast.makeText(context, "Semua riwayat dihapus", Toast.LENGTH_SHORT).show() }) { Text("Hapus Semua", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showConfirmDeleteAll = false }) { Text("Batal") } }
        )
    }
}
