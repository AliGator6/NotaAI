package com.notaai.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.notaai.app.NotaApplication

@Composable
fun CameraScreen(onImageCaptured: (Uri) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val imageStorage = (context.applicationContext as NotaApplication).imageStorage
    var hasPerm by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPerm = it }
    LaunchedEffect(Unit) { if (!hasPerm) launcher.launch(Manifest.permission.CAMERA) }

    if (!hasPerm) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Izin kamera diperlukan untuk memotret nota.", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Berikan Izin Kamera") }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onBack) { Text("Kembali") }
            }
        }
        return
    }

    var flashOn by remember { mutableStateOf(false) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build() }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { ctx ->
            PreviewView(ctx).also { pv ->
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    val provider = future.get()
                    val preview = Preview.Builder().build().apply { surfaceProvider = pv.surfaceProvider }
                    provider.unbindAll()
                    camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture)
                }, ContextCompat.getMainExecutor(ctx))
            }
        })
        Row(Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopCenter), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Kembali", tint = Color.White) }
            Text("Arahkan ke Nota", color = Color.White, style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = { flashOn = !flashOn; camera?.cameraControl?.enableTorch(flashOn) }) {
                Icon(if (flashOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff, "Lampu Kilat", tint = Color.White)
            }
        }
        Box(Modifier.fillMaxWidth().padding(bottom = 36.dp).align(Alignment.BottomCenter), contentAlignment = Alignment.Center) {
            FilledIconButton(
                onClick = {
                    val outFile = imageStorage.createTempCameraFile()
                    imageCapture.takePicture(ImageCapture.OutputFileOptions.Builder(outFile).build(), ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(res: ImageCapture.OutputFileResults) { onImageCaptured(Uri.fromFile(outFile)) }
                        override fun onError(e: ImageCaptureException) { Toast.makeText(context, "Gagal: ${e.message}", Toast.LENGTH_SHORT).show() }
                    })
                },
                shape = CircleShape, modifier = Modifier.size(76.dp)
            ) { Icon(Icons.Rounded.CameraAlt, "Ambil Foto", modifier = Modifier.size(34.dp)) }
        }
    }
}
