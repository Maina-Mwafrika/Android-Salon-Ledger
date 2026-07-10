package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payment_rows")
data class PaymentRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val spreadsheetId: String,
    val rowIndex: Int, // The exact row number in Google Sheets (1-indexed or 0-indexed)
    val timestamp: String,
    val name: String,
    val section: String,
    val serviceName: String,
    val amountPaid: Double,
    val paymentMethod: String,
    val commissionPct: Double = 0.0,
    val staffCommission: Double = 0.0,
    val salonShare: Double = 0.0,
    val notes: String,
    val paid: Boolean, // True = Paid, False = Unpaid
    val month: String = ""
)

@Entity(tableName = "sheet_config")
data class SheetConfig(
    @PrimaryKey val id: String = "active_config",
    val spreadsheetUrl: String,
    val spreadsheetId: String,
    val sheetName: String = "Services Ledger",
    val ownerPin: String, // Verification pin set by the user to authorize edits
    val isVerified: Boolean = false,
    val useLocalDemo: Boolean = true,
    val lastSyncTime: Long = 0L
)
