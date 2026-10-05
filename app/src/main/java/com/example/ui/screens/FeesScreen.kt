package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.FeeRecord
import com.example.data.SchoolClass
import com.example.data.Student
import com.example.data.User
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeesScreen(
    fees: List<FeeRecord>,
    students: List<Student>,
    classes: List<SchoolClass>,
    currentUser: User?,
    onRecordFeePayment: (Long, Long, Double, String, String, String, (Boolean, String) -> Unit) -> Unit,
    onDeleteFeeClick: (Long) -> Unit,
    onBackClick: () -> Unit
) {
    val isTeacher = currentUser?.role == "TEACHER"
    val isCashier = currentUser?.role == "CASHIER"
    val isCashierOrAdmin = currentUser?.role == "CASHIER" || currentUser?.role == "ADMIN" || currentUser == null

    // Default to 0L so ALL students of the school are visible immediately in Fee Register
    var selectedClassId by remember { mutableLongStateOf(0L) }
    var selectedStatusFilter by remember { mutableStateOf("ALL") } // ALL, PAID, PENDING, FREE
    var searchQuery by remember { mutableStateOf("") }

    // Dialog state for adding payment
    var studentForFee by remember { mutableStateOf<Student?>(null) }
    var feeAmountInput by remember { mutableStateOf("20") }
    var feeCurrencyInput by remember { mutableStateOf("USD") }
    var feeTypeInput by remember { mutableStateOf("Tuition") }
    var feeNotesInput by remember { mutableStateOf("") }

    // Dialog states for viewing history & deletion
    var viewingHistoryStudent by remember { mutableStateOf<Student?>(null) }
    var feeToDelete by remember { mutableStateOf<FeeRecord?>(null) }
    var showAllTransactionsDialog by remember { mutableStateOf(false) }

    // Confirmation dialog state
    var showConfirmationDialog by remember { mutableStateOf(false) }

    // School-wide totals for Full Transparency
    val schoolUsdTotal = remember(fees) { fees.filter { it.currency == "USD" && it.paidStatus == "Paid" }.sumOf { it.amount } }
    val schoolSlsTotal = remember(fees) { fees.filter { it.currency == "SLS" && it.paidStatus == "Paid" }.sumOf { it.amount } }
    val schoolEtbTotal = remember(fees) { fees.filter { it.currency == "ETB" && it.paidStatus == "Paid" }.sumOf { it.amount } }
    val schoolFreeCount = remember(students) { students.count { it.isFree } }
    val schoolPaidCount = remember(students, fees) {
        students.count { s -> !s.isFree && fees.any { it.studentId == s.id && it.paidStatus == "Paid" } }
    }
    val schoolPendingCount = remember(students, schoolPaidCount, schoolFreeCount) {
        (students.size - schoolPaidCount - schoolFreeCount).coerceAtLeast(0)
    }

    // Filter students by class, search query, and payment status
    val filteredStudents = remember(students, fees, selectedClassId, searchQuery, selectedStatusFilter) {
        students.filter { s ->
            val matchesClass = selectedClassId == 0L || s.classId == selectedClassId
            val matchesSearch = searchQuery.isBlank() ||
                s.name.contains(searchQuery, ignoreCase = true) ||
                s.studentId.contains(searchQuery, ignoreCase = true)

            val studentPaidFees = fees.filter { it.studentId == s.id && it.paidStatus == "Paid" }
            val isPaid = studentPaidFees.isNotEmpty()
            val matchesStatus = when (selectedStatusFilter) {
                "PAID" -> isPaid && !s.isFree
                "PENDING" -> !isPaid && !s.isFree
                "FREE" -> s.isFree
                else -> true
            }

            matchesClass && matchesSearch && matchesStatus
        }
    }

    // Totals by currency for selected class (or school-wide if selectedClassId == 0L)
    val scopeStudents = remember(students, selectedClassId) {
        if (selectedClassId == 0L) students else students.filter { it.classId == selectedClassId }
    }

    val scopeFees = remember(fees, selectedClassId) {
        if (selectedClassId == 0L) fees else fees.filter { it.classId == selectedClassId }
    }

    val usdTotal = scopeFees.filter { it.currency == "USD" && it.paidStatus == "Paid" }.sumOf { it.amount }
    val slsTotal = scopeFees.filter { it.currency == "SLS" && it.paidStatus == "Paid" }.sumOf { it.amount }
    val etbTotal = scopeFees.filter { it.currency == "ETB" && it.paidStatus == "Paid" }.sumOf { it.amount }

    val paidStudentsCount = remember(scopeStudents, scopeFees) {
        scopeStudents.count { s ->
            !s.isFree && scopeFees.any { it.studentId == s.id && it.paidStatus == "Paid" }
        }
    }

    val freeStudentsCount = remember(scopeStudents) {
        scopeStudents.count { it.isFree }
    }

    val pendingStudentsCount = remember(scopeStudents, paidStudentsCount, freeStudentsCount) {
        val count = scopeStudents.size - paidStudentsCount - freeStudentsCount
        if (count > 0) count else 0
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("💰 Fee Register & Tracking", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(
                            text = when {
                                isTeacher -> "Muuqaalka Macalinka • Dhammaan Lacagaha (Si Guud & Si Fasal)"
                                isCashier -> "Muuqaalka Xisaabiyaha • Dhammaan Ardayda Dugsiga & Fasalada"
                                else -> "Admin & Cashier • Financial Management Desk"
                            },
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    // Diiwaanka Lacagaha (All Transactions & Deletion)
                    IconButton(onClick = { showAllTransactionsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "Transactions Log",
                            tint = Color.White
                        )
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
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            // --- Financial Transparency Cards (School-Wide & Class Breakdown) ---
            if (selectedClassId != 0L) {
                // Dual Transparency View: School Total + Selected Class Total
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // School Wide Card
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDFA)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, TealPrimary.copy(alpha = 0.3f)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("🏫 Dugsiga Guud", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TealDark)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (schoolUsdTotal > 0) Text("$$schoolUsdTotal", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                if (schoolSlsTotal > 0) Text("${schoolSlsTotal}Sh", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                if (schoolEtbTotal > 0) Text("${schoolEtbTotal}Br", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = DarkText)
                            }
                            Text("Bixiyay: $schoolPaidCount | Lagu leeyahay: $schoolPendingCount | Free: $schoolFreeCount", fontSize = 8.sp, color = MutedText)
                        }
                    }

                    // Selected Class Card
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("📚 ${classes.find { it.id == selectedClassId }?.name ?: "Fasalka"}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (usdTotal > 0) Text("$$usdTotal", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                if (slsTotal > 0) Text("${slsTotal}Sh", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                if (etbTotal > 0) Text("${etbTotal}Br", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = DarkText)
                            }
                            Text("Bixiyay: $paidStudentsCount | Lagu leeyahay: $pendingStudentsCount | Free: $freeStudentsCount", fontSize = 8.sp, color = MutedText)
                        }
                    }
                }
            } else {
                // School Wide Single Comprehensive Card
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Default.AccountBalance, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(14.dp))
                                Text(
                                    text = "Dhammaan Dugsiga (Wadar: ${students.size} Arday)",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TealPrimary
                                )
                            }

                            // Compact Currency Badges
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (usdTotal > 0) Text("$$usdTotal", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TealDark)
                                if (slsTotal > 0) Text("${slsTotal}Sh", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TealDark)
                                if (etbTotal > 0) Text("${etbTotal}Br", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TealDark)
                            }
                        }

                        // Compact Counts Pill Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = PassGreen.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.clickable { selectedStatusFilter = if (selectedStatusFilter == "PAID") "ALL" else "PAID" }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = PassGreen, modifier = Modifier.size(11.dp))
                                    Text("Bixiyay: $paidStudentsCount", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = PassGreen)
                                }
                            }

                            Surface(
                                color = FailRed.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.clickable { selectedStatusFilter = if (selectedStatusFilter == "PENDING") "ALL" else "PENDING" }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, tint = FailRed, modifier = Modifier.size(11.dp))
                                    Text("Lagu leeyahay: $pendingStudentsCount", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FailRed)
                                }
                            }

                            Surface(
                                color = Color(0xFFD97706).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.clickable { selectedStatusFilter = if (selectedStatusFilter == "FREE") "ALL" else "FREE" }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(11.dp))
                                    Text("Bilaash: $freeStudentsCount", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD97706))
                                }
                            }
                        }
                    }
                }
            }

            // --- Compact Filter Bar: Search + Class Selector ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Compact Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Raadi arday...", fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(12.dp))
                            }
                        }
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    shape = RoundedCornerShape(8.dp)
                )

                // Quick Status Reset / Filter Indicator Icon
                if (selectedStatusFilter != "ALL") {
                    IconButton(
                        onClick = { selectedStatusFilter = "ALL" },
                        modifier = Modifier
                            .size(36.dp)
                            .background(TealContainer, RoundedCornerShape(8.dp))
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear Filter", tint = TealDark, modifier = Modifier.size(16.dp))
                    }
                }
            }

            // Compact Classes Horizontal Row (Icons & Chips)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                item {
                    Surface(
                        color = if (selectedClassId == 0L) TealPrimary else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = if (selectedClassId != 0L) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                        modifier = Modifier.clickable { selectedClassId = 0L }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                tint = if (selectedClassId == 0L) Color.White else DarkText,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Dhammaan",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selectedClassId == 0L) Color.White else DarkText
                            )
                        }
                    }
                }
                items(classes) { cls ->
                    val isSelected = selectedClassId == cls.id
                    Surface(
                        color = if (isSelected) TealPrimary else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(16.dp),
                        border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                        modifier = Modifier.clickable { selectedClassId = cls.id }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                Icons.Default.Home,
                                contentDescription = null,
                                tint = if (isSelected) Color.White else MutedText,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = cls.name,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else DarkText
                            )
                        }
                    }
                }
            }

            // --- Auto-Enrolled All Students List in Fee Register ---
            if (filteredStudents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Wax arday ah oo buuxiyay shuruudaha lama helin.",
                        color = MutedText,
                        fontSize = 12.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredStudents, key = { it.id }) { student ->
                        val studentPaidFees = fees.filter { it.studentId == student.id && it.paidStatus == "Paid" }
                        val isPaid = studentPaidFees.isNotEmpty()
                        val latestFee = studentPaidFees.lastOrNull()
                        val clsName = classes.find { it.id == student.classId }?.name ?: "No Class"

                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (student.isFree) Color(0xFFFFFBEB) else MaterialTheme.colorScheme.surface
                            ),
                            border = if (student.isFree) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD97706)) else null,
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewingHistoryStudent = student }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Status Icon Indicator (Left)
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .background(
                                            color = when {
                                                student.isFree -> Color(0xFFD97706).copy(alpha = 0.15f)
                                                isPaid -> PassGreen.copy(alpha = 0.15f)
                                                else -> FailRed.copy(alpha = 0.15f)
                                            },
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when {
                                            student.isFree -> Icons.Default.Star
                                            isPaid -> Icons.Default.Check
                                            else -> Icons.Default.Info
                                        },
                                        contentDescription = null,
                                        tint = when {
                                            student.isFree -> Color(0xFFD97706)
                                            isPaid -> PassGreen
                                            else -> FailRed
                                        },
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                // Student Info & Fee Status (Compact)
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = student.name,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (student.isFree) Color(0xFF78350F) else DarkText,
                                            maxLines = 1
                                        )
                                        if (student.isFree) {
                                            Surface(
                                                color = Color(0xFFD97706),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "FREE",
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "${student.studentId} • $clsName",
                                            fontSize = 10.sp,
                                            color = MutedText,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text("•", fontSize = 10.sp, color = MutedText)
                                        if (student.isFree) {
                                            Text(
                                                text = "Lacagta laga dhaafay",
                                                fontSize = 10.sp,
                                                color = Color(0xFFB45309),
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        } else if (isPaid && latestFee != null) {
                                            val totalPaidForStudent = studentPaidFees.sumOf { it.amount }
                                            Text(
                                                text = "${latestFee.currency} ${String.format("%.0f", totalPaidForStudent)} (${latestFee.paidDate.take(10)})",
                                                fontSize = 10.sp,
                                                color = PassGreen,
                                                fontWeight = FontWeight.Bold
                                            )
                                        } else {
                                            Text(
                                                text = "Lagu leeyahay",
                                                fontSize = 10.sp,
                                                color = FailRed,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // Right Action Section: Compact Icon Buttons
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (isPaid) {
                                        IconButton(
                                            onClick = { viewingHistoryStudent = student },
                                            modifier = Modifier
                                                .size(28.dp)
                                                .background(TealContainer.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.List,
                                                contentDescription = "History",
                                                tint = TealPrimary,
                                                modifier = Modifier.size(15.dp)
                                            )
                                        }
                                    }

                                    if (isCashierOrAdmin) {
                                        if (student.isFree) {
                                            Box(
                                                modifier = Modifier
                                                    .size(28.dp)
                                                    .background(Color(0xFFD97706).copy(alpha = 0.12f), RoundedCornerShape(6.dp)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Default.Lock, contentDescription = "Free Locked", tint = Color(0xFFB45309), modifier = Modifier.size(14.dp))
                                            }
                                        } else {
                                            IconButton(
                                                onClick = {
                                                    studentForFee = student
                                                    feeAmountInput = "20"
                                                    feeCurrencyInput = "USD"
                                                    feeTypeInput = "Tuition"
                                                    feeNotesInput = ""
                                                },
                                                modifier = Modifier
                                                    .size(30.dp)
                                                    .background(
                                                        if (isPaid) TealPrimary else PassGreen,
                                                        RoundedCornerShape(6.dp)
                                                    )
                                            ) {
                                                Icon(
                                                    imageVector = if (isPaid) Icons.Default.Add else Icons.Default.Payment,
                                                    contentDescription = "Pay Fee",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        // Teacher compact status icon
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .background(
                                                    if (student.isFree) Color(0xFFD97706).copy(alpha = 0.15f) else if (isPaid) PassGreen.copy(alpha = 0.15f) else FailRed.copy(alpha = 0.15f),
                                                    RoundedCornerShape(6.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (student.isFree) Icons.Default.Star else if (isPaid) Icons.Default.Check else Icons.Default.Close,
                                                contentDescription = null,
                                                tint = if (student.isFree) Color(0xFFB45309) else if (isPaid) PassGreen else FailRed,
                                                modifier = Modifier.size(14.dp)
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

    // --- Add Fee Dialog ---
    if (studentForFee != null && !showConfirmationDialog) {
        val s = studentForFee!!
        AlertDialog(
            onDismissRequest = { studentForFee = null },
            title = { Text("💰 Record Payment for ${s.name}", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Student: ${s.name} (${s.studentId})", fontSize = 12.sp, color = TealDark, fontWeight = FontWeight.SemiBold)

                    // Amount
                    OutlinedTextField(
                        value = feeAmountInput,
                        onValueChange = { feeAmountInput = it },
                        label = { Text("Amount") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Currency Selector
                    Column {
                        Text("Currency:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                            listOf("USD", "SLS", "ETB").forEach { curr ->
                                FilterChip(
                                    selected = feeCurrencyInput == curr,
                                    onClick = { feeCurrencyInput = curr },
                                    label = { Text(curr, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // Fee Type Selector
                    Column {
                        Text("Fee Type:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                            listOf("Tuition", "Exam", "Transport", "Registration").forEach { type ->
                                FilterChip(
                                    selected = feeTypeInput == type,
                                    onClick = { feeTypeInput = type },
                                    label = { Text(type, fontSize = 10.sp) }
                                )
                            }
                        }
                    }

                    // Notes
                    OutlinedTextField(
                        value = feeNotesInput,
                        onValueChange = { feeNotesInput = it },
                        label = { Text("Notes (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "📅 Current Date & Time will be automatically attached upon confirmation.",
                        fontSize = 10.sp,
                        color = MutedText
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = feeAmountInput.toDoubleOrNull()
                        if (amt != null && amt > 0) {
                            showConfirmationDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    Text("Proceed to Confirm", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { studentForFee = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Save Confirmation Dialog (User requested: "Are you sure student has paid?") ---
    if (showConfirmationDialog && studentForFee != null) {
        val s = studentForFee!!
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            title = { Text("⚠️ Confirm Payment Receipt", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = "Are you sure that student '${s.name}' has paid $feeCurrencyInput $feeAmountInput for $feeTypeInput?",
                    fontSize = 14.sp,
                    color = DarkText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = feeAmountInput.toDoubleOrNull() ?: 0.0
                        onRecordFeePayment(
                            s.id,
                            s.classId,
                            amt,
                            feeCurrencyInput,
                            feeTypeInput,
                            feeNotesInput
                        ) { success, _ ->
                            if (success) {
                                showConfirmationDialog = false
                                studentForFee = null
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PassGreen)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Yes, Confirm & Save", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // --- Student Payment History Dialog ---
    if (viewingHistoryStudent != null) {
        val s = viewingHistoryStudent!!
        val studentHistoryFees = fees.filter { it.studentId == s.id }.sortedByDescending { it.id }

        AlertDialog(
            onDismissRequest = { viewingHistoryStudent = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.List, contentDescription = null, tint = TealPrimary)
                    Column {
                        Text(s.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("Diiwaanka Lacagaha • ${s.studentId}", fontSize = 11.sp, color = MutedText)
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                    if (s.isFree) {
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = "⭐ Ardaygan waxa laga dhaafay lacagta dugsiga (Free Student).",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    if (studentHistoryFees.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Weli wax lacag ah lagama diiwaangelin ardaygan.", fontSize = 12.sp, color = MutedText)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(studentHistoryFees) { fee ->
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    text = "${fee.currency} ${if (fee.amount % 1.0 == 0.0) fee.amount.toInt().toString() else fee.amount}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = if (fee.paidStatus == "Paid") PassGreen else FailRed
                                                )
                                                Surface(
                                                    color = if (fee.paidStatus == "Paid") PassGreen.copy(alpha = 0.15f) else FailRed.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = fee.paidStatus,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = if (fee.paidStatus == "Paid") PassGreen else FailRed,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = "${fee.feeType} • ${fee.paidDate.ifBlank { fee.dueDate }}",
                                                fontSize = 10.sp,
                                                color = DarkText
                                            )
                                            if (fee.notes.isNotBlank()) {
                                                Text("Xusuus: ${fee.notes}", fontSize = 9.sp, color = MutedText)
                                            }
                                        }

                                        // Delete Action for Admins
                                        if (isCashierOrAdmin) {
                                            IconButton(
                                                onClick = { feeToDelete = fee },
                                                modifier = Modifier.size(30.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete Fee Record",
                                                    tint = FailRed,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewingHistoryStudent = null }) {
                    Text("Xidh", color = TealPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // --- All Transactions Log Dialog (with Deletion for Admins) ---
    if (showAllTransactionsDialog) {
        val targetFees = if (selectedClassId == 0L) fees else fees.filter { it.classId == selectedClassId }
        val sortedFees = targetFees.sortedByDescending { it.id }

        AlertDialog(
            onDismissRequest = { showAllTransactionsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.List, contentDescription = null, tint = TealPrimary)
                    Column {
                        Text("Diiwaanka Dhammaan Lacagaha", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(
                            text = if (selectedClassId == 0L) "Dhammaan Fasalada (${sortedFees.size} Diiwaan)"
                                   else "${classes.find { it.id == selectedClassId }?.name} (${sortedFees.size} Diiwaan)",
                            fontSize = 11.sp,
                            color = MutedText
                        )
                    }
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                    if (sortedFees.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
                            Text("Weli wax lacago ah lagama helin.", fontSize = 12.sp, color = MutedText)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(sortedFees, key = { it.id }) { fee ->
                                val studentName = students.find { it.id == fee.studentId }?.name ?: "Arday #${fee.studentId}"
                                val clsName = classes.find { it.id == fee.classId }?.name ?: "Fasal"

                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(studentName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkText)
                                                Text("• $clsName", fontSize = 10.sp, color = MutedText)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                Text(
                                                    text = "${fee.currency} ${if (fee.amount % 1.0 == 0.0) fee.amount.toInt().toString() else fee.amount}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp,
                                                    color = if (fee.paidStatus == "Paid") PassGreen else FailRed
                                                )
                                                Text("• ${fee.feeType}", fontSize = 10.sp, color = MutedText)
                                                Text("• ${fee.paidDate.ifBlank { fee.dueDate }.take(10)}", fontSize = 10.sp, color = MutedText)
                                            }
                                        }

                                        // Delete payment record button for Admin
                                        if (isCashierOrAdmin) {
                                            IconButton(
                                                onClick = { feeToDelete = fee },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = "Delete Fee Record",
                                                    tint = FailRed,
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAllTransactionsDialog = false }) {
                    Text("Xidh", color = TealPrimary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // --- Delete Fee Record Confirmation Dialog ---
    if (feeToDelete != null) {
        val targetFee = feeToDelete!!
        val studentName = students.find { it.id == targetFee.studentId }?.name ?: "Student"

        AlertDialog(
            onDismissRequest = { feeToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = FailRed, modifier = Modifier.size(32.dp)) },
            title = { Text("🗑️ Tirtir Lacag-bixinta", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Ma hubtaa inaad tirtirto diiwaanka lacag-bixintan?",
                        fontSize = 13.sp,
                        color = DarkText
                    )
                    Surface(
                        color = Color(0xFFFEF2F2),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text("👤 Ardayga: $studentName", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = DarkText)
                            Text(
                                "💵 Qadarka: ${targetFee.currency} ${if (targetFee.amount % 1.0 == 0.0) targetFee.amount.toInt().toString() else targetFee.amount}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = FailRed
                            )
                            Text("📋 Nooca: ${targetFee.feeType} (${targetFee.month.ifBlank { "Monthly" }})", fontSize = 11.sp, color = MutedText)
                            Text("📅 Taariikhda: ${targetFee.paidDate.ifBlank { targetFee.dueDate }}", fontSize = 11.sp, color = MutedText)
                        }
                    }
                    Text(
                        text = "⚠️ Tallaabadan dib looma noqon karo. Diiwaanka lacag-bixintan gabi ahaanba waa la tirtirayaa.",
                        fontSize = 11.sp,
                        color = FailRed,
                        fontWeight = FontWeight.Medium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idToDelete = targetFee.id
                        feeToDelete = null
                        onDeleteFeeClick(idToDelete)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FailRed)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Haa, Tirtir", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { feeToDelete = null }) {
                    Text("Jooji")
                }
            }
        )
    }
}
