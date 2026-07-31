package com.example.data

import android.util.Log
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

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val _firstRowPreview = MutableStateFlow<String?>(null)
    val firstRowPreview: StateFlow<String?> = _firstRowPreview.asStateFlow()

    // The GID for the "Service Ledger" worksheet
    private val SERVICE_LEDGER_GID = 155371327

    fun clearFirstRowPreview() {
        _firstRowPreview.value = null
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
        if (trimmed.isEmpty()) return "Unknown"
        val firstWord = trimmed.split(Regex("[\\s.,/_-]+")).firstOrNull { it.isNotBlank() } ?: trimmed

        var normalized = firstWord.lowercase().replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString()
        }

        when (normalized.lowercase()) {
            "suzzy", "suzy", "suzi", "susie" -> normalized = "Susan"
            "jane" -> normalized = "Jane"
            "mary" -> normalized = "Mary"
            "john" -> normalized = "John"
            "grace" -> normalized = "Grace"
        }

        return normalized
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

            val parsedRows = parseDataRows(parsedSheetData, headerResult, fileSpreadsheetId)

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
        val sheetName = "Service Ledger"
        
        if (spreadsheetId.isBlank() || spreadsheetId == "demo_spreadsheet") {
            return@withContext Result.failure(Exception("Invalid spreadsheet ID. Please check your configuration."))
        }

        var parsedSheetData: List<List<String>>? = null
        var fetchErrorDetail = ""
        var successfulMethod = ""

        onProgress("Connecting to spreadsheet...", 0.1f)

        // --- STEP 1: Export using the hardcoded GID (Most Reliable) ---
        onProgress("Exporting 'Service Ledger' worksheet using GID: $SERVICE_LEDGER_GID...", 0.2f)
        
        try {
            val gidUrls = listOf(
                "https://docs.google.com/spreadsheets/d/$spreadsheetId/export?format=csv&gid=$SERVICE_LEDGER_GID",
                "https://docs.google.com/spreadsheets/d/$spreadsheetId/gviz/tq?tqx=out:csv&gid=$SERVICE_LEDGER_GID",
                "https://docs.google.com/spreadsheets/d/$spreadsheetId/export?gid=$SERVICE_LEDGER_GID&format=csv"
            )
            
            for (url in gidUrls) {
                Log.d("PaymentRepository", "Trying GID URL: $url")
                try {
                    val request = Request.Builder()
                        .url(url)
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .build()
                        
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val content = response.body?.string() ?: ""
                            if (content.isNotBlank() && !content.startsWith("<") && !content.contains("<!DOCTYPE html")) {
                                val lines = content.split("\n").map { it.trim() }
                                if (lines.any { it.isNotEmpty() }) {
                                    parsedSheetData = lines.map { line ->
                                        if (line.isNotEmpty()) parseCsvLine(line, ",") else emptyList()
                                    }
                                    successfulMethod = "CSV Export (by GID: $SERVICE_LEDGER_GID)"
                                    onProgress("Data retrieved from 'Service Ledger'", 0.5f)
                                    Log.d("PaymentRepository", "Successfully retrieved ${parsedSheetData?.size} rows from 'Service Ledger' using GID $SERVICE_LEDGER_GID")
                                    break
                                }
                            } else {
                                fetchErrorDetail = "GID export returned HTML - sheet may be private"
                            }
                        } else {
                            fetchErrorDetail = "HTTP ${response.code}: ${response.message}"
                        }
                    }
                } catch (e: Exception) {
                    Log.w("PaymentRepository", "GID export attempt failed: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("PaymentRepository", "GID export failed", e)
            fetchErrorDetail = e.localizedMessage ?: "Unknown error"
        }

        // --- STEP 2: Fallback - Try with sheet name (in case GID didn't work) ---
        if (parsedSheetData == null) {
            onProgress("Trying alternative export method...", 0.3f)
            try {
                val encodedSheetName = java.net.URLEncoder.encode(sheetName, "UTF-8")
                
                val exportUrls = listOf(
                    "https://docs.google.com/spreadsheets/d/$spreadsheetId/export?format=csv&sheet=$encodedSheetName",
                    "https://docs.google.com/spreadsheets/d/$spreadsheetId/export?sheet=$encodedSheetName&format=csv",
                    "https://docs.google.com/spreadsheets/d/$spreadsheetId/gviz/tq?tqx=out:csv&sheet=$encodedSheetName"
                )
                
                for (url in exportUrls) {
                    Log.d("PaymentRepository", "Trying export URL: $url")
                    try {
                        val request = Request.Builder()
                            .url(url)
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                            .build()
                            
                        client.newCall(request).execute().use { response ->
                            if (response.isSuccessful) {
                                val content = response.body?.string() ?: ""
                                if (content.isNotBlank()) {
                                    val trimmed = content.trim()
                                    if (trimmed.startsWith("<") || trimmed.contains("<!DOCTYPE html") || 
                                        trimmed.contains("<html") || trimmed.contains("sign in") ||
                                        trimmed.contains("Sign in")) {
                                        if (fetchErrorDetail.isBlank()) {
                                            fetchErrorDetail = "Sheet appears to be private. Please change sharing to 'Anyone with the link can view'."
                                        }
                                    } else {
                                        val lines = content.split("\n").map { it.trim() }
                                        if (lines.any { it.isNotEmpty() }) {
                                            val parsedLines = lines.map { line ->
                                                if (line.isNotEmpty()) parseCsvLine(line, ",") else emptyList()
                                            }
                                            
                                            // Verify this is actually the Service Ledger by checking headers
                                            if (parsedLines.isNotEmpty() && parsedLines.first().any { 
                                                normalizeHeaderCell(it).contains("handled by") || 
                                                normalizeHeaderCell(it).contains("amount paid") ||
                                                normalizeHeaderCell(it).contains("service") 
                                            }) {
                                                parsedSheetData = parsedLines
                                                successfulMethod = "CSV Export (by name: $sheetName)"
                                                onProgress("Data retrieved via export", 0.5f)
                                                Log.d("PaymentRepository", "Successfully retrieved ${parsedSheetData?.size} rows from Service Ledger")
                                                break
                                            } else {
                                                // Check if this is the wrong sheet
                                                if (parsedLines.isNotEmpty() && parsedLines.first().any { 
                                                    normalizeHeaderCell(it).contains("payment form") ||
                                                    normalizeHeaderCell(it).contains("form") 
                                                }) {
                                                    fetchErrorDetail = "Found 'Payment Form Import' sheet instead of 'Service Ledger'. The GID method failed, and the name-based export is returning the wrong sheet."
                                                    Log.w("PaymentRepository", fetchErrorDetail)
                                                } else {
                                                    // It might be the Service Ledger with different headers
                                                    parsedSheetData = parsedLines
                                                    successfulMethod = "CSV Export (auto-detected)"
                                                    onProgress("Data retrieved via export", 0.5f)
                                                    Log.d("PaymentRepository", "Auto-detected sheet data")
                                                    break
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                if (fetchErrorDetail.isBlank()) {
                                    fetchErrorDetail = "HTTP ${response.code}: ${response.message}"
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w("PaymentRepository", "Export attempt failed: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("PaymentRepository", "Export method failed", e)
                if (fetchErrorDetail.isBlank()) {
                    fetchErrorDetail = e.localizedMessage ?: "Unknown error"
                }
            }
        }

        // --- STEP 3: Try TSV Export (Alternative format) ---
        if (parsedSheetData == null) {
            onProgress("Trying TSV format...", 0.35f)
            try {
                val tsvUrl = "https://docs.google.com/spreadsheets/d/$spreadsheetId/gviz/tq?tqx=out:tsv&gid=$SERVICE_LEDGER_GID"
                
                Log.d("PaymentRepository", "Trying TSV export with GID: $tsvUrl")
                
                val request = Request.Builder()
                    .url(tsvUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()
                    
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val content = response.body?.string() ?: ""
                        if (content.isNotBlank() && !content.startsWith("<") && !content.contains("<!DOCTYPE html")) {
                            val lines = content.split("\n").map { it.trim() }
                            if (lines.any { it.isNotEmpty() }) {
                                parsedSheetData = lines.map { line ->
                                    if (line.isNotEmpty()) parseCsvLine(line, "\t") else emptyList()
                                }
                                successfulMethod = "TSV Export (by GID: $SERVICE_LEDGER_GID)"
                                onProgress("Data retrieved via TSV", 0.5f)
                                Log.d("PaymentRepository", "Successfully retrieved ${parsedSheetData?.size} rows via TSV")
                            }
                        }
                    } else {
                        Log.w("PaymentRepository", "TSV export failed with code ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.w("PaymentRepository", "TSV export failed: ${e.message}")
            }
        }

        // --- STEP 4: Try OpenSheet API (Alternative) ---
        if (parsedSheetData == null) {
            onProgress("Trying alternative API...", 0.4f)
            try {
                val encodedSheetName = java.net.URLEncoder.encode(sheetName, "UTF-8")
                val apiUrl = "https://opensheet.elk.sh/$spreadsheetId/$encodedSheetName"
                
                Log.d("PaymentRepository", "Trying OpenSheet API: $apiUrl")
                
                val request = Request.Builder()
                    .url(apiUrl)
                    .header("Accept", "application/json")
                    .build()
                    
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val json = response.body?.string() ?: ""
                        if (json.isNotBlank() && json.startsWith("[")) {
                            val jsonArray = JSONArray(json)
                            if (jsonArray.length() > 0) {
                                parsedSheetData = mutableListOf()
                                val firstObj = jsonArray.getJSONObject(0)
                                val headers = firstObj.keys().asSequence().toList()
                                (parsedSheetData as MutableList).add(headers)
                                
                                for (i in 0 until jsonArray.length()) {
                                    val obj = jsonArray.getJSONObject(i)
                                    val row = headers.map { header ->
                                        obj.optString(header, "")
                                    }
                                    (parsedSheetData as MutableList).add(row)
                                }
                                successfulMethod = "OpenSheet API"
                                onProgress("Data retrieved via API", 0.5f)
                                Log.d("PaymentRepository", "Successfully retrieved ${parsedSheetData?.size} rows via OpenSheet API")
                            }
                        } else {
                            if (fetchErrorDetail.isBlank()) {
                                fetchErrorDetail = "Invalid response from OpenSheet API"
                            }
                        }
                    } else {
                        Log.w("PaymentRepository", "OpenSheet API failed with code ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.w("PaymentRepository", "OpenSheet API failed: ${e.message}")
            }
        }

        val sheetData = parsedSheetData

        // If all methods failed
        if (sheetData == null || sheetData.isEmpty()) {
            val errorMsg = buildString {
                append("❌ Failed to fetch the 'Service Ledger' worksheet.\n\n")
                append("🔑 REQUIRED: Your Google Sheet must be publicly shared.\n\n")
                append("📊 The app is using GID: $SERVICE_LEDGER_GID to target the 'Service Ledger' sheet.\n\n")
                if (fetchErrorDetail.isNotBlank()) {
                    append("📌 Error details: $fetchErrorDetail\n\n")
                }
                append("💡 Troubleshooting:\n")
                append("1. Make sure the sheet is named exactly: 'Service Ledger'\n")
                append("2. Check that the sheet is not hidden or protected\n")
                append("3. Ensure the workbook is shared with 'Anyone with the link can view'\n")
                append("4. Try using the direct download option in Settings instead\n")
                append("5. Verify the GID $SERVICE_LEDGER_GID is correct for your workbook")
            }
            return@withContext Result.failure(Exception(errorMsg))
        }

        // Verify we got the right sheet by checking headers
        if (sheetData.isNotEmpty() && sheetData.first().isNotEmpty()) {
            val headers = sheetData.first().map { normalizeHeaderCell(it) }
            val hasHandledBy = headers.any { it.contains("handled") || it.contains("name") || it.contains("staff") }
            val hasAmount = headers.any { it.contains("amount") || it.contains("paid") || it.contains("kes") }
            
            if (!hasHandledBy || !hasAmount) {
                // This might be the wrong sheet
                val errorMsg = buildString {
                    append("⚠️ The data retrieved doesn't appear to be from 'Service Ledger'.\n\n")
                    append("📊 The app may have fetched the wrong worksheet.\n\n")
                    append("🔍 Expected headers: 'Handled By', 'Amount Paid (KES)', etc.\n")
                    append("📌 Found headers: ${sheetData.first().joinToString(", ")}\n\n")
                    append("💡 Make sure:\n")
                    append("1. The GID $SERVICE_LEDGER_GID is correct for the 'Service Ledger' sheet\n")
                    append("2. The sheet has the correct column headers\n")
                    append("3. Try using the 'Download Worksheet from Online URL' option in Settings")
                }
                return@withContext Result.failure(Exception(errorMsg))
            }
        }

        onProgress("Processing data...", 0.6f)
        
        val remotePreviewText = formatFirstRowsPreview(sheetData)
        _firstRowPreview.value = remotePreviewText

        try {
            val headerResult = findHeaderRow(sheetData)
            val headerRowIndex = headerResult.headerRowIndex
            
            if (headerRowIndex == -1) {
                val errorMsg = "❌ Could not find the header row in 'Service Ledger'.\n\n" +
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
                val errorMsg = "❌ Critical column headers missing in 'Service Ledger': ${missingColumns.joinToString(", ")}.\n\n" +
                        "Found header row at position ${headerRowIndex + 1}:\n" +
                        "${sheetData[headerRowIndex].joinToString(" | ")}\n\n" +
                        "Please ensure your 'Service Ledger' sheet has the required columns."
                return@withContext Result.failure(Exception(errorMsg))
            }

            onProgress("Parsing rows...", 0.8f)
            
            val parsedRows = parseDataRows(sheetData, headerResult, spreadsheetId)
            
            if (parsedRows.isEmpty()) {
                val errorMsg = "❌ No valid data rows found in 'Service Ledger'.\n\n" +
                        "First 5 rows of data:\n" +
                        sheetData.take(5).joinToString("\n") { row -> 
                            row.joinToString(" | ") 
                        }
                return@withContext Result.failure(Exception(errorMsg))
            }

            onProgress("Saving data...", 0.9f)

            paymentDao.clearPaymentsForSpreadsheet(spreadsheetId)
            paymentDao.insertPayments(parsedRows)

            val updatedConfig = config.copy(
                lastSyncTime = System.currentTimeMillis(),
                useLocalDemo = false,
                isVerified = true
            )
            paymentDao.insertConfig(updatedConfig)

            onProgress("✅ Sync completed! ${parsedRows.size} rows loaded from 'Service Ledger' (via $successfulMethod)", 1.0f)
            Log.d("PaymentRepository", "Successfully synced ${parsedRows.size} rows from 'Service Ledger' using $successfulMethod")
            
            return@withContext Result.success(Unit)
            
        } catch (e: Exception) {
            Log.e("PaymentRepository", "Error processing spreadsheet data", e)
            return@withContext Result.failure(Exception("Data processing failed: ${e.localizedMessage}"))
        }
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
        spreadsheetId: String
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

            val amountStr = if (headerResult.amountIdx != -1 && headerResult.amountIdx < cols.size) {
                cols[headerResult.amountIdx].trim()
            } else ""
            val amountPaid = cleanAndParseDouble(amountStr)
            if (amountPaid == null || amountPaid < 0.0) continue

            val rowKey = "$rawName|$amountPaid|${rowIndex}"
            if (seenRows.contains(rowKey)) continue
            seenRows.add(rowKey)

            val name = normalizeEmployeeName(rawName)

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
            val paid = when (paidVal) {
                "true", "1", "yes", "paid", "t", "✓", "x", "✔" -> true
                "false", "0", "no", "unpaid", "f", "" -> false
                else -> false
            }

            val month = if (headerResult.monthIdx != -1 && headerResult.monthIdx < cols.size) {
                cols[headerResult.monthIdx].trim()
            } else ""
            val finalMonth = if (month.isNotBlank()) month else {
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
}