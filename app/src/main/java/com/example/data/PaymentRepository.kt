package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi

class PaymentRepository(private val paymentDao: PaymentDao) {

    val activeConfigFlow: Flow<SheetConfig?> = paymentDao.getActiveConfigFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val allPaymentsFlow: Flow<List<PaymentRow>> = activeConfigFlow.flatMapLatest { config ->
        val spreadsheetId = config?.spreadsheetId ?: "demo_spreadsheet"
        paymentDao.getPaymentsBySpreadsheetFlow(spreadsheetId)
    }

    private val client = OkHttpClient()

    /**
     * Extracts spreadsheet ID from Google Sheet URL
     */
    fun extractSpreadsheetId(url: String): String? {
        val pattern = "/d/([a-zA-Z0-9-_]+)".toRegex()
        return pattern.find(url)?.groupValues?.get(1)
    }

    /**
     * Normalizes employee name to avoid redundancies.
     * Categorizes by first name and maps variations like "Suzzy" to "Susan".
     */
    fun normalizeEmployeeName(rawName: String): String {
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) return "Unknown"
        // Split by whitespace and common separators to get the first word/name
        val firstWord = trimmed.split(Regex("[\\s.,/_-]+")).firstOrNull { it.isNotBlank() } ?: trimmed
        
        // Normalize to TitleCase (first letter capital, other letters lowercase)
        var normalized = firstWord.lowercase().replaceFirstChar { 
            if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() 
        }
        
        // In cases where the name is Suzzy/Suzy, map it to Susan
        if (normalized.equals("Suzzy", ignoreCase = true) || 
            normalized.equals("Suzy", ignoreCase = true) || 
            normalized.equals("Suzi", ignoreCase = true) || 
            normalized.equals("Susie", ignoreCase = true)) {
            normalized = "Susan"
        }
        
