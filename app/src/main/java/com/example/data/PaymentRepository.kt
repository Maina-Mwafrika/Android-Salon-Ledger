package com.example.data

import android.util.Log
import com.example.ui.parseTimestampToMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.json.JSONArray

/**
 * PaymentRepository - Handles all data operations for the SheeGlam app
 * No API keys required - works with publicly shared Google Sheets
 * Specifically targets the "Service Ledger" worksheet using its GID
 */
class PaymentRepository(private val paymentDao: PaymentDao) {

    val activeConfigFlow: Flow<SheetConfig?> = paymentDao.getActiveConfigFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val allPaymentsFlow: Flow<List<PaymentRow>> = activeConfigFlow.flatMapLatest { config ->
        val spreadsheetId = config?.spreadsheetId ?: "demo_spreadsheet"
        paymentDao.getPaymentsBySpreadsheetFlow(spreadsheetId)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val allExpensesFlow: Flow<List<ExpenseRow>> = activeConfigFlow.flatMapLatest { config ->
        val spreadsheetId = config?.spreadsheetId ?: "demo_spreadsheet"
        paymentDao.getExpensesBySpreadsheetFlow(spreadsheetId)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val _firstRowPreview = MutableStateFlow<String?>(null)
    val firstRowPreview: StateFlow<String?> = _firstRowPreview.asStateFlow()

    // The GID for the "Service Ledger" worksheet (207825371) and "Expenses" worksheet (1292264559)
    val SERVICE_LEDGER_GID = 207825371
    val EXPENSES_GID = 1292264559

    fun clearFirstRowPreview() {
        _firstRowPreview.value = null
    }

    fun generatePaidCacheKeys(
        spreadsheetId: String,
        rowIndex: Int,
        name: String,
        amountPaid: Double,
        serviceName: String,
        timestamp: String
    ): List<String> {
        val nameClean = name.trim().lowercase()
        val serviceClean = serviceName.trim().lowercase()
        val timeClean = timestamp.trim().lowercase()

        return listOf(
            "${spreadsheetId}_row_${rowIndex}",
            "${spreadsheetId}_${timeClean}_${nameClean}_${serviceClean}_${amountPaid}"
        )
    }

    private suspend fun seedAndFetchPaidCache(): Set<String> {
        try {
            return paymentDao.getAllPaidCache().map { it.recordKey }.toSet()
        } catch (e: Exception) {
            Log.e("PaymentRepository", "Error fetching paid cache", e)
            return emptySet()
        }
    }

    fun formatFirstRowsPreview(sheetData: List<List<String>>, limit: Int = 3): String {
        if (sheetData.isEmpty()) return "[No row data found in spreadsheet]"
        val sb = StringBuilder()
        for (i in 0 until minOf(sheetData.size, limit)) {
            val row = sheetData[i]
            val formattedRow = if (row.isEmpty()) "[Empty Row]" else row.joinToString(" | ") { "\"$it\"" }
            sb.append("Row ${i + 1}: $formattedRow\n")
        }
        return sb.toString().trimEnd()
    }

    fun extractSpreadsheetId(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.matches(Regex("^[a-zA-Z0-9-_]{30,100}$"))) {
            return trimmed
        }
        val pattern = "/d/([a-zA-Z0-9-_]+)".toRegex()
        return pattern.find(url)?.groupValues?.get(1)
    }

    fun normalizeEmployeeName(rawName: String): String {
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) return ""

        val lower = trimmed.lowercase()

        // 1. Omit employee names that are "0", "o", "0.0", "none", "null", "unknown"
        if (lower == "0" || lower == "o" || lower == "0.0" || lower == "none" || lower == "null" || lower == "unknown") {
            return ""
        }

        // 2. Mark Bornventure and bonventure as the same -> "Bonventure"
        if (lower.contains("bornventure") || lower.contains("bonventure") || lower.contains("bonaventure") || lower.contains("born venture")) {
            return "Bonventure"
        }

        // 3. Peter Ngigi as Galaxy
        if (lower.contains("peter ngigi") || lower.contains("galaxy") || lower == "peter") {
            return "Galaxy"
        }

        // 4. Smiles as Virginiah
        if (lower.contains("smiles") || lower.contains("virginiah") || lower.contains("virginia")) {
            return "Virginiah"
        }

        // 5. Susan Ngigi as Susanne and the other iterations
        if (lower.contains("susan ngigi") || lower.contains("susanne") || lower.contains("suzzy") ||
            lower.contains("suzy") || lower.contains("suzi") || lower.contains("susie") || lower.contains("susan")) {
            return "Susanne"
        }

        val firstWord = trimmed.split(Regex("[\\s.,/_-]+")).firstOrNull { it.isNotBlank() } ?: trimmed
        return firstWord.lowercase().replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString()
        }
    }

    suspend fun resetToDemoData() {
        withContext(Dispatchers.IO) {
            paymentDao.clearAllPayments()
            
            val samplePayments = listOf(
                PaymentRow(
                    spreadsheetId = "demo_spreadsheet",
                    rowIndex = 2,
                    timestamp = "2026-07-01 09:30",
                    name = "Jane Wambui",
                    section = "Nails",
                    serviceName = "Gel Manicure",
                    amountPaid = 1500.0,
                    paymentMethod = "Mpesa",
                    commissionPct = 0.20,
                    staffCommission = 300.0,
                    salonShare = 1200.0,
                    notes = "Client requested specific peach shade",
                    paid = false,
                    month = "July 2026"
                ),
                PaymentRow(
                    spreadsheetId = "demo_spreadsheet",
                    rowIndex = 3,
                    timestamp = "2026-07-02 11:00",
                    name = "Jane Wambui",
                    section = "Hair",
                    serviceName = "Braiding",
                    amountPaid = 3500.0,
                    paymentMethod = "Mpesa",
                    commissionPct = 0.30,
                    staffCommission = 1050.0,
                    salonShare = 2450.0,
                    notes = "Long knotless braids with extensions",
                    paid = false,
                    month = "July 2026"
                ),
                PaymentRow(
                    spreadsheetId = "demo_spreadsheet",
                    rowIndex = 4,
                    timestamp = "2026-07-02 14:15",
                    name = "John Mwangi",
                    section = "Massage",
                    serviceName = "Deep Tissue Massage",
                    amountPaid = 3000.0,
                    paymentMethod = "Cash",
                    commissionPct = 0.40,
                    staffCommission = 1200.0,
                    salonShare = 1800.0,
                    notes = "Focused on lower back pain relief",
                    paid = false,
                    month = "July 2026"
                ),
                PaymentRow(
                    spreadsheetId = "demo_spreadsheet",
                    rowIndex = 5,
                    timestamp = "2026-07-03 10:00",
                    name = "Mary Atieno",
                    section = "Hair",
                    serviceName = "Wash and Blow",
                    amountPaid = 800.0,
                    paymentMethod = "Cash",
                    commissionPct = 0.30,
                    staffCommission = 240.0,
                    salonShare = 560.0,
                    notes = "Sleek blow dry, hair treatment added",
                    paid = true,
                    month = "July 2026"
                ),
                PaymentRow(
                    spreadsheetId = "demo_spreadsheet",
                    rowIndex = 6,
                    timestamp = "2026-07-03 12:30",
                    name = "Mary Atieno",
                    section = "Nails",
                    serviceName = "Acrylic Full Set",
                    amountPaid = 2500.0,
                    paymentMethod = "Mpesa",
                    commissionPct = 0.20,
                    staffCommission = 500.0,
                    salonShare = 2000.0,
                    notes = "White glitter tips",
                    paid = false,
                    month = "July 2026"
                )
            )
            
            val samplePaymentsNormalized = samplePayments.map {
                it.copy(name = normalizeEmployeeName(it.name))
            }
            paymentDao.insertPayments(samplePaymentsNormalized)

            paymentDao.clearAllExpenses()
            val sampleExpenses = listOf(
                ExpenseRow(
                    spreadsheetId = "demo_spreadsheet",
                    rowIndex = 2,
                    date = "2026-07-01",
                    recordedBy = "Jane Wambui",
                    department = "Nails",
                    expenseType = "Inventory",
                    itemPurchased = "Gel Polish Set (Peach & Pastel)",
                    quantity = 2.0,
                    amountSpent = 3500.0,
                    paymentMethod = "Mpesa",
                    month = "July 2026"
                ),
                ExpenseRow(
                    spreadsheetId = "demo_spreadsheet",
                    rowIndex = 3,
                    date = "2026-07-02",
                    recordedBy = "Manager Mary",
                    department = "Utilities",
                    expenseType = "Bills & Tokens",
                    itemPurchased = "Electricity Token (Tokens)",
                    quantity = 1.0,
                    amountSpent = 4500.0,
                    paymentMethod = "Mpesa",
                    month = "July 2026"
                ),
                ExpenseRow(
                    spreadsheetId = "demo_spreadsheet",
                    rowIndex = 4,
                    date = "2026-07-03",
                    recordedBy = "John Mwangi",
                    department = "Massage",
                    expenseType = "Supplies",
                    itemPurchased = "Aromatherapy Oils & Towels",
                    quantity = 3.0,
                    amountSpent = 2800.0,
                    paymentMethod = "Cash",
                    month = "July 2026"
                ),
                ExpenseRow(
                    spreadsheetId = "demo_spreadsheet",
                    rowIndex = 5,
                    date = "2026-07-05",
                    recordedBy = "Manager Mary",
                    department = "Salon Admin",
                    expenseType = "Rent & Premises",
                    itemPurchased = "Monthly Parlor Space Rent",
                    quantity = 1.0,
                    amountSpent = 15000.0,
                    paymentMethod = "Bank Transfer",
                    month = "July 2026"
                ),
                ExpenseRow(
                    spreadsheetId = "demo_spreadsheet",
                    rowIndex = 6,
                    date = "2026-07-08",
                    recordedBy = "Mary Atieno",
                    department = "Hair",
                    expenseType = "Inventory",
                    itemPurchased = "Shampoo 5L & Deep Conditioner",
                    quantity = 2.0,
                    amountSpent = 3200.0,
                    paymentMethod = "Mpesa",
                    month = "July 2026"
                ),
                ExpenseRow(
                    spreadsheetId = "demo_spreadsheet",
                    rowIndex = 7,
                    date = "2026-07-12",
                    recordedBy = "Jane Wambui",
                    department = "Salon Admin",
                    expenseType = "Tea & Refreshments",
                    itemPurchased = "Milk, Tea Leaves & Snacks",
                    quantity = 1.0,
                    amountSpent = 1200.0,
                    paymentMethod = "Cash",
                    month = "July 2026"
                )
            )
            paymentDao.insertExpenses(sampleExpenses)
            
            val defaultConfig = SheetConfig(
                spreadsheetUrl = "https://docs.google.com/spreadsheets/d/demo_spreadsheet/edit",
                spreadsheetId = "demo_spreadsheet",
                sheetName = "Service Ledger",
                ownerPin = "1234",
                isVerified = true,
                useLocalDemo = true,
                lastSyncTime = System.currentTimeMillis()
            )
            paymentDao.insertConfig(defaultConfig)
        }
    }

    suspend fun saveConfig(config: SheetConfig) {
        paymentDao.insertConfig(config)
    }

    private fun parseCommissionPct(pctStr: String): Double {
        val clean = pctStr.replace("%", "").trim()
        val value = clean.toDoubleOrNull() ?: 0.0
        return if (value >= 1.0) {
            value / 100.0
        } else {
            value
        }
    }

    private fun normalizeHeaderCell(valStr: String): String {
        var s = valStr.trim().replace("\"", "").replace("'", "")
        if (s.startsWith("=")) {
            s = s.substring(1).trim().replace("\"", "").replace("'", "")
        }
        s = s.replace(Regex("\\s+"), " ")
        return s.lowercase()
    }

    private fun saveFileToPhoneDownloads(fileName: String, bytes: ByteArray, context: android.content.Context? = null): String {
        val mimeType = when {
            fileName.endsWith(".xlsx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            fileName.endsWith(".tsv", ignoreCase = true) -> "text/tab-separated-values"
            fileName.endsWith(".csv", ignoreCase = true) -> "text/csv"
            else -> "text/csv"
        }

        if (context != null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val collection = android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
                val uri = resolver.insert(collection, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(bytes)
                        outputStream.flush()
                    }
                    contentValues.clear()
                    contentValues.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)
                    Log.d("PaymentRepository", "Saved file via MediaStore: $fileName")
                    return "Android Downloads folder ($fileName)"
                }
            } catch (e: Exception) {
                Log.w("PaymentRepository", "MediaStore write failed", e)
            }
        }

        return try {
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val file = java.io.File(downloadsDir, fileName)
            file.writeBytes(bytes)

            if (context != null) {
                try {
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath),
                        arrayOf(mimeType),
                        null
                    )
                } catch (e: Exception) {
                    Log.w("PaymentRepository", "MediaScanner failed: ${e.message}")
                }
            }

            Log.d("PaymentRepository", "Saved file: ${file.absolutePath}")
            "Android Downloads folder (${file.name})"
        } catch (e: Exception) {
            Log.w("PaymentRepository", "Downloads write failed", e)
            try {
                if (context != null) {
                    val appDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
                    val file = java.io.File(appDir, fileName)
                    file.writeBytes(bytes)
                    "Android Downloads directory (${file.name})"
                } else {
                    fileName
                }
            } catch (e2: Exception) {
                Log.e("PaymentRepository", "Failed to save file", e2)
                fileName
            }
        }
    }

    /**
     * Download worksheet directly from an online URL (CSV, TSV, XLSX, or Google Sheet direct URL),
     * saves the raw file locally to the phone's Downloads directory, and imports rows into "Service Ledger".
     * Uses the hardcoded GID to target the specific worksheet.
     */
    suspend fun downloadWorksheetFromUrl(
        onlineUrl: String,
        context: android.content.Context? = null,
        targetSheetName: String = "Service Ledger"
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val trimmedUrl = onlineUrl.trim()
            if (trimmedUrl.isBlank()) {
                return@withContext Result.failure(Exception("URL cannot be empty"))
            }

            val isGoogleSheets = trimmedUrl.contains("docs.google.com/spreadsheets/d/")
            val sheetId = if (isGoogleSheets) extractSpreadsheetId(trimmedUrl) else null

            val downloadUrlsToTry = mutableListOf<String>()

            if (isGoogleSheets && sheetId != null) {
                // PRIMARY: Use the hardcoded GID (most reliable)
                downloadUrlsToTry.add("https://docs.google.com/spreadsheets/d/$sheetId/export?format=csv&gid=$SERVICE_LEDGER_GID")
                downloadUrlsToTry.add("https://docs.google.com/spreadsheets/d/$sheetId/gviz/tq?tqx=out:csv&gid=$SERVICE_LEDGER_GID")
                
                // SECONDARY: Try with sheet name
                val encodedSheetName = java.net.URLEncoder.encode("Service Ledger", "UTF-8")
                downloadUrlsToTry.add("https://docs.google.com/spreadsheets/d/$sheetId/export?format=csv&sheet=$encodedSheetName")
                
                // FALLBACK: Try without specifying sheet (will need validation)
                downloadUrlsToTry.add("https://docs.google.com/spreadsheets/d/$sheetId/export?format=csv")
            } else {
                downloadUrlsToTry.add(trimmedUrl)
            }

            var lastException: Exception = Exception("Failed to download worksheet")

            for (attemptUrl in downloadUrlsToTry.distinct()) {
                Log.d("PaymentRepository", "Attempting download from URL: $attemptUrl")
                try {
                    val request = Request.Builder()
                        .url(attemptUrl)
                        .header("User-Agent", "Mozilla/5.0 (Android; Mobile)")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            lastException = Exception("HTTP ${response.code} ${response.message}")
                            return@use
                        }
                        val bytes = response.body?.bytes()
                        if (bytes == null || bytes.isEmpty()) {
                            lastException = Exception("Downloaded content is empty")
                            return@use
                        }

                        if (bytes.size < 2500) {
                            val sampleStr = String(bytes, Charsets.UTF_8).trim()
                            if (sampleStr.startsWith("<") || sampleStr.contains("<!DOCTYPE html", ignoreCase = true) || sampleStr.contains("<html", ignoreCase = true)) {
                                lastException = Exception("The URL returned an HTML page. Ensure the spreadsheet is shared as 'Anyone with the link can view'.")
                                return@use
                            }
                        }

                        // Parse the data to check if it's the right sheet
                        val isZip = bytes.size >= 4 &&
                                bytes[0] == 0x50.toByte() &&
                                bytes[1] == 0x4B.toByte() &&
                                bytes[2] == 0x03.toByte() &&
                                bytes[3] == 0x04.toByte()

                        val parsedData: List<List<String>> = if (isZip) {
                            parseXlsxData(bytes)
                        } else {
                            val fileContent = String(bytes, Charsets.UTF_8)
                            val lines = fileContent.split("\n").map { it.trim() }
                            if (lines.none { it.isNotEmpty() }) {
                                lastException = Exception("No data in spreadsheet file")
                                return@use
                            }
                            val scanLines = lines.filter { it.isNotEmpty() }.take(20)
                            val tabCount = scanLines.sumOf { line -> line.count { it == '\t' } }
                            val commaCount = scanLines.sumOf { line -> line.count { it == ',' } }
                            val delimiter = if (tabCount > commaCount) "\t" else ","
                            lines.map { if (it.isEmpty()) emptyList() else parseCsvLine(it, delimiter) }
                        }

                        // Verify this is the Service Ledger sheet
                        if (parsedData.isNotEmpty() && parsedData.first().isNotEmpty()) {
                            val headers = parsedData.first().map { normalizeHeaderCell(it) }
                            val hasHandledBy = headers.any { it.contains("handled") || it.contains("name") || it.contains("staff") }
                            val hasAmount = headers.any { it.contains("amount") || it.contains("paid") || it.contains("kes") }
                            
                            if (hasHandledBy && hasAmount) {
                                // This is the Service Ledger sheet!
                                var fileName = "Service_Ledger_download.csv"
                                
                                val disposition = response.header("Content-Disposition")
                                if (!disposition.isNullOrBlank() && disposition.contains("filename=")) {
                                    val extractedName = disposition.substringAfter("filename=").replace("\"", "").trim()
                                    if (extractedName.isNotBlank()) {
                                        fileName = extractedName
                                    }
                                } else if (attemptUrl.contains(".xlsx", ignoreCase = true)) {
                                    fileName = "Service_Ledger_download.xlsx"
                                } else if (attemptUrl.contains(".tsv", ignoreCase = true)) {
                                    fileName = "Service_Ledger_download.tsv"
                                }
                                
                                val savedPath = saveFileToPhoneDownloads(fileName, bytes, context)
                                
                                val importResult = importLocalSpreadsheetData(fileName, bytes)
                                if (importResult.isSuccess) {
                                    val activeConfig = paymentDao.getActiveConfig()
                                    val updatedConfig = (activeConfig ?: SheetConfig(
                                        spreadsheetId = sheetId ?: "downloaded_sheet",
                                        spreadsheetUrl = trimmedUrl,
                                        sheetName = "Service Ledger",
                                        ownerPin = "1234"
                                    )).copy(
                                        spreadsheetId = sheetId ?: activeConfig?.spreadsheetId ?: "downloaded_sheet",
                                        spreadsheetUrl = trimmedUrl,
                                        sheetName = "Service Ledger",
                                        useLocalDemo = false
                                    )
                                    paymentDao.insertConfig(updatedConfig)
                                    return@withContext Result.success(savedPath)
                                } else {
                                    lastException = importResult.exceptionOrNull() as? Exception ?: Exception("Failed to import worksheet data")
                                }
                            } else {
                                // Check if this is the wrong sheet
                                if (headers.any { it.contains("payment form") || it.contains("form") }) {
                                    lastException = Exception("Downloaded 'Payment Form Import' sheet instead of 'Service Ledger'. Please use the correct GID: $SERVICE_LEDGER_GID")
                                } else {
                                    lastException = Exception("Downloaded data does not match 'Service Ledger' format. Please verify the sheet name and headers.")
                                }
                            }
                        } else {
                            lastException = Exception("Could not parse downloaded data")
                        }
                    }
                } catch (e: Exception) {
                    lastException = e
                }
            }

            return@withContext Result.failure(Exception("Download failed: ${lastException.localizedMessage}"))
        } catch (e: Exception) {
            Log.e("PaymentRepository", "Download failed", e)
            Result.failure(Exception("Download failed: ${e.localizedMessage ?: "Network error"}"))
        }
    }

    suspend fun importLocalSpreadsheetData(fileName: String, fileBytes: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (fileBytes.isEmpty()) {
                return@withContext Result.failure(Exception("File is empty"))
            }

            val isZip = fileBytes.size >= 4 &&
                    fileBytes[0] == 0x50.toByte() &&
                    fileBytes[1] == 0x4B.toByte() &&
                    fileBytes[2] == 0x03.toByte() &&
                    fileBytes[3] == 0x04.toByte()

            val parsedSheetData: List<List<String>> = if (isZip) {
                parseXlsxData(fileBytes)
            } else {
                val fileContent = String(fileBytes, Charsets.UTF_8)
                val lines = fileContent.split("\n").map { it.trim() }
                if (lines.none { it.isNotEmpty() }) {
                    return@withContext Result.failure(Exception("No data in spreadsheet file"))
                }
                val scanLines = lines.filter { it.isNotEmpty() }.take(20)
                val tabCount = scanLines.sumOf { line -> line.count { it == '\t' } }
                val commaCount = scanLines.sumOf { line -> line.count { it == ',' } }
                val delimiter = if (tabCount > commaCount) "\t" else ","
                lines.map { if (it.isEmpty()) emptyList() else parseCsvLine(it, delimiter) }
            }

            if (parsedSheetData.isEmpty() || parsedSheetData.none { it.isNotEmpty() }) {
                return@withContext Result.failure(Exception("Could not parse spreadsheet data. Please ensure it is a valid .csv, .tsv, or .xlsx file."))
            }

            val localPreviewText = formatFirstRowsPreview(parsedSheetData)
            _firstRowPreview.value = localPreviewText

            val headerResult = findHeaderRow(parsedSheetData)
            val headerRowIndex = headerResult.headerRowIndex

            if (headerRowIndex == -1) {
                return@withContext Result.failure(Exception("Could not locate the header row! Ensure your spreadsheet contains column headers for Handled By (or Name) and Amount Paid.\n\nFirst Row(s) Content Read:\n$localPreviewText"))
            }

            val missingColumns = mutableListOf<String>()
            if (headerResult.nameIdx == -1) missingColumns.add("Handled By (or Name)")
            if (headerResult.amountIdx == -1) missingColumns.add("Amount Paid")

            if (missingColumns.isNotEmpty()) {
                val errorMsg = "Critical column headers missing: ${missingColumns.joinToString(", ")}. " +
                        "Please ensure your spreadsheet contains all required columns: Handled By (or Name) and Amount Paid.\n\nFirst Row(s) Content Read:\n$localPreviewText"
                return@withContext Result.failure(Exception(errorMsg))
            }

            val fileSpreadsheetId = "uploaded_file"
            val newConfig = SheetConfig(
                spreadsheetUrl = "Local Upload: $fileName",
                spreadsheetId = fileSpreadsheetId,
                sheetName = fileName.take(minOf(24, fileName.length)),
                ownerPin = "1234",
                isVerified = true,
                useLocalDemo = false,
                lastSyncTime = System.currentTimeMillis()
            )

            val cachedPaidKeys = seedAndFetchPaidCache()
            val parsedRows = parseDataRows(parsedSheetData, headerResult, fileSpreadsheetId, cachedPaidKeys)

            if (parsedRows.isEmpty()) {
                val errorMsg = "No valid data rows could be parsed. " +
                        "Please verify that: \n" +
                        "1. Your data rows are below the header row (found at row ${headerRowIndex + 1}).\n" +
                        "2. Every row contains valid values for Handled By (or Name) and a numeric Amount Paid."
                return@withContext Result.failure(Exception(errorMsg))
            }

            paymentDao.insertConfig(newConfig)
            paymentDao.clearPaymentsForSpreadsheet(fileSpreadsheetId)
            paymentDao.insertPayments(parsedRows)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("PaymentRepository", "Error parsing local spreadsheet file", e)
            Result.failure(e)
        }
    }

    suspend fun setVerified(isVerified: Boolean) {
        paymentDao.updateConfigVerified(isVerified)
    }

    /**
     * Fetch and parse the spreadsheet data from "Service Ledger" worksheet.
     * Uses the hardcoded GID (155371327) to ensure we always get the correct worksheet.
     * No Google Sheets API key is required - works with "Anyone with the link" sharing.
     */
    suspend fun refreshSheetData(
    onProgress: (milestone: String, progress: Float) -> Unit = { _, _ -> }
): Result<Unit> = withContext(Dispatchers.IO) {
    val config = paymentDao.getActiveConfig() ?: return@withContext Result.failure(Exception("No configuration set up"))

    if (config.useLocalDemo && config.spreadsheetId == "demo_spreadsheet") {
        onProgress("Using local demo data...", 0.1f)
        kotlinx.coroutines.delay(250)
        onProgress("Data loaded successfully", 1.0f)
        return@withContext Result.success(Unit)
    }

    val spreadsheetId = config.spreadsheetId

    if (spreadsheetId.isBlank() || spreadsheetId == "demo_spreadsheet") {
        return@withContext Result.failure(Exception("Invalid spreadsheet ID. Please check your configuration."))
    }

    var parsedSheetData: List<List<String>>? = null
    var fetchErrorDetail = ""
    var successfulMethod = ""

    onProgress("Connecting to spreadsheet...", 0.1f)
    onProgress("Fetching worksheet at GID $SERVICE_LEDGER_GID...", 0.2f)

    // --- Fetch explicitly by the known GID. This GID is treated as authoritative:
    //     we don't cross-check it against header heuristics or fall back to a
    //     name-based lookup. We only try multiple URL *forms* of the same GID,
    //     since Google occasionally blocks one endpoint (e.g. export) while the
    //     gviz endpoint still works, or vice versa. ---
    val gidUrls = listOf(
        "https://docs.google.com/spreadsheets/d/$spreadsheetId/export?format=csv&gid=$SERVICE_LEDGER_GID",
        "https://docs.google.com/spreadsheets/d/$spreadsheetId/gviz/tq?tqx=out:csv&gid=$SERVICE_LEDGER_GID",
        "https://docs.google.com/spreadsheets/d/$spreadsheetId/export?gid=$SERVICE_LEDGER_GID&format=csv"
    )

    for (url in gidUrls) {
        Log.d("PaymentRepository", "Fetching GID URL: $url")
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    fetchErrorDetail = "HTTP ${response.code}: ${response.message}"
                    return@use
                }

                val content = response.body?.string() ?: ""
                val trimmed = content.trim()

                if (trimmed.isBlank()) {
                    fetchErrorDetail = "GID $SERVICE_LEDGER_GID returned an empty response."
                    return@use
                }
                if (trimmed.startsWith("<") || trimmed.contains("<!DOCTYPE html", ignoreCase = true) ||
                    trimmed.contains("<html", ignoreCase = true) || trimmed.contains("Sign in", ignoreCase = true)) {
                    fetchErrorDetail = "GID $SERVICE_LEDGER_GID returned an HTML/login page — " +
                            "the sheet is likely not shared as 'Anyone with the link can view'."
                    return@use
                }

                // Proper CSV parse that respects quoted newlines, instead of the old
                // naive content.split("\n") which breaks on any multi-line cell.
                val parsed = parseCsvContent(content)
                if (parsed.isEmpty() || parsed.all { it.isEmpty() || it.all { c -> c.isBlank() } }) {
                    fetchErrorDetail = "GID $SERVICE_LEDGER_GID returned no usable rows."
                    return@use
                }

                parsedSheetData = parsed
                successfulMethod = "CSV Export (explicit GID: $SERVICE_LEDGER_GID) via $url"
                onProgress("Data retrieved from GID $SERVICE_LEDGER_GID", 0.5f)
                Log.d("PaymentRepository", "Successfully retrieved ${parsed.size} rows via GID $SERVICE_LEDGER_GID")
            }
        } catch (e: Exception) {
            Log.w("PaymentRepository", "GID fetch attempt failed: ${e.message}")
            if (fetchErrorDetail.isBlank()) fetchErrorDetail = e.localizedMessage ?: "Unknown network error"
        }

        if (parsedSheetData != null) break
    }

    val sheetData = parsedSheetData

    if (sheetData == null || sheetData.isEmpty()) {
        val errorMsg = buildString {
            append("❌ Failed to fetch worksheet at GID $SERVICE_LEDGER_GID.\n\n")
            append("🔑 REQUIRED: Your Google Sheet must be publicly shared (Anyone with the link can view/edit).\n\n")
            if (fetchErrorDetail.isNotBlank()) {
                append("📌 Error details: $fetchErrorDetail\n\n")
            }
            append("💡 Troubleshooting:\n")
            append("1. Confirm GID $SERVICE_LEDGER_GID is correct — open the tab in a browser and check the ?gid= value in the URL bar.\n")
            append("2. Check that the tab is not hidden or protected.\n")
            append("3. Ensure the workbook sharing is set to 'Anyone with the link can view'.\n")
            append("4. Try the direct 'Download Worksheet from Online URL' option in Settings as an alternative.")
        }
        return@withContext Result.failure(Exception(errorMsg))
    }

    onProgress("Processing data...", 0.6f)

    val remotePreviewText = formatFirstRowsPreview(sheetData)
    _firstRowPreview.value = remotePreviewText

    try {
        // No coarse pre-check here — findHeaderRow() below scans up to 100 rows and
        // scores candidates directly, which is more reliable than a blunt "does the
        // first row contain these substrings" gate. Since we trust the GID, let this
        // be the single source of truth for whether the data is usable.
        val headerResult = findHeaderRow(sheetData)
        val headerRowIndex = headerResult.headerRowIndex

        if (headerRowIndex == -1) {
            val errorMsg = "❌ Could not find the header row in the worksheet at GID $SERVICE_LEDGER_GID.\n\n" +
                    "Ensure your sheet has a header row with these columns:\n" +
                    "• Date\n• Section\n• Service Done\n• Handled By\n• Amount Paid (KES)\n" +
                    "• Payment Method\n• Commission %\n• Staff Commission\n• Salon Share\n• Paid\n• Notes\n• Month\n\n" +
                    "First rows of data:\n$remotePreviewText"
            return@withContext Result.failure(Exception(errorMsg))
        }

        val missingColumns = mutableListOf<String>()
        if (headerResult.nameIdx == -1) missingColumns.add("Handled By (or Name)")
        if (headerResult.amountIdx == -1) missingColumns.add("Amount Paid (KES)")

        if (missingColumns.isNotEmpty()) {
            val errorMsg = "❌ Critical column headers missing at GID $SERVICE_LEDGER_GID: ${missingColumns.joinToString(", ")}.\n\n" +
                    "Found header row at position ${headerRowIndex + 1}:\n" +
                    "${sheetData[headerRowIndex].joinToString(" | ")}\n\n" +
                    "Please ensure that tab has the required columns."
            return@withContext Result.failure(Exception(errorMsg))
        }

        onProgress("Parsing rows...", 0.8f)

        val cachedPaidKeys = seedAndFetchPaidCache()
        val parsedRows = parseDataRows(sheetData, headerResult, spreadsheetId, cachedPaidKeys)

        if (parsedRows.isEmpty()) {
            val errorMsg = "❌ No valid data rows found at GID $SERVICE_LEDGER_GID.\n\n" +
                    "First 5 rows of data:\n" +
                    sheetData.take(5).joinToString("\n") { row -> row.joinToString(" | ") }
            return@withContext Result.failure(Exception(errorMsg))
        }

        onProgress("Saving data...", 0.9f)

        paymentDao.clearPaymentsForSpreadsheet(spreadsheetId)
        paymentDao.insertPayments(parsedRows)

        onProgress("Fetching Expenses sheet at GID $EXPENSES_GID...", 0.95f)
        try {
            refreshExpensesSheetDataInternal(spreadsheetId)
        } catch (e: Exception) {
            Log.w("PaymentRepository", "Failed to sync expenses GID $EXPENSES_GID: ${e.message}")
        }

        val updatedConfig = config.copy(
            lastSyncTime = System.currentTimeMillis(),
            useLocalDemo = false,
            isVerified = true
        )
        paymentDao.insertConfig(updatedConfig)

        onProgress("✅ Sync completed! ${parsedRows.size} rows loaded (via $successfulMethod)", 1.0f)
        Log.d("PaymentRepository", "Successfully synced ${parsedRows.size} rows using $successfulMethod")

        return@withContext Result.success(Unit)

    } catch (e: Exception) {
        Log.e("PaymentRepository", "Error processing spreadsheet data", e)
        return@withContext Result.failure(Exception("Data processing failed: ${e.localizedMessage}"))
    }
}

