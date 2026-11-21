package com.notaai.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    totalSavedCount: Int,
    onTakePhotoClick: () -> Unit,
    onPickGalleryClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Column {
                    Text("NotaAI", fontWeight = FontWeight.Bold)
                    Text("Cek Hitungan Nota • 100% Offline", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            })
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.VerifiedUser, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Gratis & Aman di HP Anda", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("Foto dan hitungan nota tidak pernah dikirim ke internet. Tersimpan: $totalSavedCount nota.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Button(onClick = onTakePhotoClick, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(74.dp)) {
                    Icon(Icons.Rounded.CameraAlt, null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("1. Foto Nota", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Potret nota belanja langsung", style = MaterialTheme.typography.labelSmall)
                    }
                }
                FilledTonalButton(onClick = onPickGalleryClick, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().height(66.dp)) {
                    Icon(Icons.Rounded.PhotoLibrary, null, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("2. Pilih dari Galeri", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    HomeSecondaryTile(Modifier.weight(1f), Icons.Rounded.History, "3. Riwayat Nota", "$totalSavedCount tersimpan", onHistoryClick)
                    HomeSecondaryTile(Modifier.weight(1f), Icons.Rounded.Settings, "4. Pengaturan", "OCR & Ekspor", onSettingsClick)
                }
            }
        }
    }
}

@Composable
private fun HomeSecondaryTile(modifier: Modifier, icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, shape = RoundedCornerShape(16.dp), modifier = modifier.height(104.dp)) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
