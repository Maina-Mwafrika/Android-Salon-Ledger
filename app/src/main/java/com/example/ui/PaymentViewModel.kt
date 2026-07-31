package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface PaymentUiState {
    object Loading : PaymentUiState
    data class Success(
        val config: SheetConfig?,
        val payments: List<PaymentRow>,
        val filteredEmployee: String? = null,
        val filteredSection: String? = null,
        val searchWord: String = ""
    ) : PaymentUiState
}

class PaymentViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    private val paymentDao = db.paymentDao()
    val repository = PaymentRepository(paymentDao)

    // Current State
    private val _filteredEmployee = MutableStateFlow<String?>(null)
    private val _filteredSection = MutableStateFlow<String?>(null)
    private val _searchWord = MutableStateFlow<String>("")

    // API webhook URL to push changes back to Sheets
    val webhookUrl = MutableStateFlow<String>("")

    // Authentication lock status
    private val _isUnlocked = MutableStateFlow(false)
    val isUnlocked: StateFlow<Boolean> = _isUnlocked.asStateFlow()

    // Loading / Status Messages
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _syncProgress = MutableStateFlow<Float?>(null)
    val syncProgress: StateFlow<Float?> = _syncProgress.asStateFlow()

    private val _syncMilestone = MutableStateFlow<String?>(null)
    val syncMilestone: StateFlow<String?> = _syncMilestone.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val firstRowPreview: StateFlow<String?> = repository.firstRowPreview

    init {
        // Initialize with default demo data on first-ever launch if no config exists
        viewModelScope.launch {
            val existingConfig = paymentDao.getActiveConfig()
            if (existingConfig == null) {
                repository.resetToDemoData()
            }
        }
    }

    // Combine database and UI filter states
    val uiState: StateFlow<PaymentUiState> = combine(
        repository.activeConfigFlow,
        repository.allPaymentsFlow,
        _filteredEmployee,
        _filteredSection,
        _searchWord
    ) { config, payments, filteredEmployee, filteredSection, searchWord ->
        PaymentUiState.Success(
            config = config,
            payments = payments,
            filteredEmployee = filteredEmployee,
            filteredSection = filteredSection,
            searchWord = searchWord
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PaymentUiState.Loading
    )

    // --- Computed Metrics & Selections (Derived from full database state) ---

    val allPayments: StateFlow<List<PaymentRow>> = repository.allPaymentsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // List of unique employee names
    val employeesList: StateFlow<List<String>> = allPayments.map { payments ->
        payments.map { it.name }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // List of unique sections
    val sectionsList: StateFlow<List<String>> = allPayments.map { payments ->
        payments.map { it.section }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Map: Employee Name -> Total Amount Paid (where paid = true)
    val totalPaidByEmployee: StateFlow<Map<String, Double>> = allPayments.map { payments ->
        payments.filter { it.paid }
            .groupBy { it.name }
            .mapValues { (_, rows) -> rows.sumOf { it.staffCommission } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Total Paid to ALL employees
    val totalPaidToAll: StateFlow<Double> = totalPaidByEmployee.map { map ->
        map.values.sum()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Map: Employee Name -> Unpaid Dues List
    val unpaidDuesByEmployee: StateFlow<Map<String, List<PaymentRow>>> = allPayments.map { payments ->
        payments.filter { !it.paid }
            .groupBy { it.name }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Map: Employee Name -> Date range of unpaid dues (e.g. "July 1 - July 5" or "Row 2 - Row 10")
    val unpaidDuesRangeByEmployee: StateFlow<Map<String, String>> = unpaidDuesByEmployee.map { unpaidMap ->
        unpaidMap.mapValues { (_, rows) ->
            if (rows.isEmpty()) return@mapValues "No outstanding dues"
            // Sort by row index or timestamp to find range
            val sortedRows = rows.sortedBy { it.rowIndex }
            val first = parseAndFormatDate(sortedRows.first().timestamp)
            val last = parseAndFormatDate(sortedRows.last().timestamp)
            if (first == last) first else "$first to $last"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // Chart Data: Revenue by Section (includes both paid and unpaid, or let's do total incoming revenue)
    val revenueBySection: StateFlow<List<Pair<String, Double>>> = allPayments.map { payments ->
        payments.groupBy { it.section }
            .mapValues { (_, rows) -> rows.sumOf { it.amountPaid } }
            .toList()
            .sortedByDescending { it.second }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Chart Data: Revenue by Service Name
    val revenueByService: StateFlow<List<Pair<String, Double>>> = allPayments.map { payments ->
        payments.groupBy { it.serviceName }
            .mapValues { (_, rows) -> rows.sumOf { it.amountPaid } }
            .toList()
            .sortedByDescending { it.second }
            .take(6) // Top 6 services to keep chart tidy
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Intentional Actions & Logic ---

    fun setFilterEmployee(name: String?) {
        _filteredEmployee.value = name
    }

    fun setFilterSection(section: String?) {
        _filteredSection.value = section
    }

    fun setSearchWord(word: String) {
        _searchWord.value = word
    }

    fun clearStatus() {
        _statusMessage.value = null
        _errorMessage.value = null
    }

    fun clearFirstRowPreview() {
        repository.clearFirstRowPreview()
    }

    // PIN lock-unlock action
    fun verifyPin(pin: String, configPin: String): Boolean {
        return if (pin == configPin) {
            _isUnlocked.value = true
            _statusMessage.value = "Owner verified and access unlocked!"
            true
        } else {
            _errorMessage.value = "Incorrect verification PIN. Please try again."
            false
        }
    }

    fun lockAccess() {
        _isUnlocked.value = false
        _statusMessage.value = "Logged out. Editor access locked."
    }

    /**
     * Map spreadsheet URL, extract key ID, save config, and run first sync.
     * The sheet name is always "Service Ledger" — no API key needed, sync uses the
     * public GViz/export endpoints, which only require "Anyone with the link" sharing.
     */
    fun mapSpreadsheet(url: String, pin: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            _syncProgress.value = 0f
            _syncMilestone.value = "Starting mapping..."
            _syncError.value = null
            clearStatus()
            val sheetId = repository.extractSpreadsheetId(url)
            if (sheetId == null) {
                val err = "Invalid Google Sheets URL. Could not parse Spreadsheet ID."
                _syncError.value = err
                _errorMessage.value = err
                _isRefreshing.value = false
                return@launch
            }

            val newConfig = SheetConfig(
                spreadsheetUrl = url,
                spreadsheetId = sheetId,
                sheetName = "Service Ledger",
                ownerPin = pin,
                isVerified = true,
                useLocalDemo = false,
                lastSyncTime = 0L
            )

            repository.saveConfig(newConfig)
            _isUnlocked.value = true // Automatically unlock upon configuring

            // Refresh sheets data from the newly mapped URL
            val result = repository.refreshSheetData { milestone, progress ->
                _syncMilestone.value = milestone
                _syncProgress.value = progress
            }
            if (result.isSuccess) {
                _statusMessage.value = "Spreadsheet mapped and synchronized successfully!"
                _syncProgress.value = null
                _syncMilestone.value = null
            } else {
                val errMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                _syncError.value = errMsg
                _errorMessage.value = "Mapped URL saved, but sync failed. See details below."
                _syncProgress.value = null
                _syncMilestone.value = null
            }
            _isRefreshing.value = false
        }
    }

    /**
     * Switch back to local demo mode for testing/showcase
     */
    fun switchToDemoMode() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _syncError.value = null
            _syncProgress.value = null
            _syncMilestone.value = null
            clearStatus()
            repository.resetToDemoData()
            _isUnlocked.value = true
            _statusMessage.value = "Switched to Local Demo Mode with fully interactive sample records."
            _isRefreshing.value = false
        }
    }

    /**
     * Sync data from current sheet
     */
    fun syncData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            _syncProgress.value = 0f
            _syncMilestone.value = "Starting synchronization..."
            _syncError.value = null
            clearStatus()
            val result = repository.refreshSheetData { milestone, progress ->
                _syncMilestone.value = milestone
                _syncProgress.value = progress
            }
            if (result.isSuccess) {
                _statusMessage.value = "Data synchronized successfully!"
                _syncProgress.value = null
                _syncMilestone.value = null
            } else {
                val errMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                _syncError.value = errMsg
                _errorMessage.value = "Sync failed. See details below."
                _syncProgress.value = null
                _syncMilestone.value = null
            }
            _isRefreshing.value = false
        }
    }

    fun clearSyncError() {
        _syncError.value = null
    }

    /**
     * Mark an entire list of unpaid rows for an employee as Paid
     */
    fun markEmployeeAsPaid(employeeName: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            clearStatus()

            val unpaidList = unpaidDuesByEmployee.value[employeeName] ?: emptyList()
            if (unpaidList.isEmpty()) {
                _errorMessage.value = "No outstanding dues found for $employeeName"
                _isRefreshing.value = false
                return@launch
            }

            var successCount = 0
            var failMessage: String? = null

            for (payment in unpaidList) {
                val result = repository.markRowAsPaid(payment, webhookUrl.value.ifBlank { null })
                if (result.isSuccess) {
                    successCount++
                } else {
                    failMessage = result.exceptionOrNull()?.message
                }
            }

            if (successCount == unpaidList.size) {
                _statusMessage.value = "All ${unpaidList.size} dues for $employeeName marked as Paid!"
            } else {
                _statusMessage.value = "$successCount dues marked as Paid locally."
                if (failMessage != null) {
                    _errorMessage.value = "Google Sheets update failed: $failMessage"
                }
            }

            _isRefreshing.value = false
        }
    }

    /**
     * Mark a single specific row as Paid
     */
    fun markRowAsPaid(payment: PaymentRow) {
        viewModelScope.launch {
            _isRefreshing.value = true
            clearStatus()
            val result = repository.markRowAsPaid(payment, webhookUrl.value.ifBlank { null })
            if (result.isSuccess) {
                _statusMessage.value = "Payment for ${payment.name} (${payment.serviceName}) marked as Paid!"
            } else {
                _statusMessage.value = "Marked as Paid locally."
                val error = result.exceptionOrNull()?.message
                if (error != null) {
                    _errorMessage.value = "Google Sheet sync error: $error"
                }
            }
            _isRefreshing.value = false
        }
    }

    /**
     * Download worksheet directly from an online URL (CSV, TSV, XLSX, or Google Sheet direct URL)
     * Downloads file locally to phone storage and imports into "Service Ledger"
     */
    fun downloadWorksheetFromUrl(onlineUrl: String) {
        viewModelScope.launch {
            _isRefreshing.value = true
            clearStatus()
            val context = getApplication<Application>().applicationContext
            val result = repository.downloadWorksheetFromUrl(onlineUrl, context, "Service Ledger")
            if (result.isSuccess) {
                _isUnlocked.value = true
                val savePath = result.getOrNull() ?: "Downloads folder"
                _statusMessage.value = "Successfully downloaded 'Service Ledger' locally to phone storage ($savePath) and imported into your app!"
            } else {
                _errorMessage.value = "Download failed: ${result.exceptionOrNull()?.message}"
            }
            _isRefreshing.value = false
        }
    }

    /**
     * Import a local spreadsheet file (.tsv/.csv/.xlsx) as the source of truth
     */
    fun importLocalFile(fileName: String, fileBytes: ByteArray) {
        viewModelScope.launch {
            _isRefreshing.value = true
            clearStatus()
            val result = repository.importLocalSpreadsheetData(fileName, fileBytes)
            if (result.isSuccess) {
                _isUnlocked.value = true
                _statusMessage.value = "Successfully imported '$fileName' offline ledger!"
            } else {
                _errorMessage.value = "Import failed: ${result.exceptionOrNull()?.message}"
            }
            _isRefreshing.value = false
        }
    }

    /**
     * Clear all database data (payments, config) to start fresh or reset
     */
    fun clearAllData() {
        viewModelScope.launch {
            _isRefreshing.value = true
            clearStatus()
            repository.clearAll()
            _statusMessage.value = "All local data and offline ledger cleared successfully!"
            _isRefreshing.value = false
        }
    }
}

/**
 * Top-level date parser helper that handles multiple common date string formats
 * and normalizes them into "DD MMM YYYY" (e.g., "01 Jul 2026") for calculation and display.
 */
fun parseAndFormatDate(dateStr: String): String {
    val clean = dateStr.trim()
    if (clean.isEmpty()) return "Unknown"
    if (clean.startsWith("Row", ignoreCase = true)) return clean

    val datePart = if (clean.contains("T")) {
        clean.substringBefore("T")
    } else if (clean.contains(" ")) {
        val parts = clean.split(Regex("\\s+"))
        if (parts.size >= 3 && parts[1].lowercase() in listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec", "january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")) {
            parts.take(3).joinToString(" ")
        } else if (parts.size >= 2) {
            if (parts[1].contains(":")) {
                parts[0]
            } else {
                parts.take(3).joinToString(" ")
            }
        } else {
            clean
        }
    } else {
        clean
    }

    // Try YYYY-MM-DD
    val yyyyMmDdRegex = """^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})$""".toRegex()
    yyyyMmDdRegex.matchEntire(datePart)?.let { match ->
        val y = match.groupValues[1]
        val m = match.groupValues[2].toIntOrNull() ?: 1
        val d = match.groupValues[3].toIntOrNull() ?: 1
        return formatToDdMmYyyy(d, m, y.toIntOrNull() ?: 2026)
    }

    // Try DD/MM/YYYY or MM/DD/YYYY
    val ddMmYyyyRegex = """^(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})$""".toRegex()
    ddMmYyyyRegex.matchEntire(datePart)?.let { match ->
        val first = match.groupValues[1].toIntOrNull() ?: 1
        val second = match.groupValues[2].toIntOrNull() ?: 1
        var yVal = match.groupValues[3].toIntOrNull() ?: 2026
        if (yVal < 100) {
            yVal += 2000
        }

        val d: Int
        val m: Int
        if (second > 12) {
            m = first
            d = second
        } else {
            d = first
            m = second
        }
        return formatToDdMmYyyy(d, m, yVal)
    }

    // Try Textual months
    val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    val fullMonths = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")

    val lower = datePart.lowercase()
    var foundMonthIdx = -1
    for (i in months.indices) {
        if (lower.contains(months[i]) || lower.contains(fullMonths[i])) {
            foundMonthIdx = i
            break
        }
    }

    if (foundMonthIdx != -1) {
        val m = foundMonthIdx + 1
        val digits = datePart.replace(Regex("[^0-9]"), " ").split(Regex("\\s+")).filter { it.isNotEmpty() }
        var d = 1
        var y = 2026
        if (digits.size == 1) {
            val num = digits[0].toIntOrNull() ?: 1
            if (num > 1000) y = num else d = num
        } else if (digits.size >= 2) {
            val firstNum = digits[0].toIntOrNull() ?: 1
            val secondNum = digits[1].toIntOrNull() ?: 2026
            if (firstNum > 1000) {
                y = firstNum
                d = secondNum
            } else {
                d = firstNum
                y = secondNum
            }
        }
        return formatToDdMmYyyy(d, m, y)
    }

    return datePart
}

private fun formatToDdMmYyyy(day: Int, month: Int, year: Int): String {
    val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val mName = monthNames[(month - 1).coerceIn(0, 11)]
    return String.format("%02d %s %04d", day, mName, year)
}