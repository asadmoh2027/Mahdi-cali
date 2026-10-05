package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
    exams: List<Exam>,
    fees: List<FeeRecord>,
    attendance: List<AttendanceRecord>,
    onExportExamCsv: () -> Unit,
    onExportFeeCsv: () -> Unit,
    onPrintSingleStudentReport: (Student) -> Unit,
    onPrintFeeReport: () -> Unit,
    onPrintClassReport: (Long) -> Unit,
    onPrintAttendanceReport: (Long) -> Unit,
    onPrintExamReport: (Long) -> Unit,
    onOpenClearanceClick: () -> Unit = {},
    onBackClick: () -> Unit
) {
    var schoolNameInput by remember(schoolName) { mutableStateOf(schoolName) }
    var selectedStudentId by remember { mutableStateOf<Long?>(students.firstOrNull()?.id) }
    var selectedClassReportId by remember { mutableLongStateOf(0L) }

    val totalStudents = students.size
    val totalClasses = classes.size

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

                            Button(
                                onClick = {
                                    val st = students.find { it.id == selectedStudentId }
                                    if (st != null) onPrintSingleStudentReport(st)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("🖨️ PRINT STUDENT REPORT CARD", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("🏫 2. Class, Attendance & Exam Reports", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TealPrimary)

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

                        OutlinedButton(
                            onClick = { onPrintClassReport(selectedClassReportId) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("🖨️ Print Class Roster Report", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { onPrintAttendanceReport(selectedClassReportId) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("🖨️ Print Class Attendance Report", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { onPrintExamReport(selectedClassReportId) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("🖨️ Print Class Exam Results Report", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
