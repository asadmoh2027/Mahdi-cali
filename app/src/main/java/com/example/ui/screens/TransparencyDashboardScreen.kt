package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AuditLog
import com.example.data.SchoolClass
import com.example.data.User
import com.example.ui.SchoolViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransparencyDashboardScreen(
    viewModel: SchoolViewModel,
    currentUser: User?,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val auditLogs by viewModel.auditLogs.collectAsState()
    val classes by viewModel.classes.collectAsState()
    val users by viewModel.users.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("ALL") }
    var selectedClassId by remember { mutableStateOf(0L) }
    var selectedUserName by remember { mutableStateOf("ALL") }

    var logToInspect by remember { mutableStateOf<AuditLog?>(null) }
    var showClearConfirmation by remember { mutableStateOf(false) }

    // Filter logic
    val filteredLogs = remember(auditLogs, searchQuery, selectedCategory, selectedClassId, selectedUserName, classes) {
        auditLogs.filter { log ->
            // Filter out logs from Hidden Admin ("Sacad Admin 2")
            if (log.userName == "Sacad Admin 2") return@filter false
            
            val matchesCategory = when (selectedCategory) {
                "ALL" -> true
                "ATTENDANCE" -> log.actionCategory == "ATTENDANCE"
                "GRADING" -> log.actionCategory == "GRADING"
                "FINANCE" -> log.actionCategory == "FINANCE"
                "STUDENT" -> log.actionCategory == "STUDENT" || log.actionCategory == "PROMOTION"
                else -> true
            }

            val matchesClass = if (selectedClassId == 0L) {
                true
            } else {
                val targetClassName = classes.find { it.id == selectedClassId }?.name ?: ""
                log.className.equals(targetClassName, ignoreCase = true) || log.details.contains(targetClassName, ignoreCase = true)
            }

            val matchesUser = if (selectedUserName == "ALL") {
                true
            } else {
                log.userName.equals(selectedUserName, ignoreCase = true)
            }

            val q = searchQuery.trim().lowercase()
            val matchesQuery = if (q.isBlank()) {
                true
            } else {
                log.title.lowercase().contains(q) ||
                log.details.lowercase().contains(q) ||
                log.userName.lowercase().contains(q) ||
                log.className.lowercase().contains(q) ||
                log.timestamp.lowercase().contains(q) ||
                log.rawDataSummary.lowercase().contains(q)
            }

            matchesCategory && matchesClass && matchesUser && matchesQuery
        }
    }

    // Stats calculations
    val attendanceCount = remember(auditLogs) { auditLogs.count { it.actionCategory == "ATTENDANCE" } }
    val gradingCount = remember(auditLogs) { auditLogs.count { it.actionCategory == "GRADING" } }
    val financeCount = remember(auditLogs) { auditLogs.count { it.actionCategory == "FINANCE" } }
    val studentCount = remember(auditLogs) { auditLogs.count { it.actionCategory == "STUDENT" || it.actionCategory == "PROMOTION" } }
    val uniqueTeachers = remember(auditLogs) { auditLogs.map { it.userName }.distinct().size }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Kormeerka & Daah-furnaanta", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
                        Text("Transparency & Audit Dashboard • Admin", fontSize = 11.sp, color = Color.White.copy(alpha = 0.85f))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    // Export to CSV / Excel
                    IconButton(onClick = { viewModel.exportAuditLogsCsv(context, filteredLogs) }) {
                        Icon(Icons.Default.Share, contentDescription = "Export Excel", tint = Color.White)
                    }
                    // Print Official Audit Trail
                    IconButton(onClick = { viewModel.printAuditLogsHtml(context, filteredLogs) }) {
                        Icon(Icons.Default.Info, contentDescription = "Print Audit Report", tint = Color.White)
                    }
                    // Clear logs for admin
                    if (currentUser?.role == "ADMIN") {
                        IconButton(onClick = { showClearConfirmation = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear Audit Logs", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TealPrimary)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // --- Compact Search & Filters Toolbar ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Row 1: Search Field + Results Counter Badge
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                            placeholder = { Text("Raadi qoraal, macalin, fasal, taariikh...", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TealPrimary, modifier = Modifier.size(18.dp)) },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear", tint = MutedText, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TealPrimary,
                                unfocusedBorderColor = Color(0xFFCBD5E1)
                            )
                        )

                        // Compact Count Badge
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = TealContainer.copy(alpha = 0.6f),
                            modifier = Modifier.height(46.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("📋", fontSize = 12.sp)
                                Text(
                                    "${filteredLogs.size}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TealDark
                                )
                            }
                        }
                    }

                    // Row 2: Scrollable Icon Filter Chips & Dropdowns
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Category: ALL
                        item {
                            FilterChip(
                                selected = selectedCategory == "ALL",
                                onClick = { selectedCategory = "ALL" },
                                leadingIcon = { Text("🌐", fontSize = 11.sp) },
                                label = { Text("All", fontSize = 11.sp, fontWeight = if (selectedCategory == "ALL") FontWeight.Bold else FontWeight.Normal) }
                            )
                        }

                        // Category: Attendance
                        item {
                            FilterChip(
                                selected = selectedCategory == "ATTENDANCE",
                                onClick = { selectedCategory = "ATTENDANCE" },
                                leadingIcon = { Text("📅", fontSize = 11.sp) },
                                label = { Text("Xaadirin ($attendanceCount)", fontSize = 11.sp) }
                            )
                        }

                        // Category: Grading
                        item {
                            FilterChip(
                                selected = selectedCategory == "GRADING",
                                onClick = { selectedCategory = "GRADING" },
                                leadingIcon = { Text("📝", fontSize = 11.sp) },
                                label = { Text("Dhibco ($gradingCount)", fontSize = 11.sp) }
                            )
                        }

                        // Category: Finance
                        item {
                            FilterChip(
                                selected = selectedCategory == "FINANCE",
                                onClick = { selectedCategory = "FINANCE" },
                                leadingIcon = { Text("💰", fontSize = 11.sp) },
                                label = { Text("Lacag ($financeCount)", fontSize = 11.sp) }
                            )
                        }

                        // Category: Student
                        item {
                            FilterChip(
                                selected = selectedCategory == "STUDENT",
                                onClick = { selectedCategory = "STUDENT" },
                                leadingIcon = { Text("🎓", fontSize = 11.sp) },
                                label = { Text("Arday ($studentCount)", fontSize = 11.sp) }
                            )
                        }

                        // Class Filter Icon Dropdown
                        item {
                            var classMenuExpanded by remember { mutableStateOf(false) }
                            val currentClassName = classes.find { it.id == selectedClassId }?.name ?: "All Fasal"
                            val isClassFiltered = selectedClassId != 0L

                            Box {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (isClassFiltered) TealPrimary else Color(0xFFCBD5E1)),
                                    color = if (isClassFiltered) TealContainer.copy(alpha = 0.5f) else Color.White,
                                    modifier = Modifier
                                        .height(32.dp)
                                        .clickable { classMenuExpanded = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("🏫", fontSize = 11.sp)
                                        Text(
                                            text = currentClassName,
                                            fontSize = 11.sp,
                                            fontWeight = if (isClassFiltered) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isClassFiltered) TealDark else DarkText
                                        )
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                }

                                DropdownMenu(
                                    expanded = classMenuExpanded,
                                    onDismissRequest = { classMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("🏫 Dhammaan Fasalada (All)", fontSize = 11.sp) },
                                        onClick = {
                                            selectedClassId = 0L
                                            classMenuExpanded = false
                                        }
                                    )
                                    classes.forEach { c ->
                                        DropdownMenuItem(
                                            text = { Text(c.name, fontSize = 11.sp) },
                                            onClick = {
                                                selectedClassId = c.id
                                                classMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Teacher / User Filter Icon Dropdown
                        item {
                            var userMenuExpanded by remember { mutableStateOf(false) }
                            val currentTeacherDisplay = if (selectedUserName == "ALL") "All Macalin" else selectedUserName
                            val isUserFiltered = selectedUserName != "ALL"

                            Box {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, if (isUserFiltered) TealPrimary else Color(0xFFCBD5E1)),
                                    color = if (isUserFiltered) TealContainer.copy(alpha = 0.5f) else Color.White,
                                    modifier = Modifier
                                        .height(32.dp)
                                        .clickable { userMenuExpanded = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text("👤", fontSize = 11.sp)
                                        Text(
                                            text = currentTeacherDisplay,
                                            fontSize = 11.sp,
                                            fontWeight = if (isUserFiltered) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isUserFiltered) TealDark else DarkText
                                        )
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
                                    }
                                }

                                DropdownMenu(
                                    expanded = userMenuExpanded,
                                    onDismissRequest = { userMenuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("👤 Dhammaan Macalimiinta (All)", fontSize = 11.sp) },
                                        onClick = {
                                            selectedUserName = "ALL"
                                            userMenuExpanded = false
                                        }
                                    )
                                    val userNames = auditLogs.map { it.userName }.distinct().filter { it.isNotBlank() }
                                    userNames.forEach { uName ->
                                        DropdownMenuItem(
                                            text = { Text(uName, fontSize = 11.sp) },
                                            onClick = {
                                                selectedUserName = uName
                                                userMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // --- Audit Log Feed ---
            if (filteredLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MutedText, modifier = Modifier.size(44.dp))
                        Text(
                            text = "Wax hawlo ah oo ku habboon lama helin.",
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            color = MutedText
                        )
                        Text(
                            text = "Isku day inaad beddesho filter-ka ama raadinta.",
                            fontSize = 11.sp,
                            color = MutedText
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 6.dp)
                ) {
                    items(filteredLogs, key = { it.id }) { log ->
                        AuditLogCard(
                            log = log,
                            onClick = { logToInspect = log }
                        )
                    }
                }
            }
        }
    }

    // --- Detailed Inspection Dialog ---
    if (logToInspect != null) {
        val log = logToInspect!!
        val badgeColor = when (log.actionCategory) {
            "ATTENDANCE" -> PassGreen
            "GRADING" -> Color(0xFF2563EB)
            "FINANCE" -> Color(0xFFD97706)
            "STUDENT" -> Color(0xFF7C3AED)
            else -> TealPrimary
        }

        AlertDialog(
            onDismissRequest = { logToInspect = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = badgeColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = when (log.actionCategory) {
                                    "ATTENDANCE" -> "✅"
                                    "GRADING" -> "📝"
                                    "FINANCE" -> "💰"
                                    "STUDENT" -> "👨‍🎓"
                                    else -> "📋"
                                },
                                fontSize = 18.sp
                            )
                        }
                    }
                    Column {
                        Text(log.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("${log.actionCategory} • ${log.timestamp}", fontSize = 11.sp, color = MutedText)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("👤 Qofka Geliyay: ${log.userName} (${log.userRole})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkText)
                            if (log.className.isNotBlank()) {
                                Text("🏫 Fasalka: ${log.className}", fontSize = 12.sp, color = DarkText)
                            }
                            Text("⏰ Taariikhda: ${log.timestamp}", fontSize = 11.sp, color = MutedText)
                            Text("📌 Xaaladda: ${log.status}", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = if (log.status == "DELETED") FailRed else PassGreen)
                        }
                    }

                    Text("📝 Faahfaahinta Kooban:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkText)
                    Text(log.details, fontSize = 12.sp, color = DarkText)

                    if (log.rawDataSummary.isNotBlank()) {
                        Text("📋 Xogta Dheeraadka ah ee la Diiwaangeliyay:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkText)
                        Surface(
                            color = Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = log.rawDataSummary,
                                fontSize = 11.sp,
                                color = Color(0xFF334155),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { logToInspect = null }) {
                    Text("Xidh", fontWeight = FontWeight.Bold, color = TealPrimary)
                }
            }
        )
    }

    // --- Clear All Audit Logs Confirmation Dialog ---
    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearConfirmation = false },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = FailRed, modifier = Modifier.size(32.dp)) },
            title = { Text("🗑️ Tirtir Dhammaan Diiwaanka Kormeerka", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Text(
                    text = "Ma hubtaa inaad tirtirto dhammaan diiwaanka kormeerka (Audit Logs)? Tallaabadan dib looma noqon karo.",
                    fontSize = 13.sp,
                    color = DarkText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmation = false
                        viewModel.clearAllAuditLogs()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FailRed)
                ) {
                    Text("Haa, Tirtir Dhammaan", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text("Jooji")
                }
            }
        )
    }
}

