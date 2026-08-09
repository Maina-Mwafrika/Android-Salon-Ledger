package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payment_rows ORDER BY id DESC")
    fun getAllPaymentsFlow(): Flow<List<PaymentRow>>

    @Query("SELECT * FROM payment_rows WHERE spreadsheetId = :spreadsheetId ORDER BY rowIndex ASC")
    fun getPaymentsBySpreadsheetFlow(spreadsheetId: String): Flow<List<PaymentRow>>

    @Query("SELECT * FROM sheet_config WHERE id = 'active_config'")
    fun getActiveConfigFlow(): Flow<SheetConfig?>

    @Query("SELECT * FROM sheet_config WHERE id = 'active_config'")
    suspend fun getActiveConfig(): SheetConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: SheetConfig)

    @Query("UPDATE sheet_config SET isVerified = :isVerified WHERE id = 'active_config'")
    suspend fun updateConfigVerified(isVerified: Boolean)

    @Query("DELETE FROM payment_rows WHERE spreadsheetId = :spreadsheetId")
    suspend fun clearPaymentsForSpreadsheet(spreadsheetId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayments(payments: List<PaymentRow>)

    @Update
    suspend fun updatePayment(payment: PaymentRow)

    @Query("DELETE FROM payment_rows")
    suspend fun clearAllPayments()

    @Query("SELECT * FROM payment_rows WHERE paid = 1")
    suspend fun getAllPaidPayments(): List<PaymentRow>

    @Query("SELECT * FROM paid_records_cache")
    suspend fun getAllPaidCache(): List<PaidRecordCache>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaidCache(cache: PaidRecordCache)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaidCacheList(caches: List<PaidRecordCache>)

    @Query("DELETE FROM paid_records_cache WHERE recordKey = :key")
    suspend fun deletePaidCache(key: String)

    @Query("DELETE FROM paid_records_cache")
    suspend fun clearPaidCache()
}