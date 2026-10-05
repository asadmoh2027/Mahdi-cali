package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.StudentReportCard
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentPortalScreen(
    reportCard: StudentReportCard?,
    onSearchClick: (String) -> Unit,
    onPrintClick: (StudentReportCard) -> Unit,
    onBackClick: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("AUTO-001") }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎓 Student Result Portal", fontSize = 18.sp, fontWeight = FontWeight.Bold) },
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
            // Search Bar
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🔍 Enter Student ID to Check Exam Results",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TealPrimary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("e.g. AUTO-001") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { onSearchClick(searchQuery) }),
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = { onSearchClick(searchQuery) },
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SEARCH", fontWeight = FontWeight.Bold)
                        }
                    }

                    // Quick Chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        Text("Samples:", fontSize = 11.sp, color = MutedText, modifier = Modifier.align(Alignment.CenterVertically))
                        listOf("AUTO-001", "AUTO-002", "AUTO-003").forEach { id ->
                            SuggestionChip(
                                onClick = {
                                    searchQuery = id
                                    onSearchClick(id)
                                },
                                label = { Text(id, fontSize = 10.sp) }
                            )
                        }
                    }
                }
            }

            // Results Display
            if (reportCard == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Enter a valid Student ID above to view digital report card.",
                        color = MutedText,
                        fontSize = 13.sp
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    // Header Student Details Card
                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = TealContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = reportCard.student.name,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TealDark
                                        )
                                        Text(
                                            text = "ID: ${reportCard.student.studentId} • Class: ${reportCard.className}",
                                            fontSize = 12.sp,
                                            color = MutedText
                                        )
                                    }

                                    IconButton(
                                        onClick = { onPrintClick(reportCard) },
                                        modifier = Modifier.background(TealPrimary, shape = RoundedCornerShape(10.dp))
                                    ) {
                                        Icon(Icons.Default.Print, contentDescription = "Print", tint = Color.White)
                                    }
                                }

                                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = TealDark.copy(alpha = 0.2f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Gender: ${reportCard.student.gender}", fontSize = 11.sp, color = DarkText)
                                    Text("Mother: ${reportCard.student.motherName}", fontSize = 11.sp, color = DarkText)
                                    Text("Phone: ${reportCard.student.phone}", fontSize = 11.sp, color = DarkText)
                                }
                            }
                        }
                    }

                    // Key Overview Metrics
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Average %
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (reportCard.averagePercentage >= 50) GoldContainer else MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth()
                                ) {
                                    Text(
                                        text = String.format("%.1f%%", reportCard.averagePercentage),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (reportCard.averagePercentage >= 50) TealDark else FailRed
                                    )
                                    Text("Average Score", fontSize = 10.sp, color = DarkText)
                                }
                            }

                            // Passed Exams
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${reportCard.totalPassed} / ${reportCard.totalExams}",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PassGreen
                                    )
                                    Text("Passed Exams", fontSize = 10.sp, color = DarkText)
                                }
                            }

                            // Attendance %
                            Card(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = TealContainer)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .padding(12.dp)
                                        .fillMaxWidth()
                                ) {
                                    Text(
                                        text = String.format("%.0f%%", reportCard.attendanceRate),
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TealDark
                                    )
                                    Text("Attendance Rate", fontSize = 10.sp, color = DarkText)
                                }
                            }
                        }
                    }

                    // Pending Fees Notification
                    item {
                        if (reportCard.pendingFees.isNotEmpty()) {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = FailRed)
                                    Column {
                                        Text(
                                            text = "⚠️ Pending Fee Notification",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        val feeSummary = reportCard.pendingFees.joinToString(", ") { "${it.feeType}: ${it.amount} ${it.currency}" }
                                        Text(
                                            text = feeSummary,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        } else {
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = TealContainer),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PassGreen)
                                    Text(
                                        text = "Fee Status: Clear (No Pending Payments)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TealDark
                                    )
                                }
                            }
                        }
                    }

                    // Exam Marks Table Header
                    item {
                        Text(
                            text = "Subject Marks & Performance",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TealPrimary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    // Subject Results List
                    items(reportCard.subjectResults) { res ->
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                                    Text(
                                        text = res.subject,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = res.examName,
                                        fontSize = 11.sp,
                                        color = MutedText
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${res.score} / ${res.totalMarks}",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        val pct = if (res.totalMarks > 0) (res.score / res.totalMarks) * 100 else 0.0
                                        Text(
                                            text = String.format("%.1f%%", pct),
                                            fontSize = 10.sp,
                                            color = MutedText
                                        )
                                    }

                                    Surface(
                                        color = if (res.isAbsent) MaterialTheme.colorScheme.surfaceVariant else if (res.isPassed) GoldContainer else MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text(
                                            text = if (res.isAbsent) "ABSENT" else if (res.isPassed) "PASS" else "FAIL",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (res.isAbsent) MutedText else if (res.isPassed) TealDark else FailRed,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
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