/**
 * RFC4180-style CSV parser that correctly handles quoted fields containing
 * commas, newlines, and escaped double-quotes ("" inside a quoted field).
 * Replaces the old approach of splitting the whole response on "\n" first,
 * which corrupted any row whose cell (e.g. Notes) contained a real newline.
 */
private fun parseCsvContent(content: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    val currentRow = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var i = 0
    val len = content.length

    while (i < len) {
        val c = content[i]
        when {
            inQuotes -> {
                if (c == '"') {
                    if (i + 1 < len && content[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(c)
                }
            }
            c == '"' -> inQuotes = true
            c == ',' -> {
                currentRow.add(field.toString().trim())
                field.setLength(0)
            }
            c == '\r' -> { /* skip, \n handles the line break */ }
            c == '\n' -> {
                currentRow.add(field.toString().trim())
                field.setLength(0)
                rows.add(currentRow.toList())
                currentRow.clear()
            }
            else -> field.append(c)
        }
        i++
    }
    // last field/row if content doesn't end with a newline
    if (field.isNotEmpty() || currentRow.isNotEmpty()) {
        currentRow.add(field.toString().trim())
        rows.add(currentRow.toList())
    }

    return rows.filter { row -> row.any { it.isNotBlank() } || row.isEmpty() }
}

    private fun findHeaderRow(sheetData: List<List<String>>): HeaderResult {
        var headerRowIndex = -1
        var nameIdx = -1
        var serviceNameIdx = -1
        var amountIdx = -1
        var timestampIdx = -1
        var sectionIdx = -1
        var paymentMethodIdx = -1
        var notesIdx = -1
        var paidIdx = -1
        var commissionPctIdx = -1
        var staffCommissionIdx = -1
        var salonShareIdx = -1
        var monthIdx = -1

        var maxScore = -1
        val scanLimit = minOf(sheetData.size, 100)

        for (rIdx in 0 until scanLimit) {
            val row = sheetData[rIdx]
            var foundName = -1
            var foundService = -1
            var foundAmount = -1
            var foundSection = -1
            var foundPaymentMethod = -1
            var foundPaid = -1
            var foundNotes = -1
            var foundTimestamp = -1
            var foundCommissionPct = -1
            var foundStaffCommission = -1
            var foundSalonShare = -1
            var foundMonth = -1

            for ((cIdx, valStr) in row.withIndex()) {
                val lower = normalizeHeaderCell(valStr)

                when (lower) {
                    "date", "timestamp" -> foundTimestamp = cIdx
                    "section" -> foundSection = cIdx
                    "service done", "service", "service name" -> foundService = cIdx
                    "handled by", "staff", "employee", "name" -> foundName = cIdx
                    "amount paid (kes)", "amount paid", "amount", "kes" -> foundAmount = cIdx
                    "payment method", "method", "payment" -> foundPaymentMethod = cIdx
                    "commission %", "commission", "comm %" -> foundCommissionPct = cIdx
                    "staff commission", "staff comm" -> foundStaffCommission = cIdx
                    "salon share", "salon" -> foundSalonShare = cIdx
                    "paid", "status" -> foundPaid = cIdx
                    "notes", "comment", "remarks" -> foundNotes = cIdx
                    "month" -> foundMonth = cIdx
                }

                if (foundTimestamp == -1 && (lower.contains("date") || lower.contains("time"))) {
                    foundTimestamp = cIdx
                }
                if (foundName == -1 && (lower.contains("handled") || lower.contains("name") || lower.contains("staff"))) {
                    foundName = cIdx
                }
                if (foundAmount == -1 && (lower.contains("amount") || lower.contains("paid") || lower.contains("kes"))) {
                    foundAmount = cIdx
                }
                if (foundSection == -1 && (lower.contains("section") || lower.contains("category"))) {
                    foundSection = cIdx
                }
                if (foundPaymentMethod == -1 && (lower.contains("method") || lower.contains("payment"))) {
                    foundPaymentMethod = cIdx
                }
                if (foundPaid == -1 && (lower.contains("paid") || lower.contains("status"))) {
                    foundPaid = cIdx
                }
            }

            var score = 0
            if (foundName != -1) score += 5
            if (foundService != -1) score += 5
            if (foundAmount != -1) score += 5
            if (foundTimestamp != -1) score += 5
            if (foundSection != -1) score += 4
            if (foundPaymentMethod != -1) score += 4
            if (foundPaid != -1) score += 4
            if (foundCommissionPct != -1) score += 4
            if (foundStaffCommission != -1) score += 4
            if (foundSalonShare != -1) score += 4
            if (foundNotes != -1) score += 4
            if (foundMonth != -1) score += 4

            val hasCritical = (foundName != -1 && foundAmount != -1)
            val isValidCandidate = hasCritical || (score >= 10 && (foundName != -1 || foundAmount != -1))

            if (isValidCandidate && score > maxScore) {
                maxScore = score
                headerRowIndex = rIdx
                nameIdx = foundName
                serviceNameIdx = foundService
                amountIdx = foundAmount
                sectionIdx = foundSection
                paymentMethodIdx = foundPaymentMethod
                paidIdx = foundPaid
                notesIdx = foundNotes
                timestampIdx = foundTimestamp
                commissionPctIdx = foundCommissionPct
                staffCommissionIdx = foundStaffCommission
                salonShareIdx = foundSalonShare
                monthIdx = foundMonth
            }
        }

        return HeaderResult(
            headerRowIndex,
            nameIdx,
            serviceNameIdx,
            amountIdx,
            timestampIdx,
            sectionIdx,
            paymentMethodIdx,
            notesIdx,
            paidIdx,
            commissionPctIdx,
            staffCommissionIdx,
            salonShareIdx,
            monthIdx
        )
    }

    private fun parseDataRows(
        sheetData: List<List<String>>,
        headerResult: HeaderResult,
        spreadsheetId: String,
        cachedPaidKeys: Set<String> = emptySet()
    ): List<PaymentRow> {
        val parsedRows = mutableListOf<PaymentRow>()
        val seenRows = mutableSetOf<String>()

        for (rowIndex in (headerResult.headerRowIndex + 1) until sheetData.size) {
            val cols = sheetData[rowIndex]

            if (cols.isEmpty() || cols.all { it.isBlank() }) continue

            val rawName = if (headerResult.nameIdx != -1 && headerResult.nameIdx < cols.size) {
                cols[headerResult.nameIdx].trim()
            } else ""
            if (rawName.isBlank()) continue

            val name = normalizeEmployeeName(rawName)
            if (name.isBlank() || name == "0" || name.equals("o", ignoreCase = true) || name.equals("0.0", ignoreCase = true)) continue

            val amountStr = if (headerResult.amountIdx != -1 && headerResult.amountIdx < cols.size) {
                cols[headerResult.amountIdx].trim()
            } else ""
            val amountPaid = cleanAndParseDouble(amountStr) ?: 0.0

            val rowKey = "$name|$amountPaid|${rowIndex}"
            if (seenRows.contains(rowKey)) continue
            seenRows.add(rowKey)

            val sectionRaw = if (headerResult.sectionIdx != -1 && headerResult.sectionIdx < cols.size) {
                cols[headerResult.sectionIdx].trim()
            } else ""
            val section = when {
                sectionRaw.isBlank() -> "General"
                sectionRaw.lowercase().contains("hair") -> "Hair"
                sectionRaw.lowercase().contains("nail") -> "Nails"
                sectionRaw.lowercase().contains("massage") -> "Massage"
                sectionRaw.lowercase().contains("wax") -> "Waxing"
                else -> sectionRaw.toTitleCase()
            }

            val timestamp = if (headerResult.timestampIdx != -1 && headerResult.timestampIdx < cols.size) {
                cols[headerResult.timestampIdx].trim()
            } else ""
            val finalTimestamp = if (timestamp.isNotBlank()) timestamp else "Row ${rowIndex + 1}"

            val serviceName = if (headerResult.serviceNameIdx != -1 && headerResult.serviceNameIdx < cols.size) {
                cols[headerResult.serviceNameIdx].trim()
            } else ""
            val finalServiceName = if (serviceName.isNotBlank()) serviceName else "Service"

            val paymentMethod = if (headerResult.paymentMethodIdx != -1 && headerResult.paymentMethodIdx < cols.size) {
                cols[headerResult.paymentMethodIdx].trim()
            } else ""
            val finalPaymentMethod = if (paymentMethod.isNotBlank()) paymentMethod else "Cash"

            val notes = if (headerResult.notesIdx != -1 && headerResult.notesIdx < cols.size) {
                cols[headerResult.notesIdx].trim()
            } else ""

            var commissionPct = 0.0
            var staffCommission = 0.0
            var salonShare = 0.0

            if (headerResult.staffCommissionIdx != -1 && headerResult.staffCommissionIdx < cols.size) {
                val staffCommStr = cols[headerResult.staffCommissionIdx].trim()
                val staffComm = cleanAndParseDouble(staffCommStr)
                if (staffComm != null && staffComm > 0 && amountPaid > 0) {
                    staffCommission = staffComm
                    commissionPct = staffComm / amountPaid
                    salonShare = amountPaid - staffCommission
                }
            }

            if (commissionPct == 0.0 && headerResult.commissionPctIdx != -1 && headerResult.commissionPctIdx < cols.size) {
                val pctStr = cols[headerResult.commissionPctIdx].trim()
                commissionPct = parseCommissionPct(pctStr)
                staffCommission = amountPaid * commissionPct
                salonShare = amountPaid - staffCommission
            }

            if (salonShare == 0.0 && headerResult.salonShareIdx != -1 && headerResult.salonShareIdx < cols.size) {
                val salonShareStr = cols[headerResult.salonShareIdx].trim()
                val salonShareVal = cleanAndParseDouble(salonShareStr)
                if (salonShareVal != null && salonShareVal > 0 && amountPaid > salonShareVal) {
                    salonShare = salonShareVal
                    staffCommission = amountPaid - salonShare
                    commissionPct = if (amountPaid > 0) staffCommission / amountPaid else 0.0
                }
            }

            if (commissionPct == 0.0 && staffCommission == 0.0 && amountPaid > 0) {
                commissionPct = 0.30
                staffCommission = amountPaid * commissionPct
                salonShare = amountPaid - staffCommission
            }

            val paidVal = if (headerResult.paidIdx != -1 && headerResult.paidIdx < cols.size) {
                cols[headerResult.paidIdx].trim().lowercase()
            } else ""
            val isSheetPaid = when (paidVal) {
                "true", "1", "yes", "paid", "t", "✓", "x", "✔" -> true
                "false", "0", "no", "unpaid", "f", "" -> false
                else -> false
            }

            val candidateKeys = generatePaidCacheKeys(
                spreadsheetId = spreadsheetId,
                rowIndex = rowIndex + 1,
                name = name,
                amountPaid = amountPaid,
                serviceName = finalServiceName,
                timestamp = finalTimestamp
            )
            val isCachedPaid = candidateKeys.any { cachedPaidKeys.contains(it) }
            val paid = isSheetPaid || isCachedPaid

            val month = if (headerResult.monthIdx != -1 && headerResult.monthIdx < cols.size) {
                cols[headerResult.monthIdx].trim()
            } else ""
            val finalMonth = if (month.isNotBlank()) month else {
                val ms = parseTimestampToMillis(finalTimestamp)
                if (ms > 0L) {
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
                    val monthFull = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
                    monthFull[cal.get(java.util.Calendar.MONTH)]
                } else {
                    val timestampLower = finalTimestamp.lowercase()
                    when {
                        timestampLower.contains("jan") -> "January"
                        timestampLower.contains("feb") -> "February"
                        timestampLower.contains("mar") -> "March"
                        timestampLower.contains("apr") -> "April"
                        timestampLower.contains("may") -> "May"
                        timestampLower.contains("jun") -> "June"
                        timestampLower.contains("jul") -> "July"
                        timestampLower.contains("aug") -> "August"
                        timestampLower.contains("sep") -> "September"
                        timestampLower.contains("oct") -> "October"
                        timestampLower.contains("nov") -> "November"
                        timestampLower.contains("dec") -> "December"
                        else -> "Unknown"
                    }
                }
            }

            parsedRows.add(
                PaymentRow(
                    spreadsheetId = spreadsheetId,
                    rowIndex = rowIndex + 1,
                    timestamp = finalTimestamp,
                    name = name,
                    section = section,
                    serviceName = finalServiceName,
                    amountPaid = amountPaid,
                    paymentMethod = finalPaymentMethod,
                    commissionPct = commissionPct,
                    staffCommission = staffCommission,
                    salonShare = salonShare,
                    notes = notes,
                    paid = paid,
                    month = finalMonth
                )
            )
        }

        return parsedRows
    }

    suspend fun markRowAsPaid(payment: PaymentRow, webhookUrl: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val updatedPayment = payment.copy(paid = true)
            paymentDao.updatePayment(updatedPayment)

            // Cache paid status locally in paid_records_cache
            val cacheKeys = generatePaidCacheKeys(
                payment.spreadsheetId,
                payment.rowIndex,
                payment.name,
                payment.amountPaid,
                payment.serviceName,
                payment.timestamp
            )
            val cacheEntries = cacheKeys.map { key ->
                PaidRecordCache(
                    recordKey = key,
                    spreadsheetId = payment.spreadsheetId,
                    rowIndex = payment.rowIndex,
                    name = payment.name,
                    amountPaid = payment.amountPaid,
                    serviceName = payment.serviceName,
                    timestamp = payment.timestamp
                )
            }
            paymentDao.insertPaidCacheList(cacheEntries)

            if (!webhookUrl.isNullOrBlank() && payment.spreadsheetId != "demo_spreadsheet") {
                val config = paymentDao.getActiveConfig()
                val currentSheetName = "Service Ledger"
                val json = """
                    {
                      "action": "mark_as_paid",
                      "spreadsheetId": "${payment.spreadsheetId}",
                      "sheetName": "$currentSheetName",
                      "rowIndex": ${payment.rowIndex},
                      "name": "${payment.name}",
                      "section": "${payment.section}",
                      "serviceName": "${payment.serviceName}",
                      "amountPaid": ${payment.amountPaid},
                      "paid": true
                    }
                """.trimIndent()

                val requestBody = json.toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url(webhookUrl)
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e("PaymentRepository", "Webhook update failed with code: ${response.code}")
                        return@withContext Result.failure(IOException("Database updated locally, but Google Sheet webhook update failed (HTTP ${response.code})."))
                    }
                }
            }

            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.e("PaymentRepository", "Error marking row as paid", e)
            return@withContext Result.failure(e)
        }
    }

    suspend fun markRowAsUnpaid(payment: PaymentRow): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val updatedPayment = payment.copy(paid = false)
            paymentDao.updatePayment(updatedPayment)

            val cacheKeys = generatePaidCacheKeys(
                payment.spreadsheetId,
                payment.rowIndex,
                payment.name,
                payment.amountPaid,
                payment.serviceName,
                payment.timestamp
            )
            for (key in cacheKeys) {
                paymentDao.deletePaidCache(key)
            }
            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.e("PaymentRepository", "Error marking row as unpaid", e)
            return@withContext Result.failure(e)
        }
    }

    suspend fun clearAll() {
        paymentDao.clearAllPayments()
    }

    // ============= XLSX Parsing Utilities =============

    data class XlsxSheet(val name: String, val rId: String)

    private fun parseWorkbookSheets(bytes: ByteArray): List<XlsxSheet> {
        val list = mutableListOf<XlsxSheet>()
        try {
            val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(java.io.ByteArrayInputStream(bytes), "UTF-8")

            var eventType = parser.eventType
            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "sheet") {
                    val name = parser.getAttributeValue(null, "name") ?: ""
                    var rId = ""
                    for (i in 0 until parser.attributeCount) {
                        val attrName = parser.getAttributeName(i)
                        if (attrName.contains("id") || attrName.endsWith("id")) {
                            rId = parser.getAttributeValue(i)
                        }
                    }
                    if (name.isNotEmpty() && rId.isNotEmpty()) {
                        list.add(XlsxSheet(name, rId))
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("PaymentRepository", "Error parsing workbook sheets", e)
        }
        return list
    }

    private fun parseWorkbookRels(bytes: ByteArray): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(java.io.ByteArrayInputStream(bytes), "UTF-8")

            var eventType = parser.eventType
            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                if (eventType == org.xmlpull.v1.XmlPullParser.START_TAG && parser.name == "Relationship") {
                    val id = parser.getAttributeValue(null, "Id") ?: ""
                    val target = parser.getAttributeValue(null, "Target") ?: ""
                    if (id.isNotEmpty() && target.isNotEmpty()) {
                        map[id] = target
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("PaymentRepository", "Error parsing workbook rels", e)
        }
        return map
    }

    private fun parseXlsxData(bytes: ByteArray): List<List<String>> {
        val resultRows = mutableListOf<List<String>>()
        try {
            val zipEntries = mutableMapOf<String, ByteArray>()
            val zipInputStream = java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(bytes))
            var entry = zipInputStream.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name == "xl/sharedStrings.xml" ||
                    name == "xl/workbook.xml" ||
                    name == "xl/_rels/workbook.xml.rels" ||
                    (name.startsWith("xl/worksheets/") && name.endsWith(".xml"))) {
                    zipEntries[name] = readAllBytes(zipInputStream)
                }
                zipInputStream.closeEntry()
                entry = zipInputStream.nextEntry
            }

            var sharedStrings = listOf<String>()
            val sharedStringsBytes = zipEntries["xl/sharedStrings.xml"]
            if (sharedStringsBytes != null) {
                sharedStrings = parseSharedStrings(sharedStringsBytes)
            }

            val workbookBytes = zipEntries["xl/workbook.xml"]
            val relsBytes = zipEntries["xl/_rels/workbook.xml.rels"]
            var sheetBytes: ByteArray? = null
            val targetSheetName = "Service Ledger"

            if (workbookBytes != null && relsBytes != null) {
                val sheets = parseWorkbookSheets(workbookBytes)
                val rels = parseWorkbookRels(relsBytes)

                val targetSheet = sheets.find { it.name.trim().lowercase() == targetSheetName.lowercase() }
                    ?: sheets.find { it.name.trim().lowercase().contains(targetSheetName.lowercase()) }
                    ?: sheets.find { it.name.trim().lowercase().contains("ledger") }
                    ?: sheets.find { it.name.trim().lowercase().contains("service") }
                    ?: sheets.firstOrNull()

                if (targetSheet != null) {
                    val relTarget = rels[targetSheet.rId]
                    if (relTarget != null) {
                        var entryPath = relTarget
                        if (!entryPath.startsWith("xl/")) {
                            if (entryPath.startsWith("/xl/")) {
                                entryPath = entryPath.substring(1)
                            } else if (entryPath.startsWith("/")) {
                                entryPath = "xl" + entryPath
                            } else {
                                entryPath = "xl/" + entryPath
                            }
                        }
                        sheetBytes = zipEntries[entryPath]
                        Log.d("PaymentRepository", "Found target sheet '${targetSheet.name}'")
                    }
                }
            }

            if (sheetBytes == null) {
                val fallbackKey = zipEntries.keys.find { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }
                if (fallbackKey != null) {
                    sheetBytes = zipEntries[fallbackKey]
                    Log.d("PaymentRepository", "Fallback: using worksheet from $fallbackKey")
                }
            }

            if (sheetBytes != null) {
                return parseSheet(sheetBytes, sharedStrings)
            }
        } catch (e: Exception) {
            Log.e("PaymentRepository", "Error parsing XLSX bytes", e)
        }
        return resultRows
    }

    private fun readAllBytes(inputStream: java.io.InputStream): ByteArray {
        val buffer = java.io.ByteArrayOutputStream()
        val data = ByteArray(16384)
        var nRead: Int
        while (inputStream.read(data, 0, data.size).also { nRead = it } != -1) {
            buffer.write(data, 0, nRead)
        }
        return buffer.toByteArray()
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val list = mutableListOf<String>()
        try {
            val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(java.io.ByteArrayInputStream(bytes), "UTF-8")

            var eventType = parser.eventType
            var inSi = false
            var inT = false
            val siAccumulator = StringBuilder()

            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        val name = parser.name
                        if (name == "si") {
                            inSi = true
                            siAccumulator.setLength(0)
                        } else if (name == "t" && inSi) {
                            inT = true
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.TEXT -> {
                        if (inT) {
                            siAccumulator.append(parser.text)
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        val name = parser.name
                        if (name == "t") {
                            inT = false
                        } else if (name == "si") {
                            inSi = false
                            list.add(siAccumulator.toString())
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("PaymentRepository", "Error parsing shared strings", e)
        }
        return list
    }

    private fun colRefToIndex(colRef: String): Int {
        var index = 0
        for (char in colRef) {
            if (char in 'A'..'Z') {
                index = index * 26 + (char - 'A' + 1)
            }
        }
        return index - 1
    }

    private fun getColIndexFromCellRef(cellRef: String): Int {
        val letters = cellRef.takeWhile { it.isLetter() }.uppercase()
        if (letters.isEmpty()) return -1
        return colRefToIndex(letters)
    }

    private fun parseSheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val resultRows = mutableListOf<List<String>>()
        try {
            val factory = org.xmlpull.v1.XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(java.io.ByteArrayInputStream(bytes), "UTF-8")

            var eventType = parser.eventType
            var currentCols = mutableMapOf<Int, String>()
            var cellRef = ""
            var cellType = ""
            var inV = false
            val vAccumulator = StringBuilder()
            var currentRowNumber = 0

            while (eventType != org.xmlpull.v1.XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    org.xmlpull.v1.XmlPullParser.START_TAG -> {
                        val name = parser.name
                        if (name == "row") {
                            currentCols.clear()
                            val rStr = parser.getAttributeValue(null, "r")
                            currentRowNumber = rStr?.toIntOrNull() ?: (currentRowNumber + 1)
                        } else if (name == "c") {
                            cellRef = parser.getAttributeValue(null, "r") ?: ""
                            cellType = parser.getAttributeValue(null, "t") ?: ""
                        } else if (name == "v" || name == "t") {
                            inV = true
                            vAccumulator.setLength(0)
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.TEXT -> {
                        if (inV) {
                            vAccumulator.append(parser.text)
                        }
                    }
                    org.xmlpull.v1.XmlPullParser.END_TAG -> {
                        val name = parser.name
                        if (name == "v" || name == "t") {
                            inV = false
                            val rawVal = vAccumulator.toString()
                            val finalVal = if (cellType == "s") {
                                val strIdx = rawVal.toIntOrNull()
                                if (strIdx != null && strIdx >= 0 && strIdx < sharedStrings.size) {
                                    sharedStrings[strIdx]
                                } else {
                                    rawVal
                                }
                            } else {
                                rawVal
                            }
                            val colIdx = getColIndexFromCellRef(cellRef)
                            if (colIdx != -1) {
                                currentCols[colIdx] = finalVal
                            }
                        } else if (name == "row") {
                            while (resultRows.size < currentRowNumber - 1) {
                                resultRows.add(emptyList())
                            }
                            if (currentCols.isNotEmpty()) {
                                val maxCol = currentCols.keys.maxOrNull() ?: -1
                                val rowList = ArrayList<String>(maxCol + 1)
                                for (i in 0..maxCol) {
                                    rowList.add(currentCols[i] ?: "")
                                }
                                resultRows.add(rowList)
                            } else {
                                resultRows.add(emptyList())
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("PaymentRepository", "Error parsing sheet XML", e)
        }
        return resultRows
    }

    private fun cleanAndParseDouble(str: String): Double? {
        val clean = str.replace(Regex("[^0-9.-]"), "").trim()
        return clean.toDoubleOrNull()
    }

    private fun parseCsvLine(line: String, delimiter: String): List<String> {
        if (delimiter == "\t") {
            return line.split("\t").map { it.replace("\"", "").trim() }
        }
        val result = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                inQuotes = !inQuotes
            } else if (c == ',' && !inQuotes) {
                result.add(cur.toString().trim())
                cur.setLength(0)
            } else {
                cur.append(c)
            }
            i++
        }
        result.add(cur.toString().trim())
        return result
    }

    private fun String.toTitleCase(): String {
        return this.split(" ")
            .filter { it.isNotEmpty() }
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
            }
    }

    private data class HeaderResult(
        val headerRowIndex: Int,
        val nameIdx: Int,
        val serviceNameIdx: Int,
        val amountIdx: Int,
        val timestampIdx: Int,
        val sectionIdx: Int,
        val paymentMethodIdx: Int,
        val notesIdx: Int,
        val paidIdx: Int,
        val commissionPctIdx: Int,
        val staffCommissionIdx: Int,
        val salonShareIdx: Int,
        val monthIdx: Int
    )

    // --- Expense Sync & Helper Methods ---
    suspend fun addExpense(expense: ExpenseRow) {
        withContext(Dispatchers.IO) {
            paymentDao.insertExpense(expense)
        }
    }

    suspend fun deleteExpense(id: Long) {
        withContext(Dispatchers.IO) {
            paymentDao.deleteExpenseById(id)
        }
    }

    private suspend fun refreshExpensesSheetDataInternal(
        spreadsheetId: String
    ) = withContext(Dispatchers.IO) {
        if (spreadsheetId.isBlank() || spreadsheetId == "demo_spreadsheet") return@withContext

        var parsedSheetData: List<List<String>>? = null

        val gidUrls = listOf(
            "https://docs.google.com/spreadsheets/d/$spreadsheetId/export?format=csv&gid=$EXPENSES_GID",
            "https://docs.google.com/spreadsheets/d/$spreadsheetId/gviz/tq?tqx=out:csv&gid=$EXPENSES_GID",
            "https://docs.google.com/spreadsheets/d/$spreadsheetId/export?gid=$EXPENSES_GID&format=csv"
        )

        for (url in gidUrls) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val content = response.body?.string() ?: ""
                    val trimmed = content.trim()
                    if (trimmed.isBlank() || trimmed.startsWith("<") || trimmed.contains("<!DOCTYPE html", ignoreCase = true)) return@use

                    val parsed = parseCsvContent(content)
                    if (parsed.isNotEmpty()) {
                        parsedSheetData = parsed
                    }
                }
            } catch (e: Exception) {
                Log.w("PaymentRepository", "Expenses fetch failed for $url: ${e.message}")
            }
            if (parsedSheetData != null) break
        }

        val sheetData = parsedSheetData ?: return@withContext

        val headerResult = findExpensesHeaderRow(sheetData)
        if (headerResult.headerRowIndex == -1) {
            Log.w("PaymentRepository", "No valid header found for Expenses sheet at GID $EXPENSES_GID")
            return@withContext
        }

        val parsedExpenseRows = parseExpenseDataRows(sheetData, headerResult, spreadsheetId)
        if (parsedExpenseRows.isNotEmpty()) {
            paymentDao.clearExpensesForSpreadsheet(spreadsheetId)
            paymentDao.insertExpenses(parsedExpenseRows)
            Log.d("PaymentRepository", "Successfully synced ${parsedExpenseRows.size} expense rows from GID $EXPENSES_GID")
        }
    }

    private data class ExpensesHeaderResult(
        val headerRowIndex: Int = -1,
        val dateIdx: Int = -1,
        val recordedByIdx: Int = -1,
        val departmentIdx: Int = -1,
        val expenseTypeIdx: Int = -1,
        val itemPurchasedIdx: Int = -1,
        val quantityIdx: Int = -1,
        val amountSpentIdx: Int = -1,
        val paymentMethodIdx: Int = -1,
        val monthIdx: Int = -1
    )

    private fun findExpensesHeaderRow(sheetData: List<List<String>>): ExpensesHeaderResult {
        var maxScore = -1
        var bestResult = ExpensesHeaderResult()
        val scanLimit = minOf(sheetData.size, 100)

        for (rIdx in 0 until scanLimit) {
            val row = sheetData[rIdx]
            var dateIdx = -1
            var recordedByIdx = -1
            var departmentIdx = -1
            var expenseTypeIdx = -1
            var itemPurchasedIdx = -1
            var quantityIdx = -1
            var amountSpentIdx = -1
            var paymentMethodIdx = -1
            var monthIdx = -1

            for ((cIdx, valStr) in row.withIndex()) {
                val lower = normalizeHeaderCell(valStr)
                if (lower.isBlank()) continue

                // Explicit priority check for Amount / Cost column:
                if (lower.contains("cost") || lower.contains("amount") || lower.contains("spent") || lower.contains("price") || lower.contains("kes") || lower.contains("ksh") || lower.contains("val") || lower.contains("total")) {
                    if (amountSpentIdx == -1 && !lower.contains("quantity") && !lower.contains("qty")) {
                        amountSpentIdx = cIdx
                        continue
                    }
                }

                // Recorded By
                if (lower.contains("recorded") || lower.contains("entered") || lower.contains("staff") || lower.contains("by") || lower.contains("user")) {
                    if (recordedByIdx == -1) {
                        recordedByIdx = cIdx
                        continue
                    }
                }

                // Department
                if (lower.contains("department") || lower.contains("dept") || lower.contains("section")) {
                    if (departmentIdx == -1) {
                        departmentIdx = cIdx
                        continue
                    }
                }

                // Expense Type / Category
                if (lower.contains("expense type") || lower.contains("category") || (lower.contains("type") && !lower.contains("item"))) {
                    if (expenseTypeIdx == -1) {
                        expenseTypeIdx = cIdx
                        continue
                    }
                }

                // Item Purchased / Particulars / Description
                if (lower.contains("item") || lower.contains("purchased") || lower.contains("particular") || lower.contains("description") || lower.contains("product") || lower.contains("service")) {
                    if (itemPurchasedIdx == -1) {
                        itemPurchasedIdx = cIdx
                        continue
                    }
                }

                // Quantity
                if (lower.contains("quantity") || lower.contains("qty") || lower.contains("count") || lower.contains("units")) {
                    if (quantityIdx == -1) {
                        quantityIdx = cIdx
                        continue
                    }
                }

                // Payment Method
                if (lower.contains("payment") || lower.contains("method") || lower.contains("paid via") || lower.contains("mode")) {
                    if (paymentMethodIdx == -1) {
                        paymentMethodIdx = cIdx
                        continue
                    }
                }

                // Month
                if (lower.contains("month") || lower.contains("period")) {
                    if (monthIdx == -1) {
                        monthIdx = cIdx
                        continue
                    }
                }

                // Date
                if (lower == "date" || lower == "timestamp" || lower.contains("date") || lower.contains("day")) {
                    if (dateIdx == -1) {
                        dateIdx = cIdx
                        continue
                    }
                }
            }

            var score = 0
            if (dateIdx != -1) score += 3
            if (recordedByIdx != -1) score += 3
            if (departmentIdx != -1) score += 3
            if (expenseTypeIdx != -1) score += 4
            if (itemPurchasedIdx != -1) score += 5
            if (amountSpentIdx != -1) score += 5
            if (paymentMethodIdx != -1) score += 2
            if (monthIdx != -1) score += 2

            if (score > maxScore && (amountSpentIdx != -1 || itemPurchasedIdx != -1)) {
                maxScore = score
                bestResult = ExpensesHeaderResult(
                    headerRowIndex = rIdx,
                    dateIdx = dateIdx,
                    recordedByIdx = recordedByIdx,
                    departmentIdx = departmentIdx,
                    expenseTypeIdx = expenseTypeIdx,
                    itemPurchasedIdx = itemPurchasedIdx,
                    quantityIdx = quantityIdx,
                    amountSpentIdx = amountSpentIdx,
                    paymentMethodIdx = paymentMethodIdx,
                    monthIdx = monthIdx
                )
            }
        }

        // FALLBACK: If header row was found but amountSpentIdx was still -1, scan row 1 data to find the column index containing numeric amounts
        if (bestResult.headerRowIndex != -1 && bestResult.amountSpentIdx == -1) {
            val startDataRow = bestResult.headerRowIndex + 1
            if (startDataRow < sheetData.size) {
                val candidateColCounts = mutableMapOf<Int, Int>()
                for (r in startDataRow until minOf(sheetData.size, startDataRow + 10)) {
                    val row = sheetData[r]
                    for ((cIdx, cell) in row.withIndex()) {
                        if (cIdx != bestResult.itemPurchasedIdx && cIdx != bestResult.dateIdx && cIdx != bestResult.departmentIdx) {
                            val amt = parseAmountValue(cell)
                            if (amt > 0.0) {
                                candidateColCounts[cIdx] = (candidateColCounts[cIdx] ?: 0) + 1
                            }
                        }
                    }
                }
                val bestCol = candidateColCounts.maxByOrNull { it.value }?.key ?: -1
                if (bestCol != -1) {
                    bestResult = bestResult.copy(amountSpentIdx = bestCol)
                }
            }
        }

        return bestResult
    }

    private fun parseAmountValue(str: String): Double {
        if (str.isBlank()) return 0.0
        var clean = str.replace("KES", "", ignoreCase = true)
            .replace("KSHS", "", ignoreCase = true)
            .replace("KSH", "", ignoreCase = true)
            .replace("USD", "", ignoreCase = true)
            .replace("$", "")
            .replace(",", "")
            .replace("=", "")
            .replace("/=", "")
            .replace("-", "")
            .trim()
        val regex = """\d+(\.\d+)?""".toRegex()
        val match = regex.find(clean)
        return match?.value?.toDoubleOrNull() ?: 0.0
    }

    private fun parseExpenseDataRows(
        sheetData: List<List<String>>,
        header: ExpensesHeaderResult,
        spreadsheetId: String
    ): List<ExpenseRow> {
        val result = mutableListOf<ExpenseRow>()
        for (rIdx in (header.headerRowIndex + 1) until sheetData.size) {
            val row = sheetData[rIdx]
            if (row.all { it.isBlank() }) continue

            fun getCell(idx: Int): String = if (idx in row.indices) row[idx].trim() else ""

            val dateStr = getCell(header.dateIdx)
            val recordedByStr = getCell(header.recordedByIdx)
            val departmentStr = getCell(header.departmentIdx)
            val expenseTypeStr = getCell(header.expenseTypeIdx)
            val itemPurchasedStr = getCell(header.itemPurchasedIdx)
            val quantityRaw = parseAmountValue(getCell(header.quantityIdx)).let { if (it <= 0.0) 1.0 else it }
            val amountSpent = parseAmountValue(getCell(header.amountSpentIdx))
            val paymentMethodStr = getCell(header.paymentMethodIdx)
            val monthStr = getCell(header.monthIdx)
            val finalExpenseMonth = if (monthStr.isNotBlank()) monthStr else {
                val ms = parseTimestampToMillis(dateStr)
                if (ms > 0L) {
                    val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
                    val monthFull = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
                    "${monthFull[cal.get(java.util.Calendar.MONTH)]} ${cal.get(java.util.Calendar.YEAR)}"
                } else ""
            }

            if (itemPurchasedStr.isBlank() && amountSpent == 0.0) continue

            result.add(
                ExpenseRow(
                    spreadsheetId = spreadsheetId,
                    rowIndex = rIdx + 1,
                    date = dateStr,
                    recordedBy = recordedByStr,
                    department = departmentStr.ifBlank { "General" },
                    expenseType = expenseTypeStr.ifBlank { "Operational" },
                    itemPurchased = itemPurchasedStr.ifBlank { "Expense Item #${rIdx + 1}" },
                    quantity = quantityRaw,
                    amountSpent = amountSpent,
                    paymentMethod = paymentMethodStr.ifBlank { "Mpesa" },
                    month = finalExpenseMonth
                )
            )
        }
        return result
    }
}