package com.notaai.app.ui.navigation

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.notaai.app.NotaApplication
import com.notaai.app.ui.screens.*
import com.notaai.app.ui.viewmodel.MainViewModel

@Composable
fun NotaNavHost(mainViewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val app = context.applicationContext as NotaApplication
    val receipts by mainViewModel.receipts.collectAsState()
    val settings by mainViewModel.settings.collectAsState()

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            val copied = app.imageStorage.copyUriToTempFile(uri)
            if (copied != null) navController.navigate("crop/${Uri.encode(Uri.fromFile(copied).toString())}")
            else Toast.makeText(context, "Gagal membuka gambar dari galeri", Toast.LENGTH_SHORT).show()
        }
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                totalSavedCount = receipts.size,
                onTakePhotoClick = { navController.navigate("camera") },
                onPickGalleryClick = { galleryPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                onHistoryClick = { navController.navigate("history") },
                onSettingsClick = { navController.navigate("settings") }
            )
        }
        composable("camera") {
            CameraScreen(
                onImageCaptured = { uri ->
                    navController.navigate("crop/${Uri.encode(uri.toString())}") { popUpTo("camera") { inclusive = true } }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable("crop/{imageUri}", arguments = listOf(navArgument("imageUri") { type = NavType.StringType })) { entry ->
            val decoded = Uri.parse(Uri.decode(entry.arguments?.getString("imageUri").orEmpty()))
            ImageCropScreen(
                imageUri = decoded,
                onProcessCroppedBitmap = { mainViewModel.processCroppedBitmapAndCreateReceipt(it) },
                onReceiptCreated = { id -> navController.navigate("review/$id") { popUpTo("home") { inclusive = false } } },
                onBack = { navController.popBackStack() }
            )
        }
        composable("review/{receiptId}", arguments = listOf(navArgument("receiptId") { type = NavType.LongType })) { entry ->
            ReceiptReviewScreen(receiptId = entry.arguments?.getLong("receiptId") ?: 0L, onBack = { navController.popBackStack() })
        }
        composable("history") {
            HistoryScreen(receipts = receipts, onOpenReceipt = { navController.navigate("review/$it") }, onDeleteReceipt = { mainViewModel.deleteReceipt(it) }, onBack = { navController.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(
                settings = settings,
                onToggleEnhanceContrast = { mainViewModel.setEnhanceContrast(it) },
                onToggleFlagLowConfidence = { mainViewModel.setFlagLowConfidence(it) },
                onExportAllCsv = { onReady, onEmpty -> mainViewModel.exportAllReceiptsCsv(onReady, onEmpty) },
                onDeleteAllHistory = { mainViewModel.deleteAllReceipts() },
                onBack = { navController.popBackStack() }
            )
        }
    }
}
