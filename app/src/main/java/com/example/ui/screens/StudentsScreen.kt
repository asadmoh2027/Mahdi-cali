package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentsScreen(
    students: List<Student>,
    classes: List<SchoolClass>,
    currentUser: User?,
    fees: List<FeeRecord> = emptyList(),
    exams: List<Exam> = emptyList(),
    marks: List<ExamMark> = emptyList(),
    attendance: List<AttendanceRecord> = emptyList(),
    schoolName: String = "Mahdi Cali School",
    onAddStudentClick: () -> Unit,
    onBulkUploadClick: (Long, String, (Int, String) -> Unit) -> Unit,
    onDownloadSampleSheet: () -> Unit,
    onExportStudentsCsv: (Long) -> Unit = {},
    onPrintStudentsListMarkHtml: (Long, String, String, Double, String, String) -> Unit = { _, _, _, _, _, _ -> },
    onExportStudentsListMarkCsv: (Long, String, String, Double) -> Unit = { _, _, _, _ -> },
    onDeleteStudentClick: (Long) -> Unit,
    onDeleteAllClassStudentsClick: (Long) -> Unit = {},
    onToggleFreeClick: (Long, Boolean) -> Unit = { _, _ -> },
    onPrintStudentReport: (Student) -> Unit = {},
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf("All") }
    var selectedClassId by remember { mutableLongStateOf(0L) }
    var showBulkDialog by remember { mutableStateOf(false) }
    var showListMarkDialog by remember { mutableStateOf(false) }

    var selectedReportStudent by remember { mutableStateOf<Student?>(null) }
    var profileStudent by remember { mutableStateOf<Student?>(null) }
    var studentToDelete by remember { mutableStateOf<Student?>(null) }
    var showDeleteAllClassStudentsConfirmDialog by remember { mutableStateOf(false) }

    val isAdmin = currentUser == null || currentUser.role == "ADMIN"
    val isCashier = currentUser?.role == "CASHIER"
    val isTeacher = currentUser?.role == "TEACHER"

    val accessibleClasses: List<SchoolClass> = remember(classes, currentUser) {
        if (currentUser == null || currentUser.role == "ADMIN" || currentUser.role == "CASHIER") {
            classes
        } else {
            val assigned = currentUser.getAssignedClassIdSet()
            classes.filter { it.id in assigned }
        }
    }

    val filteredStudents: List<Student> = remember(students, searchQuery, selectedGender, selectedClassId, accessibleClasses, currentUser) {
        val baseList = if (currentUser != null && currentUser.role == "TEACHER") {
            val assigned = currentUser.getAssignedClassIdSet()
            students.filter { it.classId in assigned }
        } else {
            students
        }

        baseList.filter { student ->
            val matchesClass = selectedClassId == 0L || student.classId == selectedClassId
            val matchesName = searchQuery.isBlank() ||
                student.name.contains(searchQuery, ignoreCase = true) ||
                student.studentId.contains(searchQuery, ignoreCase = true) ||
                student.phone.contains(searchQuery, ignoreCase = true)
            val matchesGender = selectedGender == "All" ||
                student.gender.equals(selectedGender, ignoreCase = true)

            matchesClass && matchesName && matchesGender
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Students (${filteredStudents.size})",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (isAdmin) "Admin Access • Full Control" else if (isCashier) "Cashier Access • All Students & Classes (Full Management)" else "Teacher View • Assigned Classes",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    // Students List Mark Action (Printable grading sheet for teachers)
                    IconButton(onClick = { showListMarkDialog = true }) {
                        Icon(Icons.Default.Assignment, contentDescription = "Students List Mark", tint = Color.White)
                    }
                    // Export Students to Excel/CSV
                    IconButton(onClick = { onExportStudentsCsv(selectedClassId) }) {
                        Icon(Icons.Default.FileDownload, contentDescription = "Export Excel", tint = Color.White)
                    }
                    // Bulk upload icon
                    IconButton(onClick = { showBulkDialog = true }) {
                        Icon(Icons.Default.UploadFile, contentDescription = "Bulk Upload", tint = Color.White)
                    }
                    // Download CSV sample
                    IconButton(onClick = onDownloadSampleSheet) {
                        Icon(Icons.Default.Download, contentDescription = "Sample CSV", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TealPrimary)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddStudentClick,
                containerColor = TealPrimary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Student")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // --- Global Student Search Bar & Filter Section ---
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Global Search Bar for Name or ID
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Raadi magaca ardayga ama ID (Search by name or ID)...", fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search Students",
                                tint = TealPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "Clear Search",
                                        tint = MutedText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TealPrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("student_search_bar")
                    )

                    // Match results counter info if search query is present
                    if (searchQuery.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Natiijada raadinta: ${filteredStudents.size} arday oo u dhiganta \"$searchQuery\"",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TealPrimary
                            )
                            TextButton(
                                onClick = { searchQuery = "" },
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.height(20.dp)
                            ) {
                                Text("Nadiifi (Clear)", fontSize = 11.sp, color = FailRed)
                            }
                        }
                    }

                    // Compact Filter Chips Bar (Gender Icons + Class Chips)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Gender quick filters
                        item {
                            FilterChip(
                                selected = selectedGender == "All",
                                onClick = { selectedGender = "All" },
                                label = { Text("All", fontSize = 11.sp) },
                                leadingIcon = { Icon(Icons.Default.People, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedGender == "Male",
                                onClick = { selectedGender = "Male" },
                                label = { Text("Boys", fontSize = 11.sp) },
                                leadingIcon = { Icon(Icons.Default.Boy, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedGender == "Female",
                                onClick = { selectedGender = "Female" },
                                label = { Text("Girls", fontSize = 11.sp) },
                                leadingIcon = { Icon(Icons.Default.Girl, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }

                        item {
                            FilterChip(
                                selected = showListMarkDialog,
                                onClick = { showListMarkDialog = true },
                                label = { Text("📝 Students List Mark", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TealDark) },
                                leadingIcon = { Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(14.dp), tint = TealDark) }
                            )
                        }

                        item {
                            FilterChip(
                                selected = false,
                                onClick = { onExportStudentsCsv(selectedClassId) },
                                label = { Text("📥 Degso Excel", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp), tint = TealPrimary) }
                            )
                        }

                        // Class quick filters
                        item {
                            FilterChip(
                                selected = selectedClassId == 0L,
                                onClick = { selectedClassId = 0L },
                                label = { Text(if (isAdmin) "All Classes" else "My Classes", fontSize = 11.sp) },
                                leadingIcon = { Icon(Icons.Default.School, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                        }

                        items(accessibleClasses) { cls ->
                            FilterChip(
                                selected = selectedClassId == cls.id,
                                onClick = { selectedClassId = cls.id },
                                label = { Text(cls.name, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            }

            // Delete All Students of Class banner (Admin Only)
            if (isAdmin && selectedClassId != 0L && filteredStudents.isNotEmpty()) {
                val currentClass = classes.find { it.id == selectedClassId }
                val className = currentClass?.name ?: ""
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Maamulida Fasalka '$className'",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = FailRed
                            )
                            Text(
                                text = "Waxaad halmar wada tirtiri kartaa dhamaan ardayda ${filteredStudents.size} ee fasalkan.",
                                fontSize = 10.sp,
                                color = DarkText
                            )
                        }
                        Button(
                            onClick = { showDeleteAllClassStudentsConfirmDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = FailRed),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Tirtir Dhamaan", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // --- Student List ---
            if (filteredStudents.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (!isAdmin && accessibleClasses.isEmpty()) {
                            "Ma laguu qoondeyn wax fasal ah weli.\nLa xidhiidh Maamulaha (Admin) si lagugu daro fasalkaaga."
                        } else {
                            "No students match the criteria.\nTap '+' to register a student or use 'Bulk Upload'."
                        },
                        color = MutedText,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredStudents, key = { it.id }) { student ->
                        val clsName = classes.find { it.id == student.classId }?.name ?: "N/A"

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (student.isFree) Color(0xFFFFFBEB) else MaterialTheme.colorScheme.surface
                            ),
                            border = if (student.isFree) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFD97706)) else null,
                            elevation = CardDefaults.cardElevation(defaultElevation = if (student.isFree) 3.dp else 1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Student Name & Badges
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = student.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (student.isFree) Color(0xFF78350F) else DarkText
                                        )

                                        if (student.isFree) {
                                            Surface(
                                                color = Color(0xFFD97706),
                                                shape = RoundedCornerShape(6.dp)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Star,
                                                        contentDescription = null,
                                                        tint = Color.White,
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                    Text(
                                                        text = "FREE",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Black,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(2.dp))

                                    Text(
                                        text = "${student.studentId} • $clsName • ${student.gender}" + if (student.isFree) " • (Fee Exempt)" else "",
                                        fontSize = 11.sp,
                                        color = if (student.isFree) Color(0xFFB45309) else MutedText,
                                        fontWeight = if (student.isFree) FontWeight.SemiBold else FontWeight.Normal
                                    )
                                }

                                // Icons Action Area: REPORT, CALL, DELETE & PROFILE
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Student Comprehensive Report Icon Button (NEW!)
                                    IconButton(
                                        onClick = { selectedReportStudent = student },
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0xFF2563EB).copy(alpha = 0.15f),
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Assessment,
                                                    contentDescription = "Warbixinta Guud ee Ardayga",
                                                    tint = Color(0xFF1D4ED8),
                                                    modifier = Modifier.size(19.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Call Icon Button
                                    if (student.phone.isNotBlank()) {
                                        IconButton(
                                            onClick = {
                                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${student.phone.trim()}"))
                                                context.startActivity(intent)
                                            },
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = PassGreen.copy(alpha = 0.15f),
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.Phone,
                                                        contentDescription = "Call Guardian",
                                                        tint = PassGreen,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Delete Icon Button (Admin only - triggers Confirmation Dialog)
                                    if (isAdmin) {
                                        IconButton(
                                            onClick = { studentToDelete = student },
                                            modifier = Modifier.size(38.dp)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = FailRed.copy(alpha = 0.12f),
                                                modifier = Modifier.fillMaxSize()
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = Icons.Default.DeleteOutline,
                                                        contentDescription = "Delete Student",
                                                        tint = FailRed,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Profile Icon Button (Opens full info bottom sheet)
                                    IconButton(
                                        onClick = { profileStudent = student },
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = TealContainer,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.Person,
                                                    contentDescription = "View Profile",
                                                    tint = TealPrimary,
                                                    modifier = Modifier.size(20.dp)
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
    }

    // --- Profile Bottom Sheet Dialog ---
    if (profileStudent != null) {
        val s = profileStudent!!
        val clsName = classes.find { it.id == s.classId }?.name ?: "Unknown Class"
        val studentFees = fees.filter { it.studentId == s.id }
        val studentMarks = marks.filter { it.studentId == s.id }
        val studentAtt = attendance.filter { it.studentId == s.id }

        val totalDays = studentAtt.size
        val presentDays = studentAtt.count { it.status == "Present" }
        val attRate = if (totalDays > 0) (presentDays * 100) / totalDays else 100

        val totalPaid = studentFees.filter { it.paidStatus == "Paid" }.sumOf { it.amount }
        val pendingFees = studentFees.filter { it.paidStatus != "Paid" }

        ModalBottomSheet(
            onDismissRequest = { profileStudent = null },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(text = s.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (s.isFree) Color(0xFF78350F) else DarkText)
                            if (s.isFree) {
                                Surface(
                                    color = Color(0xFFD97706),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                        Text("FREE / SCHOLARSHIP", fontSize = 10.sp, fontWeight = FontWeight.Black, color = Color.White)
                                    }
                                }
                            }
                        }
                        Text(text = "ID: ${s.studentId} • Class: $clsName", fontSize = 12.sp, color = TealDark, fontWeight = FontWeight.SemiBold)
                    }

                    IconButton(onClick = { profileStudent = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                HorizontalDivider()

                // Basic Details Grid
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = TealContainer.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("📋 Student Details", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TealDark)
                        Text("• Gender: ${s.gender}", fontSize = 12.sp)
                        Text("• Mother's Name: ${s.motherName.ifBlank { "N/A" }}", fontSize = 12.sp)
                        Text("• Guardian Phone: ${s.phone.ifBlank { "N/A" }}", fontSize = 12.sp)
                    }
                }

                // Attendance & Academics Summary
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("📊 Academic & Attendance Overview", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("• Attendance Rate: $attRate% ($presentDays / $totalDays days attended)", fontSize = 12.sp)
                        Text("• Exams Recorded: ${studentMarks.size} subjects", fontSize = 12.sp)
                    }
                }

                // Financial Overview
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = if (s.isFree) Color(0xFFFFFBEB) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = if (s.isFree) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFD97706)) else null
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("💰 Fee & Financial Status", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (s.isFree) Color(0xFFB45309) else DarkText)
                        if (s.isFree) {
                            Text("• Fee Status: 🌟 EXEMPT (Lacagta waa laga dhaafay)", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                            Text("• Ardaygan wax lacag ah lagama qaado. Macalin iyo Cashier toona waxba kama beddeli karaan xaaladdiisa Free-ga ah.", fontSize = 11.sp, color = Color(0xFF78350F))
                        } else {
                            Text("• Total Paid Fees: $${String.format("%.0f", totalPaid)}", fontSize = 12.sp, color = PassGreen, fontWeight = FontWeight.SemiBold)
                            Text("• Pending Invoices: ${pendingFees.size}", fontSize = 12.sp, color = if (pendingFees.isNotEmpty()) FailRed else DarkText)
                        }
                    }
                }

                // Admin Controls (Toggle Free & Delete)
                if (isAdmin) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("⚙️ Administrator Options", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TealDark)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                onToggleFreeClick(s.id, !s.isFree)
                                profileStudent = profileStudent?.copy(isFree = !s.isFree)
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(if (s.isFree) "Make Standard" else "Mark as Free", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                studentToDelete = s
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = FailRed),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Delete Student", fontSize = 12.sp, color = Color.White)
                        }
                    }
                } else {
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = MutedText, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Ogeysiis: Macalimiintu wax ma tirtiri karaan, mana beddeli karaan ardayda Free-ga ah. Waxaad kaliya ku dari kartaa arday cusub fasalkaaga.",
                                fontSize = 11.sp,
                                color = MutedText
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }

    // Delete Confirmation Dialog (Admin only - Prevents Accidental Data Loss)
    if (studentToDelete != null) {
        val s = studentToDelete!!
        val sClassName = classes.find { it.id == s.classId }?.name ?: "Unknown Class"
        
        AlertDialog(
            onDismissRequest = { studentToDelete = null },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = FailRed.copy(alpha = 0.15f),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Warning",
                            tint = FailRed,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "Tirtir Ardayga (Permanent Deletion)?",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("👤 Magaca: ${s.name}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkText)
                            Text("🆔 ID-ga: ${s.studentId}", fontSize = 12.sp, color = TealDark, fontWeight = FontWeight.SemiBold)
                            Text("🏫 Fasalka: $sClassName", fontSize = 12.sp, color = MutedText)
                            if (s.phone.isNotBlank()) {
                                Text("📞 Phone: ${s.phone}", fontSize = 12.sp, color = MutedText)
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFEBEE)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = FailRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Digniin: Ardaygan iyo dhammaan xogtiisa (buundooyinka imtixaanaadka, diiwaanka xaadirinta, iyo taariikhda lacagaha) waxaa si rasmi ah looga tirtirayaa database-ka. Tallaabadan dib looma noqon karo (Irreversible).",
                                fontSize = 11.sp,
                                color = FailRed,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteStudentClick(s.id)
                        studentToDelete = null
                        if (profileStudent?.id == s.id) profileStudent = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FailRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Haa, Tirtir Ardayga", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { studentToDelete = null },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Jooji (Cancel)", color = DarkText, fontSize = 12.sp)
                }
            }
        )
    }

    // Delete All Class Students Confirmation Dialog (Admin only)
    if (showDeleteAllClassStudentsConfirmDialog) {
        val currentClass = classes.find { it.id == selectedClassId }
        val className = currentClass?.name ?: ""
        
        AlertDialog(
            onDismissRequest = { showDeleteAllClassStudentsConfirmDialog = false },
            icon = {
                Surface(
                    shape = CircleShape,
                    color = FailRed.copy(alpha = 0.15f),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Warning",
                            tint = FailRed,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            },
            title = {
                Text(
                    text = "Tirtir Dhamaan Ardayda Fasalka?",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("🏫 Fasalka: $className", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkText)
                            Text("👥 Tirada Ardayda la tirtirayo: ${filteredStudents.size} Arday", fontSize = 12.sp, color = FailRed, fontWeight = FontWeight.Bold)
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFFEBEE)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = FailRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Digniin Aad U Culus: Waxaad si rasmi ah u tirtiraysaa DHAMMAAN ardayda ku dhex jirta fasalka '$className'. Tani waxay meesha ka saaraysaa dhammaan xogta buundooyinka, xaadiriska, iyo lacagaha ay bixiyeen ardaydaas. Tallaabadan dib looma noqon karo (Irreversible)!",
                                fontSize = 11.sp,
                                color = FailRed,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAllClassStudentsClick(selectedClassId)
                        showDeleteAllClassStudentsConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FailRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Haa, Tirtir Dhamaan", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteAllClassStudentsConfirmDialog = false },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Jooji (Cancel)", color = DarkText, fontSize = 12.sp)
                }
            }
        )
    }

    // Bulk Upload Dialog
    if (showBulkDialog) {
        BulkUploadDialog(
            classes = accessibleClasses,
            onDismiss = { showBulkDialog = false },
            onUpload = onBulkUploadClick,
            onDownloadSample = onDownloadSampleSheet
        )
    }

    // Students List Mark Dialog (Printable Grade Entry Sheet)
    if (showListMarkDialog) {
        StudentsListMarkDialog(
            classes = accessibleClasses,
            students = students,
            initialClassId = if (selectedClassId != 0L) selectedClassId else (accessibleClasses.firstOrNull()?.id ?: 0L),
            currentUser = currentUser,
            onDismiss = { showListMarkDialog = false },
            onPrint = { classId, subject, examTitle, maxMarks, yr, teacher ->
                onPrintStudentsListMarkHtml(classId, subject, examTitle, maxMarks, yr, teacher)
            },
            onExportCsv = { classId, subject, examTitle, maxMarks ->
                onExportStudentsListMarkCsv(classId, subject, examTitle, maxMarks)
            }
        )
    }

    // Student Comprehensive Report Dialog (Information, Exams, Attendance, Fees, Print)
    if (selectedReportStudent != null) {
        StudentComprehensiveReportDialog(
            student = selectedReportStudent!!,
            classes = classes,
            fees = fees,
            exams = exams,
            marks = marks,
            attendance = attendance,
            schoolName = schoolName,
            onDismiss = { selectedReportStudent = null },
            onPrint = {
                onPrintStudentReport(selectedReportStudent!!)
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StudentsListMarkDialog(
    classes: List<SchoolClass>,
    students: List<Student>,
    initialClassId: Long,
    currentUser: User?,
    onDismiss: () -> Unit,
    onPrint: (Long, String, String, Double, String, String) -> Unit,
    onExportCsv: (Long, String, String, Double) -> Unit
) {
    var selectedClassId by remember { mutableLongStateOf(initialClassId) }
    var subjectName by remember { mutableStateOf("Xisaab (Math)") }
    var examTitle by remember { mutableStateOf("Imtixaanka Bisha (Monthly Test)") }
    var maxMarksText by remember { mutableStateOf("100") }
    var academicYear by remember { mutableStateOf("2026/2027") }
    var teacherName by remember { mutableStateOf(currentUser?.fullName ?: "Macalinka Maadada") }

    val commonSubjects = listOf("Diin", "Somali", "Carabi", "English", "Xisaab", "Saynis", "C/Bulsho", "Tarbiyo")
    val commonAssessments = listOf(
        "Imtixaanka Bisha (Monthly Test)",
        "Imtixaanka Term 1",
        "Imtixaanka Term 2",
        "Imtixaanka Guud (Final)",
        "Quiz / Gaaban",
        "Shaqo-Guri (Homework)"
    )

    val classStudents = remember(students, selectedClassId) {
        students.filter { selectedClassId == 0L || it.classId == selectedClassId }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    }

    val selectedClassName = classes.find { it.id == selectedClassId }?.name ?: "All Classes"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = CircleShape,
                    color = TealContainer,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Assignment, contentDescription = null, tint = TealDark, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Students List Mark",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = TealDark
                    )
                    Text(
                        text = "Xaashida Dhibcaha & Qiimaynta Fasalka",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Dooro fasalka, maadada, iyo faahfaahinta si aad u daabacdo shaxda loogada iyo magaca dugsiga wadata oo macalinku dhibcaha ugu buuxiyo gacanta:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 1. Class selection chips
                Text("1. Dooro Fasalka (Select Class):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TealDark)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(classes) { cls ->
                        FilterChip(
                            selected = selectedClassId == cls.id,
                            onClick = { selectedClassId = cls.id },
                            label = { Text(cls.name, fontSize = 11.sp) }
                        )
                    }
                }

                // 2. Subject Selection
                Text("2. Maadada (Subject Name):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TealDark)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(commonSubjects) { sub ->
                        FilterChip(
                            selected = subjectName.startsWith(sub, ignoreCase = true),
                            onClick = { subjectName = sub },
                            label = { Text(sub, fontSize = 11.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = subjectName,
                    onValueChange = { subjectName = it },
                    label = { Text("Magaca Maadada") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 3. Assessment / Exam Type
                Text("3. Nooca Qiimaynta (Assessment Type):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TealDark)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(commonAssessments) { asm ->
                        FilterChip(
                            selected = examTitle == asm,
                            onClick = { examTitle = asm },
                            label = { Text(asm.take(18), fontSize = 11.sp) }
                        )
                    }
                }
                OutlinedTextField(
                    value = examTitle,
                    onValueChange = { examTitle = it },
                    label = { Text("Nooca Imtixaanka / Qiimaynta") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 4. Max Marks & Academic Year
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = maxMarksText,
                        onValueChange = { maxMarksText = it },
                        label = { Text("Wadarta Dhibcaha") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = academicYear,
                        onValueChange = { academicYear = it },
                        label = { Text("Sanad-Dugsiyeedka") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 5. Teacher Name
                OutlinedTextField(
                    value = teacherName,
                    onValueChange = { teacherName = it },
                    label = { Text("Magaca Macalinka (Teacher Name)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 6. Live Table Preview
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Shaxda: $selectedClassName (${classStudents.size} Arday):",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TealDark
                            )
                            Text(
                                "ST ID | Name | Marks",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        classStudents.take(5).forEachIndexed { idx, s ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${idx + 1}. [${s.studentId}] ${s.name}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Surface(
                                    border = androidx.compose.foundation.BorderStroke(1.dp, TealPrimary),
                                    shape = RoundedCornerShape(4.dp),
                                    color = Color.White
                                ) {
                                    Text("___ / ${maxMarksText.ifBlank { "100" }}", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TealDark, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                            }
                        }
                        if (classStudents.size > 5) {
                            Text("+ ${classStudents.size - 5} arday oo kale oo shaxda ku jira...", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = {
                        val maxM = maxMarksText.toDoubleOrNull() ?: 100.0
                        onExportCsv(selectedClassId, subjectName, examTitle, maxM)
                    }
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Excel", fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        val maxM = maxMarksText.toDoubleOrNull() ?: 100.0
                        onPrint(selectedClassId, subjectName, examTitle, maxM, academicYear, teacherName)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("🖨️ Daabac", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Xidh")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BulkUploadDialog(
    classes: List<SchoolClass>,
    onDismiss: () -> Unit,
    onUpload: (Long, String, (Int, String) -> Unit) -> Unit,
    onDownloadSample: () -> Unit
) {
    val context = LocalContext.current
    var selectedClassId by remember { mutableLongStateOf(classes.firstOrNull()?.id ?: 0L) }
    var csvText by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader().use { it.readText() }
                }
                if (!content.isNullOrBlank()) {
                    csvText = content
                    statusMessage = "✅ File loaded! Tap 'Upload Students' to proceed."
                }
            } catch (e: Exception) {
                statusMessage = "❌ Error reading file: ${e.localizedMessage}"
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📁 Bulk Upload Class Students", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("Select the target class and upload a CSV file or paste lines.", fontSize = 12.sp, color = MutedText)

                // Class Dropdown / Selector
                Column {
                    Text("Target Class:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        classes.take(4).forEach { cls ->
                            FilterChip(
                                selected = selectedClassId == cls.id,
                                onClick = { selectedClassId = cls.id },
                                label = { Text(cls.name, fontSize = 11.sp) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { filePicker.launch("text/*") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pick CSV", fontSize = 11.sp)
                    }

                    OutlinedButton(
                        onClick = onDownloadSample,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sample CSV", fontSize = 11.sp)
                    }
                }

                OutlinedTextField(
                    value = csvText,
                    onValueChange = { csvText = it },
                    label = { Text("CSV Data (Name, Gender, Mother, Phone)") },
                    placeholder = { Text("Ali Ahmed, Male, Amina, 0634123456\nFadumo Hassan, Female, Maryan, 0635112233") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                )

                statusMessage?.let {
                    Text(it, fontSize = 11.sp, color = TealDark, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (selectedClassId == 0L || csvText.isBlank()) {
                        statusMessage = "❌ Please select a class and provide CSV data!"
                    } else {
                        onUpload(selectedClassId, csvText) { count, msg ->
                            statusMessage = msg
                            if (count > 0) {
                                onDismiss()
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
            ) {
                Text("Upload Students", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentComprehensiveReportDialog(
    student: Student,
    classes: List<SchoolClass>,
    fees: List<FeeRecord>,
    exams: List<Exam>,
    marks: List<ExamMark>,
    attendance: List<AttendanceRecord>,
    schoolName: String,
    onDismiss: () -> Unit,
    onPrint: () -> Unit
) {
    val className = remember(classes, student.classId) {
        classes.firstOrNull { it.id == student.classId }?.name ?: "Fasalka N/A"
    }

    // Filter student specific records
    val studentMarks = remember(marks, student.id) {
        marks.filter { it.studentId == student.id }
    }
    val studentAttendance = remember(attendance, student.id) {
        attendance.filter { it.studentId == student.id }
    }
    val studentFees = remember(fees, student.id) {
        fees.filter { it.studentId == student.id }
    }

    // Exam Metrics
    val totalScore = studentMarks.sumOf { it.score }
    val totalMax = studentMarks.sumOf { m ->
        exams.firstOrNull { it.id == m.examId }?.totalMarks ?: 100.0
    }
    val averageScore = if (totalMax > 0) (totalScore / totalMax) * 100.0 else 0.0

    // Attendance Metrics
    val totalAtt = studentAttendance.size
    val presentCount = studentAttendance.count { it.status == "Present" }
    val absentCount = studentAttendance.count { it.status == "Absent" || it.status == "A" }
    val lateCount = studentAttendance.count { it.status == "Late" || it.status == "Habsan" || it.status == "H" }
    val attPercentage = if (totalAtt > 0) (presentCount.toDouble() / totalAtt) * 100 else 100.0

    // Fee Metrics
    val totalFeeAmount = studentFees.sumOf { it.amount }
    val paidFeeAmount = studentFees.filter { it.paidStatus.equals("Paid", ignoreCase = true) }.sumOf { it.amount }
    val pendingFeeAmount = totalFeeAmount - paidFeeAmount

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top Header Card
                Surface(
                    color = TealPrimary,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                schoolName.uppercase(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = Color.White,
                                modifier = Modifier.size(54.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        student.name.take(1).uppercase(),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Black,
                                        color = TealPrimary
                                    )
                                }
                            }

                            Column {
                                Text(
                                    student.name,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    "ID: ${student.studentId} • $className",
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. GENERAL INFORMATION
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.Person, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(18.dp))
                                Text("1. XAALADDA GUUD (GENERAL INFO)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TealDark)
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Jinsiga:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(student.gender, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Hooyada:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(student.motherName.ifBlank { "N/A" }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Taleefanka:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(student.phone.ifBlank { "N/A" }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Xaaladda Waxbarasho:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(if (student.isFree) "Bilaash (Scholarship)" else "Caadi (Standard)", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (student.isFree) Color(0xFFD97706) else Color(0xFF16A34A))
                                }
                            }
                        }
                    }

                    // 2. EXAM & ACADEMIC PERFORMANCE
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.School, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(18.dp))
                                Text("2. NATIIJADA IMTIXAANAADKA (EXAMS)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TealDark)
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    color = TealLight.copy(alpha = 0.3f)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("WADARTA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TealDark)
                                        Text(String.format("%.1f / %.1f", totalScore, totalMax), fontSize = 13.sp, fontWeight = FontWeight.Black, color = TealDark)
                                    }
                                }
                                Surface(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (averageScore >= 50) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("CEL-CELIS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (averageScore >= 50) Color(0xFF15803D) else Color(0xFFB91C1C))
                                        Text(String.format("%.1f%%", averageScore), fontSize = 13.sp, fontWeight = FontWeight.Black, color = if (averageScore >= 50) Color(0xFF15803D) else Color(0xFFB91C1C))
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (studentMarks.isEmpty()) {
                                Text("Weli wax dhibco ah looma diiwaangelin ardaygan.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                studentMarks.take(6).forEach { mark ->
                                    val ex = exams.firstOrNull { it.id == mark.examId }
                                    val subName = ex?.subject?.ifBlank { ex.name } ?: "Exam #${mark.examId}"
                                    val maxM = ex?.totalMarks ?: 100.0
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(subName, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                        Text(
                                            if (mark.isAbsent) "Maqnaa" else "${mark.score.toInt()} / ${maxM.toInt()}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (mark.isAbsent) Color.Red else TealDark
                                        )
                                    }
                                }
                                if (studentMarks.size > 6) {
                                    Text("+ ${studentMarks.size - 6} imtixaan oo kale...", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    // 3. ATTENDANCE STATUS
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.EventAvailable, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(18.dp))
                                Text("3. XAADIRINTA (ATTENDANCE)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TealDark)
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Joogay", fontSize = 10.sp, color = Color(0xFF16A34A), fontWeight = FontWeight.Bold)
                                    Text("$presentCount", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFF16A34A))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Maqnaa", fontSize = 10.sp, color = Color(0xFFDC2626), fontWeight = FontWeight.Bold)
                                    Text("$absentCount", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFFDC2626))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Habsan", fontSize = 10.sp, color = Color(0xFFD97706), fontWeight = FontWeight.Bold)
                                    Text("$lateCount", fontSize = 15.sp, fontWeight = FontWeight.Black, color = Color(0xFFD97706))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Boqolkiiba", fontSize = 10.sp, color = TealDark, fontWeight = FontWeight.Bold)
                                    Text(String.format("%.0f%%", attPercentage), fontSize = 15.sp, fontWeight = FontWeight.Black, color = TealDark)
                                }
                            }
                        }
                    }

                    // 4. FEE PAYMENT SUMMARY
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Default.AttachMoney, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(18.dp))
                                Text("4. BIXINTA LACAGTA (FEES & PAYMENTS)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TealDark)
                            }
                            Spacer(modifier = Modifier.height(10.dp))

                            if (student.isFree) {
                                Surface(
                                    color = Color(0xFFFEF3C7),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFFD97706))
                                        Text("Ardaygan waa bilaash (Free Scholarship) wax lacag ah lagama rabo.", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFB45309))
                                    }
                                }
                            } else {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Wadarta Lagu Yeeshay:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(String.format("$%.2f", totalFeeAmount), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Column {
                                        Text("Bixiyay (Paid):", fontSize = 11.sp, color = Color(0xFF16A34A))
                                        Text(String.format("$%.2f", paidFeeAmount), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF16A34A))
                                    }
                                    Column {
                                        Text("Haraaga (Balance):", fontSize = 11.sp, color = if (pendingFeeAmount > 0) Color(0xFFDC2626) else Color(0xFF16A34A))
                                        Text(String.format("$%.2f", pendingFeeAmount), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = if (pendingFeeAmount > 0) Color(0xFFDC2626) else Color(0xFF16A34A))
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom Action Buttons
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Xidh (Close)")
                        }

                        Button(
                            onClick = onPrint,
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("🖨️ Daabac Warbixinta", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
