package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    schoolName: String,
    onSaveSchoolName: (String) -> Unit,
    classes: List<SchoolClass>,
    students: List<Student>,
    users: List<User> = emptyList(),
    exams: List<Exam>,
    fees: List<FeeRecord>,
    attendance: List<AttendanceRecord>,
    classroomsCount: Int = 12,
    chairsCount: Int = 350,
    toiletsCount: Int = 10,
    officesCount: Int = 4,
    kitchenFeedingCount: Int = 1,
    onUpdateFacilities: (classrooms: Int, chairs: Int, toilets: Int, offices: Int, kitchenFeeding: Int) -> Unit = { _, _, _, _, _ -> },
    onPrintSchoolOverviewReport: () -> Unit = {},
    onExportExamCsv: () -> Unit,
    onExportFeeCsv: () -> Unit,
    onPrintSingleStudentReport: (Student) -> Unit,
    onPrintAllStudentsReportCards: (Long) -> Unit = {},
    onPrintFeeReport: () -> Unit,
    onPrintClassReport: (Long) -> Unit,
    onPrintAttendanceReport: (Long, String?) -> Unit,
    onPrintStudentAttendanceReport: (Student, String, String?) -> Unit = { _, _, _ -> },
    onPrintExamReport: (Long) -> Unit,
    onOpenClearanceClick: () -> Unit = {},
    onBackClick: () -> Unit
) {
    var schoolNameInput by remember(schoolName) { mutableStateOf(schoolName) }
    var selectedStudentId by remember(students) { mutableStateOf<Long?>(students.firstOrNull()?.id) }
    var selectedClassReportId by remember { mutableLongStateOf(0L) }
    var selectedAttMonthFilter by remember { mutableStateOf<String?>(null) }
    var selectedAttStudentId by remember(students) { mutableStateOf<Long?>(students.firstOrNull()?.id) }
    var selectedAttReportMode by remember { mutableStateOf("MONTH") } // "MONTH" or "TERM"
    var selectedAttSingleMonth by remember { mutableStateOf<String?>(null) }
    var showFacilityDialog by remember { mutableStateOf(false) }

    val totalStudents = students.size
    val totalClasses = classes.size

    val boysCount = students.count { it.gender.equals("Male", ignoreCase = true) || it.gender.equals("Wiil", ignoreCase = true) }
    val girlsCount = students.count { it.gender.equals("Female", ignoreCase = true) || it.gender.equals("Gabdho", ignoreCase = true) || it.gender.equals("Gabdhaha", ignoreCase = true) }
    val boysPct = if (totalStudents > 0) (boysCount.toFloat() / totalStudents.toFloat()) else 0.5f
    val girlsPct = if (totalStudents > 0) (girlsCount.toFloat() / totalStudents.toFloat()) else 0.5f

    val totalTeachers = users.count { it.role.equals("TEACHER", ignoreCase = true) || it.role.equals("ADMIN", ignoreCase = true) }.coerceAtLeast(1)

    if (showFacilityDialog) {
        var clsInput by remember { mutableStateOf(classroomsCount.toString()) }
        var chrInput by remember { mutableStateOf(chairsCount.toString()) }
        var tltInput by remember { mutableStateOf(toiletsCount.toString()) }
        var offInput by remember { mutableStateOf(officesCount.toString()) }
        var ktcInput by remember { mutableStateOf(kitchenFeedingCount.toString()) }

        AlertDialog(
            onDismissRequest = { showFacilityDialog = false },
            title = { Text("✏️ Bedel Agabka Dugsiga (Facilities)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = clsInput, onValueChange = { clsInput = it }, label = { Text("Fasalada (Classrooms)") }, singleLine = true)
                    OutlinedTextField(value = chrInput, onValueChange = { chrInput = it }, label = { Text("Kuraasta (Chairs)") }, singleLine = true)
                    OutlinedTextField(value = tltInput, onValueChange = { tltInput = it }, label = { Text("Musqulaha (Toilets)") }, singleLine = true)
                    OutlinedTextField(value = offInput, onValueChange = { offInput = it }, label = { Text("Xafiisyada (Offices)") }, singleLine = true)
                    OutlinedTextField(value = ktcInput, onValueChange = { ktcInput = it }, label = { Text("Jikada Cuntada (Kitchen Feeding)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateFacilities(
                            clsInput.toIntOrNull() ?: classroomsCount,
                            chrInput.toIntOrNull() ?: chairsCount,
                            tltInput.toIntOrNull() ?: toiletsCount,
                            offInput.toIntOrNull() ?: officesCount,
                            ktcInput.toIntOrNull() ?: kitchenFeedingCount
                        )
                        showFacilityDialog = false
                    }
                ) {
                    Text("KAYDI (SAVE)")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFacilityDialog = false }) {
                    Text("KA NOQ")
                }
            }
        )
    }

    val attPresent = attendance.count { it.status == "Present" }
    val attTotal = attendance.size
    val attRate = if (attTotal > 0) (attPresent.toDouble() / attTotal.toDouble()) * 100 else 100.0

    // Fees Totals
    val usdPaid = fees.filter { it.currency == "USD" && it.paidStatus == "Paid" }.sumOf { it.amount }
    val usdPending = fees.filter { it.currency == "USD" && it.paidStatus != "Paid" }.sumOf { it.amount }

    val slsPaid = fees.filter { it.currency == "SLS" && it.paidStatus == "Paid" }.sumOf { it.amount }
    val slsPending = fees.filter { it.currency == "SLS" && it.paidStatus != "Paid" }.sumOf { it.amount }

    val etbPaid = fees.filter { it.currency == "ETB" && it.paidStatus == "Paid" }.sumOf { it.amount }
    val etbPending = fees.filter { it.currency == "ETB" && it.paidStatus != "Paid" }.sumOf { it.amount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📊 Printable Reports & Analytics", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TealPrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // --- School Name Settings Header Card ---
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🏫 Magaca Dugsiga / School Header Name", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                        Text(
                            "Magacan wuxuu ku qormayaa dhammaan warbixinada (report sheets) aad degsato ama print garayso:",
                            fontSize = 11.sp,
                            color = MutedText
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = schoolNameInput,
                                onValueChange = { schoolNameInput = it },
                                label = { Text("Magaca Dugsiga (School Name)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )

                            Button(
                                onClick = { onSaveSchoolName(schoolNameInput) },
                                colors = ButtonDefaults.buttonColors(containerColor = TealDark),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.height(54.dp)
                            ) {
                                Text("SAVE", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Overall Executive Summary Cards
            item {
                Text("📈 Executive Summary", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = TealContainer)) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$totalStudents", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TealDark)
                            Text("Total Students", fontSize = 10.sp, color = DarkText)
                        }
                    }
                    Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = GoldContainer)) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$totalClasses", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = DarkText)
                            Text("Classes", fontSize = 10.sp, color = DarkText)
                        }
                    }
                    Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(String.format("%.0f%%", attRate), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = PassGreen)
                            Text("Attendance Rate", fontSize = 10.sp, color = DarkText)
                        }
                    }
                }
            }

            // --- School Overview Report: Assets, Teachers & Boys/Girls Chart ---
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🏫 Warbixinta Dugsiga (Facilities, Staff & Gender Chart)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TealPrimary
                            )
                            IconButton(onClick = { showFacilityDialog = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Facilities", tint = TealPrimary)
                            }
                        }

                        // 1. Facilities Grid
                        Text("1. Agabka Dugsiga (Facilities & Assets):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = TealContainer)) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${classroomsCount.coerceAtLeast(classes.size)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TealDark)
                                    Text("Fasalada", fontSize = 9.sp, color = DarkText)
                                }
                            }
                            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = GoldContainer)) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$chairsCount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                    Text("Kuraasta", fontSize = 9.sp, color = DarkText)
                                }
                            }
                            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$toiletsCount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TealDark)
                                    Text("Musqulaha", fontSize = 9.sp, color = DarkText)
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$officesCount", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                    Text("Office", fontSize = 9.sp, color = DarkText)
                                }
                            }
                            Card(modifier = Modifier.weight(2f), colors = CardDefaults.cardColors(containerColor = TealContainer)) {
                                Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("$kitchenFeedingCount Jiko", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TealDark)
                                    Text("Kitchen Feeding Program", fontSize = 9.sp, color = DarkText)
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                        // 2. Teachers Summary
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("2. Macalimiinta Dugsiga (Teachers):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkText)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = TealPrimary.copy(alpha = 0.1f)
                            ) {
                                Text("👨‍🏫 $totalTeachers Macalin", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                        // 3. Boys & Girls Graph
                        Text("3. Garaafka Wiilasha & Gabdhaha (Boys & Girls Graph):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(10.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("👦 Wiilasha (Boys): $boysCount (${String.format(java.util.Locale.US, "%.1f", boysPct * 100)}%)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                                Text("👧 Gabdhaha (Girls): $girlsCount (${String.format(java.util.Locale.US, "%.1f", girlsPct * 100)}%)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFD946EF))
                            }

                            // Visual Stacked Progress Bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(20.dp)
                                    .background(Color.LightGray.copy(alpha = 0.3f), shape = RoundedCornerShape(10.dp))
                            ) {
                                if (boysPct > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .weight(boysPct.coerceAtLeast(0.01f))
                                            .background(Color(0xFF0284C7), shape = RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                                    )
                                }
                                if (girlsPct > 0f) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .weight(girlsPct.coerceAtLeast(0.01f))
                                            .background(Color(0xFFD946EF), shape = RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                                    )
                                }
                            }

                            Text("Wadarta Guud ee Ardayda: $totalStudents arday", fontSize = 10.sp, color = MutedText, modifier = Modifier.align(Alignment.CenterHorizontally))
                        }

                        // Print School Report Button
                        Button(
                            onClick = onPrintSchoolOverviewReport,
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("🖨️ DAABAC WARBIXINTA GUUD EE DUGSIGA (PDF)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // --- 1. Individual Student Report Print ---
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("👤 1. Student Individual Report / Warbixinta Ardayga", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                        Text("Choose an individual student to generate & print their full official report card statement:", fontSize = 11.sp, color = MutedText)

                        if (students.isEmpty()) {
                            Text("No students registered yet.", fontSize = 11.sp, color = FailRed)
                        } else {
                            var expanded by remember { mutableStateOf(false) }
                            val currentStudent = students.find { it.id == selectedStudentId } ?: students.first()

                            ExposedDropdownMenuBox(
                                expanded = expanded,
                                onExpandedChange = { expanded = !expanded },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = "${currentStudent.name} (${currentStudent.studentId})",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Select Student") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth()
                                )
                                ExposedDropdownMenu(
                                    expanded = expanded,
                                    onDismissRequest = { expanded = false }
                                ) {
                                    students.forEach { st ->
                                        DropdownMenuItem(
                                            text = { Text("${st.name} • ${st.studentId}", fontSize = 12.sp) },
                                            onClick = {
                                                selectedStudentId = st.id
                                                expanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = {
                                        val st = students.find { it.id == selectedStudentId } ?: students.firstOrNull()
                                        if (st != null) onPrintSingleStudentReport(st)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("🖨️ Keli (Single)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        val st = students.find { it.id == selectedStudentId } ?: students.firstOrNull()
                                        val classId = st?.classId ?: selectedClassReportId
                                        onPrintAllStudentsReportCards(classId)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = TealDark),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("📚 Fasalka (All PDF)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            OutlinedButton(
                                onClick = onOpenClearanceClick,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("📄 SAXEE / DAABAC WARQADDA ARDAYGA", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TealDark)
                            }
                        }
                    }
                }
            }

            // --- 2. Class, Attendance & Exam Reports ---
            item {
                val availableAttMonths = remember(attendance) {
                    val monthsFromData = attendance.map { it.date.take(7) }.filter { it.length == 7 }.distinct()
                    val currentM = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
                    (monthsFromData + currentM).distinct().sortedDescending()
                }

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("🏫 2. Class, Attendance & Exam Reports", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TealPrimary)

                        Text("1. Dooro Fasalka (Select Class):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = selectedClassReportId == 0L,
                                onClick = { selectedClassReportId = 0L },
                                label = { Text("All Classes", fontSize = 11.sp) }
                            )
                            classes.forEach { cls ->
                                FilterChip(
                                    selected = selectedClassReportId == cls.id,
                                    onClick = { selectedClassReportId = cls.id },
                                    label = { Text(cls.name, fontSize = 11.sp) }
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                        Text("2. Dooro Bisha/Xilliga Xaadirinta (Attendance Month Filter):", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = DarkText)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = selectedAttMonthFilter == null,
                                onClick = { selectedAttMonthFilter = null },
                                label = { Text("Dhamaan (All Time)", fontSize = 11.sp) }
                            )
                            availableAttMonths.forEach { mStr ->
                                FilterChip(
                                    selected = selectedAttMonthFilter == mStr,
                                    onClick = { selectedAttMonthFilter = mStr },
                                    label = { Text("Bisha $mStr", fontSize = 11.sp) }
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = { onPrintClassReport(selectedClassReportId) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("🖨️ Print Class Roster Report", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onPrintAttendanceReport(selectedClassReportId, selectedAttMonthFilter) },
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            val mText = if (selectedAttMonthFilter != null) "Bisha $selectedAttMonthFilter" else "Dhamaan Xilliyada"
                            Text("🖨️ DAABAC WARBIXINTA XAADIRINTA & WAKHTIGA MACALINKU XAADIRIYAY ($mText)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Text(
                            "ℹ️ Warbixintani waxay muujinaysaa wakhtiga saxda ah ee macalinku xaadiriyay ardayda iyo haday ku dhex jirtay saacadaha shaqada (08:00-12:00 / 14:00-16:30) ama ka baxsan.",
                            fontSize = 10.sp,
                            color = MutedText
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        // --- Sub-section: Single Student Attendance Report (Month & Term) ---
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("👤 Warbixinta Xaadirinta ee Ardayga Keliya (Single Student Attendance):", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TealDark)

                                val attStudent = students.find { it.id == selectedAttStudentId } ?: students.firstOrNull()
                                var studentPickerExpanded by remember { mutableStateOf(false) }

                                if (students.isNotEmpty() && attStudent != null) {
                                    ExposedDropdownMenuBox(
                                        expanded = studentPickerExpanded,
                                        onExpandedChange = { studentPickerExpanded = !studentPickerExpanded },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        OutlinedTextField(
                                            value = "${attStudent.name} (${attStudent.studentId})",
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Dooro Ardayga", fontSize = 11.sp) },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = studentPickerExpanded) },
                                            modifier = Modifier.menuAnchor().fillMaxWidth()
                                        )
                                        ExposedDropdownMenu(
                                            expanded = studentPickerExpanded,
                                            onDismissRequest = { studentPickerExpanded = false }
                                        ) {
                                            students.forEach { st ->
                                                DropdownMenuItem(
                                                    text = { Text("${st.name} • ${st.studentId}", fontSize = 11.5.sp) },
                                                    onClick = {
                                                        selectedAttStudentId = st.id
                                                        studentPickerExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }

                                    // Mode Selector: MONTH vs TERM
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        FilterChip(
                                            selected = selectedAttReportMode == "MONTH",
                                            onClick = { selectedAttReportMode = "MONTH" },
                                            label = { Text("📅 Bisha (Monthly)", fontSize = 11.sp) }
                                        )
                                        FilterChip(
                                            selected = selectedAttReportMode == "TERM",
                                            onClick = { selectedAttReportMode = "TERM" },
                                            label = { Text("🎓 Teeramka (Term / Full)", fontSize = 11.sp) }
                                        )
                                    }

                                    // If MONTH selected, show month selector
                                    if (selectedAttReportMode == "MONTH") {
                                        val currentM = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
                                        val monthsList = (availableAttMonths + currentM).distinct().sortedDescending()
                                        var monthMenuOpen by remember { mutableStateOf(false) }

                                        ExposedDropdownMenuBox(
                                            expanded = monthMenuOpen,
                                            onExpandedChange = { monthMenuOpen = !monthMenuOpen },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            OutlinedTextField(
                                                value = "Bisha: ${selectedAttSingleMonth ?: currentM}",
                                                onValueChange = {},
                                                readOnly = true,
                                                label = { Text("Dooro Bisha", fontSize = 11.sp) },
                                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = monthMenuOpen) },
                                                modifier = Modifier.menuAnchor().fillMaxWidth()
                                            )
                                            ExposedDropdownMenu(
                                                expanded = monthMenuOpen,
                                                onDismissRequest = { monthMenuOpen = false }
                                            ) {
                                                monthsList.forEach { m ->
                                                    DropdownMenuItem(
                                                        text = { Text(m, fontSize = 11.5.sp) },
                                                        onClick = {
                                                            selectedAttSingleMonth = m
                                                            monthMenuOpen = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            val currentM = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
                                            onPrintStudentAttendanceReport(
                                                attStudent,
                                                selectedAttReportMode,
                                                if (selectedAttReportMode == "MONTH") (selectedAttSingleMonth ?: currentM) else null
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        val modeDesc = if (selectedAttReportMode == "MONTH") "Bisha" else "Teeramka"
                                        Text("🖨️ DAABAC XAADIRINTA ARDAYGA ($modeDesc - PDF)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                        OutlinedButton(
                            onClick = { onPrintExamReport(selectedClassReportId) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("🖨️ Print Class Exam Results Report", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { onPrintAllStudentsReportCards(selectedClassReportId) },
                            colors = ButtonDefaults.buttonColors(containerColor = TealDark),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            val cName = classes.find { it.id == selectedClassReportId }?.name ?: "Dhamaan Fasalada (All)"
                            Text("📚 DAABAC DHAMAAN WARBIXIN-SANADEEDKA FASALKA ($cName)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // --- 3. Fee Collection Report & CSV Exports ---
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("💰 3. Financial Fee Collections & CSV Exports", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TealPrimary)

                        // USD / SLS / ETB Breakdown
                        Column(modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp)).padding(10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("USD ($):", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("Paid: $${String.format("%.0f", usdPaid)} | Pending: $${String.format("%.0f", usdPending)}", fontSize = 11.sp)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("SLS (Sh):", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("Paid: ${String.format("%.0f", slsPaid)} | Pending: ${String.format("%.0f", slsPending)}", fontSize = 11.sp)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("ETB (Birr):", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("Paid: ${String.format("%.0f", etbPaid)} Br | Pending: ${String.format("%.0f", etbPending)} Br", fontSize = 11.sp)
                            }
                        }

                        Button(
                            onClick = onPrintFeeReport,
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("🖨️ PRINT FULL FEE COLLECTION REPORT", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = onExportExamCsv,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Exams CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = onExportFeeCsv,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Fees CSV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
