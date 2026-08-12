package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.example.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import com.example.data.PaymentRow
import com.example.data.ExpenseRow
import com.example.data.SheetConfig
import com.example.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentAppScreen(viewModel: PaymentViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val syncMilestone by viewModel.syncMilestone.collectAsStateWithLifecycle()
    val syncError by viewModel.syncError.collectAsStateWithLifecycle()
    val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val firstRowPreview by viewModel.firstRowPreview.collectAsStateWithLifecycle()
    val isUnlocked by viewModel.isUnlocked.collectAsStateWithLifecycle()
    val config = (uiState as? PaymentUiState.Success)?.config

    var activeTab by remember { mutableStateOf(0) }
    val tabs = listOf("Dues List", "Revenue Insights", "Expenses", "Settings & Mapping")

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatus()
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatus()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // SheeGlam Logo Avatar Thumbnail Badge
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(listOf(HotPink, RichGold)),
                                    CircleShape
                                )
                                .padding(2.5.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E1E1E)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.sheeglam_logo),
                                contentDescription = "SheeGlam Logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        }
                        Column {
                            Text(
                                text = "SheeGlam",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (config?.useLocalDemo == true) "Local Demo Ledger" else "SheeGlam Ledger: ${config?.sheetName}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                },
                actions = {
                    // Sync Button
                    IconButton(
                        onClick = { viewModel.syncData() },
                        modifier = Modifier
                            .testTag("sync_button")
                            .size(38.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sync",
                            tint = if (isRefreshing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Owner Verification lock icon
                    IconButton(
                        onClick = {
                            if (isUnlocked) {
                                viewModel.lockAccess()
                            } else {
                                activeTab = 2 // Direct to settings to unlock/map
                            }
                        },
                        modifier = Modifier
                            .testTag("auth_toggle_button")
                            .size(38.dp)
                            .background(
                                if (isUnlocked) SuccessGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.errorContainer,
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (isUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = if (isUnlocked) "Unlock Access" else "Lock Access",
                            tint = if (isUnlocked) SuccessGreen else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                modifier = Modifier.drawBehind {
                    drawLine(
                        color = outlineColor,
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            ) {
                tabs.forEachIndexed { index, label ->
                    val icon = when (index) {
                        0 -> if (activeTab == index) Icons.Default.Dashboard else Icons.Outlined.Dashboard
                        1 -> if (activeTab == index) Icons.Default.Analytics else Icons.Outlined.Analytics
                        2 -> if (activeTab == index) Icons.Default.ReceiptLong else Icons.Outlined.ReceiptLong
                        else -> if (activeTab == index) Icons.Default.Settings else Icons.Outlined.Settings
                    }
                    val tabName = when (index) {
                        0 -> "Dues"
                        1 -> "Revenue"
                        2 -> "Expenses"
                        else -> "Config"
                    }
                    NavigationBarItem(
                        selected = activeTab == index,
                        onClick = { activeTab = index },
                        label = { Text(tabName, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                        icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp)) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.secondary,
                            unselectedTextColor = MaterialTheme.colorScheme.secondary,
                            indicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (val state = uiState) {
                is PaymentUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is PaymentUiState.Success -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (isRefreshing || syncProgress != null || syncMilestone != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = syncMilestone ?: "Synchronizing data...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    val pct = syncProgress?.let { "${(it * 100).toInt()}%" } ?: ""
                                    Text(
                                        text = pct,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                if (syncProgress != null) {
                                    LinearProgressIndicator(
                                        progress = { syncProgress ?: 0f },
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                    )
                                } else {
                                    LinearProgressIndicator(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        syncError?.let { err ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                ),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = "Sync Error",
                                                tint = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                            Text(
                                                text = "Sync / Access Issue",
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                        IconButton(
                                            onClick = { viewModel.clearSyncError() },
                                            modifier = Modifier
                                                .size(32.dp)
                                                .testTag("close_sync_error_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Dismiss",
                                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 200.dp)
                                            .background(
                                                color = MaterialTheme.colorScheme.surface,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .border(
                                                width = 1.dp,
                                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.3f),
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(10.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        SelectionContainer {
                                            Text(
                                                text = err,
                                                style = androidx.compose.ui.text.TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 12.sp,
                                                    color = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(10.dp))
                                    OutlinedButton(
                                        onClick = { viewModel.clearSyncError() },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(38.dp)
                                            .testTag("dismiss_sync_error_button"),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        ),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.5f)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Close / Dismiss Notice", style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }

                        firstRowPreview?.let { preview ->
                            FirstRowPreviewNotificationCard(
                                previewText = preview,
                                onDismiss = { viewModel.clearFirstRowPreview() }
                            )
                        }

                        // Render Tab Content
                        Box(modifier = Modifier.weight(1f)) {
                            when (activeTab) {
                                0 -> EmployeesListTab(state, viewModel, isUnlocked, onNavigateToHistory = { activeTab = 1 })
                                1 -> InsightsTab(viewModel)
                                2 -> ExpensesTabContent(viewModel, isUnlocked)
                                3 -> SettingsTab(state, viewModel, isUnlocked)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionHeader(
    config: SheetConfig?,
    isUnlocked: Boolean,
    onSync: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (config?.useLocalDemo == true) Icons.Default.Storage else Icons.Default.CloudQueue,
                        contentDescription = "Source",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = if (config?.useLocalDemo == true) "Local Demo Environment" else "Google Sheets Active",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (config?.useLocalDemo == true) "Using mock salon database" else "Sheet: ${config?.sheetName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Owner lock badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isUnlocked) SuccessGreen.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isUnlocked) "OWNER UNLOCKED" else "LOCKED (VIEW ONLY)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isUnlocked) SuccessGreen else MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------
// TAB 1: EMPLOYEES & PAYMENT DETAILS LIST
// ------------------------------------------------------------------------------------
@Composable
fun EmployeesListTab(
    state: PaymentUiState.Success,
    viewModel: PaymentViewModel,
    isUnlocked: Boolean,
    onNavigateToHistory: (() -> Unit)? = null
) {
    val employees by viewModel.employeesList.collectAsStateWithLifecycle()
    val unpaidDuesMap by viewModel.unpaidDuesByEmployee.collectAsStateWithLifecycle()
    val unpaidRangeMap by viewModel.unpaidDuesRangeByEmployee.collectAsStateWithLifecycle()
    val totalPaidMap by viewModel.totalPaidByEmployee.collectAsStateWithLifecycle()
    val allPayments by viewModel.allPayments.collectAsStateWithLifecycle()
    val allExpenses by viewModel.allExpenses.collectAsStateWithLifecycle()

    val paidDuesMap = remember(allPayments) {
        allPayments.filter { it.paid }.groupBy { it.name }
    }

    var showPaidDuesMode by remember { mutableStateOf(false) }

    var selectedEmployeeForPaymentConfirm by remember { mutableStateOf<String?>(null) }
    var singlePaymentToConfirm by remember { mutableStateOf<PaymentRow?>(null) }

    var expandedEmployeeName by remember { mutableStateOf<String?>(null) }

    val filteredEmployees = employees.filter { employeeName ->
        val matchesSearch = employeeName.lowercase().contains(state.searchWord.lowercase())
        if (!matchesSearch) return@filter false

        if (showPaidDuesMode) {
            val paidList = paidDuesMap[employeeName] ?: emptyList()
            paidList.isNotEmpty()
        } else {
            val unpaidList = unpaidDuesMap[employeeName] ?: emptyList()
            unpaidList.isNotEmpty()
        }
    }

    val totalPaidToAll by viewModel.totalPaidToAll.collectAsStateWithLifecycle()
    // Dynamic real-time calculation of total unpaid staff commission dues
    val totalOutstanding = remember(allPayments) {
        allPayments.filter { !it.paid }.sumOf { it.staffCommission }
    }
    val pendingCount = remember(allPayments) {
        allPayments.filter { !it.paid }.size
    }

    // Weekly comparison trend: current calendar week vs previous calendar week profitability (income - expenses)
    val revenueTrendData = remember(allPayments, allExpenses) {
        val parsedPayments = allPayments.map { row ->
            row to parseTimestampToMillis(row.timestamp, row.month)
        }
        val parsedExpenses = allExpenses.map { row ->
            row to parseTimestampToMillis(row.date, row.month)
        }

        val allMillis = (parsedPayments.map { it.second } + parsedExpenses.map { it.second }).filter { it > 0L }
        if (allMillis.isEmpty()) {
            Triple("0.0% profit vs last week", "neutral", Icons.AutoMirrored.Filled.TrendingUp)
        } else {
            val maxDataTime = maxOf(allMillis.maxOrNull() ?: 0L, System.currentTimeMillis())
            val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getDefault()).apply {
                timeInMillis = maxDataTime
                firstDayOfWeek = java.util.Calendar.MONDAY
            }

            val currentWeekStartCal = (calendar.clone() as java.util.Calendar).apply {
                set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.MONDAY)
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val currentWeekStartMs = currentWeekStartCal.timeInMillis

            val previousWeekStartCal = (currentWeekStartCal.clone() as java.util.Calendar).apply {
                add(java.util.Calendar.WEEK_OF_YEAR, -1)
            }
            val previousWeekStartMs = previousWeekStartCal.timeInMillis

            val thisWeekRevenue = parsedPayments.filter { it.second >= currentWeekStartMs }.sumOf { it.first.amountPaid }
            val thisWeekExpenses = parsedExpenses.filter { it.second >= currentWeekStartMs }.sumOf { it.first.amountSpent }
            val thisWeekProfit = thisWeekRevenue - thisWeekExpenses

            val lastWeekRevenue = parsedPayments.filter { it.second in previousWeekStartMs until currentWeekStartMs }.sumOf { it.first.amountPaid }
            val lastWeekExpenses = parsedExpenses.filter { it.second in previousWeekStartMs until currentWeekStartMs }.sumOf { it.first.amountSpent }
            val lastWeekProfit = lastWeekRevenue - lastWeekExpenses

            if (lastWeekProfit == 0.0) {
                if (thisWeekProfit > 0.0) {
                    Triple("+100% profit vs last week", "success", Icons.AutoMirrored.Filled.TrendingUp)
                } else if (thisWeekProfit < 0.0) {
                    Triple("-100% profit vs last week", "error", Icons.Default.TrendingDown)
                } else {
                    Triple("0.0% profit vs last week", "neutral", Icons.AutoMirrored.Filled.TrendingUp)
                }
            } else {
                val diffPct = ((thisWeekProfit - lastWeekProfit) / kotlin.math.abs(lastWeekProfit)) * 100.0
                val formattedPct = String.format(java.util.Locale.US, "%.1f", diffPct)
                if (diffPct >= 0) {
                    Triple("+$formattedPct% profit vs last week", "success", Icons.AutoMirrored.Filled.TrendingUp)
                } else {
                    Triple("$formattedPct% profit vs last week", "error", Icons.Default.TrendingDown)
                }
            }
        }
    }

    val trendColor = when (revenueTrendData.second) {
        "success" -> SuccessGreen
        "error" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // Search & Filters Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = state.searchWord,
                    onValueChange = { viewModel.setSearchWord(it) },
                    placeholder = { Text("Search employees...", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (state.searchWord.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchWord("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    singleLine = true
                )
            }
        }

        // Quick Totals Grid of 2 Metric Cards
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Card 1: Total Revenue (Hero Gradient Card with History Link)
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .then(if (onNavigateToHistory != null) Modifier.clickable { onNavigateToHistory() } else Modifier),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Brush.horizontalGradient(listOf(HotPink, RichGold)))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "TOTAL REVENUE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White.copy(alpha = 0.9f),
                                    letterSpacing = 1.sp
                                )
                                if (onNavigateToHistory != null) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = "Earnings History",
                                        tint = Color.White.copy(alpha = 0.9f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Text(
                                text = formatKES(allPayments.sumOf { it.amountPaid }),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = revenueTrendData.third,
                                    contentDescription = "Trending",
                                    tint = IvoryCream,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = revenueTrendData.first,
                                    fontSize = 10.sp,
                                    color = IvoryCream,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Card 2: Unpaid Dues Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xFFFF6B81),
                                        Color(0xFFFFA07A)
                                    )
                                )
                            )
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "UNPAID DUES",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White.copy(alpha = 0.95f),
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = formatKES(totalOutstanding),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.White.copy(alpha = 0.25f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "$pendingCount Pending",
                                        fontSize = 10.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Employee List Title & Toggle Buttons
        item {
            Column {
                Text(
                    text = "Employees & Payroll Details",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val pendingPayCount = allPayments.count { !it.paid }
                    val paidPayCount = allPayments.count { it.paid }

                    Button(
                        onClick = { showPaidDuesMode = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!showPaidDuesMode) Color(0xFFFF6B81) else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (!showPaidDuesMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("toggle_unpaid_dues")
                    ) {
                        Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Outstanding ($pendingPayCount)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { showPaidDuesMode = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (showPaidDuesMode) SuccessGreen else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (showPaidDuesMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("toggle_paid_dues")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paid Ledger ($paidPayCount)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (filteredEmployees.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = "Empty",
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No employees found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        } else {
            items(filteredEmployees, key = { it }) { employeeName ->
                val unpaidDues = unpaidDuesMap[employeeName] ?: emptyList()
                val totalPaid = totalPaidMap[employeeName] ?: 0.0
                val unpaidRange = unpaidRangeMap[employeeName] ?: "No outstanding dues"
                val isExpanded = expandedEmployeeName == employeeName
                val paidDues = paidDuesMap[employeeName] ?: emptyList()
                val paidRange = remember(paidDues) {
                    if (paidDues.isEmpty()) "No paid records"
                    else {
                        val sorted = paidDues.sortedBy { it.rowIndex }
                        val first = parseAndFormatDate(sorted.first().timestamp)
                        val last = parseAndFormatDate(sorted.last().timestamp)
                        if (first == last) first else "$first to $last"
                    }
                }

                EmployeeItemCard(
                    name = employeeName,
                    unpaidDues = unpaidDues,
                    paidDues = paidDues,
                    showPaidMode = showPaidDuesMode,
                    totalPaid = totalPaid,
                    unpaidRange = unpaidRange,
                    paidRange = paidRange,
                    isExpanded = isExpanded,
                    onExpandToggle = {
                        expandedEmployeeName = if (isExpanded) null else employeeName
                    },
                    onMarkPaidAll = {
                        selectedEmployeeForPaymentConfirm = employeeName
                    },
                    onMarkSinglePaid = { payment ->
                        singlePaymentToConfirm = payment
                    },
                    onMarkSingleUnpaid = { payment ->
                        viewModel.markRowAsUnpaid(payment)
                    },
                    isUnlocked = isUnlocked
                )
            }
        }
    }

    // Confirmation dialog for ALL employee payments
    selectedEmployeeForPaymentConfirm?.let { name ->
        val unpaidDuesList = unpaidDuesMap[name] ?: emptyList()
        val totalUnpaidAmt = unpaidDuesList.sumOf { it.staffCommission }
        val unpaidRange = unpaidRangeMap[name] ?: "No outstanding dues"

        ConfirmPaymentDialog(
            employeeName = name,
            totalAmount = totalUnpaidAmt,
            itemCount = unpaidDuesList.size,
            periodRange = unpaidRange,
            isUnlocked = isUnlocked,
            onConfirm = { pin ->
                if (isUnlocked || viewModel.verifyPin(pin, state.config?.ownerPin ?: "")) {
                    viewModel.markEmployeeAsPaid(name)
                    selectedEmployeeForPaymentConfirm = null
                }
            },
            onDismiss = { selectedEmployeeForPaymentConfirm = null }
        )
    }

    // Confirmation dialog for SINGLE payment row
    singlePaymentToConfirm?.let { payment ->
        ConfirmPaymentDialog(
            employeeName = payment.name,
            totalAmount = payment.staffCommission,
            itemCount = 1,
            serviceName = payment.serviceName,
            periodRange = payment.timestamp,
            isUnlocked = isUnlocked,
            onConfirm = { pin ->
                if (isUnlocked || viewModel.verifyPin(pin, state.config?.ownerPin ?: "")) {
                    viewModel.markRowAsPaid(payment)
                    singlePaymentToConfirm = null
                }
            },
            onDismiss = { singlePaymentToConfirm = null }
        )
    }
}

@Composable
fun EmployeeItemCard(
    name: String,
    unpaidDues: List<PaymentRow>,
    paidDues: List<PaymentRow>,
    showPaidMode: Boolean,
    totalPaid: Double,
    unpaidRange: String,
    paidRange: String,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    onMarkPaidAll: () -> Unit,
    onMarkSinglePaid: (PaymentRow) -> Unit,
    onMarkSingleUnpaid: ((PaymentRow) -> Unit)? = null,
    isUnlocked: Boolean
) {
    val totalUnpaid = remember(unpaidDues) { unpaidDues.sumOf { it.staffCommission } }
    val duesToShow = if (showPaidMode) paidDues else unpaidDues
    val totalAmount = remember(duesToShow) { duesToShow.sumOf { it.staffCommission } }

    val hasPending = totalUnpaid > 0
    val cardBackground = if (!showPaidMode && hasPending) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
    val cardBorder = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("employee_card_${name.replace(" ", "_")}"),
        colors = CardDefaults.cardColors(
            containerColor = cardBackground
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = cardBorder
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
        ) {
            if (!showPaidMode && hasPending) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(14.dp)
            ) {
                // Header Row: Employee Avatar and Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Avatar placeholder with custom color
                        val avatarBg = remember(name) { getAvatarColor(name) }
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(avatarBg, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = name.take(2).uppercase(),
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                fontSize = 15.sp
                            )
                        }

                        Column {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Paid: ${formatKES(totalPaid)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Balance Badge or Chevron
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (showPaidMode) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SuccessGreen)
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = "${paidDues.size} Paid",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        } else {
                            if (hasPending) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    Color(0xFFFF6B81),
                                                    Color(0xFFFFA07A)
                                                )
                                            )
                                        )
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "${unpaidDues.size} Dues",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SuccessGreen)
                                        .padding(horizontal = 10.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Text(
                                            text = "Paid up",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        IconButton(
                            onClick = onExpandToggle,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = "Expand details",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Unpaid/Paid summary & actions block
                if (!showPaidMode) {
                    if (hasPending) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "UNPAID DUES AMOUNT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = formatKES(totalUnpaid),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Period: $unpaidRange",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Button(
                                onClick = onMarkPaidAll,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(20.dp),
                                modifier = Modifier.testTag("pay_button_${name.replace(" ", "_")}"),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Mark Paid", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    if (paidDues.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "TOTAL DISBURSED AMOUNT",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen
                                )
                                Text(
                                    text = formatKES(totalAmount),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessGreen
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Period: $paidRange",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // Expanded detail row
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (showPaidMode) "Disbursement Ledger" else "Individual Service Ledger",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )

                        if (duesToShow.isEmpty()) {
                            Text(
                                text = if (showPaidMode) "No paid records to show." else "No outstanding dues to show.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )
                        } else {
                            duesToShow.forEach { row ->
                                LedgerItemRow(
                                    row = row,
                                    onMarkPaid = { onMarkSinglePaid(row) },
                                    onMarkUnpaid = { onMarkSingleUnpaid?.invoke(row) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LedgerItemRow(
    row: PaymentRow,
    onMarkPaid: () -> Unit,
    onMarkUnpaid: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
        ),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = row.section,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = row.paymentMethod,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = row.serviceName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (row.notes.isNotBlank()) {
                    Text(
                        text = "Notes: ${row.notes}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                val formattedDate = remember(row.timestamp) { parseAndFormatDate(row.timestamp) }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (formattedDate != row.timestamp && formattedDate != "Unknown") "Date: $formattedDate (${row.timestamp})" else "Date: ${row.timestamp}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatKES(row.staffCommission),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (row.paid) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SuccessGreen)
                            .then(if (onMarkUnpaid != null) Modifier.clickable { onMarkUnpaid() } else Modifier)
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Paid",
                                tint = Color.White,
                                modifier = Modifier.size(10.dp)
                            )
                            Text(
                                text = "Paid",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                } else {
                    IconButton(
                        onClick = onMarkPaid,
                        modifier = Modifier.size(28.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Mark single paid",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------
// TAB 2: VISUALIZATION REVENUE DASHBOARD
// ------------------------------------------------------------------------------------
enum class LedgerSort {
    NAME_ASC, NAME_DESC,
    PAID_ASC, PAID_DESC,
    PENDING_ASC, PENDING_DESC,
    PAYOUT_ASC, PAYOUT_DESC
}

enum class PeriodMode {
    ALL_TIME, MONTH, YEAR, CUSTOM
}

@Composable
fun SalonEarningsHistorySection(
    allPayments: List<PaymentRow>
) {
    var selectedPeriodMode by remember { mutableStateOf(PeriodMode.MONTH) }
    var selectedYear by remember { mutableIntStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)) }
    var searchQuery by remember { mutableStateOf("") }
    var isExpandedList by remember { mutableStateOf(false) }

    val parsedPayments = remember(allPayments) {
        var prevMs = 0L
        allPayments.map { row ->
            val ms = parseTimestampToMillis(row.timestamp, row.month, prevMs)
            if (ms > 0L) prevMs = ms
            val cal = java.util.Calendar.getInstance()
            if (ms > 0L) {
                cal.timeInMillis = ms
            }
            Triple(row, cal, ms)
        }
    }

    val monthNames = remember {
        listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    }

    val periodPayments = remember(parsedPayments, selectedPeriodMode, selectedYear, selectedMonth) {
        parsedPayments.filter { (_, cal, _) ->
            when (selectedPeriodMode) {
                PeriodMode.ALL_TIME -> true
                PeriodMode.MONTH -> cal.get(java.util.Calendar.YEAR) == selectedYear && cal.get(java.util.Calendar.MONTH) == selectedMonth
                PeriodMode.YEAR -> cal.get(java.util.Calendar.YEAR) == selectedYear
                PeriodMode.CUSTOM -> true
            }
        }
    }

    val totalGrossRevenue = remember(periodPayments) { periodPayments.sumOf { it.first.amountPaid } }
    val totalSalonShare = remember(periodPayments) { periodPayments.sumOf { it.first.salonShare } }
    val totalStaffCommissions = remember(periodPayments) { periodPayments.sumOf { it.first.staffCommission } }
    val totalServicesCount = remember(periodPayments) { periodPayments.size }
    val paidServicesCount = remember(periodPayments) { periodPayments.count { it.first.paid } }
    val unpaidDuesSum = remember(periodPayments) { periodPayments.filter { !it.first.paid }.sumOf { it.first.staffCommission } }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("earnings_history_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            // Header Title
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(
                                Brush.linearGradient(listOf(HotPink, RichGold)),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "History",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Salon Earnings History",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Filter history by Month, Year, or All Time",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Period Selector Tabs (Month, Year, All Time)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    PeriodMode.MONTH to "Month",
                    PeriodMode.YEAR to "Year",
                    PeriodMode.ALL_TIME to "All Time"
                ).forEach { (mode, label) ->
                    val isSelected = selectedPeriodMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { selectedPeriodMode = mode }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Period Controls
            when (selectedPeriodMode) {
                PeriodMode.MONTH -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (selectedMonth > 0) {
                                    selectedMonth -= 1
                                } else {
                                    selectedMonth = 11
                                    selectedYear -= 1
                                }
                            }
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Prev Month")
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${monthNames[selectedMonth]} $selectedYear",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "$totalServicesCount transactions",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        IconButton(
                            onClick = {
                                if (selectedMonth < 11) {
                                    selectedMonth += 1
                                } else {
                                    selectedMonth = 0
                                    selectedYear += 1
                                }
                            }
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
                        }
                    }
                }
                PeriodMode.YEAR -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { selectedYear -= 1 }
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Prev Year")
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Year $selectedYear",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "$totalServicesCount transactions",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        IconButton(
                            onClick = { selectedYear += 1 }
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "Next Year")
                        }
                    }
                }
                PeriodMode.ALL_TIME -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Full History (${periodPayments.size} total entries)",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                else -> {}
            }

            // Key Period Metrics Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "Gross Revenue", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(text = formatKES(totalGrossRevenue), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = SuccessGreen.copy(alpha = 0.12f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "Salon Net", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SuccessGreen)
                        Text(text = formatKES(totalSalonShare), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = SuccessGreen)
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(text = "Staff Dues", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                        Text(text = formatKES(totalStaffCommissions), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black)
                    }
                }
            }

            // Secondary Info Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Paid Services: $paidServicesCount / $totalServicesCount",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Unpaid Dues: ${formatKES(unpaidDuesSum)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (unpaidDuesSum > 0) MaterialTheme.colorScheme.error else SuccessGreen
                )
            }

            // Visual Period Revenue Bar Chart
            if (periodPayments.isNotEmpty()) {
                Text(
                    text = "Period Revenue Breakdown",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                PeriodRevenueVisualizer(
                    periodPayments = periodPayments,
                    periodMode = selectedPeriodMode,
                    selectedYear = selectedYear,
                    selectedMonth = selectedMonth
                )
            }

            // Expandable Period Transactions List
            OutlinedButton(
                onClick = { isExpandedList = !isExpandedList },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (isExpandedList) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle List"
                    )
                    Text(
                        text = if (isExpandedList) "Hide Period Transactions List" else "View Period Transactions (${periodPayments.size})",
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isExpandedList) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search period entries...", fontSize = 14.sp, color = MaterialTheme.colorScheme.outline) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    ),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                )

                val filteredPeriodList = remember(periodPayments, searchQuery) {
                    if (searchQuery.isBlank()) periodPayments.map { it.first }
                    else periodPayments.map { it.first }.filter {
                        it.name.contains(searchQuery, ignoreCase = true) ||
                        it.serviceName.contains(searchQuery, ignoreCase = true) ||
                        it.section.contains(searchQuery, ignoreCase = true) ||
                        it.paymentMethod.contains(searchQuery, ignoreCase = true)
                    }
                }

                if (filteredPeriodList.isEmpty()) {
                    Text("No transactions match search criteria.", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        filteredPeriodList.forEach { row ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        val formattedDate = remember(row.timestamp, row.month) { parseAndFormatDate(row.timestamp, row.month) }
                                        Text(text = row.serviceName, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(
                                            text = "${row.name} • ${if (formattedDate.isNotBlank() && formattedDate != "Unknown") formattedDate else row.timestamp.ifEmpty { "Row #${row.rowIndex}" }}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(text = formatKES(row.amountPaid), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
                                        Text(
                                            text = if (row.paid) "PAID" else "UNPAID",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (row.paid) SuccessGreen else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PeriodRevenueVisualizer(
    periodPayments: List<Triple<PaymentRow, java.util.Calendar, Long>>,
    periodMode: PeriodMode,
    selectedYear: Int,
    selectedMonth: Int
) {
    val chartData = remember(periodPayments, periodMode, selectedYear, selectedMonth) {
        when (periodMode) {
            PeriodMode.MONTH -> {
                val cal = java.util.Calendar.getInstance()
                cal.set(selectedYear, selectedMonth, 1)
                val maxDay = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                val dayMap = periodPayments.groupBy { it.second.get(java.util.Calendar.DAY_OF_MONTH) }
                (1..maxDay).map { day ->
                    val sum = dayMap[day]?.sumOf { it.first.amountPaid } ?: 0.0
                    "$day" to sum
                }
            }
            PeriodMode.YEAR -> {
                val monthsShort = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                val monthMap = periodPayments.groupBy { it.second.get(java.util.Calendar.MONTH) }
                (0..11).map { monthIdx ->
                    val sum = monthMap[monthIdx]?.sumOf { it.first.amountPaid } ?: 0.0
                    monthsShort[monthIdx] to sum
                }
            }
            else -> {
                periodPayments.groupBy { it.first.serviceName }
                    .mapValues { (_, list) -> list.sumOf { it.first.amountPaid } }
                    .toList()
                    .take(8)
            }
        }
    }

    val maxVal = remember(chartData) {
        val max = chartData.maxOfOrNull { it.second } ?: 1.0
        if (max <= 0) 1.0 else max
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                chartData.forEach { (_, value) ->
                    val heightRatio = (value / maxVal).toFloat().coerceIn(0.05f, 1f)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (value > 0) {
                            val kVal = value / 1000.0
                            val kStr = if (kVal >= 10 || kVal % 1.0 == 0.0) {
                                "${kVal.toInt()}k"
                            } else {
                                String.format(java.util.Locale.US, "%.1fk", kVal)
                            }
                            Text(
                                text = kStr,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.65f)
                                .fillMaxHeight(heightRatio)
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    if (value > 0) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                                )
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                chartData.forEach { (label, _) ->
                    Text(
                        text = label,
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun InsightsTab(viewModel: PaymentViewModel) {
    val totalPaidToAll by viewModel.totalPaidToAll.collectAsStateWithLifecycle()
    val allPayments by viewModel.allPayments.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()

    var showOnlyPaidRevenue by remember { mutableStateOf(false) }
    var currentSort by remember { mutableStateOf(LedgerSort.NAME_ASC) }

    val activePayments = remember(allPayments, showOnlyPaidRevenue) {
        if (showOnlyPaidRevenue) {
            allPayments.filter { it.paid }
        } else {
            allPayments
        }
    }

    val activeTotalRevenue = remember(activePayments, showOnlyPaidRevenue) {
        if (showOnlyPaidRevenue) {
            activePayments.sumOf { it.salonShare }
        } else {
            activePayments.sumOf { it.amountPaid }
        }
    }
    
    val activeRevenueBySection = remember(activePayments, showOnlyPaidRevenue) {
        activePayments.groupBy { it.section }
            .mapValues { (_, rows) ->
                if (showOnlyPaidRevenue) rows.sumOf { it.salonShare } else rows.sumOf { it.amountPaid }
            }
            .toList()
            .sortedByDescending { it.second }
    }

    val activeRevenueByService = remember(activePayments, showOnlyPaidRevenue) {
        activePayments.groupBy { it.serviceName }
            .mapValues { (_, rows) ->
                if (showOnlyPaidRevenue) rows.sumOf { it.salonShare } else rows.sumOf { it.amountPaid }
            }
            .toList()
            .sortedByDescending { it.second }
    }

    val activeRevenueByPaymentMethod = remember(activePayments, showOnlyPaidRevenue) {
        activePayments.groupBy { it.paymentMethod }
            .mapValues { (_, rows) ->
                if (showOnlyPaidRevenue) rows.sumOf { it.salonShare } else rows.sumOf { it.amountPaid }
            }
            .toList()
            .sortedByDescending { it.second }
    }

    val employeeStats = remember(allPayments, currentSort) {
        val list = allPayments.groupBy { it.name }.map { (name, rows) ->
            val total = rows.sumOf { it.staffCommission }
            val paid = rows.filter { it.paid }.sumOf { it.staffCommission }
            val unpaid = rows.filter { !it.paid }.sumOf { it.staffCommission }
            val pct = if (total > 0) (paid / total * 100).toInt() else 0
            Triple(name, paid to unpaid, pct)
        }
        
        when (currentSort) {
            LedgerSort.NAME_ASC -> list.sortedBy { it.first.lowercase() }
            LedgerSort.NAME_DESC -> list.sortedByDescending { it.first.lowercase() }
            LedgerSort.PAID_ASC -> list.sortedBy { it.second.first }
            LedgerSort.PAID_DESC -> list.sortedByDescending { it.second.first }
            LedgerSort.PENDING_ASC -> list.sortedBy { it.second.second }
            LedgerSort.PENDING_DESC -> list.sortedByDescending { it.second.second }
            LedgerSort.PAYOUT_ASC -> list.sortedBy { it.third }
            LedgerSort.PAYOUT_DESC -> list.sortedByDescending { it.third }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Business Revenue Dashboard",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Salon Earnings History Component
        SalonEarningsHistorySection(allPayments = allPayments)

        // Modern Segmented Toggle for Revenue Stats
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (!showOnlyPaidRevenue) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { showOnlyPaidRevenue = false }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Before Dues Paid (Gross)",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (!showOnlyPaidRevenue) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (showOnlyPaidRevenue) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .clickable { showOnlyPaidRevenue = true }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "After Dues Paid (Net)",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (showOnlyPaidRevenue) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Highlight Info Cards Grid
        val totalOutstanding = remember(allPayments) {
            allPayments.filter { !it.paid }.sumOf { it.staffCommission }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InfoSummaryCard(
                title = if (showOnlyPaidRevenue) "Disbursed (Net)" else "Revenue (Gross)",
                value = formatKES(activeTotalRevenue),
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )

            InfoSummaryCard(
                title = "Outstanding Dues",
                value = formatKES(totalOutstanding),
                icon = Icons.Default.Group,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )

            InfoSummaryCard(
                title = "Percent Disbursed",
                value = if (showOnlyPaidRevenue) "100%" else {
                    val totalDuesToAll = allPayments.sumOf { it.staffCommission }
                    if (totalDuesToAll > 0) "${((totalPaidToAll / totalDuesToAll) * 100).toInt()}%" else "0%"
                },
                icon = Icons.Default.AccountBalanceWallet,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
        }

        // Section Revenue Visualization
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (showOnlyPaidRevenue) "Net Revenue Share by Section" else "Gross Revenue Share by Section",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (showOnlyPaidRevenue) "Relative share of Nails, Hair, and Massage (paid bookings only)" else "Relative share of Nails, Hair, and Massage services",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (activeRevenueBySection.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No data available", color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RevenueDonutChart(
                            data = activeRevenueBySection,
                            modifier = Modifier
                                .weight(1.2f)
                                .fillMaxHeight()
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.Start
                        ) {
                            activeRevenueBySection.take(4).forEachIndexed { idx, pair ->
                                val color = chartColors[idx % chartColors.size]
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(12.dp)
                                            .background(color, CircleShape)
                                    )
                                    Column {
                                        Text(
                                            text = pair.first,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = formatKES(pair.second),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Service Revenue Visualization
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (showOnlyPaidRevenue) "Top Disbursed Services" else "Top Revenue Generating Services",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (showOnlyPaidRevenue) "Breakdown of the top paid services" else "Breakdown of the top performing specific services",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (activeRevenueByService.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No service data available", color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        RevenueBarChart(
                            data = activeRevenueByService,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Payment Method Popularity Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Revenue by Payment Method",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Distribution of collection methods (Mpesa vs Cash)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                activeRevenueByPaymentMethod.forEachIndexed { index, (method, amount) ->
                    val percentage = if (activeTotalRevenue > 0) (amount / activeTotalRevenue * 100).toInt() else 0
                    Column(modifier = Modifier.padding(vertical = 6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(text = method, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(text = "${formatKES(amount)} ($percentage%)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(if (activeTotalRevenue > 0) (amount / activeTotalRevenue).toFloat() else 0f)
                                    .fillMaxHeight()
                                    .clip(CircleShape)
                                    .background(if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                            )
                        }
                    }
                }
            }
        }

        // Detailed Employee Ledger Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Payroll Ledger Summary",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Detailed overview of gross dues, paid, and outstanding by person",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1.2f)
                            .clickable {
                                currentSort = if (currentSort == LedgerSort.NAME_ASC) LedgerSort.NAME_DESC else LedgerSort.NAME_ASC
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Employee",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (currentSort == LedgerSort.NAME_ASC || currentSort == LedgerSort.NAME_DESC) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        if (currentSort == LedgerSort.NAME_ASC) Text(" ▲", fontSize = 8.sp, color = MaterialTheme.colorScheme.primary)
                        if (currentSort == LedgerSort.NAME_DESC) Text(" ▼", fontSize = 8.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    Row(
                        modifier = Modifier
                            .weight(1.1f)
                            .clickable {
                                currentSort = if (currentSort == LedgerSort.PAID_DESC) LedgerSort.PAID_ASC else LedgerSort.PAID_DESC
                            },
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Paid",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (currentSort == LedgerSort.PAID_ASC || currentSort == LedgerSort.PAID_DESC) MaterialTheme.colorScheme.primary else SuccessGreen
                        )
                        if (currentSort == LedgerSort.PAID_ASC) Text(" ▲", fontSize = 8.sp, color = MaterialTheme.colorScheme.primary)
                        if (currentSort == LedgerSort.PAID_DESC) Text(" ▼", fontSize = 8.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    Row(
                        modifier = Modifier
                            .weight(1.1f)
                            .clickable {
                                currentSort = if (currentSort == LedgerSort.PENDING_DESC) LedgerSort.PENDING_ASC else LedgerSort.PENDING_DESC
                            },
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Pending",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (currentSort == LedgerSort.PENDING_ASC || currentSort == LedgerSort.PENDING_DESC) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                        if (currentSort == LedgerSort.PENDING_ASC) Text(" ▲", fontSize = 8.sp, color = MaterialTheme.colorScheme.primary)
                        if (currentSort == LedgerSort.PENDING_DESC) Text(" ▼", fontSize = 8.sp, color = MaterialTheme.colorScheme.primary)
                    }

                    Row(
                        modifier = Modifier
                            .weight(0.9f)
                            .clickable {
                                currentSort = if (currentSort == LedgerSort.PAYOUT_DESC) LedgerSort.PAYOUT_ASC else LedgerSort.PAYOUT_DESC
                            },
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Payout %",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (currentSort == LedgerSort.PAYOUT_ASC || currentSort == LedgerSort.PAYOUT_DESC) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                        if (currentSort == LedgerSort.PAYOUT_ASC) Text(" ▲", fontSize = 8.sp, color = MaterialTheme.colorScheme.primary)
                        if (currentSort == LedgerSort.PAYOUT_DESC) Text(" ▼", fontSize = 8.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                employeeStats.forEach { (name, dues, pct) ->
                    val (paid, unpaid) = dues
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1.2f),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = formatKES(paid),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1.1f),
                            textAlign = TextAlign.End,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = formatKES(unpaid),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1.1f),
                            textAlign = TextAlign.End,
                            color = if (unpaid > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline
                        )
                        Box(
                            modifier = Modifier
                                .weight(0.9f),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (pct == 100) SuccessGreen.copy(alpha = 0.12f)
                                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$pct%",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (pct == 100) SuccessGreen else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(64.dp))
    }
}

@Composable
fun InfoSummaryCard(
    title: String,
    value: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(tint, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

// Custom Colors for visual charts & employee avatars
val chartColors = listOf(
    Color(0xFFD7205C), // Hot Pink
    Color(0xFFE9B747), // Metallic Gold
    Color(0xFFF26894), // Light Pink
    Color(0xFFB4791B), // Rich Gold
    Color(0xFF7E3420), // Dark Bronze
    Color(0xFFF2C3B1)  // Warm Beige
)

val avatarPalette = listOf(
    Color(0xFFD7205C), // Hot Pink
    Color(0xFFB4791B), // Rich Gold
    Color(0xFF1565C0), // Info Blue
    Color(0xFF2E7D32), // Success Green
    Color(0xFF7E3420), // Dark Bronze
    Color(0xFFEF6C00), // Alert Orange
    Color(0xFF8E24AA)  // Deep Purple
)

fun getAvatarColor(name: String): Color {
    val hash = kotlin.math.abs(name.hashCode())
    return avatarPalette[hash % avatarPalette.size]
}

@Composable
fun RevenueDonutChart(
    data: List<Pair<String, Double>>,
    modifier: Modifier = Modifier
) {
    val totalVal = remember(data) { data.sumOf { it.second } }

    Canvas(modifier = modifier) {
        val chartSize = minOf(size.width, size.height)
        val strokeWidth = 24.dp.toPx()
        val radius = (chartSize - strokeWidth) / 2
        val center = Offset(size.width / 2, size.height / 2)

        var startAngle = -90f

        if (totalVal == 0.0) {
            drawArc(
                color = Color.LightGray.copy(alpha = 0.3f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth),
                size = Size(radius * 2, radius * 2),
                topLeft = Offset(center.x - radius, center.y - radius)
            )
        } else {
            data.forEachIndexed { idx, pair ->
                val sweepAngle = ((pair.second / totalVal) * 360f).toFloat()
                val color = chartColors[idx % chartColors.size]

                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    size = Size(radius * 2, radius * 2),
                    topLeft = Offset(center.x - radius, center.y - radius)
                )

                startAngle += sweepAngle
            }
        }
    }
}

@Composable
fun RevenueBarChart(
    data: List<Pair<String, Double>>,
    modifier: Modifier = Modifier
) {
    val maxVal = remember(data) { data.maxOfOrNull { it.second } ?: 1.0 }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        data.forEachIndexed { idx, pair ->
            val color = chartColors[idx % chartColors.size]
            val ratio = if (maxVal > 0.0) (pair.second / maxVal).toFloat() else 0f

            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = pair.first,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatKES(pair.second),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio.coerceAtLeast(0.04f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        color.copy(alpha = 0.6f),
                                        color
                                    )
                                )
                            )
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------
// TAB 3: SETTINGS & SPREADSHEET MAPPING (UPDATED - NO API KEY)
// ------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTab(
    state: PaymentUiState.Success,
    viewModel: PaymentViewModel,
    isUnlocked: Boolean
) {
    var sheetUrl by remember { mutableStateOf(state.config?.spreadsheetUrl ?: "") }
    var sheetName by remember { mutableStateOf(state.config?.sheetName.takeIf { !it.isNullOrBlank() } ?: "Service Ledger") }
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }

    // PIN unlock dialog states
    var showUnlockDialog by remember { mutableStateOf(false) }
    var showClearLedgerPinConfirm by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()
    val firstRowPreview by viewModel.firstRowPreview.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val contentResolver = context.contentResolver
                val fileName = getFileName(context, uri)
                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val bytes = inputStream.use { it.readBytes() }
                    viewModel.importLocalFile(fileName, bytes)
                }
            } catch (e: Exception) {
                Log.e("PaymentScreens", "Failed to open or read file", e)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Owner Panel & Sheets Config",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        // Minimalistic Key Access Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (isUnlocked) SuccessGreen.copy(alpha = 0.08f)
                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                1.dp,
                if (isUnlocked) SuccessGreen.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(
                                if (isUnlocked) SuccessGreen else MaterialTheme.colorScheme.primary,
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isUnlocked) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = "Lock State",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = if (isUnlocked) "Authorized Owner Access" else "Owner Access Key Required",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isUnlocked) SuccessGreen else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isUnlocked) "You have full access to edit settings and manage ledger."
                            else "Click to enter PIN key and unlock full configuration.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                if (!isUnlocked) {
                    Button(
                        onClick = { showUnlockDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.testTag("open_unlock_dialog_button")
                    ) {
                        Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Enter Key", fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = { viewModel.lockAccess() },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                    ) {
                        Text("Lock", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Key Entry Dialog
        if (showUnlockDialog) {
            var pinInput by remember { mutableStateOf("") }
            var pinError by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { showUnlockDialog = false },
                icon = {
                    Icon(
                        imageVector = Icons.Default.VpnKey,
                        contentDescription = "Key Icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = "Enter Owner Key",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Please enter your owner security PIN key to unlock configuration and ledger controls.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = {
                                pinInput = it
                                pinError = false
                            },
                            label = { Text("Owner PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            isError = pinError,
                            supportingText = if (pinError) {
                                { Text("Incorrect PIN. Please try again.", color = MaterialTheme.colorScheme.error) }
                            } else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("unlock_pin_input"),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val success = viewModel.verifyPin(pinInput, state.config?.ownerPin ?: "1234")
                            if (success) {
                                showUnlockDialog = false
                            } else {
                                pinError = true
                            }
                        },
                        modifier = Modifier.testTag("submit_unlock_pin_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Verify & Access", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUnlockDialog = false }) {
                        Text("Cancel")
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }

        firstRowPreview?.let { preview ->
            FirstRowPreviewNotificationCard(
                previewText = preview,
                onDismiss = { viewModel.clearFirstRowPreview() }
            )
        }

        // Blurred / Protected Configuration Controls
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = if (isUnlocked) 1.0f else 0.45f
                }
                .blur(if (isUnlocked) 0.dp else 10.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Mapping Form Panel
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = "Map Google Form Spreadsheet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Text(
                            text = "Ensure your spreadsheet share permissions are set to 'Anyone with the link can view' so the app can fetch rows securely.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )

                        OutlinedTextField(
                            value = sheetUrl,
                            onValueChange = { sheetUrl = it },
                            label = { Text("Spreadsheet URL") },
                            placeholder = { Text("https://docs.google.com/spreadsheets/d/...") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("spreadsheet_url_input"),
                            shape = RoundedCornerShape(10.dp),
                            maxLines = 2,
                            enabled = isUnlocked || state.config == null
                        )

                        OutlinedTextField(
                            value = sheetName,
                            onValueChange = { sheetName = it },
                            label = { Text("Sheet Name") },
                            placeholder = { Text("Service Ledger") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("sheet_name_input"),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            enabled = isUnlocked || state.config == null
                        )

                        if (state.config == null || isUnlocked) {
                            Text(
                                text = "Configure/Change Owner Security PIN:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = pin,
                                    onValueChange = { pin = it },
                                    label = { Text("New PIN") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("setup_pin_input"),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true
                                )

                                OutlinedTextField(
                                    value = confirmPin,
                                    onValueChange = { confirmPin = it },
                                    label = { Text("Confirm PIN") },
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("setup_confirm_pin_input"),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = {
                                if (sheetUrl.isBlank()) return@Button
                                if (pin.isNotBlank() && pin != confirmPin) {
                                    return@Button
                                }
                                val savePin = if (pin.isNotBlank()) pin else (state.config?.ownerPin ?: "1234")
                                viewModel.mapSpreadsheet(sheetUrl, savePin)
                                pin = ""
                                confirmPin = ""
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("save_mapping_button"),
                            shape = RoundedCornerShape(10.dp),
                            enabled = (isUnlocked || state.config == null) && sheetUrl.isNotBlank()
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Save & Synchronize Map", fontWeight = FontWeight.Bold)
                        }

                        // Switch back to Demo Button
                        OutlinedButton(
                            onClick = { viewModel.switchToDemoMode() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("reset_demo_button"),
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Icon(Icons.Default.Restore, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Load Local Demo Database", fontWeight = FontWeight.Bold)
                        }

                        // Clear existing data button
                        OutlinedButton(
                            onClick = { showClearLedgerPinConfirm = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("clear_data_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clear Local Ledger / Dummy Data", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Upload spreadsheet local file card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = "Upload Icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "Import Local Spreadsheet File",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Text(
                            text = "If you have exported your Google Sheet or Excel as an Excel Spreadsheet (.xlsx), CSV, or TSV file, you can upload it directly below to run completely offline without mapping URLs.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        Button(
                            onClick = {
                                filePickerLauncher.launch("*/*")
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("upload_local_file_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            enabled = isUnlocked || state.config == null
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Select & Import File", fontWeight = FontWeight.Bold)
                        }

                        Text(
                            text = "Supported formats: .xlsx, .csv, .tsv",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }

            // Click interceptor over blurred content when locked
            if (!isUnlocked) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showUnlockDialog = true }
                )
            }
        }

        if (showClearLedgerPinConfirm) {
            ConfirmClearLedgerDialog(
                isUnlocked = isUnlocked,
                ownerPin = state.config?.ownerPin ?: "1234",
                onConfirm = {
                    viewModel.clearAllData()
                    showClearLedgerPinConfirm = false
                },
                onDismiss = {
                    showClearLedgerPinConfirm = false
                }
            )
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

// ------------------------------------------------------------------------------------
// VERIFICATION & CONFIRMATION DIALOG
// ------------------------------------------------------------------------------------
@Composable
fun ConfirmPaymentDialog(
    employeeName: String,
    totalAmount: Double,
    itemCount: Int,
    serviceName: String? = null,
    periodRange: String? = null,
    isUnlocked: Boolean,
    onConfirm: (pin: String) -> Unit,
    onDismiss: () -> Unit
) {
    var pinValue by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Info",
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Confirm Payment",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = buildAnnotatedString {
                        append("Ensure you have paid ")
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(employeeName)
                        }
                        append(" first before committing the changes. This will update the status to 'Paid' in the spreadsheet.")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )

                Text(
                    text = if (serviceName != null) {
                        "This will mark the service \"$serviceName\" (${formatKES(totalAmount)}) as fully Paid."
                    } else {
                        "This will mark all $itemCount outstanding dues (totaling ${formatKES(totalAmount)}) for $employeeName as Paid."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                )

                if (periodRange != null && periodRange.isNotBlank()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = "Period",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Period/Date: $periodRange",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (!isUnlocked) {
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Verification Required:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Enter Owner PIN to authorize changes to the Google Form ledger:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f)
                    )

                    OutlinedTextField(
                        value = pinValue,
                        onValueChange = {
                            pinValue = it
                            pinError = false
                        },
                        label = { Text("Owner PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("dialog_pin_input"),
                        isError = pinError,
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true
                    )

                    if (pinError) {
                        Text(
                            text = "PIN cannot be empty",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isUnlocked && pinValue.isBlank()) {
                        pinError = true
                    } else {
                        onConfirm(pinValue)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.testTag("confirm_pay_button")
            ) {
                Text("Commit", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.testTag("cancel_pay_button")
            ) {
                Text("Cancel", fontWeight = FontWeight.Medium)
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
fun ConfirmClearLedgerDialog(
    isUnlocked: Boolean,
    ownerPin: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var pinValue by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = "Clear Local Ledger?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Are you absolutely sure you want to delete all payment records and reset the local ledger? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!isUnlocked) {
                    Spacer(modifier = Modifier.height(6.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Verification Required:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Enter Owner PIN to authorize clearing the local offline ledger:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )

                    OutlinedTextField(
                        value = pinValue,
                        onValueChange = {
                            pinValue = it
                            pinError = false
                        },
                        label = { Text("Owner PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = pinError,
                        modifier = Modifier.fillMaxWidth().testTag("clear_ledger_pin_input"),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    if (pinError) {
                        Text(
                            text = "Incorrect PIN. Authorization failed.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isUnlocked || pinValue == ownerPin) {
                        onConfirm()
                    } else {
                        pinError = true
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("confirm_clear_ledger_btn")
            ) {
                Text("Delete Everything", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_clear_ledger_btn")
            ) {
                Text("Cancel", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

// Helper: Format double values to Kenyan Shillings (KES)
fun formatKES(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-KE"))
    val formatted = formatter.format(amount)
    return if (formatted.contains("KES") || formatted.contains("Ksh")) {
        formatted
    } else {
        "KES " + NumberFormat.getNumberInstance(Locale.US).format(amount)
    }
}

// Helper: Retrieve actual filename from Uri
fun getFileName(context: Context, uri: Uri): String {
    var result: String? = null
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        try {
            if (cursor != null && cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    result = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            Log.e("PaymentScreens", "Error querying filename", e)
        } finally {
            cursor?.close()
        }
    }
    if (result == null) {
        result = uri.path
        val cut = result?.lastIndexOf('/') ?: -1
        if (cut != -1) {
            result = result?.substring(cut + 1)
        }
    }
    return result ?: "spreadsheet.tsv"
}

@Composable
fun FirstRowPreviewNotificationCard(
    previewText: String,
    onDismiss: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "First Row Preview",
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "First Row Data Read (Spreadsheet Inspection)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("close_preview_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Raw text extracted from the first row(s) of your spreadsheet:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp, max = 220.dp)
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState())
                    .horizontalScroll(rememberScrollState())
            ) {
                SelectionContainer {
                    Text(
                        text = previewText,
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
            if (onDismiss != null) {
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .testTag("dismiss_preview_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Close / Dismiss Inspection Box", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

// ==========================================
// EXPENSES TAB & PROFITABILITY COMPOSABLES
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesTabContent(viewModel: PaymentViewModel, isUnlocked: Boolean) {
    val expenses by viewModel.filteredExpenses.collectAsStateWithLifecycle()
    val totalExpenses by viewModel.totalExpensesAmount.collectAsStateWithLifecycle()
    val grossRevenue by viewModel.totalGrossRevenueForPeriod.collectAsStateWithLifecycle()
    val salonShare by viewModel.totalSalonShareForPeriod.collectAsStateWithLifecycle()
    val commissions by viewModel.totalCommissionsForPeriod.collectAsStateWithLifecycle()
    val netProfit by viewModel.netProfit.collectAsStateWithLifecycle()
    val profitMargin by viewModel.profitMarginPct.collectAsStateWithLifecycle()
    val selectedPeriod by viewModel.expensesTimePeriod.collectAsStateWithLifecycle()
    val searchQuery by viewModel.expensesSearchQuery.collectAsStateWithLifecycle()
    val selectedDept by viewModel.expensesSelectedDept.collectAsStateWithLifecycle()
    val selectedType by viewModel.expensesSelectedType.collectAsStateWithLifecycle()
    val departments by viewModel.expenseDepartments.collectAsStateWithLifecycle()
    val types by viewModel.expenseTypes.collectAsStateWithLifecycle()
    val availableMonths by viewModel.availableMonths.collectAsStateWithLifecycle()
    val expensesByType by viewModel.expensesByType.collectAsStateWithLifecycle()
    val expensesByDept by viewModel.expensesByDepartment.collectAsStateWithLifecycle()
    val topCosts by viewModel.topCostItems.collectAsStateWithLifecycle()

    val periods = remember(availableMonths) {
        listOf("All Time") + availableMonths + listOf("This Week", "Today")
    }

    Scaffold { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp)
        ) {
            // 1. Title & Time Period Selector
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Expenses & Profitability",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Period Pills
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        periods.forEach { period ->
                            val isSelected = period == selectedPeriod
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setExpensesTimePeriod(period) },
                                label = {
                                    Text(
                                        text = period,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp)) }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                modifier = Modifier.testTag("period_chip_$period")
                            )
                        }
                    }
                }
            }

            // 2. Profitability Overview Card
            item {
                ExpensesProfitabilityCard(
                    grossRevenue = grossRevenue,
                    commissions = commissions,
                    salonShare = salonShare,
                    totalExpenses = totalExpenses,
                    netProfit = netProfit,
                    profitMargin = profitMargin,
                    periodName = selectedPeriod
                )
            }

            // 3. Cost Breakdown Visualizer Card ("What costs are the most")
            item {
                ExpensesVisualizerCard(
                    totalExpenses = totalExpenses,
                    expensesByType = expensesByType,
                    expensesByDept = expensesByDept,
                    topCostItems = topCosts
                )
            }

            // 4. Search and Filters
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setExpensesSearchQuery(it) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("expenses_search_input"),
                        placeholder = { Text("Search item, type, department, recorded by...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { viewModel.setExpensesSearchQuery("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        } else null,
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Department & Category Chips
                    if (departments.isNotEmpty() || types.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = selectedDept == null && selectedType == null,
                                onClick = {
                                    viewModel.setExpensesDepartmentFilter(null)
                                    viewModel.setExpensesTypeFilter(null)
                                },
                                label = { Text("All Records") }
                            )

                            departments.forEach { dept ->
                                FilterChip(
                                    selected = selectedDept == dept,
                                    onClick = {
                                        viewModel.setExpensesDepartmentFilter(if (selectedDept == dept) null else dept)
                                    },
                                    label = { Text("Dept: $dept") }
                                )
                            }

                            types.forEach { type ->
                                FilterChip(
                                    selected = selectedType == type,
                                    onClick = {
                                        viewModel.setExpensesTypeFilter(if (selectedType == type) null else type)
                                    },
                                    label = { Text("Type: $type") }
                                )
                            }
                        }
                    }
                }
            }

            // 5. Expense Items Header & List
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Expense Records (${expenses.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Total Spent: KES ${String.format("%,.0f", totalExpenses)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            if (expenses.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "No Expense Records Found",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "No expenses recorded for $selectedPeriod. All expense entries are synced directly from the Google Sheets ledger.",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(expenses, key = { it.id }) { expense ->
                    ExpenseItemCard(
                        expense = expense
                    )
                }
            }
        }
    }
}

@Composable
fun ExpensesProfitabilityCard(
    grossRevenue: Double,
    commissions: Double,
    salonShare: Double,
    totalExpenses: Double,
    netProfit: Double,
    profitMargin: Double,
    periodName: String
) {
    val isProfitable = netProfit >= 0
    val profitColor = if (isProfitable) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
    val cardBg = if (isProfitable) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profitability_overview_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, profitColor.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp)
                ) {
                    Text(
                        text = if (isProfitable) "Net Operating Profit" else "Net Operating Loss",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Period: $periodName",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "KES ${String.format("%,.0f", netProfit)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = profitColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = profitColor,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.wrapContentWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (isProfitable) Icons.AutoMirrored.Filled.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        val marginLabel = if (isProfitable) {
                            String.format(java.util.Locale.US, "+%.1f%% Margin", profitMargin)
                        } else {
                            String.format(java.util.Locale.US, "%.1f%% Loss", profitMargin)
                        }
                        Text(
                            text = marginLabel,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            HorizontalDivider(color = profitColor.copy(alpha = 0.2f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Gross Income",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Black.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "KES ${String.format("%,.0f", grossRevenue)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1B5E20)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Staff Commissions",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Black.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "- KES ${String.format("%,.0f", commissions)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFC62828)
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Salon Net Share",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Black.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "KES ${String.format("%,.0f", salonShare)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0D47A1)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Less Total Expenses:",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Black.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "- KES ${String.format("%,.0f", totalExpenses)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Visual Progress Comparison Bar
            val totalBase = maxOf(salonShare, totalExpenses, 1.0)
            val expFraction = (totalExpenses / totalBase).toFloat().coerceIn(0f, 1f)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(Color.White)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(maxOf(expFraction, 0.01f))
                            .background(MaterialTheme.colorScheme.error)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(maxOf(1f - expFraction, 0.01f))
                            .background(Color(0xFF2E7D32))
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Expenses: ${if (salonShare > 0) String.format("%.0f%%", (totalExpenses / salonShare) * 100) else "0%"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Black.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Retained Profit: ${if (salonShare > 0) String.format("%.0f%%", (netProfit / salonShare) * 100) else "0%"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.Black.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesVisualizerCard(
    totalExpenses: Double,
    expensesByType: List<Pair<String, Double>>,
    expensesByDept: List<Pair<String, Double>>,
    topCostItems: List<ExpenseRow>
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: By Type, 1: By Dept, 2: Top Items

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cost_breakdown_visualizer_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PieChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Cost Breakdown Visualizer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Sub-tabs
            SecondaryTabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Expense Types", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Departments", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Cost Items (${topCostItems.size})", fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
                )
            }

            when (selectedTab) {
                0 -> {
                    if (expensesByType.isEmpty()) {
                        Text("No expense type breakdown available", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            expensesByType.forEach { (type, amount) ->
                                val pct = if (totalExpenses > 0) amount / totalExpenses else 0.0
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(type, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                        Text("KES ${String.format("%,.0f", amount)} (${String.format("%.1f%%", pct * 100)})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    LinearProgressIndicator(
                                        progress = { pct.toFloat() },
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                        color = MaterialTheme.colorScheme.primary,
                                        trackColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    if (expensesByDept.isEmpty()) {
                        Text("No department breakdown available", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            expensesByDept.forEach { (dept, amount) ->
                                val pct = if (totalExpenses > 0) amount / totalExpenses else 0.0
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(dept, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                        Text("KES ${String.format("%,.0f", amount)} (${String.format("%.1f%%", pct * 100)})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    LinearProgressIndicator(
                                        progress = { pct.toFloat() },
                                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                                        color = MaterialTheme.colorScheme.secondary,
                                        trackColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }
                    }
                }
                2 -> {
                    if (topCostItems.isEmpty()) {
                        Text("No items available", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "All Cost Items (Sorted Highest to Lowest)",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Scroll down ↓",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 340.dp)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                topCostItems.forEachIndexed { index, item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                color = if (index < 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                                                shape = CircleShape,
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text("#${index + 1}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Column {
                                                Text(item.itemPurchased, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    text = "${item.department} • ${item.expenseType}${if (item.date.isNotBlank()) " • ${item.date}" else ""}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                        Text(
                                            text = "KES ${String.format("%,.0f", item.amountSpent)}",
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpenseItemCard(
    expense: ExpenseRow
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("expense_item_card_${expense.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = expense.expenseType,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = expense.department,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = expense.itemPurchased,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Text(
                    text = "KES ${String.format("%,.0f", expense.amountSpent)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.error
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Recorded by: ${expense.recordedBy.ifBlank { "Staff" }} • ${expense.date}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Method: ${expense.paymentMethod.ifBlank { "Mpesa" }} • Qty: ${if (expense.quantity % 1.0 == 0.0) expense.quantity.toInt().toString() else expense.quantity.toString()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = CircleShape
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Locked Ledger Record",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = "Ledger Record",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AddExpenseDialog(
    availableMonths: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (
        date: String,
        recordedBy: String,
        department: String,
        expenseType: String,
        itemPurchased: String,
        quantity: Double,
        amountSpent: Double,
        paymentMethod: String,
        month: String
    ) -> Unit
) {
    var date by remember { mutableStateOf("2026-07-12") }
    var recordedBy by remember { mutableStateOf("Manager Mary") }
    var department by remember { mutableStateOf("Salon Admin") }
    var expenseType by remember { mutableStateOf("Inventory") }
    var itemPurchased by remember { mutableStateOf("") }
    var quantityText by remember { mutableStateOf("1") }
    var amountText by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf("Mpesa") }
    var selectedMonth by remember { mutableStateOf(availableMonths.firstOrNull() ?: "July 2026") }

    val departmentsList = listOf("Salon Admin", "Nails", "Hair", "Massage", "Utilities", "General")
    val typesList = listOf("Inventory", "Bills & Tokens", "Tea & Refreshments", "Rent & Premises", "Supplies", "Salaries")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).testTag("add_expense_dialog")
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Record New Expense",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                OutlinedTextField(
                    value = itemPurchased,
                    onValueChange = { itemPurchased = it },
                    label = { Text("Item / Particular Purchased *") },
                    placeholder = { Text("e.g. Gel Polish Bottles, Electricity Tokens") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_item_purchased")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Amount (KES) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("input_amount_spent")
                    )

                    OutlinedTextField(
                        value = quantityText,
                        onValueChange = { quantityText = it },
                        label = { Text("Quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(0.8f).testTag("input_quantity")
                    )
                }

                OutlinedTextField(
                    value = recordedBy,
                    onValueChange = { recordedBy = it },
                    label = { Text("Recorded By") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("input_recorded_by")
                )

                // Department Options
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Department", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        departmentsList.forEach { dept ->
                            FilterChip(
                                selected = department == dept,
                                onClick = { department = dept },
                                label = { Text(dept, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Expense Type Options
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Expense Type", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        typesList.forEach { type ->
                            FilterChip(
                                selected = expenseType == type,
                                onClick = { expenseType = type },
                                label = { Text(type, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                // Payment Method & Date
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = date,
                        onValueChange = { date = it },
                        label = { Text("Date") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("input_expense_date")
                    )

                    OutlinedTextField(
                        value = paymentMethod,
                        onValueChange = { paymentMethod = it },
                        label = { Text("Method") },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("input_payment_method")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull() ?: 0.0
                            val qty = quantityText.toDoubleOrNull() ?: 1.0
                            if (itemPurchased.isNotBlank() && amount > 0) {
                                onConfirm(
                                    date,
                                    recordedBy,
                                    department,
                                    expenseType,
                                    itemPurchased,
                                    qty,
                                    amount,
                                    paymentMethod,
                                    selectedMonth
                                )
                            }
                        },
                        enabled = itemPurchased.isNotBlank() && (amountText.toDoubleOrNull() ?: 0.0) > 0,
                        modifier = Modifier.testTag("save_expense_button")
                    ) {
                        Text("Save Expense")
                    }
                }
            }
        }
    }
}