        return normalized
    }


    /**
     * Resets the database to pristine, beautiful sample data for local demo mode.
     */
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
                ),
                PaymentRow(
                    spreadsheetId = "demo_spreadsheet",
                    rowIndex = 7,
                    timestamp = "2026-07-04 16:00",
                    name = "John Mwangi",
                    section = "Massage",
                    serviceName = "Aromatherapy Massage",
                    amountPaid = 4000.0,
                    paymentMethod = "Mpesa",
                    commissionPct = 0.40,
                    staffCommission = 1600.0,
                    salonShare = 2400.0,
                    notes = "Used lavender and chamomile oils",
                    paid = true,
                    month = "July 2026"
                ),
                PaymentRow(
                    spreadsheetId = "demo_spreadsheet",
                    rowIndex = 8,
                    timestamp = "2026-07-05 08:45",
                    name = "Jane Wambui",
                    section = "Waxing",
                    serviceName = "Underarm Waxing",
                    amountPaid = 1200.0,
                    paymentMethod = "Mpesa",
                    commissionPct = 0.25,
                    staffCommission = 300.0,
                    salonShare = 900.0,
                    notes = "Quick appointment before work",
                    paid = false,
                    month = "July 2026"
                ),
                PaymentRow(
                    spreadsheetId = "demo_spreadsheet",
                    rowIndex = 9,
                    timestamp = "2026-07-05 13:00",
                    name = "Grace Kendi",
                    section = "Nails",
                    serviceName = "Pedicure",
                    amountPaid = 1200.0,
                    paymentMethod = "Cash",
                    commissionPct = 0.20,
                    staffCommission = 240.0,
                    salonShare = 960.0,
                    notes = "Soak, scrub, massage, clean up",
                    paid = false,
                    month = "July 2026"
                ),
                PaymentRow(
                    spreadsheetId = "demo_spreadsheet",
                    rowIndex = 10,
                    timestamp = "2026-07-05 15:30",
                    name = "Grace Kendi",
                    section = "Waxing",
                    serviceName = "Full Leg Waxing",
                    amountPaid = 2400.0,
                    paymentMethod = "Mpesa",
                    commissionPct = 0.25,
                    staffCommission = 600.0,
                    salonShare = 1800.0,
                    notes = "Used organic honey wax",
                    paid = false,
                    month = "July 2026"
                ),
                PaymentRow(
                    spreadsheetId = "demo_spreadsheet",
                    rowIndex = 11,
                    timestamp = "2026-07-06 15:30",
                    name = "Grace Kendi",
                    section = "Massage",
                    serviceName = "Foot Reflexology",
                    amountPaid = 2000.0,
                    paymentMethod = "Mpesa",
                    commissionPct = 0.40,
                    staffCommission = 800.0,
                    salonShare = 1200.0,
                    notes = "45 minutes focused session",
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
                sheetName = "Services Ledger",
                ownerPin = "1234", // Simple default PIN
                isVerified = true,
                useLocalDemo = true,
                lastSyncTime = System.currentTimeMillis()
            )
            paymentDao.insertConfig(defaultConfig)
        }
    }

    /**
     * Set sheet mapping configuration
     */
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

    /**
     * Import spreadsheet file bytes (TSV, CSV, or XLSX) locally
     */
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
                // Detect delimiter dynamically from the first non-empty line
                val firstLine = lines.firstOrNull { it.isNotEmpty() } ?: ""
                val delimiter = if (firstLine.count { it == '\t' } > firstLine.count { it == ',' }) "\t" else ","
                lines.map { if (it.isEmpty()) emptyList() else parseCsvLine(it, delimiter) }
            }

            if (parsedSheetData.isEmpty() || parsedSheetData.none { it.isNotEmpty() }) {
                return@withContext Result.failure(Exception("Could not parse spreadsheet data. Please ensure it is a valid .csv, .tsv, or .xlsx file."))
            }

            // Find header row dynamically containing all critical columns
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

            for (rIdx in 0 until parsedSheetData.size) {
                val row = parsedSheetData[rIdx]
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
                    val lower = valStr.lowercase().trim().replace("\"", "")
                    if (lower.contains("service done") || lower == "service" || lower.contains("treatment") || lower.contains("procedure") || lower.contains("job") || lower.contains("work")) {
                        foundService = cIdx
                    } else if (lower.contains("amount paid (kes)") || lower.contains("amount paid") || lower.contains("amount") || lower.contains("kes") || lower.contains("price") || lower.contains("cost") || lower.contains("fee") || lower.contains("total") || lower.contains("wage") || lower.contains("charge")) {
                        foundAmount = cIdx
                    } else if ((lower.contains("handled by") || lower.contains("name") || lower.contains("staff") || lower.contains("employee") || lower.contains("beautician") || lower.contains("stylist") || lower.contains("member") || lower.contains("person")) && !lower.contains("service") && !lower.contains("commission") && !lower.contains("comm")) {
                        foundName = cIdx
                    } else if (lower.contains("section") || lower.contains("dept") || lower.contains("category") || lower.contains("area") || lower.contains("division")) {
                        foundSection = cIdx
                    } else if (lower.contains("payment method") || lower.contains("method") || lower.contains("mode") || lower.contains("payment") || lower.contains("mpesa") || lower.contains("cash")) {
                        foundPaymentMethod = cIdx
                    } else if (lower.contains("staff commission") || lower.contains("staff comm")) {
                        foundStaffCommission = cIdx
                    } else if (lower.contains("salon share") || lower.contains("salon")) {
                        foundSalonShare = cIdx
                    } else if (lower.contains("commission %") || lower.contains("commission") || lower.contains("comm %") || lower == "commission") {
                        foundCommissionPct = cIdx
                    } else if ((lower.contains("status") || lower == "paid" || lower.contains("is paid") || lower.contains("cleared") || lower.contains("settle") || lower.contains("payout")) && !lower.contains("amount") && !lower.contains("kes") && !lower.contains("commission") && !lower.contains("comm")) {
                        foundPaid = cIdx
                    } else if (lower.contains("note") || lower.contains("comment") || lower.contains("remark") || lower.contains("desc") || lower.contains("detail")) {
                        foundNotes = cIdx
                    } else if (lower.contains("date") || lower.contains("timestamp") || lower.contains("time")) {
                        foundTimestamp = cIdx
                    } else if (lower == "month" || lower.contains("month")) {
                        foundMonth = cIdx
                    }
                }
                
                // Primary key triad to identify candidate row
                if (foundName != -1 && foundService != -1 && foundAmount != -1) {
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
                    break
                }
            }

            if (headerRowIndex == -1) {
                return@withContext Result.failure(Exception("Could not locate the header row! Ensure your spreadsheet contains column headers for Handled By, Section, Service Done, and Amount Paid (KES)."))
            }

            // Check for missing critical columns and raise specific errors
            val missingColumns = mutableListOf<String>()
            if (nameIdx == -1) missingColumns.add("Handled By (or Name)")
            if (sectionIdx == -1) missingColumns.add("Section (e.g. Hair, Nails, Massage, Waxing)")
            if (serviceNameIdx == -1) missingColumns.add("Service Done")
            if (amountIdx == -1) missingColumns.add("Amount Paid (KES)")

            if (missingColumns.isNotEmpty()) {
                val errorMsg = "Critical column headers missing: ${missingColumns.joinToString(", ")}. " +
                        "Please ensure your spreadsheet contains all required columns: Handled By, Section, Service Done, and Amount Paid (KES)."
                return@withContext Result.failure(Exception(errorMsg))
            }

            val headers = parsedSheetData[headerRowIndex]
            Log.d("PaymentRepository", "Found headers row at index $headerRowIndex: $headers")

            // Set up a dynamic offline configuration representing this uploaded file
            val fileSpreadsheetId = "uploaded_file"
            val newConfig = SheetConfig(
                spreadsheetUrl = "Local Upload: $fileName",
                spreadsheetId = fileSpreadsheetId,
                sheetName = fileName.take(minOf(24, fileName.length)), // Keep it reasonably short
                ownerPin = "1234", // Safe default configuration PIN
                isVerified = true,
                useLocalDemo = false,
                lastSyncTime = System.currentTimeMillis()
            )

            val parsedRows = mutableListOf<PaymentRow>()

            for (rowIndex in (headerRowIndex + 1) until parsedSheetData.size) {
                val lineCols = parsedSheetData[rowIndex]
                if (lineCols.size < minOf(nameIdx, serviceNameIdx, amountIdx) + 1) continue

                // Section Filter: major categories Hair, Nails, Massage, Waxing (case-insensitive)
                val sectionRaw = if (sectionIdx != -1 && sectionIdx < lineCols.size) lineCols[sectionIdx].trim() else ""
                val sectionLower = sectionRaw.lowercase()
                if (sectionLower != "hair" && sectionLower != "nails" && sectionLower != "massage" && sectionLower != "waxing") {
                    continue // Only include rows where the Section matches one of the major categories
                }
                val section = sectionRaw.toTitleCase()

                val amountStr = if (amountIdx < lineCols.size) lineCols[amountIdx].replace(",", "").replace("KES", "").trim() else "0"
                val amountPaid = amountStr.toDoubleOrNull()
                if (amountPaid == null || amountPaid < 0.0) {
                    continue // Only include rows with valid numeric amounts
                }

                val timestamp = if (timestampIdx != -1 && timestampIdx < lineCols.size) lineCols[timestampIdx].trim() else "Row ${rowIndex + 1}"
                val rawName = if (nameIdx < lineCols.size) lineCols[nameIdx].trim() else ""
                if (rawName.isBlank()) continue
                val name = normalizeEmployeeName(rawName) // Normalize name and map variations (e.g. Suzzy -> Susan)

                val serviceName = if (serviceNameIdx < lineCols.size) lineCols[serviceNameIdx].trim() else "Service"
                val paymentMethod = if (paymentMethodIdx != -1 && paymentMethodIdx < lineCols.size) lineCols[paymentMethodIdx].trim() else "Cash"
                val notes = if (notesIdx != -1 && notesIdx < lineCols.size) lineCols[notesIdx].trim() else ""
                
                val commissionPctStr = if (commissionPctIdx != -1 && commissionPctIdx < lineCols.size) lineCols[commissionPctIdx].trim() else "0"
                val commissionPct = parseCommissionPct(commissionPctStr)

                // Calculated fields
                val staffCommission = amountPaid * commissionPct
                val salonShare = amountPaid - staffCommission

                val paidVal = if (paidIdx != -1 && paidIdx < lineCols.size) lineCols[paidIdx].trim().lowercase() else "false"
                val paid = paidVal == "true" || paidVal == "1" || paidVal == "yes" || paidVal == "paid"

                val month = if (monthIdx != -1 && monthIdx < lineCols.size) lineCols[monthIdx].trim() else ""

                parsedRows.add(
                    PaymentRow(
                        spreadsheetId = fileSpreadsheetId,
                        rowIndex = rowIndex + 1,
                        timestamp = timestamp,
                        name = name,
                        section = section,
                        serviceName = serviceName,
                        amountPaid = amountPaid,
                        paymentMethod = paymentMethod,
                        commissionPct = commissionPct,
                        staffCommission = staffCommission,
                        salonShare = salonShare,
                        notes = notes,
                        paid = paid,
                        month = month
                    )
                )
            }

            // Store new config and parsed rows in database
            paymentDao.insertConfig(newConfig)
            paymentDao.clearPaymentsForSpreadsheet(fileSpreadsheetId)
            paymentDao.insertPayments(parsedRows)

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("PaymentRepository", "Error parsing local spreadsheet file", e)
            Result.failure(e)
        }
    }

    /**
     * Mark verified
     */
    suspend fun setVerified(isVerified: Boolean) {
        paymentDao.updateConfigVerified(isVerified)
    }

    /**
     * Fetch and parse the spreadsheet TSV data.
     * Uses GViz CSV/TSV output, which requires "Anyone with link can view" permission.
     */
    suspend fun refreshSheetData(
        onProgress: (milestone: String, progress: Float) -> Unit = { _, _ -> }
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val config = paymentDao.getActiveConfig() ?: return@withContext Result.failure(Exception("No configuration set up"))
        if (config.useLocalDemo) {
            // Already initialized with local demo data. We just simulate network latency and milestones
            onProgress("Connecting to document...", 0.1f)
            kotlinx.coroutines.delay(250)
            onProgress("Document found", 0.4f)
            kotlinx.coroutines.delay(250)
            onProgress("Columns detected", 0.6f)
            kotlinx.coroutines.delay(250)
            onProgress("Data found", 0.8f)
            kotlinx.coroutines.delay(250)
            onProgress("Mapping the Data", 0.9f)
            kotlinx.coroutines.delay(250)
            onProgress("Sync completed", 1.0f)
            return@withContext Result.success(Unit)
        }

        val spreadsheetId = config.spreadsheetId
        val sheetName = config.sheetName

        onProgress("Connecting to document...", 0.1f)
        var tsvContent = ""
        var fetchErrorDetail = ""

        // --- METHOD A: GViz (Requires exact sheet name) ---
        val tsvUrl = "https://docs.google.com/spreadsheets/d/$spreadsheetId/gviz/tq?tqx=out:tsv&sheet=${java.net.URLEncoder.encode(sheetName, "UTF-8")}"
        Log.d("PaymentRepository", "Attempting Method A (GViz with sheet name): $tsvUrl")
        try {
            val request = Request.Builder().url(tsvUrl).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val content = response.body?.string() ?: ""
                    if (content.isNotBlank()) {
                        if (content.trim().startsWith("<") || content.contains("<!DOCTYPE html", ignoreCase = true) || content.contains("<html", ignoreCase = true)) {
                            fetchErrorDetail = "Method A returned HTML (Private access error/Sheet needs login)."
                            Log.w("PaymentRepository", "Method A returned HTML content. Sheet likely private.")
                        } else {
                            tsvContent = content
                            onProgress("Document found", 0.4f)
                        }
                    } else {
                        fetchErrorDetail = "Method A returned empty response."
                    }
                } else {
                    fetchErrorDetail = "Method A failed with HTTP Code ${response.code}."
                    Log.w("PaymentRepository", "Method A failed with code: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.w("PaymentRepository", "Method A threw exception", e)
            fetchErrorDetail = "Method A network error: ${e.message}"
        }

        // --- METHOD B: Direct Export (Fetches the first active sheet automatically) ---
        if (tsvContent.isBlank()) {
            val exportUrl = "https://docs.google.com/spreadsheets/d/$spreadsheetId/export?format=tsv"
            Log.d("PaymentRepository", "Attempting Method B (Export first sheet): $exportUrl")
            try {
                val request = Request.Builder().url(exportUrl).build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val content = response.body?.string() ?: ""
                        if (content.isNotBlank()) {
                            if (content.trim().startsWith("<") || content.contains("<!DOCTYPE html", ignoreCase = true) || content.contains("<html", ignoreCase = true)) {
                                fetchErrorDetail = "Direct export returned HTML. This means Google restricted access because the spreadsheet is private (Viewer permissions required)."
                                Log.w("PaymentRepository", "Method B returned HTML. Private spreadsheet.")
                            } else {
                                tsvContent = content
                                onProgress("Document found", 0.4f)
                            }
                        } else {
                            fetchErrorDetail = "Direct export returned empty response."
                        }
                    } else {
                        fetchErrorDetail = "Direct export failed with HTTP Code ${response.code}."
                        Log.w("PaymentRepository", "Method B failed with code: ${response.code}")
                    }
                }
            } catch (e: Exception) {
                Log.e("PaymentRepository", "Method B threw exception", e)
                fetchErrorDetail = "Direct export network error: ${e.message}"
            }
        }

        if (tsvContent.isBlank()) {
            val baseMsg = "Could not fetch spreadsheet data. Details: $fetchErrorDetail\n\n"
            val suggestion = "Please verify: \n1. Your spreadsheet sharing is set to 'Anyone with the link can view'.\n2. The URL starts with 'https://docs.google.com/spreadsheets/'.\n3. The specified sheet name exists."
            return@withContext Result.failure(Exception(baseMsg + suggestion))
        }

        onProgress("Analyzing structure...", 0.5f)
        try {
            val lines = tsvContent.split("\n").map { it.trim() }

            if (lines.none { it.isNotEmpty() }) {
                return@withContext Result.failure(Exception("Spreadsheet contains no data rows."))
            }

            // Detect delimiter dynamically like in local file import
            val firstLine = lines.firstOrNull { it.isNotEmpty() } ?: ""
            val delimiter = if (firstLine.count { it == '\t' } > firstLine.count { it == ',' }) "\t" else ","
            val parsedSheetData = lines.map { if (it.isEmpty()) emptyList() else parseCsvLine(it, delimiter) }

            // Find header row dynamically containing all critical columns
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

            for (rIdx in 0 until parsedSheetData.size) {
                val row = parsedSheetData[rIdx]
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
                    val lower = valStr.lowercase().trim().replace("\"", "")
                    if (lower.contains("service done") || lower == "service" || lower.contains("treatment") || lower.contains("procedure") || lower.contains("job") || lower.contains("work")) {
                        foundService = cIdx
                    } else if (lower.contains("amount paid (kes)") || lower.contains("amount paid") || lower.contains("amount") || lower.contains("kes") || lower.contains("price") || lower.contains("cost") || lower.contains("fee") || lower.contains("total") || lower.contains("wage") || lower.contains("charge")) {
                        foundAmount = cIdx
                    } else if ((lower.contains("handled by") || lower.contains("name") || lower.contains("staff") || lower.contains("employee") || lower.contains("beautician") || lower.contains("stylist") || lower.contains("member") || lower.contains("person")) && !lower.contains("service") && !lower.contains("commission") && !lower.contains("comm")) {
                        foundName = cIdx
                    } else if (lower.contains("section") || lower.contains("dept") || lower.contains("category") || lower.contains("area") || lower.contains("division")) {
                        foundSection = cIdx
                    } else if (lower.contains("payment method") || lower.contains("method") || lower.contains("mode") || lower.contains("payment") || lower.contains("mpesa") || lower.contains("cash")) {
                        foundPaymentMethod = cIdx
                    } else if (lower.contains("staff commission") || lower.contains("staff comm")) {
                        foundStaffCommission = cIdx
                    } else if (lower.contains("salon share") || lower.contains("salon")) {
                        foundSalonShare = cIdx
                    } else if (lower.contains("commission %") || lower.contains("commission") || lower.contains("comm %") || lower == "commission") {
                        foundCommissionPct = cIdx
                    } else if ((lower.contains("status") || lower == "paid" || lower.contains("is paid") || lower.contains("cleared") || lower.contains("settle") || lower.contains("payout")) && !lower.contains("amount") && !lower.contains("kes") && !lower.contains("commission") && !lower.contains("comm")) {
                        foundPaid = cIdx
                    } else if (lower.contains("note") || lower.contains("comment") || lower.contains("remark") || lower.contains("desc") || lower.contains("detail")) {
                        foundNotes = cIdx
                    } else if (lower.contains("date") || lower.contains("timestamp") || lower.contains("time")) {
                        foundTimestamp = cIdx
                    } else if (lower == "month" || lower.contains("month")) {
                        foundMonth = cIdx
                    }
                }
                
                // Primary key triad to identify candidate row
                if (foundName != -1 && foundService != -1 && foundAmount != -1) {
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
                    break
                }
            }

            if (headerRowIndex == -1) {
                return@withContext Result.failure(Exception("Could not locate the header row! Ensure your spreadsheet contains column headers for Handled By, Section, Service Done, and Amount Paid (KES)."))
            }

            onProgress("Columns detected", 0.6f)

            // Check for missing critical columns and raise specific errors
            val missingColumns = mutableListOf<String>()
            if (nameIdx == -1) missingColumns.add("Handled By (or Name)")
            if (sectionIdx == -1) missingColumns.add("Section (e.g. Hair, Nails, Massage, Waxing)")
            if (serviceNameIdx == -1) missingColumns.add("Service Done")
            if (amountIdx == -1) missingColumns.add("Amount Paid (KES)")

            if (missingColumns.isNotEmpty()) {
                val errorMsg = "Critical column headers missing: ${missingColumns.joinToString(", ")}. " +
                        "Please ensure your spreadsheet contains all required columns: Handled By, Section, Service Done, and Amount Paid (KES)."
                return@withContext Result.failure(Exception(errorMsg))
            }

            val headers = parsedSheetData[headerRowIndex]
            Log.d("PaymentRepository", "Found headers row at index $headerRowIndex: $headers")
            Log.d("PaymentRepository", "Indices mapped: name=$nameIdx, sec=$sectionIdx, serv=$serviceNameIdx, amt=$amountIdx, payMethod=$paymentMethodIdx, notes=$notesIdx, paid=$paidIdx, ts=$timestampIdx")

            onProgress("Data found", 0.8f)

            val parsedRows = mutableListOf<PaymentRow>()

            onProgress("Mapping the Data", 0.9f)

            // Iterate over rows starting from headerRowIndex + 1
            for (rowIndex in (headerRowIndex + 1) until parsedSheetData.size) {
                val cols = parsedSheetData[rowIndex]
                if (cols.size < minOf(nameIdx, serviceNameIdx, amountIdx) + 1) continue

                // Section Filter: major categories Hair, Nails, Massage, Waxing (case-insensitive)
                val sectionRaw = if (sectionIdx != -1 && sectionIdx < cols.size) cols[sectionIdx].trim() else ""
                val sectionLower = sectionRaw.lowercase()
                if (sectionLower != "hair" && sectionLower != "nails" && sectionLower != "massage" && sectionLower != "waxing") {
                    continue // Only include rows where the Section matches one of the major categories
                }
                val section = sectionRaw.toTitleCase()

                val amountStr = if (amountIdx < cols.size) cols[amountIdx].replace(",", "").replace("KES", "").trim() else "0"
                val amountPaid = amountStr.toDoubleOrNull()
                if (amountPaid == null || amountPaid < 0.0) {
                    continue // Only include rows with valid numeric amounts
                }

                val timestamp = if (timestampIdx != -1 && timestampIdx < cols.size) cols[timestampIdx].trim() else "Row ${rowIndex + 1}"
                val rawName = if (nameIdx < cols.size) cols[nameIdx].trim() else ""
                if (rawName.isBlank()) continue // Skip empty rows
                val name = normalizeEmployeeName(rawName) // Normalize name and map variations (e.g. Suzzy -> Susan)

                val serviceName = if (serviceNameIdx < cols.size) cols[serviceNameIdx].trim() else "Service"
                val paymentMethod = if (paymentMethodIdx != -1 && paymentMethodIdx < cols.size) cols[paymentMethodIdx].trim() else "Cash"
                val notes = if (notesIdx != -1 && notesIdx < cols.size) cols[notesIdx].trim() else ""
                
                val commissionPctStr = if (commissionPctIdx != -1 && commissionPctIdx < cols.size) cols[commissionPctIdx].trim() else "0"
                val commissionPct = parseCommissionPct(commissionPctStr)

                // Calculated fields
                val staffCommission = amountPaid * commissionPct
                val salonShare = amountPaid - staffCommission

                val paidVal = if (paidIdx != -1 && paidIdx < cols.size) cols[paidIdx].trim().lowercase() else "false"
                val paid = paidVal == "true" || paidVal == "1" || paidVal == "yes" || paidVal == "paid"

                val month = if (monthIdx != -1 && monthIdx < cols.size) cols[monthIdx].trim() else ""

                parsedRows.add(
                    PaymentRow(
                        spreadsheetId = spreadsheetId,
                        rowIndex = rowIndex + 1, // 1-based indexing for row matching (headers = row 1, first data = row 2)
                        timestamp = timestamp,
                        name = name,
                        section = section,
                        serviceName = serviceName,
                        amountPaid = amountPaid,
                        paymentMethod = paymentMethod,
                        commissionPct = commissionPct,
                        staffCommission = staffCommission,
                        salonShare = salonShare,
                        notes = notes,
                        paid = paid,
                        month = month
                    )
                )
            }

            // Save to Room database
            paymentDao.clearPaymentsForSpreadsheet(spreadsheetId)
            paymentDao.insertPayments(parsedRows)

            // Update sync metadata
            val updatedConfig = config.copy(lastSyncTime = System.currentTimeMillis())
            paymentDao.insertConfig(updatedConfig)

            onProgress("Sync completed", 1.0f)
            return@withContext Result.success(Unit)
        } catch (e: Exception) {
            Log.e("PaymentRepository", "Error parsing sheet data", e)
            return@withContext Result.failure(Exception("Data processing failed: ${e.localizedMessage}. Please verify data row formatting."))
        }
    }

    /**
     * Mark due as paid.
     * Updates the local Room DB and optionally triggers a webhook (Google Apps Script web app URL) to commit the changes live in the spreadsheet.
     */
    suspend fun markRowAsPaid(payment: PaymentRow, webhookUrl: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Update local Room database immediately
            val updatedPayment = payment.copy(paid = true)
            paymentDao.updatePayment(updatedPayment)

            // Trigger remote webapp update if configured
            if (!webhookUrl.isNullOrBlank() && payment.spreadsheetId != "demo_spreadsheet") {
                val config = paymentDao.getActiveConfig()
                val currentSheetName = config?.sheetName ?: "Payment Form Import"
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
            // Still success since local DB was updated, but report the remote error
            return@withContext Result.failure(e)
        }
    }

    /**
     * Clear all records
     */
    suspend fun clearAll() {
        paymentDao.clearAllPayments()
    }

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

    // --- High-Performance, Zero-Dependency XLSX Parsing Engine ---
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
            val targetSheetName = "Services Ledger"
            
            if (workbookBytes != null && relsBytes != null) {
                val sheets = parseWorkbookSheets(workbookBytes)
                val rels = parseWorkbookRels(relsBytes)
                
                // Find sheet matching "Services Ledger" case-insensitively
                val targetSheet = sheets.find { it.name.trim().lowercase() == targetSheetName.lowercase() }
                    ?: sheets.find { it.name.trim().lowercase().contains(targetSheetName.lowercase()) }
                    // Fallback to any sheet that has "ledger"
                    ?: sheets.find { it.name.trim().lowercase().contains("ledger") }
                    // Fallback to any sheet that has "services"
                    ?: sheets.find { it.name.trim().lowercase().contains("services") }
                    // Fallback to any sheet that has "import"
                    ?: sheets.find { it.name.trim().lowercase().contains("import") }
                    // Fallback to any sheet that has "payment"
                    ?: sheets.find { it.name.trim().lowercase().contains("payment") }
                    // Fallback to first sheet
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
                        Log.d("PaymentRepository", "Found target sheet '${targetSheet.name}' with rId '${targetSheet.rId}' mapped to '$entryPath'")
                    }
                }
            }

            if (sheetBytes == null) {
                // Fallback to any worksheets found
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
                        } else if (name == "v") {
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
                        if (name == "v") {
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
}
