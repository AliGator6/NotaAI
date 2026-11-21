package com.notaai.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ReceiptEntity::class, ReceiptItemEntity::class], version = 1, exportSchema = true)
abstract class NotaDatabase : RoomDatabase() {
    abstract fun receiptDao(): ReceiptDao
    companion object {
        @Volatile private var INSTANCE: NotaDatabase? = null
        fun getInstance(context: Context): NotaDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, NotaDatabase::class.java, "nota_ai_offline.db")
                    .build().also { INSTANCE = it }
            }
    }
}
