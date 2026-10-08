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

data class MarksheetSubjectRow(
    val subjectName: String,
    val term1Str: String,
    val term2Str: String,
    val totalStr: String,
    val avgStr: String,
    val resultStr: String,
    val term1Val: Double?,
    val term2Val: Double?,
    val totalVal: Double?
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentMarksheetScreen(
    schoolName: String,
    classes: List<SchoolClass>,
    students: List<Student>,
    exams: List<Exam>,
    allMarks: List<ExamMark>,
    onPrintMarksheetHtml: (
        context: Context,
        student: Student,
        academicYear: String,
        destinationClass: String
    ) -> Unit,
    onPrintAllClassMarksheetsHtml: (
        context: Context,
        classId: Long,
        academicYear: String,
        destinationClass: String
    ) -> Unit = { _, _, _, _ -> },
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    val eligibleClasses = classes
    val eligibleStudents = students

    var selectedStudentId by remember(eligibleStudents) { mutableStateOf(eligibleStudents.firstOrNull()?.id ?: 0L) }
    val currentStudent = eligibleStudents.find { it.id == selectedStudentId } ?: eligibleStudents.firstOrNull()

    val currentClass = eligibleClasses.find { it.id == currentStudent?.classId } ?: classes.find { it.id == currentStudent?.classId }
    val currentClassName = currentClass?.name ?: "N/A"

    // Default promoted class rule: Grade 4 promotes to Grade 5
    val defaultDestinationClass = remember(currentClassName) {
        val digits = currentClassName.filter { it.isDigit() }
        val num = digits.toIntOrNull() ?: 1
        if (num >= 4) {
            "Class 5 (Fasalka 5aad)"
        } else {
            "Class ${num + 1}"
        }
    }

    var academicYearInput by remember { mutableStateOf("2025/2026") }
    var destinationClassInput by remember(defaultDestinationClass) { mutableStateOf(defaultDestinationClass) }

    // Dynamic subjects directly from exams of this student's class
    val classExams = remember(currentStudent, exams) {
        if (currentStudent == null) emptyList()
        else exams.filter { it.classId == currentStudent.classId }
    }

    // Official 7 Standard Primary/Middle School Subjects (Strictly 7 Subjects)
    val standard7Subjects = remember {
        listOf(
            "Diinta Islaamka",
            "Af-Soomaali",
            "Xisaab",
            "Saynis",
            "Cilmiga Bulshada",
            "English",
            "Carabi"
        )
    }

    val dynamicSubjects = standard7Subjects

    // Auto-calculate marksheet data for current student from actual exams
    val (subjectRows, grandTotal, overallAvg, overallGrade, overallPassed) = remember(
        currentStudent,
        currentClass,
        classExams,
        allMarks,
        dynamicSubjects
    ) {
        if (currentStudent == null) {
            Tuple5(emptyList(), 0.0, 0.0, "F", false)
        } else {
            var sumTotals = 0.0
            var validSubjectsCount = 0

            val rows = dynamicSubjects.map { subName ->
                val t1Val = getAutoSubjectMarkScore(subName, isTerm2 = false, classExams = classExams, allMarks = allMarks, studentId = currentStudent.id)
                val t2Val = getAutoSubjectMarkScore(subName, isTerm2 = true, classExams = classExams, allMarks = allMarks, studentId = currentStudent.id)

                val t1Str = t1Val?.let { fmtVal(it) } ?: "-"
                val t2Str = t2Val?.let { fmtVal(it) } ?: "-"

                val totVal = if (t1Val != null || t2Val != null) {
                    (t1Val ?: 0.0) + (t2Val ?: 0.0)
                } else null

                val totStr = totVal?.let { fmtVal(it) } ?: "-"

                val avgVal = if (t1Val != null && t2Val != null) {
                    totVal!! / 2.0
                } else totVal

                val avgStr = avgVal?.let { fmtVal(it) } ?: "-"

                val resStr = when {
                    totVal == null -> "-"
                    (totVal ?: 0.0) >= 50.0 || (avgVal ?: 0.0) >= 25.0 -> "PASSED"
                    else -> "FAILED"
                }

                if (totVal != null) {
                    sumTotals += totVal
                    validSubjectsCount++
                }

                MarksheetSubjectRow(
                    subjectName = subName,
                    term1Str = t1Str,
                    term2Str = t2Str,
                    totalStr = totStr,
                    avgStr = avgStr,
                    resultStr = resStr,
                    term1Val = t1Val,
                    term2Val = t2Val,
                    totalVal = totVal
                )
            }

            val maxPossible = 700.0 // Exactly 7 subjects * 100 marks each
            val overallPct = if (maxPossible > 0) (sumTotals / maxPossible) * 100.0 else 0.0
            val isPassed = sumTotals >= 350.0 && sumTotals > 0.0 // 50% pass mark out of 700

            val grade = when {
                overallPct >= 90 -> "A"
                overallPct >= 80 -> "B"
                overallPct >= 70 -> "C"
                overallPct >= 50 -> "D"
                else -> "F"
            }

            Tuple5(rows, sumTotals, overallPct, grade, isPassed)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📜 Student Marksheet (Official Results)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White) },
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
            // Grade 5-8 Restriction Info Badge
            item {
                Surface(
                    color = TealPrimary.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, TealPrimary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("🏷️", fontSize = 14.sp)
                        Column {
                            Text(
                                "Warqadda Natiijada & Marksheet-ka: Fasalada 1aad – 4aad",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TealDark
                            )
                            Text(
                                "Diiwaanka 7-da Maado ee Rasmiga ah (Wadar 700 Buundo) • Lower Primary Classes (1aad - 4aad)",
                                fontSize = 10.sp,
                                color = MutedText
                            )
                        }
                    }
                }
            }

            // Student Search & Filter Card (Compact Icon Header)
            item {
                StudentSearchFilterCard(
                    students = eligibleStudents,
                    classes = eligibleClasses,
                    selectedStudentId = currentStudent?.id,
                    onStudentSelected = { selectedStudentId = it.id }
                )
            }

            // Print & Document Setup Card
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
                        Text("⚙️ Marksheet Setup & Print Configuration", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TealPrimary)

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = academicYearInput,
                                onValueChange = { academicYearInput = it },
                                label = { Text("Academic Year", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = destinationClassInput,
                                onValueChange = { destinationClassInput = it },
                                label = { Text("Promoted Class", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    if (currentStudent != null) {
                                        onPrintMarksheetHtml(
                                            context,
                                            currentStudent,
                                            academicYearInput,
                                            destinationClassInput
                                        )
                                    }
                                },
                                enabled = currentStudent != null,
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
                                    val classId = currentStudent?.classId ?: 0L
                                    onPrintAllClassMarksheetsHtml(
                                        context,
                                        classId,
                                        academicYearInput,
                                        destinationClassInput
                                    )
                                },
                                enabled = currentStudent != null,
                                colors = ButtonDefaults.buttonColors(containerColor = TealDark),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("📚 Fasalka (All PDF)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Document Preview Header
            item {
                Text("👁️ Official Student Marksheet Preview:", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
            }

            // Paper Official Marksheet Card Preview
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
                        // Official Header
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF006A6B), RoundedCornerShape(4.dp))
                                .padding(8.dp)
                        ) {
                            Text("MINISTRY OF EDUCATION & SCIENCE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF104A4B))
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(schoolName.ifBlank { "PRIMARY & MIDDLE SCHOOL" }.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF006A6B))
                            Spacer(modifier = Modifier.height(4.dp))

                            Surface(
                                color = Color(0xFF006A6B),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "OFFICIAL STUDENT MARKSHEET",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // 1. Student Profile Data
                        Text("1. STUDENT PROFILE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF006A6B))
                        Card(
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(0.5.dp, Color.LightGray)
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                MarksheetDataRow("Student Full Name:", currentStudent?.name ?: "-")
                                MarksheetDataRow("Mother's Name:", currentStudent?.motherName?.ifBlank { "N/A" } ?: "-")
                                MarksheetDataRow("Student ID / Roll No:", currentStudent?.studentId ?: "-")
                                MarksheetDataRow("Gender:", currentStudent?.gender ?: "-")
                                MarksheetDataRow("Guardian Phone:", currentStudent?.phone?.ifBlank { "N/A" } ?: "-")
                                MarksheetDataRow("Class:", currentClassName)
                                MarksheetDataRow("Academic Year:", academicYearInput)
                                MarksheetDataRow("Promoted Destination Class:", destinationClassInput)
                            }
                        }

                        // 2. Exam Scores Table
                        Text("2. ANNUAL ACADEMIC EXAM RESULTS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF006A6B))

                        Surface(
                            color = Color.White,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0xFF006A6B), RoundedCornerShape(4.dp))
                        ) {
                            Column(modifier = Modifier.padding(4.dp)) {
                                // Table Header
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF006A6B))
                                        .padding(vertical = 4.dp, horizontal = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Subject", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.weight(2f))
                                    Text("Term 1 (/50)", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.weight(1.2f))
                                    Text("Term 2 (/50)", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.weight(1.2f))
                                    Text("Total (/100)", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, modifier = Modifier.weight(1.2f))
                                    Text("Status", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
                                }

                                HorizontalDivider(color = Color(0xFF006A6B), thickness = 1.dp)

                                // Rows
                                subjectRows.forEach { row ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp, horizontal = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(row.subjectName, fontSize = 9.sp, color = DarkText, modifier = Modifier.weight(2f), maxLines = 1)
                                        Text(row.term1Str.ifBlank { "—" }, fontSize = 9.sp, color = if (row.term1Str.isNotBlank()) TealDark else Color.LightGray, textAlign = TextAlign.Center, modifier = Modifier.weight(1.2f))
                                        Text(row.term2Str.ifBlank { "—" }, fontSize = 9.sp, color = if (row.term2Str.isNotBlank()) TealDark else Color.LightGray, textAlign = TextAlign.Center, modifier = Modifier.weight(1.2f))
                                        Text(row.totalStr.ifBlank { "—" }, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if (row.totalStr.isNotBlank()) TealDark else Color.LightGray, textAlign = TextAlign.Center, modifier = Modifier.weight(1.2f))

                                        val resColor = if (row.resultStr == "PASSED") PassGreen else if (row.resultStr == "FAILED") FailRed else Color.LightGray
                                        Text(row.resultStr.ifBlank { "—" }, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = resColor, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
                                    }
                                    HorizontalDivider(color = Color(0xFFAAAAAA).copy(alpha = 0.2f), thickness = 0.5.dp)
                                }

                                // Summary Rows
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF0F7F7))
                                        .padding(horizontal = 4.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    MarksheetSummaryLine("Grand Total Score:", "${fmtVal(grandTotal)} / ${dynamicSubjects.size * 100}")
                                    MarksheetSummaryLine("Overall Average:", String.format(java.util.Locale.US, "%.1f%%", overallAvg))
                                    MarksheetSummaryLine("Grade:", overallGrade)
                                    MarksheetSummaryLine("Final Status:", if (overallPassed) "PASSED" else "FAILED", highlight = true, isPass = overallPassed)
                                }
                            }
                        }

                        // 3. Official Status & Motivation Note (Somali)
                        Text("3. OFFICIAL REMARKS & MOTIVATION (NATIIJADA & DHIIRIGELINTA)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF006A6B))
                        Card(
                            shape = RoundedCornerShape(4.dp),
                            colors = CardDefaults.cardColors(containerColor = if (overallPassed) Color(0xFFF0FDF4) else Color(0xFFFEF2F2)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, if (overallPassed) PassGreen else FailRed, RoundedCornerShape(4.dp))
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                if (overallPassed) {
                                    Text("🎉 HAMBALYO!", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PassGreen)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "“Waxaan kuu hambalyaynaynaa guusha aad ka gaadhay imtixaanka sanad-dugsiyeedka. Dadaalkaaga iyo horumarkaaga sii wad, kuna dadaal inaad mar kasta gaadho heer ka sarreeya.”",
                                        fontSize = 9.sp,
                                        color = DarkText,
                                        lineHeight = 13.sp
                                    )
                                } else {
                                    Text("📌 DARDARAN IYO DHIIRIGELIN", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FailRed)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "“Ha niyad jabin. Guuldarradu ma aha dhammaadka waxbarashada, ee waa fursad aad ku ogaan karto meelaha aad u baahan tahay inaad ku dadaasho. Dib u eeg casharradaada, dadaalka kordhi, waqtiga si wanaagsan uga faa’iidayso, waxaana rajaynaynaa inaad sannadka dambe guul weyn gaadho.”",
                                        fontSize = 9.sp,
                                        color = DarkText,
                                        lineHeight = 13.sp
                                    )
                                }
                            }
                        }

                        // 4. Signatures & Stamp
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
                                Text("Principal: __________________________", fontSize = 8.sp, color = DarkText)
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

