package com.notaai.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReceiptDao {
    @Transaction @Query("SELECT * FROM receipts ORDER BY createdAt DESC")
    fun observeAllReceipts(): Flow<List<ReceiptWithItems>>

    @Transaction @Query("SELECT * FROM receipts ORDER BY createdAt DESC")
    suspend fun getAllReceiptsOnce(): List<ReceiptWithItems>

    @Transaction @Query("SELECT * FROM receipts WHERE id = :receiptId")
    fun observeReceiptById(receiptId: Long): Flow<ReceiptWithItems?>

    @Transaction @Query("SELECT * FROM receipts WHERE id = :receiptId")
    suspend fun getReceiptById(receiptId: Long): ReceiptWithItems?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReceipt(receipt: ReceiptEntity): Long

    @Update
    suspend fun updateReceipt(receipt: ReceiptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ReceiptItemEntity>)

    @Query("DELETE FROM receipt_items WHERE receiptId = :receiptId")
    suspend fun deleteItemsForReceipt(receiptId: Long)

    @Query("DELETE FROM receipts WHERE id = :receiptId")
    suspend fun deleteReceiptById(receiptId: Long)

    @Query("DELETE FROM receipts")
    suspend fun deleteAllReceipts()

    @Transaction
    suspend fun saveReceiptWithItems(receipt: ReceiptEntity, items: List<ReceiptItemEntity>): Long {
        val targetId = if (receipt.id == 0L) insertReceipt(receipt) else { updateReceipt(receipt); receipt.id }
        deleteItemsForReceipt(targetId)
        val mapped = items.mapIndexed { idx, item -> item.copy(id = 0L, receiptId = targetId, sortOrder = idx) }
        if (mapped.isNotEmpty()) insertItems(mapped)
        return targetId
    }
}