@Composable
fun AuditLogCard(
    log: AuditLog,
    onClick: () -> Unit
) {
    val badgeColor = when (log.actionCategory) {
        "ATTENDANCE" -> PassGreen
        "GRADING" -> Color(0xFF2563EB)
        "FINANCE" -> Color(0xFFD97706)
        "STUDENT" -> Color(0xFF7C3AED)
        else -> TealPrimary
    }

    val iconText = when (log.actionCategory) {
        "ATTENDANCE" -> "✅"
        "GRADING" -> "📝"
        "FINANCE" -> "💰"
        "STUDENT" -> "👨‍🎓"
        else -> "📋"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        border = BorderStroke(0.8.dp, Color(0xFFE2E8F0))
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        color = badgeColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "$iconText ${log.actionCategory}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    if (log.className.isNotBlank()) {
                        Surface(
                            color = TealContainer.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "🏫 ${log.className}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TealPrimary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = log.timestamp.take(16),
                    fontSize = 10.sp,
                    color = MutedText,
                    fontWeight = FontWeight.Medium
                )
            }

            Text(
                text = log.title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = DarkText
            )

            Text(
                text = log.details,
                fontSize = 11.5.sp,
                color = Color(0xFF475569),
                maxLines = 2
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Galiyay: ",
                        fontSize = 10.5.sp,
                        color = MutedText
                    )
                    Text(
                        text = log.userName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TealPrimary
                    )
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(3.dp)
                    ) {
                        Text(
                            text = log.userRole,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = MutedText,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                Text(
                    text = "Faahfaahin ➔",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = TealPrimary
                )
            }
        }
    }
}
