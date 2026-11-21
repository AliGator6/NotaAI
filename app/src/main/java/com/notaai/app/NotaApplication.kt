package com.notaai.app

import android.app.Application
import com.notaai.app.data.local.NotaDatabase
import com.notaai.app.data.preferences.AppPreferences
import com.notaai.app.data.repository.ReceiptRepository
import com.notaai.app.ocr.LocalOcrEngine
import com.notaai.app.util.ImageStorageManager

class NotaApplication : Application() {
    lateinit var database: NotaDatabase
        private set
    lateinit var imageStorage: ImageStorageManager
        private set
    lateinit var repository: ReceiptRepository
        private set
    lateinit var preferences: AppPreferences
        private set
    lateinit var ocrEngine: LocalOcrEngine
        private set

    override fun onCreate() {
        super.onCreate()
        database = NotaDatabase.getInstance(this)
        imageStorage = ImageStorageManager(this)
        repository = ReceiptRepository(database.receiptDao(), imageStorage)
        preferences = AppPreferences(this)
        ocrEngine = LocalOcrEngine()
    }
}
