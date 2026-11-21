package com.notaai.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.notaai.app.domain.*
import com.notaai.app.ui.viewmodel.ReceiptReviewViewModel
import com.notaai.app.util.CsvExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptReviewScreen(receiptId: Long, onBack: () -> Unit, viewModel: ReceiptReviewViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    var showZoomDialog by remember { mutableStateOf(false) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var showAddOrEditDialog by remember { mutableStateOf(false) }
    var receiptBitmap by remember { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(receiptId) { viewModel.loadReceipt(receiptId) }
    LaunchedEffect(state.receipt?.imagePath) {
        val path = state.receipt?.imagePath
        if (!path.isNullOrBlank() && File(path).exists()) receiptBitmap = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path) }
    }
    LaunchedEffect(state.isSavedFeedbackVisible) {
        if (state.isSavedFeedbackVisible) {
            Toast.makeText(context, "Nota tersimpan di riwayat HP", Toast.LENGTH_SHORT).show()
            viewModel.clearSavedFeedback()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Periksa & Hitung Nota") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") } },
                actions = {
                    IconButton(onClick = { viewModel.exportCsv { CsvExporter.shareCsvFile(context, it) } }) { Icon(Icons.Rounded.Share, "Ekspor CSV") }
                    IconButton(onClick = { viewModel.saveExplicitly() }) { Icon(Icons.Rounded.Save, "Simpan") }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editingIndex = null; showAddOrEditDialog = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Tambah Barang") }
            )
        }
    ) { padding ->
        val receipt = state.receipt
        val summary = state.summary
        if (state.isLoading) { Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return@Scaffold }
        if (receipt == null || summary == null) { Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { Text("Nota tidak ditemukan.") }; return@Scaffold }

        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 92.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (receiptBitmap != null) {
                item {
                    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().height(170.dp).clickable { showZoomDialog = true }) {
                        Box(Modifier.fillMaxSize()) {
                            Image(receiptBitmap!!.asImageBitmap(), "Foto Nota", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            Row(Modifier.align(Alignment.BottomEnd).padding(10.dp).clip(RoundedCornerShape(20.dp)).background(Color.Black.copy(0.7f)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.ZoomIn, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Perbesar Foto", color = Color.White, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
            item {
                OutlinedTextField(value = receipt.title, onValueChange = { viewModel.updateTitle(it) }, label = { Text("Nama Toko / Catatan Nota") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            item {
                TotalComparisonCard(summary, state.writtenTotalInput) { viewModel.updateWrittenTotalInput(it) }
            }
            item {
                Text("Daftar Barang (${summary.activeItemCount} aktif, ${summary.crossedOutItemCount} dicoret)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            if (receipt.items.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Belum ada barang terbaca otomatis.", fontWeight = FontWeight.SemiBold)
                            Text("Tekan \"Tambah Barang\" di bawah untuk input manual.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            } else {
                itemsIndexed(receipt.items, key = { idx, item -> "${idx}_${item.name}_${item.lineTotal}" }) { index, item ->
                    ReceiptItemCard(
                        item = item,
                        onEdit = { editingIndex = index; showAddOrEditDialog = true },
                        onToggleCrossOut = { viewModel.toggleCrossedOut(index) },
                        onMarkVerified = { viewModel.markItemVerified(index) },
                        onDelete = { viewModel.deleteItem(index) }
                    )
                }
            }
        }
    }
    if (showZoomDialog && receiptBitmap != null) ZoomableImageDialog(receiptBitmap!!) { showZoomDialog = false }
    if (showAddOrEditDialog) {
        ItemEditDialog(editingIndex?.let { state.receipt?.items?.getOrNull(it) }, { showAddOrEditDialog = false }) { n, q, u, up, lt ->
            viewModel.addOrUpdateItem(editingIndex, n, q, u, up, lt)
            showAddOrEditDialog = false
        }
    }
}

@Composable
private fun TotalComparisonCard(summary: ReceiptSummary, writtenInput: String, onWrittenChange: (String) -> Unit) {
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Total Hasil Perhitungan", style = MaterialTheme.typography.labelLarge)
            Text(RupiahFormatter.format(summary.calculatedTotal), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
            OutlinedTextField(
                value = writtenInput, onValueChange = onWrittenChange,
                label = { Text("Total Tertulis di Nota (Rp)") }, placeholder = { Text("Contoh: 148000") }, prefix = { Text("Rp ") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth()
            )
            if (summary.writtenTotal != null && summary.difference != null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total di Nota:"); Text(RupiahFormatter.format(summary.writtenTotal), fontWeight = FontWeight.SemiBold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Selisih:")
                    Text(RupiahFormatter.formatSignedDifference(summary.difference), fontWeight = FontWeight.Bold, color = if (summary.difference == 0L) Color(0xFF15803D) else Color(0xFFDC2626))
                }
            }
            when (summary.matchStatus) {
                TotalMatchStatus.UNVERIFIED_ITEMS -> StatusBanner(Color(0xFFFEF3C7), Color(0xFF92400E), Icons.Rounded.Warning, "Hasil Belum Terverifikasi", "Ada ${summary.unverifiedActiveCount} barang perlu diperiksa. Cek atau klik \"Sudah Benar\".")
                TotalMatchStatus.MATCH -> StatusBanner(Color(0xFFDCFCE7), Color(0xFF166534), Icons.Rounded.CheckCircle, "COCOK — Total Nota Benar", "Hitungan sama persis dengan total tertulis di nota.")
                TotalMatchStatus.MISMATCH -> {
                    val diff = summary.difference ?: 0L
                    val msg = if (diff > 0L) "Hitungan lebih besar ${RupiahFormatter.format(diff)} dari nota." else "Hitungan lebih kecil ${RupiahFormatter.format(-diff)} dari nota."
                    StatusBanner(Color(0xFFFEE2E2), Color(0xFF991B1B), Icons.Rounded.Warning, "TIDAK COCOK — Ada Selisih", msg)
                }
                TotalMatchStatus.WAITING_INPUT -> StatusBanner(Color(0xFFE0F2FE), Color(0xFF075985), Icons.Rounded.Edit, "Masukkan Total di Nota", "Ketik total angka yang tertera di kertas nota untuk cek selisih.")
            }
        }
    }
}

@Composable
private fun StatusBanner(bg: Color, fg: Color, icon: ImageVector, title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(bg).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = fg); Spacer(Modifier.width(10.dp))
        Column { Text(title, color = fg, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall); Text(subtitle, color = fg, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun ReceiptItemCard(item: ReceiptItem, onEdit: () -> Unit, onToggleCrossOut: () -> Unit, onMarkVerified: () -> Unit, onDelete: () -> Unit) {
    val bg = when { item.isCrossedOut -> Color(0xFFF1F5F9); item.needsReview && !item.isVerifiedByUser -> Color(0xFFFFFBEB); else -> MaterialTheme.colorScheme.surface }
    val deco = if (item.isCrossedOut) TextDecoration.LineThrough else TextDecoration.None
    Card(shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = bg), elevation = CardDefaults.cardElevation(1.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textDecoration = deco, color = if (item.isCrossedOut) Color.Gray else MaterialTheme.colorScheme.onSurface)
                    if (item.quantity != null && item.unitPrice != null) {
                        val u = if (!item.unit.isNullOrBlank()) " ${item.unit}" else ""
                        Text("${RupiahFormatter.formatQuantity(item.quantity)}$u × ${RupiahFormatter.format(item.unitPrice)}", style = MaterialTheme.typography.bodySmall, textDecoration = deco, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(RupiahFormatter.format(item.lineTotal), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, textDecoration = deco, color = if (item.isCrossedOut) Color.Gray else MaterialTheme.colorScheme.primary)
            }
            if (item.isCrossedOut) Text("Dicoret — tidak masuk hitungan total", style = MaterialTheme.typography.labelMedium, color = Color(0xFF64748B))
            else if (item.needsReview && !item.isVerifiedByUser) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Perlu diperiksa: ${item.ocrConfidenceNote ?: "Pastikan sesuai"}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB45309), modifier = Modifier.weight(1f))
                    TextButton(onClick = onMarkVerified) { Text("Sudah Benar") }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onToggleCrossOut) {
                    Icon(if (item.isCrossedOut) Icons.Rounded.Restore else Icons.Rounded.StrikethroughS, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp)); Text(if (item.isCrossedOut) "Aktifkan" else "Coret")
                }
                TextButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Edit") }
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Hapus", tint = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun ItemEditDialog(initialItem: ReceiptItem?, onDismiss: () -> Unit, onSave: (String, Double?, String?, Long?, Long) -> Unit) {
    var name by remember { mutableStateOf(initialItem?.name ?: "") }
    var qtyText by remember { mutableStateOf(initialItem?.quantity?.let { RupiahFormatter.formatQuantity(it) } ?: "") }
    var unitText by remember { mutableStateOf(initialItem?.unit ?: "") }
    var unitPriceText by remember { mutableStateOf(initialItem?.unitPrice?.toString() ?: "") }
    var lineTotalText by remember { mutableStateOf(initialItem?.lineTotal?.toString() ?: "") }
    val parsedQty = qtyText.replace(',', '.').toDoubleOrNull()
    val parsedUnitPrice = RupiahFormatter.parseRupiahInput(unitPriceText)
    val autoTotal = if (parsedQty != null && parsedUnitPrice != null && parsedQty > 0.0 && parsedUnitPrice > 0L) ReceiptCalculator.calculateLineTotal(parsedQty, parsedUnitPrice) else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialItem == null) "Tambah Barang" else "Edit Barang") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nama Barang") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = qtyText, onValueChange = { qtyText = it }, label = { Text("Qty (opsional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = unitText, onValueChange = { unitText = it }, label = { Text("Satuan") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = unitPriceText, onValueChange = { unitPriceText = it.filter { c -> c.isDigit() } }, label = { Text("Harga Satuan (opsional)") }, prefix = { Text("Rp ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                if (autoTotal != null) Text("Total otomatis: ${RupiahFormatter.format(autoTotal)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                else OutlinedTextField(value = lineTotalText, onValueChange = { lineTotalText = it.filter { c -> c.isDigit() } }, label = { Text("Total Harga Barang") }, prefix = { Text("Rp ") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank() && (autoTotal != null || RupiahFormatter.parseRupiahInput(lineTotalText) != null), onClick = {
                onSave(name, parsedQty, unitText, parsedUnitPrice, autoTotal ?: (RupiahFormatter.parseRupiahInput(lineTotalText) ?: 0L))
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun ZoomableImageDialog(bitmap: Bitmap, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                val newScale = (scale * zoom).coerceIn(1f, 6f)
                scale = newScale
                offset = if (newScale > 1f) offset + pan else Offset.Zero
            }
        }) {
            Image(bitmap.asImageBitmap(), "Foto Nota Diperbesar", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y))
            Row(Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopCenter), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Cubit 2 jari untuk zoom", color = Color.White)
                Row {
                    OutlinedButton(onClick = { scale = 1f; offset = Offset.Zero }) { Text("Reset", color = Color.White) }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Tutup", tint = Color.White) }
                }
            }
        }
    }
}
