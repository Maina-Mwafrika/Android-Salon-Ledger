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
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- EXPENSES & PROFITABILITY STATE ---
    val allExpenses: StateFlow<List<ExpenseRow>> = repository.allExpensesFlow
        .map { dbExpenses ->
            val normalized = dbExpenses.map { row ->
                row.copy(month = normalizeMonthString(row.month, row.date))
            }
            ensureMonthlyRentExpenses(normalized)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val expensesTimePeriod = MutableStateFlow("All Time")
    val expensesSearchQuery = MutableStateFlow("")
    val expensesSelectedDept = MutableStateFlow<String?>(null)
    val expensesSelectedType = MutableStateFlow<String?>(null)

    val expenseDepartments: StateFlow<List<String>> = allExpenses.map { list ->
        list.map { it.department }.filter { it.isNotBlank() }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseTypes: StateFlow<List<String>> = allExpenses.map { list ->
        list.map { it.expenseType }.filter { it.isNotBlank() }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val availableMonths: StateFlow<List<String>> = combine(allPayments, allExpenses) { payments, expenses ->
        val todayCal = java.util.Calendar.getInstance()
        val curYear = todayCal.get(java.util.Calendar.YEAR)
        val curMonthIdx = todayCal.get(java.util.Calendar.MONTH)
        val monthNames = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

        val paymentMonths = payments.map { normalizeMonthString(it.month, it.timestamp) }.filter { it.isNotBlank() }
        val expenseMonths = expenses.map { normalizeMonthString(it.month, it.date) }.filter { it.isNotBlank() }

        (paymentMonths + expenseMonths)
            .distinct()
            .filter { mStr ->
                val parts = mStr.split(" ")
                val mName = parts[0]
                val yVal = parts.getOrNull(1)?.toIntOrNull() ?: curYear
                val mIdx = monthNames.indexOfFirst { it.equals(mName, ignoreCase = true) }
                if (mIdx != -1) {
                    yVal < curYear || (yVal == curYear && mIdx <= curMonthIdx)
                } else true
            }
            .sortedWith(Comparator { m1, m2 ->
                val ms1 = parseTimestampToMillis("01 $m1")
                val ms2 = parseTimestampToMillis("01 $m2")
                ms1.compareTo(ms2)
            })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("July 2026", "August 2026"))

    // Filtered Expenses according to Period, Search, Dept, Type
    val filteredExpenses: StateFlow<List<ExpenseRow>> = combine(
        allExpenses,
        expensesTimePeriod,
        expensesSearchQuery,
        expensesSelectedDept,
        expensesSelectedType
    ) { expenses, period, search, dept, type ->
        val refTimeMs = run {
            val eMs = expenses.map { parseTimestampToMillis(it.date, it.month) }.filter { it > 0L }
            if (eMs.isNotEmpty()) maxOf(eMs.maxOrNull() ?: 0L, System.currentTimeMillis()) else System.currentTimeMillis()
        }

        val refCal = java.util.Calendar.getInstance(java.util.TimeZone.getDefault()).apply {
            timeInMillis = refTimeMs
            firstDayOfWeek = java.util.Calendar.MONDAY
        }

        val weekStartMs = (refCal.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val weekEndMs = (refCal.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
            add(java.util.Calendar.DAY_OF_YEAR, 7)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.MILLISECOND, -1)
        }.timeInMillis

        val todayStartMs = (refCal.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todayEndMs = (refCal.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        }.timeInMillis

        expenses.filter { item ->
            val matchesPeriod = when (period) {
                "All Time" -> true
                "Today" -> {
                    val tMs = parseTimestampToMillis(item.date, item.month)
                    if (tMs > 0L) tMs in todayStartMs..todayEndMs else item.date.contains("Today", ignoreCase = true)
                }
                "This Week" -> {
                    val tMs = parseTimestampToMillis(item.date, item.month)
                    if (tMs > 0L) tMs in weekStartMs..weekEndMs else false
                }
                else -> {
                    val normM = normalizeMonthString(item.month, item.date)
                    normM.equals(period, ignoreCase = true) || item.month.equals(period, ignoreCase = true) || item.date.contains(period, ignoreCase = true)
                }
            }
            val matchesSearch = if (search.isBlank()) true else {
                item.itemPurchased.contains(search, ignoreCase = true) ||
                item.recordedBy.contains(search, ignoreCase = true) ||
                item.department.contains(search, ignoreCase = true) ||
                item.expenseType.contains(search, ignoreCase = true) ||
                item.paymentMethod.contains(search, ignoreCase = true)
            }
            val matchesDept = dept == null || item.department.equals(dept, ignoreCase = true)
            val matchesType = type == null || item.expenseType.equals(type, ignoreCase = true)

            matchesPeriod && matchesSearch && matchesDept && matchesType
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Expense Metrics
    val totalExpensesAmount: StateFlow<Double> = filteredExpenses.map { list ->
        list.sumOf { it.amountSpent }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val expensesByType: StateFlow<List<Pair<String, Double>>> = filteredExpenses.map { list ->
        list.groupBy { it.expenseType }
            .mapValues { (_, rows) -> rows.sumOf { it.amountSpent } }
            .toList()
            .sortedByDescending { it.second }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expensesByDepartment: StateFlow<List<Pair<String, Double>>> = filteredExpenses.map { list ->
        list.groupBy { it.department }
            .mapValues { (_, rows) -> rows.sumOf { it.amountSpent } }
            .toList()
            .sortedByDescending { it.second }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val topCostItems: StateFlow<List<ExpenseRow>> = filteredExpenses.map { list ->
        list.sortedByDescending { it.amountSpent }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Income for the same selected time period
    val filteredPaymentsForPeriod: StateFlow<List<PaymentRow>> = combine(allPayments, expensesTimePeriod) { payments, period ->
        val refTimeMs = run {
            val pMs = payments.map { parseTimestampToMillis(it.timestamp, it.month) }.filter { it > 0L }
            if (pMs.isNotEmpty()) maxOf(pMs.maxOrNull() ?: 0L, System.currentTimeMillis()) else System.currentTimeMillis()
        }

        val refCal = java.util.Calendar.getInstance(java.util.TimeZone.getDefault()).apply {
            timeInMillis = refTimeMs
            firstDayOfWeek = java.util.Calendar.MONDAY
        }

        val weekStartMs = (refCal.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val weekEndMs = (refCal.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
            add(java.util.Calendar.DAY_OF_YEAR, 7)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.MILLISECOND, -1)
        }.timeInMillis

        val todayStartMs = (refCal.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todayEndMs = (refCal.clone() as java.util.Calendar).apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        }.timeInMillis

        payments.filter { payment ->
            when (period) {
                "All Time" -> true
                "Today" -> {
                    val tMs = parseTimestampToMillis(payment.timestamp, payment.month)
                    if (tMs > 0L) tMs in todayStartMs..todayEndMs else payment.timestamp.contains("Today", ignoreCase = true)
                }
                "This Week" -> {
                    val tMs = parseTimestampToMillis(payment.timestamp, payment.month)
                    if (tMs > 0L) tMs in weekStartMs..weekEndMs else false
                }
                else -> {
                    val normM = normalizeMonthString(payment.month, payment.timestamp)
                    normM.equals(period, ignoreCase = true) || payment.month.equals(period, ignoreCase = true) || payment.timestamp.contains(period, ignoreCase = true)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalGrossRevenueForPeriod: StateFlow<Double> = filteredPaymentsForPeriod.map { list ->
        list.sumOf { it.amountPaid }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalCommissionsForPeriod: StateFlow<Double> = filteredPaymentsForPeriod.map { list ->
        list.sumOf { it.staffCommission }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalSalonShareForPeriod: StateFlow<Double> = filteredPaymentsForPeriod.map { list ->
        list.sumOf { if (it.salonShare > 0) it.salonShare else (it.amountPaid - it.staffCommission) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val netProfit: StateFlow<Double> = combine(totalSalonShareForPeriod, totalExpensesAmount) { salonShare, exp ->
        salonShare - exp
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val profitMarginPct: StateFlow<Double> = combine(totalSalonShareForPeriod, netProfit) { salonShare, profit ->
        if (salonShare > 0) (profit / salonShare) * 100.0 else 0.0
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun setExpensesTimePeriod(period: String) {
        expensesTimePeriod.value = period
    }

    fun setExpensesSearchQuery(query: String) {
        expensesSearchQuery.value = query
    }

    fun setExpensesDepartmentFilter(dept: String?) {
        expensesSelectedDept.value = dept
    }

    fun setExpensesTypeFilter(type: String?) {
        expensesSelectedType.value = type
    }

    fun addExpense(
        date: String,
        recordedBy: String,
        department: String,
        expenseType: String,
        itemPurchased: String,
        quantity: Double,
        amountSpent: Double,
        paymentMethod: String,
        month: String
    ) {
        viewModelScope.launch {
            clearStatus()
            val newExpense = ExpenseRow(
                spreadsheetId = repository.activeConfigFlow.firstOrNull()?.spreadsheetId ?: "demo_spreadsheet",
                rowIndex = 0,
                date = date.ifBlank { "2026-07-12" },
                recordedBy = recordedBy.ifBlank { "Staff" },
                department = department.ifBlank { "General" },
                expenseType = expenseType.ifBlank { "Operational" },
                itemPurchased = itemPurchased,
                quantity = quantity,
                amountSpent = amountSpent,
                paymentMethod = paymentMethod.ifBlank { "Mpesa" },
                month = month.ifBlank { "July 2026" }
            )
            repository.addExpense(newExpense)
            _statusMessage.value = "Recorded expense: $itemPurchased (KES ${amountSpent.toInt()})"
        }
    }

    fun deleteExpense(expense: ExpenseRow) {
        viewModelScope.launch {
            clearStatus()
            _errorMessage.value = "Expense entries are synced ledger records and cannot be deleted."
        }
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch {
            clearStatus()
            _errorMessage.value = "Expense entries are synced ledger records and cannot be deleted."
        }
    }

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
     * Mark a single specific row as Unpaid
     */
    fun markRowAsUnpaid(payment: PaymentRow) {
        viewModelScope.launch {
            _isRefreshing.value = true
            clearStatus()
            val result = repository.markRowAsUnpaid(payment)
            if (result.isSuccess) {
                _statusMessage.value = "Payment for ${payment.name} (${payment.serviceName}) marked as Unpaid!"
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
 * Primary date parser that converts month, day, year and other common formats to epoch millis.
 * Default format is MONTH, DAY, YEAR unless t0 > 12 (which forces DAY, MONTH, YEAR)
 * or t0 > 1000 (which forces YYYY, MM, DD).
 * Also accepts an optional hintMonthStr and prevMillis to handle trend jumps > 20 days.
 */
fun parseTimestampToMillis(timestamp: String, hintMonthStr: String = "", prevMillis: Long = 0L): Long {
    val clean = timestamp.trim()
    if (clean.isBlank() || clean.startsWith("Row", ignoreCase = true)) return 0L

    var hour = 0
    var minute = 0
    var second = 0
    var datePart = clean

    val timeRegex = """(\d{1,2}):(\d{2})(?::(\d{2}))?""".toRegex()
    timeRegex.find(clean)?.let { match ->
        hour = match.groupValues[1].toIntOrNull() ?: 0
        minute = match.groupValues[2].toIntOrNull() ?: 0
        second = match.groupValues[3].toIntOrNull() ?: 0
        datePart = clean.replace(match.value, "").trim()
    }

    val monthNames = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    val fullMonthNames = listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december")

    val lower = datePart.lowercase()
    var foundMonthIdx = -1
    for (i in monthNames.indices) {
        if (lower.contains(monthNames[i]) || lower.contains(fullMonthNames[i])) {
            foundMonthIdx = i
            break
        }
    }

    val cal = java.util.Calendar.getInstance()
    cal.clear()

    if (foundMonthIdx != -1) {
        val digits = datePart.replace(Regex("[^0-9]"), " ").split(Regex("\\s+")).filter { it.isNotEmpty() }
        var day = 1
        var year = 2026
        if (digits.size == 1) {
            val num = digits[0].toIntOrNull() ?: 1
            if (num > 1000) year = num else day = num
        } else if (digits.size >= 2) {
            val firstNum = digits[0].toIntOrNull() ?: 1
            val secondNum = digits[1].toIntOrNull() ?: 2026
            if (firstNum > 1000) {
                year = firstNum
                day = secondNum
            } else {
                day = firstNum
                year = secondNum
            }
        }
        cal.set(year, foundMonthIdx, day.coerceIn(1, 31), hour, minute, second)
        return cal.timeInMillis
    }

    val tokens = datePart.replace(Regex("[^0-9]"), " ").split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.size >= 3) {
        val t0 = tokens[0].toIntOrNull() ?: 1
        val t1 = tokens[1].toIntOrNull() ?: 1
        var t2 = tokens[2].toIntOrNull() ?: 2026
        if (t2 < 100) t2 += 2000

        var year = 2026
        var opt1Month = 1
        var opt1Day = 1

        if (t0 > 1000) {
            // YYYY MM DD
            year = t0
            opt1Month = t1
            opt1Day = t2
        } else {
            // User requested format: MONTH, DAY, YEAR
            opt1Month = t0
            opt1Day = t1
            year = t2
        }

        // Determine if hintMonthStr resolves to a specific month index (1..12)
        var hintMonthIdx = -1
        if (hintMonthStr.isNotBlank()) {
            val hLower = hintMonthStr.lowercase()
            for (i in monthNames.indices) {
                if (hLower.contains(monthNames[i]) || hLower.contains(fullMonthNames[i])) {
                    hintMonthIdx = i + 1
                    break
                }
            }
        }

        fun toMs(m: Int, d: Int, y: Int): Long {
            val c = java.util.Calendar.getInstance()
            c.clear()
            c.set(y, (m - 1).coerceIn(0, 11), d.coerceIn(1, 31), hour, minute, second)
            return c.timeInMillis
        }

        var finalMonth = opt1Month
        var finalDay = opt1Day

        if (opt1Month > 12) {
            // t0 was > 12, so t0 must be Day, t1 must be Month
            finalMonth = opt1Day
            finalDay = opt1Month
        } else if (opt1Day > 12 && opt1Month <= 12) {
            // t0 <= 12 and t1 > 12 -> fits MONTH = t0, DAY = t1
            finalMonth = opt1Month
            finalDay = opt1Day
        } else {
            // Both t0 and t1 <= 12
            if (hintMonthIdx != -1) {
                if (opt1Month == hintMonthIdx) {
                    finalMonth = opt1Month
                    finalDay = opt1Day
                } else if (opt1Day == hintMonthIdx) {
                    finalMonth = opt1Day
                    finalDay = opt1Month
                }
            } else {
                val nowMs = System.currentTimeMillis() + 86400000L // 1 day future allowance threshold
                val ms1 = toMs(opt1Month, opt1Day, year)
                val ms2 = toMs(opt1Day, opt1Month, year)

                if (ms1 > nowMs && ms2 <= nowMs) {
                    // Future date error guard: ms1 is in the future, pick ms2 (present/past)
                    finalMonth = opt1Day
                    finalDay = opt1Month
                } else if (prevMillis > 0L) {
                    // Jump > 20 days trend correction guard
                    val diff1 = Math.abs(ms1 - prevMillis)
                    val diff2 = Math.abs(ms2 - prevMillis)
                    val twentyDaysMs = 20L * 86400000L
                    if (diff1 > twentyDaysMs && diff2 < diff1) {
                        finalMonth = opt1Day
                        finalDay = opt1Month
                    }
                }
            }
        }

        return toMs(finalMonth, finalDay, year)
    } else if (tokens.size == 2) {
        val t0 = tokens[0].toIntOrNull() ?: 1
        val t1 = tokens[1].toIntOrNull() ?: 2026
        val year = if (t1 > 1000) t1 else 2026
        val month = if (t1 > 1000) t0 else t0
        val monthIdx = (month - 1).coerceIn(0, 11)
        cal.set(year, monthIdx, 1, hour, minute, second)
        return cal.timeInMillis
    }

    return 0L
}

fun parseAndFormatDate(dateStr: String, hintMonthStr: String = ""): String {
    val clean = dateStr.trim()
    if (clean.isEmpty()) return "Unknown"
    if (clean.startsWith("Row", ignoreCase = true)) return clean

    val ms = parseTimestampToMillis(clean, hintMonthStr)
    if (ms > 0L) {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = ms }
        val monthNames = arrayOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        val d = cal.get(java.util.Calendar.DAY_OF_MONTH)
        val m = cal.get(java.util.Calendar.MONTH)
        val y = cal.get(java.util.Calendar.YEAR)
        return String.format(java.util.Locale.US, "%02d %s %04d", d, monthNames[m], y)
    }
    return clean
}

fun normalizeMonthString(rawMonth: String, dateStr: String = ""): String {
    val clean = rawMonth.trim()
    val monthNames = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val monthShort = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")

    val lower = clean.lowercase()
    var foundMonthIdx = -1
    for (i in monthNames.indices) {
        if (lower.contains(monthNames[i].lowercase()) || lower.contains(monthShort[i].lowercase())) {
            foundMonthIdx = i
            break
        }
    }

    val yearRegex = """\b(20\d{2})\b""".toRegex()
    var yearStr = yearRegex.find(clean)?.value

    if (yearStr == null && dateStr.isNotBlank()) {
        val dateMs = parseTimestampToMillis(dateStr, rawMonth)
        if (dateMs > 0L) {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = dateMs }
            yearStr = cal.get(java.util.Calendar.YEAR).toString()
            if (foundMonthIdx == -1) {
                foundMonthIdx = cal.get(java.util.Calendar.MONTH)
            }
        }
    }

    if (yearStr == null) {
        yearStr = "2026"
    }

    if (foundMonthIdx != -1) {
        return "${monthNames[foundMonthIdx]} $yearStr"
    }

    if (clean.isNotBlank()) {
        val dateMs = parseTimestampToMillis(dateStr, clean)
        if (dateMs > 0L) {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = dateMs }
            val m = cal.get(java.util.Calendar.MONTH)
            val y = cal.get(java.util.Calendar.YEAR)
            return "${monthNames[m]} $y"
        }
        return "$clean $yearStr"
    }

    if (dateStr.isNotBlank()) {
        val dateMs = parseTimestampToMillis(dateStr)
        if (dateMs > 0L) {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = dateMs }
            val m = cal.get(java.util.Calendar.MONTH)
            val y = cal.get(java.util.Calendar.YEAR)
            return "${monthNames[m]} $y"
        }
    }

    return "August 2026"
}

/**
 * Checks if a date string represents a date on or prior to current date.
 * Expenses with dates past the current date (up to today) cannot be deleted.
 */
fun isPastOrCurrentDate(dateStr: String): Boolean {
    if (dateStr.isBlank()) return true
    val clean = dateStr.trim().lowercase()
    if (clean == "today" || clean == "yesterday") return true

    val todayCal = java.util.Calendar.getInstance()
    todayCal.set(java.util.Calendar.HOUR_OF_DAY, 23)
    todayCal.set(java.util.Calendar.MINUTE, 59)
    todayCal.set(java.util.Calendar.SECOND, 59)
    todayCal.set(java.util.Calendar.MILLISECOND, 999)

    val dateFormats = listOf(
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US),
        java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.US),
        java.text.SimpleDateFormat("dd MMMM yyyy", java.util.Locale.US),
        java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.US),
        java.text.SimpleDateFormat("MM/dd/yyyy", java.util.Locale.US),
        java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.US),
        java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.US),
        java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.US)
    )

    for (fmt in dateFormats) {
        try {
            val parsedDate = fmt.parse(dateStr.trim())
            if (parsedDate != null) {
                return !parsedDate.after(todayCal.time)
            }
        } catch (_: Exception) { }
    }

    val currentYear = todayCal.get(java.util.Calendar.YEAR)
    val yearRegex = """\b(20\d{2})\b""".toRegex()
    val match = yearRegex.find(dateStr)
    if (match != null) {
        val yr = match.value.toIntOrNull() ?: currentYear
        if (yr < currentYear) return true
    }

    return true
}

private fun ensureMonthlyRentExpenses(dbExpenses: List<ExpenseRow>): List<ExpenseRow> {
    val todayCal = java.util.Calendar.getInstance()
    val curYear = todayCal.get(java.util.Calendar.YEAR)
    val curMonthIdx = todayCal.get(java.util.Calendar.MONTH) // 0-based

    todayCal.set(java.util.Calendar.HOUR_OF_DAY, 23)
    todayCal.set(java.util.Calendar.MINUTE, 59)
    todayCal.set(java.util.Calendar.SECOND, 59)
    todayCal.set(java.util.Calendar.MILLISECOND, 999)
    val todayEndMs = todayCal.timeInMillis

    val monthNames = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")

    // Filter out dbExpenses that are past current date / future months
    val result = dbExpenses.filter { row ->
        val ms = parseTimestampToMillis(row.date, row.month)
        if (ms > 0L) {
            ms <= todayEndMs
        } else {
            val parts = row.month.split(" ")
            val mName = parts[0]
            val yVal = parts.getOrNull(1)?.toIntOrNull() ?: curYear
            val mIdx = monthNames.indexOfFirst { it.equals(mName, ignoreCase = true) }
            if (mIdx != -1) {
                yVal < curYear || (yVal == curYear && mIdx <= curMonthIdx)
            } else true
        }
    }.toMutableList()

    val monthList = mutableListOf<String>()
    // Generate months from June 2026 up to current month (e.g. August 2026)
    for (mIdx in 5..curMonthIdx) { // 5 = June
        monthList.add("${monthNames[mIdx]} $curYear")
    }

    var rentIdCounter = -350001L

    for (mName in monthList) {
        val hasRent = result.any { row ->
            row.month.equals(mName, ignoreCase = true) &&
            (row.expenseType.contains("Rent", ignoreCase = true) || row.itemPurchased.contains("Rent", ignoreCase = true))
        }
        if (!hasRent) {
            val parts = mName.split(" ")
            val monthWord = if (parts.isNotEmpty()) parts[0] else "June"
            val yearWord = if (parts.size > 1) parts[1] else "2026"

            result.add(
                ExpenseRow(
                    id = rentIdCounter--,
                    date = "05 $monthWord $yearWord",
                    recordedBy = "Admin (Fixed)",
                    department = "Operational",
                    expenseType = "Fixed Rent",
                    itemPurchased = "Monthly Premises Rent",
                    quantity = 1.0,
                    amountSpent = 35000.0,
                    paymentMethod = "Bank Transfer",
                    month = mName,
                    spreadsheetId = dbExpenses.firstOrNull()?.spreadsheetId ?: ""
                )
            )
        }
    }
    return result
}