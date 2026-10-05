package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SchoolClass
import com.example.data.Student
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFeeScreen(
    students: List<Student>,
    classes: List<SchoolClass>,
    onSaveFeeClick: (Long, Long, String, Double, String, String, String, String) -> Unit,
    onBackClick: () -> Unit
) {
    var selectedClassId by remember(classes) { mutableLongStateOf(classes.firstOrNull()?.id ?: 0L) }
    val classStudents = remember(students, selectedClassId) {
        students.filter { it.classId == selectedClassId && !it.isFree }
    }
    var selectedStudentId by remember(classStudents) { mutableLongStateOf(classStudents.firstOrNull()?.id ?: 0L) }

    var feeType by remember { mutableStateOf("Tuition") }
    var amountText by remember { mutableStateOf("20") }
    var currency by remember { mutableStateOf("USD") }
    var dueDate by remember { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())) }
    var month by remember { mutableStateOf(SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())) }
    var notes by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add Fee Invoice", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Fee Invoice Details", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TealDark)

                    errorMessage?.let {
                        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    }

                    // Class Selector
                    Column {
                        Text("Select Class:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            items(classes) { cls ->
                                FilterChip(
                                    selected = selectedClassId == cls.id,
                                    onClick = { selectedClassId = cls.id },
                                    label = { Text(cls.name, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    // Student Selector
                    Column {
                        Text("Select Student (Fee-Paying):", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (classStudents.isEmpty()) {
                            Text("No fee-paying students found in this class.", fontSize = 11.sp, color = MutedText)
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                items(classStudents) { s ->
                                    FilterChip(
                                        selected = selectedStudentId == s.id,
                                        onClick = { selectedStudentId = s.id },
                                        label = { Text(s.name, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }

                    // Amount & Currency
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = amountText,
                            onValueChange = { amountText = it },
                            label = { Text("Amount") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text("Currency:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("USD", "SLS", "ETB").forEach { curr ->
                                    FilterChip(
                                        selected = currency == curr,
                                        onClick = { currency = curr },
                                        label = { Text(curr, fontSize = 10.sp) }
                                    )
                                }
                            }
                        }
                    }

                    // Fee Type
                    Column {
                        Text("Fee Category:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                            listOf("Tuition", "Exam", "Transport", "Registration").forEach { type ->
                                FilterChip(
                                    selected = feeType == type,
                                    onClick = { feeType = type },
                                    label = { Text(type, fontSize = 11.sp) }
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = month,
                        onValueChange = { month = it },
                        label = { Text("Billing Month") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Notes (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            val amt = amountText.toDoubleOrNull()
                            if (selectedClassId == 0L || selectedStudentId == 0L || amt == null || amt <= 0) {
                                errorMessage = "Please enter valid class, student, and amount"
                                return@Button
                            }
                            onSaveFeeClick(selectedClassId, selectedStudentId, feeType, amt, currency, dueDate, month, notes)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create Fee Invoice", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