private data class Tuple5<A, B, C, D, E>(val a: A, val b: B, val c: C, val d: D, val e: E)

fun cleanExamSubjectName(raw: String): String {
    return raw.replace(Regex("(?i)\\b(term\\s*[12]|t[12]|sem\\s*[12]|semester\\s*[12]|midterm|final|imtixaan|imtixaanka|exam|teeramka\\s*[12]aad)\\b"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { raw.trim() }
}

private fun getAutoSubjectMarkScore(
    subjectName: String,
    isTerm2: Boolean,
    classExams: List<Exam>,
    allMarks: List<ExamMark>,
    studentId: Long
): Double? {
    val matchedExam = classExams.find { exam ->
        val text = (exam.name + " " + exam.subject).lowercase()
        val examIsTerm2 = text.contains("term 2") || text.contains("term2") || text.contains("t2") || text.contains("sem 2") || text.contains("final") || text.contains("2nd") || text.contains("teeramka 2")
        val isCorrectTerm = if (isTerm2) examIsTerm2 else !examIsTerm2

        val subClean = cleanExamSubjectName(exam.subject.ifBlank { exam.name }).lowercase()
        val targetClean = subjectName.lowercase()

        val matchesSubject = when {
            targetClean.contains("diin") ->
                subClean == "diin" || subClean == "diinta" || subClean == "tarbiyo" || subClean == "tarbiya" ||
                subClean.contains("diin") || subClean.contains("islam") || subClean.contains("tarbiya") || subClean.contains("tarbiyo") ||
                text.contains("diin") || text.contains("islam") || text.contains("tarbiya") || text.contains("tarbiyo")

            targetClean.contains("xisaab") ->
                subClean == "xis" || subClean == "math" || subClean == "maths" ||
                subClean.contains("xisaab") || subClean.contains("math") ||
                text.contains("xisaab") || text.contains("math") || text.contains("xis")

            targetClean.contains("saynis") ->
                subClean == "say" || subClean == "sci" || subClean == "science" ||
                subClean.contains("saynis") || subClean.contains("science") ||
                text.contains("saynis") || text.contains("science") || text.contains("say")

            targetClean.contains("soomaali") ->
                subClean == "som" || subClean == "somali" || subClean == "af-soomaali" || subClean == "af soomaali" ||
                subClean.contains("soomaali") || subClean.contains("somali") ||
                text.contains("soomaali") || text.contains("somali") || text.contains("som")

            targetClean.contains("english") ->
                subClean == "eng" || subClean == "ingiriis" || subClean == "ingriis" ||
                subClean.contains("english") || subClean.contains("ingiriis") ||
                text.contains("english") || text.contains("ingiriis") || text.contains("eng")

            targetClean.contains("carabi") ->
                subClean == "car" || subClean == "arabic" || subClean == "luuqada carabiga" ||
                subClean.contains("carabi") || subClean.contains("arabic") ||
                text.contains("carabi") || text.contains("arabic") || text.contains("car")

            targetClean.contains("bulshada") ->
                subClean == "c/b" || subClean == "cb" || subClean == "soc" || subClean == "social" ||
                subClean.contains("bulshada") || subClean.contains("social") || subClean.contains("c/b") ||
                text.contains("bulshada") || text.contains("social") || text.contains("c/b") || text.contains("cb")

            else -> subClean == targetClean || subClean.contains(targetClean) || targetClean.contains(subClean)
        }

        isCorrectTerm && matchesSubject
    }

    if (matchedExam != null) {
        val mark = allMarks.find { it.examId == matchedExam.id && it.studentId == studentId }
        if (mark != null && !mark.isAbsent) {
            var score = mark.score
            if (matchedExam.totalMarks == 100.0 && score > 50.0) {
                score /= 2.0
            }
            return score.coerceAtMost(50.0)
        }
    }
    return null
}

private fun fmtVal(n: Double): String {
    return if (n % 1.0 == 0.0) n.toInt().toString() else String.format(java.util.Locale.US, "%.1f", n)
}

@Composable
private fun MarksheetDataRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 9.sp, color = MutedText, fontWeight = FontWeight.Medium)
        Text(value, fontSize = 9.sp, color = DarkText, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MarksheetSummaryLine(label: String, value: String, highlight: Boolean = false, isPass: Boolean = true) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = TealDark)
        Text(
            text = value,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (highlight) (if (isPass) PassGreen else FailRed) else DarkText
        )
    }
}
