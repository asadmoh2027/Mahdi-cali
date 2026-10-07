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
    val allAttendance by viewModel.allAttendance.collectAsState()
    val selectedShift by viewModel.selectedShift.collectAsState()

    val todayDateStr = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date()) }
    val activeClasses = remember(classes, selectedShift) {
        classes.filter { schoolClass ->
            schoolClass.status == "ACTIVE" && (selectedShift == "Dhammaan" || schoolClass.shift.equals(selectedShift, ignoreCase = true))
        }
    }
    val unmarkedClasses = remember(activeClasses, allAttendance, todayDateStr) {
        activeClasses.filter { schoolClass ->
            allAttendance.none { att ->
                att.classId == schoolClass.id && (att.date == todayDateStr || att.date.startsWith(todayDateStr))
            }
        }
    }
    val markedClasses = remember(activeClasses, allAttendance, todayDateStr) {
        activeClasses.filter { schoolClass ->
            allAttendance.any { att ->
                att.classId == schoolClass.id && (att.date == todayDateStr || att.date.startsWith(todayDateStr))
            }
        }
    }

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
            // --- Notification Card for Unmarked Classes Today ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (unmarkedClasses.isNotEmpty()) Color(0xFFFFF1F2) else Color(0xFFF0FDF4)
                ),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (unmarkedClasses.isNotEmpty()) Color(0xFFFECDD3) else Color(0xFFBBF7D0)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (unmarkedClasses.isNotEmpty()) Color(0xFFE11D48) else Color(0xFF16A34A),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = if (unmarkedClasses.isNotEmpty()) "🔔" else "✅",
                                        fontSize = 16.sp
                                    )
                                }
                            }

                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "KORMEERKA XAADIRINTA MAANTA",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (unmarkedClasses.isNotEmpty()) Color(0xFF9F1239) else Color(0xFF14532D)
                                    )
                                    if (unmarkedClasses.isNotEmpty()) {
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color(0xFFE11D48)
                                        ) {
                                            Text(
                                                text = "${unmarkedClasses.size} Fasal",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (unmarkedClasses.isNotEmpty())
                                        "Fasalada shaqada xaadirinta maalinlaha ah AAN la samayn maanta:"
                                        else "Dhammaan ${activeClasses.size} fasal waa la xaadiriyay maanta!",
                                    fontSize = 10.sp,
                                    color = if (unmarkedClasses.isNotEmpty()) Color(0xFFBE123C) else Color(0xFF15803D),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    if (unmarkedClasses.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = Color(0xFFFECDD3).copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(6.dp))

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(unmarkedClasses) { unClass ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFFFDA4AF)),
                                    shadowElevation = 1.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = "🏫 ${unClass.name}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = DarkText
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = TealContainer.copy(alpha = 0.6f)
                                                ) {
                                                    Text(
                                                        text = unClass.shift,
                                                        fontSize = 9.sp,
                                                        color = TealDark,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "👨‍🏫 Macallin: ${unClass.inchargeTeacher.ifBlank { "Lama cayimin" }}",
                                                fontSize = 10.sp,
                                                color = Color(0xFFBE123C),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = Color(0xFFFFF1F2),
                                            border = BorderStroke(1.dp, Color(0xFFE11D48))
                                        ) {
                                            Text(
                                                text = "❌ Aan Xaadirin",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFE11D48),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (markedClasses.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = Color(0xFFBBF7D0).copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 4.dp)
                        ) {
                            Text("✅ FASALADA LA XAADIRIYAY MAANTA (${markedClasses.size}):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(markedClasses) { mClass ->
                                val classAtts = allAttendance.filter { it.classId == mClass.id && (it.date == todayDateStr || it.date.startsWith(todayDateStr)) }
                                val recTime = classAtts.firstOrNull { it.recordedAt.isNotBlank() }?.recordedAt
                                    ?: auditLogs.firstOrNull { it.actionCategory == "ATTENDANCE" && it.className.equals(mClass.name, ignoreCase = true) && it.timestamp.startsWith(todayDateStr) }?.timestamp?.takeLast(8)
                                    ?: "08:00 AM"

                                val recTeacher = classAtts.firstOrNull { it.recordedBy.isNotBlank() }?.recordedBy
                                    ?: auditLogs.firstOrNull { it.actionCategory == "ATTENDANCE" && it.className.equals(mClass.name, ignoreCase = true) && it.timestamp.startsWith(todayDateStr) }?.userName
                                    ?: mClass.inchargeTeacher.ifBlank { "Macallinka" }

                                val isOutside = isTimeOutsideWorkingHours(recTime)

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, if (isOutside) Color(0xFFFDBA74) else Color(0xFFA7F3D0)),
                                    shadowElevation = 1.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Column {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text(
                                                    text = "🏫 ${mClass.name}",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = DarkText
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = TealContainer.copy(alpha = 0.6f)
                                                ) {
                                                    Text(
                                                        text = mClass.shift,
                                                        fontSize = 9.sp,
                                                        color = TealDark,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "👨‍🏫 Macallin: $recTeacher",
                                                fontSize = 10.sp,
                                                color = Color(0xFF15803D),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                            Text(
                                                text = "⏰ Saacadda: $recTime",
                                                fontSize = 10.sp,
                                                color = if (isOutside) Color(0xFFC2410C) else DarkText,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }

                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = if (isOutside) Color(0xFFFFEDD5) else Color(0xFFDCFCE7),
                                            border = BorderStroke(1.dp, if (isOutside) Color(0xFFF97316) else Color(0xFF16A34A))
                                        ) {
                                            Text(
                                                text = if (isOutside) "⚠️ Ka baxsan Shaqada" else "✅ La Xaadiriyay",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isOutside) Color(0xFFC2410C) else Color(0xFF15803D),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

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

private fun isTimeOutsideWorkingHours(timeStr: String): Boolean {
    if (timeStr.isBlank()) return false
    try {
        val clean = timeStr.trim().lowercase()
        var hour24 = 0
        var minute = 0

        if (clean.contains("pm") || clean.contains("am")) {
            val isPm = clean.contains("pm")
            val isAm = clean.contains("am")
            val digits = clean.replace("am", "").replace("pm", "").trim()
            val timePart = if (digits.contains(" ")) digits.split(" ").last() else digits
            val parts = timePart.split(":")
            if (parts.isNotEmpty()) {
                val hour = parts[0].trim().toIntOrNull() ?: return false
                minute = if (parts.size > 1) parts[1].trim().toIntOrNull() ?: 0 else 0
                hour24 = hour
                if (isPm && hour < 12) hour24 += 12
                if (isAm && hour == 12) hour24 = 0
            }
        } else if (clean.contains(":")) {
            val timePart = if (clean.contains(" ")) clean.split(" ").last() else clean
            val parts = timePart.split(":")
            if (parts.isNotEmpty()) {
                hour24 = parts[0].trim().toIntOrNull() ?: return false
                minute = if (parts.size > 1) parts[1].trim().toIntOrNull() ?: 0 else 0
            }
        } else {
            return false
        }

        val totalMinutes = hour24 * 60 + minute
        val morningStart = 8 * 60        // 08:00 AM
        val morningEnd = 12 * 60         // 12:00 PM
        val afternoonStart = 14 * 60     // 02:00 PM
        val afternoonEnd = 16 * 60 + 30  // 04:30 PM

        val inMorningShift = totalMinutes in morningStart..morningEnd
        val inAfternoonShift = totalMinutes in afternoonStart..afternoonEnd

        return !(inMorningShift || inAfternoonShift)
    } catch (e: Exception) {
        return false
    }
}
