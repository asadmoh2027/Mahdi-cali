package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Exam
import com.example.data.ExamMark
import com.example.data.SchoolClass
import com.example.data.Student
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PromotionScreen(
    classes: List<SchoolClass>,
    students: List<Student>,
    exams: List<Exam>,
    allMarks: List<ExamMark>,
    onPromoteStudents: (List<Long>, Long) -> Unit,
    onUpdateSingleStudentClass: (Long, Long) -> Unit,
    onBackClick: () -> Unit
) {
    var selectedSourceClassId by remember(classes) { mutableLongStateOf(classes.firstOrNull()?.id ?: 0L) }
    var selectedTargetClassId by remember(classes) {
        val nextClass = if (classes.size > 1) classes[1] else classes.firstOrNull()
        mutableLongStateOf(nextClass?.id ?: 0L)
    }

    var showConfirmDialog by remember { mutableStateOf(false) }

    val sourceClassStudents = students.filter { it.classId == selectedSourceClassId }
    val classExams = exams.filter { it.classId == selectedSourceClassId }

    // Helper data structure for student calculation
    data class StudentPromotionData(
        val student: Student,
        val term1Score: Double,
        val term2Score: Double,
        val grandTotal: Double,
        val isTerm1Pass: Boolean,
        val isTerm2Pass: Boolean,
        val isAnnualPass: Boolean
    )

    val promotionDataList = sourceClassStudents.map { student ->
        var t1Score = 0.0
        var t2Score = 0.0

        classExams.forEach { exam ->
            val mark = allMarks.find { it.examId == exam.id && it.studentId == student.id }
            val score = mark?.score ?: 0.0
            val text = (exam.name + " " + exam.subject).lowercase()
            val isTerm2 = text.contains("term 2") || text.contains("term2") || text.contains("t2") || text.contains("sem 2") || text.contains("final")

            if (isTerm2) {
                t2Score += score
            } else {
                t1Score += score
            }
        }

        val total = t1Score + t2Score
        val t1Pass = t1Score >= 175.0
        val t2Pass = t2Score >= 175.0
        val annualPass = total >= 350.0

        StudentPromotionData(
            student = student,
            term1Score = t1Score,
            term2Score = t2Score,
            grandTotal = total,
            isTerm1Pass = t1Pass,
            isTerm2Pass = t2Pass,
            isAnnualPass = annualPass
        )
    }

    val passedStudents = promotionDataList.filter { it.isAnnualPass }
    val failedStudents = promotionDataList.filter { !it.isAnnualPass }

    val totalCount = promotionDataList.size
    val passedCount = passedStudents.size
    val failedCount = failedStudents.size
    val passRate = if (totalCount > 0) (passedCount.toDouble() / totalCount.toDouble()) * 100 else 0.0

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📈 Gudbinta Ardayda (Annual Promotion)", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Rules Explanation Banner
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = TealContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "📋 Sharciyada Gudbinta Sanad Dugsyeedka:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TealDark
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "• Maaddo kasta waxay leedahay 50 dhibcood teeramkiiba (7 maaddood = 350 wadarta teeramka).\n" +
                                "• Ardayga teeramka hela < 175 dhibcood waa dhacayaa teeramkaas.\n" +
                                "• **Wadarta Labada Teeram (T1 + T2):**\n" +
                                "  - **>= 350 dhibcood**: GUDBAY (Wuxuu u gudbayaa fasalka xiga).\n" +
                                "  - **<= 349 dhibcood**: DHACAY (Wuxuu ku celinayaa fasalka).",
                        fontSize = 11.sp,
                        color = DarkText,
                        lineHeight = 15.sp
                    )
                }
            }

            // Class Selector Row
            Text("1. Dooro Fasalka Hadda (Source Class) *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                classes.forEach { cls ->
                    FilterChip(
                        selected = selectedSourceClassId == cls.id,
                        onClick = { selectedSourceClassId = cls.id },
                        label = { Text(cls.name, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                    )
                }
            }

            // Class Statistics Overview Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Kulliga Ardayda", fontSize = 10.sp, color = MutedText)
                        Text("$totalCount", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TealDark)
                    }
                    Divider(modifier = Modifier
                        .height(30.dp)
                        .width(1.dp), color = MutedText.copy(alpha = 0.3f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Gudbay (Pass)", fontSize = 10.sp, color = PassGreen)
                        Text("$passedCount", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PassGreen)
                    }
                    Divider(modifier = Modifier
                        .height(30.dp)
                        .width(1.dp), color = MutedText.copy(alpha = 0.3f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Dhacay (Fail)", fontSize = 10.sp, color = FailRed)
                        Text("$failedCount", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FailRed)
                    }
                    Divider(modifier = Modifier
                        .height(30.dp)
                        .width(1.dp), color = MutedText.copy(alpha = 0.3f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Boqolleyda", fontSize = 10.sp, color = TealPrimary)
                        Text("${String.format("%.1f%%", passRate)}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                    }
                }
            }

            // Target Class Selector & Batch Promotion Action Button
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = TealPrimary.copy(alpha = 0.08f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("2. Dooro Fasalka loo gudbinayo Ardayda *", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        classes.filter { it.id != selectedSourceClassId }.forEach { cls ->
                            FilterChip(
                                selected = selectedTargetClassId == cls.id,
                                onClick = { selectedTargetClassId = cls.id },
                                label = { Text("➔ ${cls.name}", fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                            )
                        }
                    }

                    Button(
                        onClick = { showConfirmDialog = true },
                        enabled = passedCount > 0 && selectedTargetClassId != 0L && selectedTargetClassId != selectedSourceClassId,
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "GUDBI ARDAYDA BAASAY ($passedCount ARDAY)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Student Roster
            Text("Natiijada & Gudbinta Ardayda Fasalka:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DarkText)

            if (promotionDataList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Ma jiraan arday enrolled ka ah fasalkan.", color = MutedText, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(promotionDataList) { data ->
                        val student = data.student
                        val isPass = data.isAnnualPass

                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isPass) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(student.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("EMIS ID: ${student.studentId} • Gender: ${student.gender}", fontSize = 10.sp, color = MutedText)
                                    }

                                    // Status Badge
                                    Surface(
                                        color = if (isPass) PassGreen else FailRed,
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Text(
                                            text = if (isPass) "GUDBAY (PASSED)" else "KU CELINAYA (FAILED)",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }

                                Divider(color = MutedText.copy(alpha = 0.2f))

                                // Detailed Marks Breakdown
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Term 1 Score", fontSize = 10.sp, color = MutedText)
                                        Text(
                                            text = "${data.term1Score.toInt()} / 350",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (data.isTerm1Pass) PassGreen else FailRed
                                        )
                                    }

                                    Column {
                                        Text("Term 2 Score", fontSize = 10.sp, color = MutedText)
                                        Text(
                                            text = "${data.term2Score.toInt()} / 350",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (data.isTerm2Pass) PassGreen else FailRed
                                        )
                                    }

                                    Column {
                                        Text("Wadarta Guud (2 T)", fontSize = 10.sp, color = MutedText)
                                        Text(
                                            text = "${data.grandTotal.toInt()} / 700",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isPass) PassGreen else FailRed
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Confirmation Dialog
        if (showConfirmDialog) {
            val targetClassName = classes.find { it.id == selectedTargetClassId }?.name ?: "N/A"
            AlertDialog(
                onDismissRequest = { showConfirmDialog = false },
                title = { Text("⚠️ Xaqiiji Gudbinta Ardayda Baastay", fontWeight = FontWeight.Bold, color = TealPrimary) },
                text = {
                    Text(
                        "Ma weydiinaysaa inaad $passedCount arday oo baasay si otomaatig ah ugu gudbiso fasalka '$targetClassName'?\n\n" +
                                "• Ardayda baastay ($passedCount): Wuxuu fasalkoodu noqon doonaa '$targetClassName'.\n" +
                                "• Ardayda dhacday ($failedCount): Waxay ku celin doonaan fasalka hadda.",
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val passedIds = passedStudents.map { it.student.id }
                            onPromoteStudents(passedIds, selectedTargetClassId)
                            showConfirmDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                    ) {
                        Text("HAA, GUDBI ARDAYDA")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmDialog = false }) { Text("JOOJI") }
                }
            )
        }

    }
}
