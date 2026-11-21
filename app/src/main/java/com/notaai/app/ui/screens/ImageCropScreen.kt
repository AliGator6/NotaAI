package com.notaai.app.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.notaai.app.ocr.BitmapProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.min

private enum class DragCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT, NONE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageCropScreen(imageUri: Uri, onProcessCroppedBitmap: suspend (Bitmap) -> Long, onReceiptCreated: (Long) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var cropLeft by remember { mutableFloatStateOf(0.06f) }
    var cropTop by remember { mutableFloatStateOf(0.06f) }
    var cropRight by remember { mutableFloatStateOf(0.94f) }
    var cropBottom by remember { mutableFloatStateOf(0.94f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(imageUri) { bitmap = withContext(Dispatchers.IO) { BitmapProcessor.loadNormalizedBitmap(context, imageUri) } }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Potong & Putar Nota") }, navigationIcon = {
            IconButton(onClick = onBack, enabled = !isProcessing) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali") }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text("Geser sudut kotak agar hanya bagian tulisan nota yang terbaca.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF111827)).onSizeChanged { containerSize = it }, contentAlignment = Alignment.Center) {
                val bmp = bitmap
                if (bmp == null) CircularProgressIndicator(color = Color.White)
                else {
                    val scale = if (containerSize.width > 0 && containerSize.height > 0) min(containerSize.width.toFloat() / bmp.width, containerSize.height.toFloat() / bmp.height) else 1f
                    val imgW = bmp.width * scale
                    val imgH = bmp.height * scale
                    val offX = (containerSize.width - imgW) / 2f
                    val offY = (containerSize.height - imgH) / 2f
                    Image(bmp.asImageBitmap(), "Pratinjau Nota", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                    var active by remember { mutableStateOf(DragCorner.NONE) }
                    Canvas(Modifier.fillMaxSize().pointerInput(bmp.width, bmp.height, containerSize) {
                        detectDragGestures(
                            onDragStart = { pos ->
                                val l = offX + cropLeft * imgW; val t = offY + cropTop * imgH
                                val r = offX + cropRight * imgW; val b = offY + cropBottom * imgH
                                active = when {
                                    abs(pos.x - l) < 75f && abs(pos.y - t) < 75f -> DragCorner.TOP_LEFT
                                    abs(pos.x - r) < 75f && abs(pos.y - t) < 75f -> DragCorner.TOP_RIGHT
                                    abs(pos.x - l) < 75f && abs(pos.y - b) < 75f -> DragCorner.BOTTOM_LEFT
                                    abs(pos.x - r) < 75f && abs(pos.y - b) < 75f -> DragCorner.BOTTOM_RIGHT
                                    else -> DragCorner.NONE
                                }
                            },
                            onDrag = { change, drag ->
                                change.consume()
                                if (imgW <= 0f || imgH <= 0f) return@detectDragGestures
                                val dx = drag.x / imgW; val dy = drag.y / imgH; val m = 0.15f
                                when (active) {
                                    DragCorner.TOP_LEFT -> { cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - m); cropTop = (cropTop + dy).coerceIn(0f, cropBottom - m) }
                                    DragCorner.TOP_RIGHT -> { cropRight = (cropRight + dx).coerceIn(cropLeft + m, 1f); cropTop = (cropTop + dy).coerceIn(0f, cropBottom - m) }
                                    DragCorner.BOTTOM_LEFT -> { cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - m); cropBottom = (cropBottom + dy).coerceIn(cropTop + m, 1f) }
                                    DragCorner.BOTTOM_RIGHT -> { cropRight = (cropRight + dx).coerceIn(cropLeft + m, 1f); cropBottom = (cropBottom + dy).coerceIn(cropTop + m, 1f) }
                                    DragCorner.NONE -> Unit
                                }
                            },
                            onDragEnd = { active = DragCorner.NONE }
                        )
                    }) {
                        val l = offX + cropLeft * imgW; val t = offY + cropTop * imgH
                        val w = (cropRight - cropLeft) * imgW; val h = (cropBottom - cropTop) * imgH
                        drawRect(Color(0xFF2DD4BF), Offset(l, t), Size(w, h), style = Stroke(4.dp.toPx()))
                        val rad = 11.dp.toPx()
                        listOf(Offset(l, t), Offset(l + w, t), Offset(l, t + h), Offset(l + w, t + h)).forEach { c ->
                            drawCircle(Color.White, rad, c); drawCircle(Color(0xFF0F766E), rad * 0.65f, c)
                        }
                    }
                }
                if (isProcessing) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.65f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(Modifier.height(12.dp))
                            Text("Membaca tulisan nota secara offline...", color = Color.White)
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(enabled = bitmap != null && !isProcessing, onClick = { bitmap = bitmap?.let { BitmapProcessor.rotateBitmap(it, -90f) } }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.RotateLeft, null); Spacer(Modifier.width(6.dp)); Text("Putar Kiri")
                    }
                    OutlinedButton(enabled = bitmap != null && !isProcessing, onClick = { bitmap = bitmap?.let { BitmapProcessor.rotateBitmap(it, 90f) } }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Rounded.RotateRight, null); Spacer(Modifier.width(6.dp)); Text("Putar Kanan")
                    }
                }
                Button(
                    enabled = bitmap != null && !isProcessing,
                    onClick = {
                        val bmp = bitmap ?: return@Button
                        isProcessing = true
                        scope.launch {
                            val cropped = withContext(Dispatchers.IO) { BitmapProcessor.cropNormalized(bmp, cropLeft, cropTop, cropRight, cropBottom) }
                            val newId = onProcessCroppedBitmap(cropped)
                            isProcessing = false
                            onReceiptCreated(newId)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) { Icon(Icons.Rounded.Check, null); Spacer(Modifier.width(8.dp)); Text("Baca & Hitung Nota") }
            }
        }
    }
}
