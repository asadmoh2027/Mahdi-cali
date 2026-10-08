package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Exam
import com.example.data.ExamMark
import com.example.data.SchoolClass
import com.example.data.Student
import com.example.ui.theme.*

// Official 7 Subjects from Ministry Report Template
val OFFICIAL_SUBJECTS = listOf(
    "Diin" to "Diinta Islam",
    "Som" to "Soomaali",
    "Car" to "Carabi",
    "Eng" to "English",
    "Xis" to "Xisaab",
    "Say" to "Saynis",
    "C/B" to "Cilmiga Bulshada"
)

val OFFICIAL_TERMS = listOf("Term 1", "Term 2")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamsScreen(
    exams: List<Exam>,
    classes: List<SchoolClass>,
    currentUser: com.example.data.User? = null,
    onAddExamClick: (Long, String, String, Double, Double, String) -> Unit,
    onUpdateExamClick: (Exam) -> Unit,
    onEditMarksClick: (Exam) -> Unit,
    onDeleteExamClick: (Long) -> Unit,
    onBackClick: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var examToEdit by remember { mutableStateOf<Exam?>(null) }
    var examToDelete by remember { mutableStateOf<Exam?>(null) }

    val accessibleClasses: List<SchoolClass> = remember(classes, currentUser) {
        if (currentUser == null || currentUser.role == "ADMIN" || currentUser.role == "CASHIER") {
            classes
        } else {
            val assigned = currentUser.getAssignedClassIdSet()
            classes.filter { it.id in assigned }
        }
    }

    val visibleExams: List<Exam> = remember(exams, accessibleClasses, currentUser) {
        if (currentUser == null || currentUser.role == "ADMIN" || currentUser.role == "CASHIER") {
            exams
        } else {
            val assigned = currentUser.getAssignedClassIdSet()
            exams.filter { it.classId in assigned }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("📝 Examinations & Subjects", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = TealPrimary,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Subject / Exam")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            if (visibleExams.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No exams or subjects created yet for your classes. Tap '+' to add a subject/exam.", color = MutedText, fontSize = 13.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(visibleExams, key = { it.id }) { exam ->
                        val clsName = classes.find { it.id == exam.classId }?.name ?: "Unknown Class"
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Surface(
                                            color = TealContainer,
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = exam.subject.ifBlank { "Subject" },
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TealDark,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Text(
                                            text = exam.name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TealPrimary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "Class: $clsName • Date: ${exam.date.ifBlank { "N/A" }}",
                                        fontSize = 11.sp,
                                        color = DarkText
                                    )
                                    Text(
                                        text = "Max Marks: ${exam.totalMarks} • Pass Marks: ${exam.passMarks}",
                                        fontSize = 11.sp,
                                        color = MutedText
                                    )
                                }

                                Column(
                                    horizontalAlignment = Alignment.End,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        // Edit Subject Button
                                        OutlinedButton(
                                            onClick = { examToEdit = exam },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TealPrimary),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text("Edit Subject", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }

                                        // Marks Entry Button
                                        Button(
                                            onClick = { onEditMarksClick(exam) },
                                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text("Marks", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }

                                        IconButton(
                                            onClick = { examToDelete = exam },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = FailRed)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AddExamDialog(
                classes = accessibleClasses,
                onDismiss = { showAddDialog = false },
                onSave = { classId, name, subject, total, pass, date ->
                    onAddExamClick(classId, name, subject, total, pass, date)
                    showAddDialog = false
                }
            )
        }

        examToEdit?.let { exam ->
            EditSubjectDialog(
                exam = exam,
                classes = accessibleClasses,
                onDismiss = { examToEdit = null },
                onSave = { updatedExam ->
                    onUpdateExamClick(updatedExam)
                    examToEdit = null
                }
            )
        }

        examToDelete?.let { exam ->
            val clsName = classes.find { it.id == exam.classId }?.name ?: "Unknown"
            AlertDialog(
                onDismissRequest = { examToDelete = null },
                title = { Text("Tirtir Imtixaanka (Delete Exam)?", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Ma hubtaa inaad tirtirto imtixaanka '${exam.name}' (${exam.subject}) ee fasalka $clsName? Buundooyinka imtixaankan lagu duubay dhammaan waa la tirtiri doonaa.",
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeleteExamClick(exam.id)
                            examToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FailRed)
                    ) {
                        Text("Haa, Tirtir", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { examToDelete = null }) {
                        Text("Jooji")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddExamDialog(
    classes: List<SchoolClass>,
    onDismiss: () -> Unit,
    onSave: (Long, String, String, Double, Double, String) -> Unit
) {
    var selectedClassId by remember { mutableLongStateOf(classes.firstOrNull()?.id ?: 0L) }
    var selectedSubjectKey by remember { mutableStateOf("Diin") }
    var selectedTerm by remember { mutableStateOf("Term 1") }
    var examName by remember { mutableStateOf("Term 1 - Diin") }
    var totalMarksStr by remember { mutableStateOf("50") }
    var passMarksStr by remember { mutableStateOf("25") }
    var date by remember { mutableStateOf("2026-02-15") }

    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("➕ Add New Subject Exam", fontWeight = FontWeight.Bold, color = TealPrimary) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text("1. Select Class *", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    classes.forEach { cls ->
                        FilterChip(
                            selected = selectedClassId == cls.id,
                            onClick = { selectedClassId = cls.id },
                            label = { Text(cls.name, fontSize = 11.sp) }
                        )
                    }
                }

                Text("2. Select Subject (7 Standard Subjects) *", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OFFICIAL_SUBJECTS.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedSubjectKey == key,
                            onClick = {
                                selectedSubjectKey = key
                                examName = "$selectedTerm - $key"
                            },
                            label = { Text("$key ($label)", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }

                Text("3. Select Term *", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OFFICIAL_TERMS.forEach { term ->
                        FilterChip(
                            selected = selectedTerm == term,
                            onClick = {
                                selectedTerm = term
                                examName = "$term - $selectedSubjectKey"
                            },
                            label = { Text(term, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = examName,
                    onValueChange = { examName = it },
                    label = { Text("Exam Name / Description *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = totalMarksStr,
                        onValueChange = { totalMarksStr = it },
                        label = { Text("Max Marks") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Right) }),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = passMarksStr,
                        onValueChange = { passMarksStr = it },
                        label = { Text("Pass Marks") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val total = totalMarksStr.toDoubleOrNull() ?: 100.0
                    val pass = passMarksStr.toDoubleOrNull() ?: 40.0
                    if (examName.isNotBlank() && selectedClassId != 0L) {
                        onSave(selectedClassId, examName, selectedSubjectKey, total, pass, date)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
            ) {
                Text("SAVE SUBJECT EXAM")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditSubjectDialog(
    exam: Exam,
    classes: List<SchoolClass>,
    onDismiss: () -> Unit,
    onSave: (Exam) -> Unit
) {
    var selectedClassId by remember { mutableLongStateOf(exam.classId) }
    var selectedSubjectKey by remember { mutableStateOf(exam.subject.ifBlank { "Diin" }) }
    var examName by remember { mutableStateOf(exam.name) }
    var totalMarksStr by remember { mutableStateOf(if (exam.totalMarks % 1.0 == 0.0) exam.totalMarks.toInt().toString() else exam.totalMarks.toString()) }
    var passMarksStr by remember { mutableStateOf(if (exam.passMarks % 1.0 == 0.0) exam.passMarks.toInt().toString() else exam.passMarks.toString()) }
    var date by remember { mutableStateOf(exam.date) }

    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("✏️ Edit Subject Details", fontWeight = FontWeight.Bold, color = TealPrimary) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text("Class *", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    classes.forEach { cls ->
                        FilterChip(
                            selected = selectedClassId == cls.id,
                            onClick = { selectedClassId = cls.id },
                            label = { Text(cls.name, fontSize = 11.sp) }
                        )
                    }
                }

                Text("Choose Subject *", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    OFFICIAL_SUBJECTS.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedSubjectKey == key,
                            onClick = {
                                selectedSubjectKey = key
                                if (!examName.contains(key)) {
                                    examName = if (examName.contains("-")) {
                                        examName.substringBefore("-") + "- " + key
                                    } else {
                                        "$examName - $key"
                                    }
                                }
                            },
                            label = { Text("$key ($label)", fontSize = 10.sp, fontWeight = FontWeight.SemiBold) }
                        )
                    }
                }

                OutlinedTextField(
                    value = selectedSubjectKey,
                    onValueChange = { selectedSubjectKey = it },
                    label = { Text("Subject Code / Key *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = examName,
                    onValueChange = { examName = it },
                    label = { Text("Exam Name / Description *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = totalMarksStr,
                        onValueChange = { totalMarksStr = it },
                        label = { Text("Max Marks") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = passMarksStr,
                        onValueChange = { passMarksStr = it },
                        label = { Text("Pass Marks") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val total = totalMarksStr.toDoubleOrNull() ?: 100.0
                    val pass = passMarksStr.toDoubleOrNull() ?: 40.0
                    if (examName.isNotBlank() && selectedClassId != 0L) {
                        onSave(
                            exam.copy(
                                classId = selectedClassId,
                                name = examName.trim(),
                                subject = selectedSubjectKey.trim(),
                                totalMarks = total,
                                passMarks = pass,
                                date = date.trim()
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
            ) {
                Text("UPDATE SUBJECT")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMarksScreen(
    exam: Exam,
    classes: List<SchoolClass>,
    students: List<Student>,
    existingMarks: List<ExamMark>,
    onUpdateExamClick: (Exam) -> Unit,
    onSaveMarksClick: (List<ExamMark>) -> Unit,
    onBackClick: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var showEditSubjectDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Local state map of studentId -> Score String & Absent state
    val scoreMap = remember(exam.id, students, existingMarks) {
        val map = mutableStateMapOf<Long, String>()
        students.forEach { s ->
            val m = existingMarks.find { it.studentId == s.id }
            map[s.id] = if (m != null && !m.isAbsent) {
                if (m.score == 0.0) "" else if (m.score % 1.0 == 0.0) m.score.toInt().toString() else m.score.toString()
            } else ""
        }
        map
    }

    val absentMap = remember(exam.id, students, existingMarks) {
        val map = mutableStateMapOf<Long, Boolean>()
        students.forEach { s ->
            val m = existingMarks.find { it.studentId == s.id }
            map[s.id] = m?.isAbsent == true
        }
        map
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("✏️ Enter Marks: ${exam.subject} (${exam.name})", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
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
                .padding(16.dp)
        ) {
            // Exam Header Banner
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = TealContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Surface(color = TealDark, shape = RoundedCornerShape(4.dp)) {
                                Text(exam.subject, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                            }
                            Text(exam.name, fontWeight = FontWeight.Bold, color = TealDark, fontSize = 14.sp)
                        }
                        Text("Max Marks: ${exam.totalMarks} • Pass Marks: ${exam.passMarks}", fontSize = 11.sp, color = DarkText)
                    }

                    OutlinedButton(
                        onClick = { showEditSubjectDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TealDark),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit Subject", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            errorMessage?.let { err ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    Text(
                        text = "❌ $err",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }

            if (students.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text("No students in this class to enter marks.", color = MutedText, fontSize = 13.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(students, key = { it.id }) { student ->
                        val isAbsent = absentMap[student.id] == true
                        val currentScoreStr = scoreMap[student.id] ?: ""

                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isAbsent) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(student.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text("ID: ${student.studentId}", fontSize = 10.sp, color = MutedText)
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Absent Switch
                                    FilterChip(
                                        selected = isAbsent,
                                        onClick = { absentMap[student.id] = !isAbsent },
                                        label = { Text(if (isAbsent) "ABSENT" else "Present", fontSize = 10.sp) }
                                    )

                                    // Score TextField with ImeAction.Next for fast sequential input across lines!
                                    OutlinedTextField(
                                        value = if (isAbsent) "0" else currentScoreStr,
                                        onValueChange = { input ->
                                            val filtered = input.filter { c -> c.isDigit() || c == '.' }
                                            val cleaned = if (currentScoreStr == "0" && filtered.length > 1 && filtered.startsWith("0")) {
                                                filtered.substring(1)
                                            } else {
                                                filtered
                                            }
                                            scoreMap[student.id] = cleaned
                                        },
                                        placeholder = { Text("0", color = MutedText) },
                                        enabled = !isAbsent,
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number,
                                            imeAction = ImeAction.Next
                                        ),
                                        keyboardActions = KeyboardActions(
                                            onNext = { focusManager.moveFocus(FocusDirection.Down) }
                                        ),
                                        modifier = Modifier.width(80.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    errorMessage = null
                    var hasError = false
                    for (s in students) {
                        if (s.classId != exam.classId) {
                            errorMessage = "Ardaygani (${s.name}) kama tirsana fasalka aad dooratay."
                            hasError = true
                            break
                        }
                        val isAbs = absentMap[s.id] == true
                        val scoreVal = scoreMap[s.id]?.toDoubleOrNull() ?: 0.0
                        if (!isAbs && (scoreVal < 0.0 || scoreVal > exam.totalMarks)) {
                            errorMessage = "Dhibcaha ardayga ${s.name} (${scoreVal}) waa inay ka dhaxeeyaan 0 ilaa ${exam.totalMarks.toInt()}!"
                            hasError = true
                            break
                        }
                    }

                    if (!hasError) {
                        val list = students.map { s ->
                            val isAbs = absentMap[s.id] == true
                            val scoreVal = scoreMap[s.id]?.toDoubleOrNull() ?: 0.0
                            ExamMark(
                                examId = exam.id,
                                studentId = s.id,
                                score = if (isAbs) 0.0 else scoreVal,
                                isAbsent = isAbs
                            )
                        }
                        onSaveMarksClick(list)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("SAVE MARKS", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }

        if (showEditSubjectDialog) {
            EditSubjectDialog(
                exam = exam,
                classes = classes,
                onDismiss = { showEditSubjectDialog = false },
                onSave = { updatedExam ->
                    onUpdateExamClick(updatedExam)
                    showEditSubjectDialog = false
                }
            )
        }
    }
}
