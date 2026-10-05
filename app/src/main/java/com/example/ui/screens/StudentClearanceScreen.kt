package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Exam
import com.example.data.ExamMark
import com.example.data.SchoolClass
import com.example.data.Student
import com.example.ui.components.StudentSearchFilterCard
import com.example.ui.theme.*

data class SubjectRowData(
    val subjectName: String,
    val term1Str: String,
    val term2Str: String,
    val totalStr: String
)

data class ClassBoxData(
    val boxIndex: Int,
    val classLabel: String,
    val isMiddleSchoolRecorded: Boolean,
    val subjectRows: List<SubjectRowData>,
    val grandTotalTerm1Str: String,
    val grandTotalTerm2Str: String,
    val grandTotalStr: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentClearanceScreen(
    schoolName: String,
    classes: List<SchoolClass>,
    students: List<Student>,
    exams: List<Exam>,
    allMarks: List<ExamMark>,
    onPrintClearanceHtml: (
        context: Context,
        student: Student,
        previousSchool: String,
        destinationSchool: String,
        destinationClass: String,
        academicYear: String
    ) -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    var selectedStudentId by remember(students) { mutableStateOf(students.firstOrNull()?.id ?: 0L) }
    val currentStudent = students.find { it.id == selectedStudentId } ?: students.firstOrNull()

    val currentClass = classes.find { it.id == currentStudent?.classId }
    val currentClassName = currentClass?.name ?: "N/A"

    // Default destination class rule: Grade 4 is promoted to Grade 5
    val defaultDestinationClass = remember(currentClassName) {
        val digits = currentClassName.filter { it.isDigit() }
        val num = digits.toIntOrNull() ?: 1
        if (num >= 4) {
            "Class 5 (Fasalka 5aad)"
        } else {
            "Class ${num + 1}"
        }
    }

    var previousSchoolInput by remember(schoolName) { mutableStateOf(schoolName.ifBlank { "PRIMARY & MIDDLE SCHOOL" }) }
    var destinationSchoolInput by remember { mutableStateOf("SECONDARY / HIGHER SCHOOL") }
    var destinationClassInput by remember(defaultDestinationClass) { mutableStateOf(defaultDestinationClass) }
    var academicYearInput by remember { mutableStateOf("2025/2026") }

    val standardSubjects = listOf("Diinta Islaamka", "Af-Soomaali", "Xisaab", "Saynis", "Cilmiga Bulshada", "English", "Carabi")

    // Generate 4 boxes for Primary Classes (1, 2, 3, 4) with automatic exam marks calculation
    val classBoxesData = remember(currentStudent, classes, exams, allMarks) {
        if (currentStudent == null) emptyList()
        else {
            (1..4).map { boxIdx ->
                val boxLabel = "Class $boxIdx"

                // Boxes 1-4: Strictly match class in DB matching this grade boxIdx
                val matchingClass = classes.find { cls ->
                    val digits = cls.name.filter { it.isDigit() }
                    val num = digits.toIntOrNull()
                    if (num != null) num == boxIdx
                    else {
                        val nameLower = cls.name.lowercase()
                        nameLower.contains("fasalka $boxIdx") || nameLower.contains("class $boxIdx") || nameLower.contains("grade $boxIdx") || nameLower.contains("$boxIdx")
                    }
                }

                val classExams = if (matchingClass != null) {
                    exams.filter { it.classId == matchingClass.id }
                } else emptyList()

                // Extract actual subjects from the class exams and merge with standard subjects
                val activeSubjects = classExams.map { exam ->
                    cleanExamSubjectName(exam.subject.ifBlank { exam.name })
                }.filter { it.isNotBlank() }.distinct()

                val fullSubjectList = standardSubjects.toMutableList()
                activeSubjects.forEach { extraSub ->
                    val alreadyHas = fullSubjectList.any { std -> std.equals(extraSub, ignoreCase = true) || isSameSubjectName(std, extraSub) }
                    if (!alreadyHas) fullSubjectList.add(extraSub)
                }

                val subjectsList = fullSubjectList

                var sumT1 = 0.0
                var sumT2 = 0.0
                var hasAnyT1 = false
                var hasAnyT2 = false

                val rows = subjectsList.map { subName ->
                    val t1Val = getSubjectMarkValue(subName, isTerm2 = false, classExams = classExams, allExams = exams, allMarks = allMarks, studentId = currentStudent.id)
                    val t2Val = getSubjectMarkValue(subName, isTerm2 = true, classExams = classExams, allExams = exams, allMarks = allMarks, studentId = currentStudent.id)

                    if (t1Val != null) { sumT1 += t1Val; hasAnyT1 = true }
                    if (t2Val != null) { sumT2 += t2Val; hasAnyT2 = true }

                    val t1Str = t1Val?.let { fmtNum(it) } ?: ""
                    val t2Str = t2Val?.let { fmtNum(it) } ?: ""
                    val totStr = if (t1Val != null || t2Val != null) {
                        fmtNum((t1Val ?: 0.0) + (t2Val ?: 0.0))
                    } else ""

                    SubjectRowData(
                        subjectName = subName,
                        term1Str = t1Str,
                        term2Str = t2Str,
                        totalStr = totStr
                    )
                }

                val grandT1Str = if (hasAnyT1) fmtNum(sumT1) else ""
                val grandT2Str = if (hasAnyT2) fmtNum(sumT2) else ""
                val grandTotStr = if (hasAnyT1 || hasAnyT2) fmtNum(sumT1 + sumT2) else ""

                ClassBoxData(
                    boxIndex = boxIdx,
                    classLabel = boxLabel,
                    isMiddleSchoolRecorded = true,
                    subjectRows = rows,
                    grandTotalTerm1Str = grandT1Str,
                    grandTotalTerm2Str = grandT2Str,
                    grandTotalStr = grandTotStr
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📄 Student Record & Clearance Certificate", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TealPrimary)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Student Search & Filter Card (Compact Icon Header)
            item {
                StudentSearchFilterCard(
                    students = students,
                    classes = classes,
                    selectedStudentId = currentStudent?.id,
                    onStudentSelected = { selectedStudentId = it.id }
                )
            }

            // Certificate Configuration Card
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("⚙️ Certificate Settings & Details", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TealPrimary)

                        if (students.isEmpty()) {
                            Text("No registered students found.", color = FailRed, fontSize = 12.sp)
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = previousSchoolInput,
                                    onValueChange = { previousSchoolInput = it },
                                    label = { Text("Current / Previous School", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = destinationSchoolInput,
                                    onValueChange = { destinationSchoolInput = it },
                                    label = { Text("Destination School", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = destinationClassInput,
                                    onValueChange = { destinationClassInput = it },
                                    label = { Text("Promoted Class", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = academicYearInput,
                                    onValueChange = { academicYearInput = it },
                                    label = { Text("Academic Year", fontSize = 11.sp) },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Button(
                                onClick = {
                                    if (currentStudent != null) {
                                        onPrintClearanceHtml(
                                            context,
                                            currentStudent,
                                            previousSchoolInput,
                                            destinationSchoolInput,
                                            destinationClassInput,
                                            academicYearInput
                                        )
                                    }
                                },
                                enabled = currentStudent != null,
                                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("🖨️ PRINT CERTIFICATE / SAVE PDF", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Preview Title
            item {
                Text("👁️ Official Document Preview:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
            }

            // Document Preview Paper Card
            item {
                Card(
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFAFAFC)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF006A6B), RoundedCornerShape(8.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Official Header Box
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF006A6B), RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            Text("MINISTRY OF EDUCATION & SCIENCE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF104A4B))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(previousSchoolInput.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF006A6B))
                            Spacer(modifier = Modifier.height(4.dp))

                            // Banner
                            Surface(
                                color = Color(0xFF006A6B),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "STUDENT CLEARANCE & RECORD SHEET",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // Student Metadata Fields Table
                        Card(
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(0.5.dp, Color.LightGray)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                DataRow("1. Student Full Name:", currentStudent?.name ?: "-")
                                DataRow("2. Mother's Name:", currentStudent?.motherName?.ifBlank { "N/A" } ?: "-")
                                DataRow("3. Current / Previous Class:", currentClassName)
                                DataRow("4. Current / Previous School:", previousSchoolInput)
                                DataRow("5. Destination School:", destinationSchoolInput)
                                DataRow("6. Promoted / Transfer Class:", destinationClassInput)
                                DataRow("7. Academic Year:", academicYearInput)
                            }
                        }

                        // Class Records Title
                        Text(
                            text = "ACADEMIC PROGRESS RECORD (CLASSES 1 - 4 / FASALLADA 1AAD - 4AAD)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF006A6B),
                            modifier = Modifier.padding(top = 2.dp)
                        )

                        // 4 Boxes Grid Display (Classes 1 to 4)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            classBoxesData.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    pair.forEach { boxData ->
                                        Surface(
                                            color = Color.White,
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .border(1.dp, Color(0xFF006A6B), RoundedCornerShape(4.dp))
                                        ) {
                                            Column {
                                                // Box Title Banner
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(Color(0xFF006A6B))
                                                        .padding(vertical = 3.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        text = boxData.classLabel.uppercase(),
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White
                                                    )
                                                }

                                                // Subject Scores Table
                                                Column(modifier = Modifier.padding(4.dp)) {
                                                    // Header Row
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(Color(0xFFE6F2F2))
                                                            .padding(vertical = 2.dp, horizontal = 2.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text("Subject", fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.8f))
                                                        Text("T1", fontSize = 8.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                                                        Text("T2", fontSize = 8.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                                                        Text("Total", fontSize = 8.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                                                    }

                                                    HorizontalDivider(color = Color.LightGray, thickness = 0.5.dp)

                                                    // Subject Rows
                                                    boxData.subjectRows.forEach { row ->
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(vertical = 1.dp, horizontal = 2.dp),
                                                            horizontalArrangement = Arrangement.SpaceBetween,
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(row.subjectName, fontSize = 8.sp, color = DarkText, modifier = Modifier.weight(1.8f), maxLines = 1)
                                                            Text(row.term1Str.ifBlank { "—" }, fontSize = 8.sp, color = if (row.term1Str.isNotBlank()) TealDark else Color.LightGray, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                                                            Text(row.term2Str.ifBlank { "—" }, fontSize = 8.sp, color = if (row.term2Str.isNotBlank()) TealDark else Color.LightGray, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                                                            Text(row.totalStr.ifBlank { "—" }, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = if (row.totalStr.isNotBlank()) TealDark else Color.LightGray, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                                                        }
                                                        HorizontalDivider(color = Color(0xFFAAAAAA).copy(alpha = 0.2f), thickness = 0.5.dp)
                                                    }

                                                    // Summary Total Row
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .background(Color(0xFFF0F7F7))
                                                            .padding(vertical = 2.dp, horizontal = 2.dp),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text("Total", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TealDark, modifier = Modifier.weight(1.8f))
                                                        Text(boxData.grandTotalTerm1Str.ifBlank { "—" }, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TealDark, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                                                        Text(boxData.grandTotalTerm2Str.ifBlank { "—" }, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TealDark, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                                                        Text(boxData.grandTotalStr.ifBlank { "—" }, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = TealDark, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Footer Signature
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color.LightGray, RoundedCornerShape(4.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Principal Name: __________________________", fontSize = 8.sp, color = DarkText)
                                Text("Signature: __________________________", fontSize = 8.sp, color = DarkText)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Date: _____/_____/2026", fontSize = 8.sp, color = MutedText)
                                Text("[ OFFICIAL SCHOOL SEAL / STAMP ]", fontSize = 8.sp, color = MutedText, textAlign = TextAlign.End)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isSameSubjectName(s1: String, s2: String): Boolean {
    val a = s1.lowercase()
    val b = s2.lowercase()
    return a == b ||
        (a.contains("diin") && (b.contains("diin") || b.contains("islam") || b.contains("tarbiya"))) ||
        (a.contains("xisaab") && (b.contains("math") || b.contains("xisaab") || b.contains("xis"))) ||
        (a.contains("saynis") && (b.contains("science") || b.contains("saynis") || b.contains("say"))) ||
        (a.contains("soomaali") && (b.contains("soomaali") || b.contains("somali") || b.contains("som"))) ||
        (a.contains("english") && (b.contains("english") || b.contains("ingiriis") || b.contains("eng"))) ||
        (a.contains("carabi") && (b.contains("arabic") || b.contains("carabi") || b.contains("car"))) ||
        (a.contains("bulshada") && (b.contains("social") || b.contains("bulshada") || b.contains("c/b")))
}

private fun getSubjectMarkValue(
    subjectName: String,
    isTerm2: Boolean,
    classExams: List<Exam>,
    allExams: List<Exam>,
    allMarks: List<ExamMark>,
    studentId: Long
): Double? {
    // 1. Try matching within class exams
    val matchedClassExam = classExams.find { exam ->
        val text = (exam.name + " " + exam.subject).lowercase()
        val examIsTerm2 = text.contains("term 2") || text.contains("term2") || text.contains("t2") || text.contains("sem 2") || text.contains("final") || text.contains("2nd") || text.contains("teeramka 2")
        val isCorrectTerm = if (isTerm2) examIsTerm2 else !examIsTerm2

        val subClean = cleanExamSubjectName(exam.subject.ifBlank { exam.name }).lowercase()
        val targetClean = subjectName.lowercase()

        val matchesSubject = subClean == targetClean ||
            subClean.contains(targetClean) ||
            targetClean.contains(subClean) ||
            text.contains(targetClean) ||
            isSameSubjectName(subClean, targetClean) ||
            isSameSubjectName(text, targetClean)

        isCorrectTerm && matchesSubject
    }

    if (matchedClassExam != null) {
        val mark = allMarks.find { it.examId == matchedClassExam.id && it.studentId == studentId }
        if (mark != null && !mark.isAbsent) {
            var score = mark.score
            if (matchedClassExam.totalMarks == 100.0 && score > 50.0) {
                score /= 2.0
            }
            return score.coerceAtMost(50.0)
        }
    }

    return null
}

private fun fmtNum(n: Double): String {
    return if (n % 1.0 == 0.0) n.toInt().toString() else String.format(java.util.Locale.US, "%.1f", n)
}

@Composable
private fun DataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 10.sp, color = MutedText, fontWeight = FontWeight.Medium)
        Text(value, fontSize = 10.sp, color = DarkText, fontWeight = FontWeight.Bold)
    }
}
