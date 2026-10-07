package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.AttendanceRecord
import com.example.data.SchoolClass
import com.example.data.Student
import com.example.data.User
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceScreen(
    classes: List<SchoolClass>,
    students: List<Student>,
    existingAttendance: List<AttendanceRecord>,
    currentUser: User? = null,
    onSaveAttendanceClick: (Long, String, List<AttendanceRecord>) -> Unit,
    onPrintMonthlySheet: (Long, String) -> Unit = { _, _ -> },
    onExportMonthlyCsv: (Long, String) -> Unit = { _, _ -> },
    onPrintStudentLateWarning: (Student, List<AttendanceRecord>, String) -> Unit = { _, _, _ -> },
    onPrintClassLateReport: (Long, String?) -> Unit = { _, _ -> },
    onExportClassLateReportCsv: (Long, String?) -> Unit = { _, _ -> },
    onDeleteAttendanceLog: (Long, String) -> Unit = { _, _ -> },
    onBackClick: () -> Unit
) {
    val accessibleClasses: List<SchoolClass> = remember(classes, currentUser) {
        if (currentUser == null || currentUser.role == "ADMIN" || currentUser.role == "CASHIER") {
            classes
        } else {
            val assigned = currentUser.getAssignedClassIdSet()
            classes.filter { it.id in assigned }
        }
    }

    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Daily Live, 1 = Monthly Sheet, 2 = Student Late & Absent Tracker

    val todayCal = remember { Calendar.getInstance() }
    val todayStr = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(todayCal.time) }
    val todayReadable = remember {
        SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(todayCal.time)
    }

    var selectedClassId by remember(accessibleClasses) {
        mutableLongStateOf(accessibleClasses.firstOrNull()?.id ?: 0L)
    }

    val selectedClassObj = remember(accessibleClasses, selectedClassId) {
        accessibleClasses.find { it.id == selectedClassId }
    }
    val currentClassName = selectedClassObj?.name ?: "All Classes"

    // Attendance Log Delete State: Triple(classId, dateString, className)
    var logToDelete by remember { mutableStateOf<Triple<Long, String, String>?>(null) }

    // Monthly Calendar State
    var currentMonthCal by remember {
        mutableStateOf(Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) })
    }
    val selectedYearMonth = remember(currentMonthCal) {
        SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(currentMonthCal.time)
    }
    val selectedMonthReadable = remember(currentMonthCal) {
        SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(currentMonthCal.time)
    }

    // Selected historical date for READ-ONLY inspection in Daily Tab
    var viewingPastDate by remember { mutableStateOf<String?>(null) }
    val activeDate = viewingPastDate ?: todayStr
    val isViewingToday = activeDate == todayStr

    val classStudents = remember(students, selectedClassId) {
        if (selectedClassId == 0L) students else students.filter { it.classId == selectedClassId }
    }

    // Historical distinct dates for this class to allow review
    val availableDatesForClass = remember(existingAttendance, selectedClassId) {
        existingAttendance
            .filter { selectedClassId == 0L || it.classId == selectedClassId }
            .map { it.date }
            .distinct()
            .sortedDescending()
    }

    // Map of studentId -> status ("Present", "Absent", "Late", "Free") for Daily Attendance
    val attendanceMap = remember(selectedClassId, activeDate, existingAttendance, classStudents) {
        val map = mutableStateMapOf<Long, String>()
        classStudents.forEach { s ->
            val rec = existingAttendance.find { it.studentId == s.id && it.date == activeDate }
            map[s.id] = rec?.status ?: if (s.isFree) "Free" else "Present"
        }
        map
    }

    val isAlreadyMarkedToday = remember(existingAttendance, selectedClassId, todayStr) {
        existingAttendance.any { it.classId == selectedClassId && it.date == todayStr }
    }

    val presentCount = attendanceMap.values.count { it == "Present" || it == "P" }
    val absentCount = attendanceMap.values.count { it == "Absent" || it == "A" }
    val lateCount = attendanceMap.values.count { it == "Late" || it == "Habsan" || it == "H" }
    val freeCount = attendanceMap.values.count { it == "Free" || it == "F" }

    // Monthly Sheet Calculations
    val monthAttendanceRecords = remember(existingAttendance, selectedClassId, selectedYearMonth) {
        existingAttendance.filter {
            it.date.startsWith(selectedYearMonth) && (selectedClassId == 0L || it.classId == selectedClassId)
        }
    }

    val distinctMonthDates = remember(monthAttendanceRecords) {
        monthAttendanceRecords.map { it.date }.distinct().sorted()
    }

    var monthlyViewMode by remember { mutableIntStateOf(0) } // 0 = Table Matrix Grid, 1 = Student Summary List

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = when (selectedTab) {
                                0 -> "Xaadirinta Maalinlaha ah"
                                1 -> "Xaashida Bisha (Monthly Sheet)"
                                else -> "Habsanka & Maqnaanshaha Ardayda"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = when (selectedTab) {
                                0 -> if (isViewingToday) "Maanta: $todayReadable" else "Taariikh Hore: $activeDate (Read-Only)"
                                1 -> "Bisha: $selectedMonthReadable • ${classStudents.size} Arday"
                                else -> "$currentClassName • Kormeerka Habsanka & Xaadirinta"
                            },
                            fontSize = 11.sp,
                            color = if (isViewingToday || selectedTab != 0) Color.White.copy(alpha = 0.9f) else Color(0xFFFFD54F)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    when (selectedTab) {
                        0 -> {
                            val hasRecordsForActiveDate = existingAttendance.any { it.classId == selectedClassId && it.date == activeDate }
                            if (hasRecordsForActiveDate) {
                                IconButton(
                                    onClick = {
                                        logToDelete = Triple(selectedClassId, activeDate, currentClassName)
                                    }
                                ) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = "Tirtir Diiwaankan", tint = Color.White)
                                }
                            }

                            if (isViewingToday) {
                                IconButton(
                                    onClick = {
                                        val records = classStudents.map { s ->
                                            AttendanceRecord(
                                                classId = selectedClassId,
                                                studentId = s.id,
                                                date = todayStr,
                                                status = attendanceMap[s.id] ?: "Present"
                                            )
                                        }
                                        onSaveAttendanceClick(selectedClassId, todayStr, records)
                                    }
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = "Save Attendance", tint = Color.White)
                                }
                            } else {
                                IconButton(onClick = { viewingPastDate = null }) {
                                    Icon(Icons.Default.Today, contentDescription = "Back to Today", tint = Color.White)
                                }
                            }
                        }
                        1 -> {
                            IconButton(onClick = { onPrintMonthlySheet(selectedClassId, selectedYearMonth) }) {
                                Icon(Icons.Default.Print, contentDescription = "Print Monthly Sheet", tint = Color.White)
                            }
                            IconButton(onClick = { onExportMonthlyCsv(selectedClassId, selectedYearMonth) }) {
                                Icon(Icons.Default.FileDownload, contentDescription = "Export CSV", tint = Color.White)
                            }
                        }
                        2 -> {
                            IconButton(onClick = { onPrintClassLateReport(selectedClassId, null) }) {
                                Icon(Icons.Default.Print, contentDescription = "Print Late Report", tint = Color.White)
                            }
                            IconButton(onClick = { onExportClassLateReportCsv(selectedClassId, null) }) {
                                Icon(Icons.Default.FileDownload, contentDescription = "Export CSV", tint = Color.White)
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TealPrimary)
            )
        },
        bottomBar = {
            if (selectedTab == 0) {
                Surface(
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Ardayda: ${classStudents.size}  •  🟢 $presentCount  🔴 $absentCount  🟠 $lateCount",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkText
                            )
                            Text(
                                text = if (isAlreadyMarkedToday) "✓ Maanta waa la keydiyay (Hal mar)" else "● Diyaar u ah in la keydiyo",
                                fontSize = 10.sp,
                                color = if (isAlreadyMarkedToday) PassGreen else MutedText,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (isViewingToday) {
                            Button(
                                onClick = {
                                    val records = classStudents.map { s ->
                                        AttendanceRecord(
                                            classId = selectedClassId,
                                            studentId = s.id,
                                            date = todayStr,
                                            status = attendanceMap[s.id] ?: "Present"
                                        )
                                    }
                                    onSaveAttendanceClick(selectedClassId, todayStr, records)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    if (isAlreadyMarkedToday) "Cusboonaysii (Update)" else "Kaydi Xaadirinta",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = { viewingPastDate = null },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ku Noqo Maanta", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else if (selectedTab == 1) {
                Surface(
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onPrintMonthlySheet(selectedClassId, selectedYearMonth) },
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Daabac Xaashida Bisha", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { onExportMonthlyCsv(selectedClassId, selectedYearMonth) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                Surface(
                    shadowElevation = 8.dp,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { onPrintClassLateReport(selectedClassId, null) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Daabac Warbixinta Habsanka", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        OutlinedButton(
                            onClick = { onExportClassLateReportCsv(selectedClassId, null) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(0.8f)
                        ) {
                            Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export CSV", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Segmented 3-Tab Switcher (Daily Live vs Monthly Sheet vs Student Late & Absent Tracker)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = TealPrimary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Today, contentDescription = null, modifier = Modifier.size(15.dp))
                            Text("Maanta (Live)", fontSize = 11.5.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(15.dp))
                            Text("Xaashida Bisha", fontSize = 11.5.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(15.dp), tint = if (selectedTab == 2) Color(0xFFB45309) else Color.Unspecified)
                            Text("⏰ Habsanka", fontSize = 11.5.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal, color = if (selectedTab == 2) Color(0xFFB45309) else Color.Unspecified)
                        }
                    }
                )
            }

            when (selectedTab) {
                0 -> {
                    // DAILY ATTENDANCE TAB
                    DailyAttendanceContent(
                        todayReadable = todayReadable,
                        todayStr = todayStr,
                        isViewingToday = isViewingToday,
                        isAlreadyMarkedToday = isAlreadyMarkedToday,
                        accessibleClasses = accessibleClasses,
                        selectedClassId = selectedClassId,
                        onClassSelect = { selectedClassId = it },
                        availableDatesForClass = availableDatesForClass,
                        viewingPastDate = viewingPastDate,
                        onPastDateSelect = { viewingPastDate = it },
                        onDeleteDateLog = { date ->
                            logToDelete = Triple(selectedClassId, date, currentClassName)
                        },
                        classStudents = classStudents,
                        attendanceMap = attendanceMap,
                        existingAttendance = existingAttendance
                    )
                }
                1 -> {
                    // MONTHLY ATTENDANCE SHEET TAB
                    MonthlyAttendanceSheetContent(
                        accessibleClasses = accessibleClasses,
                        selectedClassId = selectedClassId,
                        onClassSelect = { selectedClassId = it },
                        selectedMonthReadable = selectedMonthReadable,
                        selectedYearMonth = selectedYearMonth,
                        onPrevMonth = {
                            val newCal = (currentMonthCal.clone() as Calendar).apply {
                                add(Calendar.MONTH, -1)
                            }
                            currentMonthCal = newCal
                        },
                        onNextMonth = {
                            val newCal = (currentMonthCal.clone() as Calendar).apply {
                                add(Calendar.MONTH, 1)
                            }
                            currentMonthCal = newCal
                        },
                        classStudents = classStudents,
                        distinctMonthDates = distinctMonthDates,
                        monthAttendanceRecords = monthAttendanceRecords,
                        monthlyViewMode = monthlyViewMode,
                        onToggleViewMode = { monthlyViewMode = it },
                        onDeleteDateLog = { date ->
                            logToDelete = Triple(selectedClassId, date, currentClassName)
                        },
                        onPrintMonthlySheet = { onPrintMonthlySheet(selectedClassId, selectedYearMonth) },
                        onExportMonthlyCsv = { onExportMonthlyCsv(selectedClassId, selectedYearMonth) }
                    )
                }
                2 -> {
                    // STUDENT TARDINESS & ABSENCES TRACKER TAB (HABSAN)
                    StudentLateTrackerContent(
                        accessibleClasses = accessibleClasses,
                        selectedClassId = selectedClassId,
                        onClassSelect = { selectedClassId = it },
                        allStudents = students,
                        existingAttendance = existingAttendance,
                        onPrintWarning = { student, sRecords, clsName ->
                            onPrintStudentLateWarning(student, sRecords, clsName)
                        },
                        onPrintClassReport = { cId ->
                            onPrintClassLateReport(cId, null)
                        },
                        onExportCsv = { cId ->
                            onExportClassLateReportCsv(cId, null)
                        }
                    )
                }
            }
        }
    }

    // Delete Confirmation Dialog for Attendance Log (Prevents Accidental Data Loss)
    if (logToDelete != null) {
        val (cId, dStr, cName) = logToDelete!!
        val recordsCount = existingAttendance.count { it.classId == cId && it.date == dStr }

        AlertDialog(
            onDismissRequest = { logToDelete = null },
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
                    text = "Tirtir Xaadirinta Maalinta?",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkText,
                    textAlign = TextAlign.Center
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
                            Text("🏫 Fasalka: $cName", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkText)
                            Text("📅 Taariikhda: $dStr", fontSize = 12.sp, color = TealDark, fontWeight = FontWeight.SemiBold)
                            Text("👥 Diiwaanka la tirtirayo: $recordsCount diiwaan", fontSize = 12.sp, color = MutedText)
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
                                text = "Digniin: Xogta xaadirinta ee fasalka '$cName' maalinta '$dStr' waxaa si rasmi ah looga tirtirayaa database-ka. Tallaabadan dib looma noqon karo (Irreversible) si looga fogaado khalad ama data duplication.",
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
                        onDeleteAttendanceLog(cId, dStr)
                        if (viewingPastDate == dStr) viewingPastDate = null
                        logToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = FailRed),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Haa, Tirtir Diiwaanka", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { logToDelete = null },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Jooji (Cancel)", color = DarkText, fontSize = 12.sp)
                }
            }
        )
    }
}

@Composable
private fun DailyAttendanceContent(
    todayReadable: String,
    todayStr: String,
    isViewingToday: Boolean,
    isAlreadyMarkedToday: Boolean,
    accessibleClasses: List<SchoolClass>,
    selectedClassId: Long,
    onClassSelect: (Long) -> Unit,
    availableDatesForClass: List<String>,
    viewingPastDate: String?,
    onPastDateSelect: (String?) -> Unit,
    onDeleteDateLog: (String) -> Unit = {},
    classStudents: List<Student>,
    attendanceMap: MutableMap<Long, String>,
    existingAttendance: List<AttendanceRecord> = emptyList()
) {
    val activeDateStr = if (isViewingToday) todayStr else viewingPastDate ?: todayStr
    val currentClassRecords = remember(existingAttendance, selectedClassId, activeDateStr) {
        existingAttendance.filter { it.classId == selectedClassId && it.date == activeDateStr }
    }
    val recordedTimeStr = remember(currentClassRecords) {
        currentClassRecords.firstOrNull { it.recordedAt.isNotBlank() }?.recordedAt ?: ""
    }
    val recordedByStr = remember(currentClassRecords) {
        currentClassRecords.firstOrNull { it.recordedBy.isNotBlank() }?.recordedBy ?: ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Auto Date Header & Security Status Banner
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isViewingToday) TealContainer else Color(0xFFFFFBEB)
            ),
            border = if (!isViewingToday) BorderStroke(1.dp, Color(0xFFF59E0B)) else null,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            imageVector = if (isViewingToday) Icons.Default.CalendarToday else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (isViewingToday) TealDark else Color(0xFFB45309),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isViewingToday) "Auto Date (Maanta oo Keliya)" else "Taariikh Hore (Locked)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isViewingToday) TealDark else Color(0xFFB45309)
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Surface(
                            color = if (isViewingToday) (if (isAlreadyMarkedToday) PassGreen else TealPrimary) else Color(0xFFB45309),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = if (isViewingToday) (if (isAlreadyMarkedToday) "✓ Maanta La Xaadiriyay" else "● Diyaar u ah Xaadirin") else "🔒 Lama Edit-garayn karo",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        if ((isViewingToday && isAlreadyMarkedToday) || !isViewingToday) {
                            Surface(
                                color = FailRed.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.clickable {
                                    onDeleteDateLog(if (isViewingToday) todayStr else viewingPastDate ?: todayStr)
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Icon(
                                        Icons.Default.DeleteOutline,
                                        contentDescription = "Delete Log",
                                        tint = FailRed,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        "Tirtir Xaadirintan",
                                        fontSize = 9.5.sp,
                                        color = FailRed,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = if (isViewingToday)
                        "Taabo calaamadda ardayga si aad u bedesho: 🟢 P (Jooga) ➔ 🔴 A (Maqan) ➔ 🟠 H (Habsan) ➔ 🔵 F (Fasax)."
                    else
                        "Maalmihii hore waa la xidhay (Locked). Waxaa kaliya oo loo geli karaa in lagu arko xogta (View Only) balse lama beddeli karo.",
                    fontSize = 10.5.sp,
                    color = if (isViewingToday) TealDark.copy(alpha = 0.85f) else Color(0xFF92400E)
                )

                if (recordedTimeStr.isNotBlank() || recordedByStr.isNotBlank() || (isViewingToday && isAlreadyMarkedToday)) {
                    val timeToShow = if (recordedTimeStr.isNotBlank()) recordedTimeStr else "08:15:00 AM"
                    val isOutside = isTimeOutsideWorkingHours(timeToShow)
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isOutside) Color(0xFFFFF7ED) else Color(0xFFF0FDF4),
                        border = BorderStroke(1.dp, if (isOutside) Color(0xFFFB923C) else Color(0xFF86EFAC)),
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "⏰ Saacadda La Xaadiriyay: $timeToShow",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOutside) Color(0xFFC2410C) else Color(0xFF15803D)
                                )
                                if (recordedByStr.isNotBlank()) {
                                    Text(
                                        text = "• 👨‍🏫 $recordedByStr",
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DarkText
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isOutside) Color(0xFFEA580C) else Color(0xFF16A34A)
                            ) {
                                Text(
                                    text = if (isOutside) "⚠️ Ka baxsan Shaqada" else "✅ Xilliga Shaqada",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }

                // Class Selector Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    items(accessibleClasses) { cls ->
                        FilterChip(
                            selected = selectedClassId == cls.id,
                            onClick = { onClassSelect(cls.id) },
                            label = {
                                Text(
                                    cls.name,
                                    fontSize = 11.sp,
                                    fontWeight = if (selectedClassId == cls.id) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }
        }

        // History Log Quick Tabs (Inspect Past Days - Read Only)
        if (availableDatesForClass.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Diiwaankii Hore:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MutedText)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    item {
                        Surface(
                            color = if (isViewingToday) TealPrimary else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            border = if (!isViewingToday) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                            modifier = Modifier.clickable { onPastDateSelect(null) }
                        ) {
                            Text(
                                text = "Maanta ($todayStr)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isViewingToday) Color.White else DarkText,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                    items(availableDatesForClass.filter { it != todayStr }) { pDate ->
                        val isSelected = viewingPastDate == pDate
                        Surface(
                            color = if (isSelected) Color(0xFFB45309) else MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(12.dp),
                            border = if (!isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null,
                            modifier = Modifier.clickable { onPastDateSelect(pDate) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    modifier = Modifier.size(10.dp),
                                    tint = if (isSelected) Color.White else MutedText
                                )
                                Text(
                                    text = pDate,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else DarkText
                                )
                            }
                        }
                    }
                }
            }
        }

        // Student Attendance List
        if (classStudents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Fasalkan arday kuma jirto. Fadlan xulo fasal kale ama arday ku dar.",
                    color = MutedText,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(classStudents, key = { it.id }) { student ->
                    val currentStatus = attendanceMap[student.id] ?: if (student.isFree) "Free" else "Present"

                    // 4-Way cycle on tap: Present (P) -> Absent (A) -> Late (H) -> Free (F) -> Present (P)
                    val nextStatus = when (currentStatus) {
                        "Present", "P" -> "Absent"
                        "Absent", "A" -> "Late"
                        "Late", "Habsan", "H" -> "Free"
                        else -> "Present"
                    }

                    val badgeColor = when (currentStatus) {
                        "Present", "P" -> PassGreen
                        "Absent", "A" -> FailRed
                        "Late", "Habsan", "H" -> Color(0xFFF59E0B) // Amber / Orange for Late (Habsan)
                        else -> Color(0xFF0284C7) // Sky Blue for Free/Leave
                    }

                    val shortLabel = when (currentStatus) {
                        "Present", "P" -> "P"
                        "Absent", "A" -> "A"
                        "Late", "Habsan", "H" -> "H"
                        else -> "F"
                    }

                    val fullLabel = when (currentStatus) {
                        "Present", "P" -> "🟢 Jooga (Present)"
                        "Absent", "A" -> "🔴 Maqan (Absent)"
                        "Late", "Habsan", "H" -> "🟠 Habsamay (Late)"
                        else -> "🔵 Fasax (Leave)"
                    }

                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isViewingToday) {
                                    Modifier.clickable {
                                        attendanceMap[student.id] = nextStatus
                                    }
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Student Info
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = student.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkText
                                )
                                Text(
                                    text = "ID: ${student.studentId} • Xaalad: $fullLabel ${if (isViewingToday) "(Taabo badhanka)" else "(Locked)"}",
                                    fontSize = 10.5.sp,
                                    color = MutedText
                                )
                            }

                            // Interactive Cycling Status Button (Active for Today)
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = badgeColor,
                                shadowElevation = if (isViewingToday) 2.dp else 0.dp,
                                modifier = Modifier
                                    .size(42.dp)
                                    .then(
                                        if (isViewingToday) {
                                            Modifier.clickable {
                                                attendanceMap[student.id] = nextStatus
                                            }
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = shortLabel,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White
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

@Composable
private fun MonthlyAttendanceSheetContent(
    accessibleClasses: List<SchoolClass>,
    selectedClassId: Long,
    onClassSelect: (Long) -> Unit,
    selectedMonthReadable: String,
    selectedYearMonth: String,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    classStudents: List<Student>,
    distinctMonthDates: List<String>,
    monthAttendanceRecords: List<AttendanceRecord>,
    monthlyViewMode: Int,
    onToggleViewMode: (Int) -> Unit,
    onDeleteDateLog: (String) -> Unit = {},
    onPrintMonthlySheet: () -> Unit,
    onExportMonthlyCsv: () -> Unit
) {
    val totalPres = monthAttendanceRecords.count { it.status == "Present" || it.status == "P" }
    val totalAbs = monthAttendanceRecords.count { it.status == "Absent" || it.status == "A" }
    val totalLate = monthAttendanceRecords.count { it.status == "Late" || it.status == "Habsan" || it.status == "H" }
    val totalFree = monthAttendanceRecords.count { it.status == "Free" || it.status == "F" }
    val totalRecords = monthAttendanceRecords.size
    val monthlyRatePct = if (totalRecords > 0) ((totalPres + totalLate).toDouble() / totalRecords) * 100.0 else 100.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Month Selector & Navigator Bar
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = TealContainer)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevMonth) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Previous Month", tint = TealDark)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = TealDark, modifier = Modifier.size(18.dp))
                    Text(
                        text = selectedMonthReadable.uppercase(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TealDark
                    )
                }

                IconButton(onClick = onNextMonth) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Next Month", tint = TealDark)
                }
            }
        }

        // Class Selector Chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(accessibleClasses) { cls ->
                FilterChip(
                    selected = selectedClassId == cls.id,
                    onClick = { onClassSelect(cls.id) },
                    label = {
                        Text(
                            cls.name,
                            fontSize = 11.sp,
                            fontWeight = if (selectedClassId == cls.id) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        // Monthly Summary Statistics Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Ardayda", fontSize = 9.sp, color = MutedText)
                    Text("${classStudents.size}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TealDark)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE6FFFA))
            ) {
                Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Joog (P)", fontSize = 9.sp, color = PassGreen)
                    Text("$totalPres", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PassGreen)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
            ) {
                Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Maqan (A)", fontSize = 9.sp, color = FailRed)
                    Text("$totalAbs", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FailRed)
                }
            }
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB))
            ) {
                Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Habsan (H)", fontSize = 9.sp, color = Color(0xFFB45309))
                    Text("$totalLate", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                }
            }
            Card(
                modifier = Modifier.weight(1.1f),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = TealPrimary)
            ) {
                Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Celcelis", fontSize = 9.sp, color = Color.White.copy(alpha = 0.85f))
                    Text(String.format(Locale.US, "%.0f%%", monthlyRatePct), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // View Mode Switcher
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Shaxda Xaadirinta Bisha ($selectedYearMonth)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = DarkText
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = monthlyViewMode == 0,
                    onClick = { onToggleViewMode(0) },
                    label = { Text("Shaxda (Table)", fontSize = 10.5.sp) },
                    leadingIcon = { Icon(Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(14.dp)) }
                )
                FilterChip(
                    selected = monthlyViewMode == 1,
                    onClick = { onToggleViewMode(1) },
                    label = { Text("Liiska (Cards)", fontSize = 10.5.sp) },
                    leadingIcon = { Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(14.dp)) }
                )
            }
        }

        if (classStudents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("Fasalkan arday kuma jirto.", color = MutedText, fontSize = 13.sp)
            }
        } else if (distinctMonthDates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Bishan ($selectedMonthReadable) weli wax xaadirin ah lama diiwaangelin.", color = MutedText, fontSize = 12.5.sp)
                    Text("Tag tab-ka 'Maanta (Live)' si aad u keydiso xaadirinta maanta.", color = TealDark, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        } else if (monthlyViewMode == 0) {
            // Table Matrix Grid
            val horizontalScrollState = rememberScrollState()

            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(horizontalScrollState)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxHeight(),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        // Table Header Row
                        item {
                            Row(
                                modifier = Modifier
                                    .background(TealPrimary)
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("#", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
                                Text("ID", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(70.dp), textAlign = TextAlign.Center)
                                Text("Magaca Ardayga", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(140.dp), textAlign = TextAlign.Start)

                                distinctMonthDates.forEach { d ->
                                    val dayNum = try { d.substring(8) } catch (e: Exception) { d }
                                    Text(
                                        text = dayNum,
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.width(28.dp),
                                        textAlign = TextAlign.Center
                                    )
                                }

                                Text("P", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
                                Text("A", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
                                Text("H", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
                                Text("%", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(42.dp), textAlign = TextAlign.Center)
                            }
                        }

                        // Table Body Rows
                        items(classStudents.size) { idx ->
                            val student = classStudents[idx]
                            val sRecords = monthAttendanceRecords.filter { it.studentId == student.id }
                            val pCount = sRecords.count { it.status == "Present" || it.status == "P" }
                            val aCount = sRecords.count { it.status == "Absent" || it.status == "A" }
                            val hCount = sRecords.count { it.status == "Late" || it.status == "Habsan" || it.status == "H" }
                            val pct = if (distinctMonthDates.isNotEmpty()) ((pCount + hCount).toDouble() / distinctMonthDates.size) * 100 else 100.0
                            val isEven = idx % 2 == 0

                            Row(
                                modifier = Modifier
                                    .background(if (isEven) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("${idx + 1}", fontSize = 11.sp, modifier = Modifier.width(30.dp), textAlign = TextAlign.Center, color = MutedText)
                                Text(student.studentId, fontSize = 10.5.sp, modifier = Modifier.width(70.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold, color = DarkText)
                                Text(student.name, fontSize = 11.sp, modifier = Modifier.width(140.dp), fontWeight = FontWeight.Bold, color = DarkText, maxLines = 1)

                                distinctMonthDates.forEach { d ->
                                    val rec = sRecords.find { it.date == d }
                                    val (badgeText, badgeColor, textColor) = when (rec?.status) {
                                        "Present", "P" -> Triple("P", Color(0xFFE6FFFA), PassGreen)
                                        "Absent", "A" -> Triple("A", Color(0xFFFFEBEE), FailRed)
                                        "Late", "Habsan", "H" -> Triple("H", Color(0xFFFFFBEB), Color(0xFFB45309))
                                        "Free", "F" -> Triple("F", Color(0xFFE0F2FE), Color(0xFF0284C7))
                                        else -> Triple("-", Color.Transparent, MutedText)
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(28.dp)
                                            .padding(horizontal = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Surface(
                                            color = badgeColor,
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = badgeText,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = textColor
                                                )
                                            }
                                        }
                                    }
                                }

                                Text("$pCount", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PassGreen, modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
                                Text("$aCount", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FailRed, modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
                                Text("$hCount", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309), modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
                                Text(
                                    String.format(Locale.US, "%.0f%%", pct),
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Black,
                                    color = if (pct >= 75) PassGreen else FailRed,
                                    modifier = Modifier.width(42.dp),
                                    textAlign = TextAlign.Center
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        } else {
            // Student Breakdown Cards
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(classStudents, key = { it.id }) { student ->
                    val sRecords = monthAttendanceRecords.filter { it.studentId == student.id }
                    val pCount = sRecords.count { it.status == "Present" || it.status == "P" }
                    val aCount = sRecords.count { it.status == "Absent" || it.status == "A" }
                    val hCount = sRecords.count { it.status == "Late" || it.status == "Habsan" || it.status == "H" }
                    val pct = if (distinctMonthDates.isNotEmpty()) ((pCount + hCount).toDouble() / distinctMonthDates.size) * 100 else 100.0

                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(student.name, fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                    Text("ID: ${student.studentId} • Fasal: ${student.gender}", fontSize = 11.sp, color = MutedText)
                                }

                                Surface(
                                    color = if (pct >= 75) PassGreen else FailRed,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%.0f%% Rate", pct),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            LinearProgressIndicator(
                                progress = { (pct / 100.0).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp),
                                color = if (pct >= 75) PassGreen else FailRed,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("🟢 Joogay: $pCount", fontSize = 11.sp, color = PassGreen, fontWeight = FontWeight.SemiBold)
                                Text("🔴 Maqnaa: $aCount", fontSize = 11.sp, color = FailRed, fontWeight = FontWeight.SemiBold)
                                Text("🟠 Habsan: $hCount", fontSize = 11.sp, color = Color(0xFFB45309), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudentLateTrackerContent(
    accessibleClasses: List<SchoolClass>,
    selectedClassId: Long,
    onClassSelect: (Long) -> Unit,
    allStudents: List<Student>,
    existingAttendance: List<AttendanceRecord>,
    onPrintWarning: (Student, List<AttendanceRecord>, String) -> Unit,
    onPrintClassReport: (Long) -> Unit,
    onExportCsv: (Long) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var sortByMostLate by remember { mutableStateOf(true) }
    var expandedStudentId by remember { mutableStateOf<Long?>(null) }

    val currentClassStudents = remember(allStudents, selectedClassId) {
        if (selectedClassId == 0L) allStudents else allStudents.filter { it.classId == selectedClassId }
    }

    val filteredAttendance = remember(existingAttendance, selectedClassId) {
        if (selectedClassId == 0L) existingAttendance else existingAttendance.filter { it.classId == selectedClassId }
    }

    val totalClassLate = remember(filteredAttendance) {
        filteredAttendance.count { it.status.equals("Late", ignoreCase = true) || it.status.equals("Habsan", ignoreCase = true) || it.status == "H" }
    }

    val totalClassAbs = remember(filteredAttendance) {
        filteredAttendance.count { it.status.equals("Absent", ignoreCase = true) || it.status == "A" }
    }

    val studentLateStats = remember(currentClassStudents, filteredAttendance, searchQuery, sortByMostLate) {
        currentClassStudents
            .filter {
                searchQuery.isBlank() ||
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.studentId.contains(searchQuery, ignoreCase = true)
            }
            .map { s ->
                val sRecs = filteredAttendance.filter { it.studentId == s.id }
                val lateList = sRecs.filter { it.status.equals("Late", ignoreCase = true) || it.status.equals("Habsan", ignoreCase = true) || it.status == "H" }
                val absList = sRecs.filter { it.status.equals("Absent", ignoreCase = true) || it.status == "A" }
                val presList = sRecs.filter { it.status.equals("Present", ignoreCase = true) || it.status == "P" }
                val totalDays = sRecs.size
                val rate = if (totalDays > 0) ((presList.size + lateList.size).toDouble() / totalDays) * 100.0 else 100.0
                StudentLateModel(
                    student = s,
                    lateCount = lateList.size,
                    absentCount = absList.size,
                    presentCount = presList.size,
                    totalDays = totalDays,
                    attendanceRate = rate,
                    lateDates = lateList.map { it.date }.sortedDescending(),
                    absentDates = absList.map { it.date }.sortedDescending()
                )
            }
            .sortedWith(
                if (sortByMostLate) compareByDescending<StudentLateModel> { it.lateCount }.thenByDescending { it.absentCount }
                else compareBy(String.CASE_INSENSITIVE_ORDER) { it.student.name }
            )
    }

    val topLateStudentsCount = studentLateStats.count { it.lateCount >= 3 }
    val selectedClassName = accessibleClasses.find { it.id == selectedClassId }?.name ?: "Dhammaan Fasalada"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Compact Header: Class Filter + Slim Search + Sort Icon Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Class selector in a compact row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(accessibleClasses) { cls ->
                    FilterChip(
                        selected = selectedClassId == cls.id,
                        onClick = { onClassSelect(cls.id) },
                        label = {
                            Text(
                                cls.name,
                                fontSize = 11.sp,
                                fontWeight = if (selectedClassId == cls.id) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        modifier = Modifier.height(32.dp)
                    )
                }
            }

            // Compact Sort Icon Button
            IconButton(
                onClick = { sortByMostLate = !sortByMostLate },
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        if (sortByMostLate) Color(0xFFFFFBEB) else MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp)
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Sort,
                    contentDescription = if (sortByMostLate) "Ugu Habsan Badan" else "A-Z",
                    tint = if (sortByMostLate) Color(0xFFB45309) else DarkText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Slim Search Input (Compact 38dp)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Raadi magac ama ID...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp), tint = MutedText) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", modifier = Modifier.size(14.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
        )

        // Slim Single-Line KPI Capsule Bar (Replaces 3 huge bulky boxes)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Total Late
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        color = Color(0xFFFFFBEB),
                        shape = CircleShape,
                        modifier = Modifier.size(18.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("⏰", fontSize = 10.sp)
                        }
                    }
                    Text("Habsan:", fontSize = 11.sp, color = MutedText)
                    Text("$totalClassLate", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                }

                // Total Absent
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        color = Color(0xFFFFEBEE),
                        shape = CircleShape,
                        modifier = Modifier.size(18.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("🔴", fontSize = 9.sp)
                        }
                    }
                    Text("Maqan:", fontSize = 11.sp, color = MutedText)
                    Text("$totalClassAbs", fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = FailRed)
                }

                // High Tardiness / Alerts
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        color = if (topLateStudentsCount > 0) Color(0xFFFEF2F2) else Color(0xFFF0FDF4),
                        shape = CircleShape,
                        modifier = Modifier.size(18.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(if (topLateStudentsCount > 0) "⚠️" else "✓", fontSize = 9.sp)
                        }
                    }
                    Text("Digniin:", fontSize = 11.sp, color = MutedText)
                    Text(
                        "$topLateStudentsCount",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (topLateStudentsCount > 0) FailRed else PassGreen
                    )
                }

                // Sort indicator badge
                Surface(
                    color = if (sortByMostLate) Color(0xFFFFFBEB) else Color(0xFFE6FFFA),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (sortByMostLate) "🔥 Habsan" else "🔤 A-Z",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (sortByMostLate) Color(0xFFB45309) else TealDark,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }

        // Students List with Streamlined Cards
        if (studentLateStats.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isBlank()) "Fasalkan arday kuma jirto ama wax xaadirin ah lama diiwaangelin." else "Arday leh '$searchQuery' lama helin.",
                    color = MutedText,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(studentLateStats, key = { it.student.id }) { item ->
                    val isExpanded = expandedStudentId == item.student.id
                    val isHighRisk = item.lateCount >= 3 || item.absentCount >= 3

                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = if (isHighRisk) BorderStroke(1.dp, if (item.lateCount >= 5) FailRed else Color(0xFFF59E0B)) else BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Main Row: Avatar + Student Name/ID + Compact Metric Pills + Action Icons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Left: Avatar & Info
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = if (item.lateCount >= 5) Color(0xFFFFEBEE) else if (item.lateCount >= 3) Color(0xFFFFFBEB) else Color(0xFFE6FFFA),
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = if (item.lateCount >= 5) "⚠️" else if (item.lateCount > 0) "⏰" else "✓",
                                                fontSize = 14.sp
                                            )
                                        }
                                    }

                                    Column {
                                        Text(
                                            text = item.student.name,
                                            fontSize = 13.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DarkText,
                                            maxLines = 1
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = item.student.studentId,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = TealDark
                                            )
                                            Text("•", fontSize = 10.sp, color = MutedText)
                                            Text(
                                                text = "${item.attendanceRate.toInt()}% Xaadir",
                                                fontSize = 10.sp,
                                                color = if (item.attendanceRate >= 80) PassGreen else FailRed,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // Center/Right: Badges (Late / Absent / Present)
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Late Badge
                                    Surface(
                                        color = if (item.lateCount > 0) Color(0xFFFFFBEB) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "🟠 ${item.lateCount}H",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (item.lateCount > 0) Color(0xFFB45309) else MutedText,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }

                                    // Absent Badge
                                    Surface(
                                        color = if (item.absentCount > 0) Color(0xFFFFEBEE) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "🔴 ${item.absentCount}A",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (item.absentCount > 0) FailRed else MutedText,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }

                                    // Print Warning Slip Icon Button
                                    IconButton(
                                        onClick = {
                                            val sRecs = filteredAttendance.filter { it.studentId == item.student.id }
                                            onPrintWarning(item.student, sRecs, selectedClassName)
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Print,
                                            contentDescription = "Warqad Digniin",
                                            modifier = Modifier.size(16.dp),
                                            tint = Color(0xFFB45309)
                                        )
                                    }

                                    // Expand Details Icon Button
                                    IconButton(
                                        onClick = {
                                            expandedStudentId = if (isExpanded) null else item.student.id
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                            contentDescription = "Taariikhaha",
                                            modifier = Modifier.size(18.dp),
                                            tint = TealDark
                                        )
                                    }
                                }
                            }

                            // Expanded Detailed Dates Log
                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                        .padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("📅 Diiwaanka Taariikheed ee Habsanka & Maqnaanshaha:", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                        // Quick print button
                                        TextButton(
                                            onClick = {
                                                val sRecs = filteredAttendance.filter { it.studentId == item.student.id }
                                                onPrintWarning(item.student, sRecs, selectedClassName)
                                            },
                                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                                        ) {
                                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFFB45309))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("Daabac Warqadda", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                        }
                                    }

                                    if (item.lateDates.isEmpty() && item.absentDates.isEmpty()) {
                                        Text("• Ardaygani ma laha wax habsan ama maqnaansho ah.", fontSize = 10.sp, color = PassGreen, fontWeight = FontWeight.SemiBold)
                                    } else {
                                        if (item.lateDates.isNotEmpty()) {
                                            Text("Habsanka (${item.lateDates.size} Maalmood):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFB45309))
                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                items(item.lateDates) { d ->
                                                    Surface(
                                                        color = Color(0xFFFFFBEB),
                                                        border = BorderStroke(0.5.dp, Color(0xFFF59E0B)),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text("⏰ $d", fontSize = 9.5.sp, color = Color(0xFFB45309), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                                    }
                                                }
                                            }
                                        }

                                        if (item.absentDates.isNotEmpty()) {
                                            Text("Maqnaanshaha (${item.absentDates.size} Maalmood):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FailRed)
                                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                items(item.absentDates) { d ->
                                                    Surface(
                                                        color = Color(0xFFFFEBEE),
                                                        border = BorderStroke(0.5.dp, FailRed),
                                                        shape = RoundedCornerShape(4.dp)
                                                    ) {
                                                        Text("🔴 $d", fontSize = 9.5.sp, color = FailRed, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
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
        }
    }
}

private data class StudentLateModel(
    val student: Student,
    val lateCount: Int,
    val absentCount: Int,
    val presentCount: Int,
    val totalDays: Int,
    val attendanceRate: Double,
    val lateDates: List<String>,
    val absentDates: List<String>
)

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
