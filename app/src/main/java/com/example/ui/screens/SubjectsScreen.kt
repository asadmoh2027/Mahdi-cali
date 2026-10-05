package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.Subject
import com.example.data.User
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectsScreen(
    currentUser: User?,
    subjects: List<Subject>,
    onAddSubject: (String, String, String, (Boolean, String) -> Unit) -> Unit,
    onDeleteSubject: (Long, (Boolean, String) -> Unit) -> Unit,
    onBackClick: () -> Unit
) {
    val isAdmin = currentUser?.role == "ADMIN" || currentUser?.role == "SUPER_ADMIN"
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }

    val filteredSubjects = subjects.filter {
        searchQuery.isBlank() || it.subjectName.contains(searchQuery, ignoreCase = true) || it.subjectCode.contains(searchQuery, ignoreCase = true)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("📚 Maadooyinka Dugsiga (Curriculum)", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (isAdmin) {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.AddCircle, contentDescription = "Add Subject", tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TealPrimary)
            )
        },
        floatingActionButton = {
            if (isAdmin) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = TealPrimary,
                    contentColor = Color.White,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Subject")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Raadi maaddo (tusaale: Xisaab, Carabi, English)...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = TealPrimary) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Summary Card
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = TealContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Wadarta Maadooyinka", fontSize = 12.sp, color = TealDark)
                        Text("${subjects.size} Maaddo", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TealDark)
                    }
                    Text("📖 Nidaamka Waxbarashada", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TealPrimary)
                }
            }

            if (filteredSubjects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📚", fontSize = 48.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Ma jiro wax maaddo ah hadda", fontWeight = FontWeight.Bold, color = DarkText)
                        Text("Guji badhanka hoose si aad maaddo cusub ugu darto.", fontSize = 12.sp, color = MutedText)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredSubjects, key = { it.id }) { subject ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = TealPrimary.copy(alpha = 0.12f),
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = subject.subjectCode.take(3),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = TealPrimary
                                            )
                                        }
                                    }

                                    Column {
                                        Text(
                                            text = subject.subjectName,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = DarkText
                                        )
                                        Text(
                                            text = "Code: ${subject.subjectCode} • Fasalka: ${subject.grade}",
                                            fontSize = 12.sp,
                                            color = MutedText
                                        )
                                    }
                                }

                                if (isAdmin) {
                                    IconButton(
                                        onClick = { onDeleteSubject(subject.id) { _, _ -> } }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete Subject",
                                            tint = Color.Red.copy(alpha = 0.7f)
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

    if (showAddDialog) {
        AddSubjectDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { code, name, grade ->
                onAddSubject(code, name, grade) { success, msg ->
                    if (success) showAddDialog = false
                }
            }
        )
    }
}

@Composable
fun AddSubjectDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf("All") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📚 Ku Dar Maaddo Cusub", fontWeight = FontWeight.Bold, color = TealPrimary) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Magaca Maaddada (tusaale: Xisaab)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Subject Code (tusaale: MATH101)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = grade,
                    onValueChange = { grade = it },
                    label = { Text("Heerka / Fasalka (tusaale: Form 1 ama All)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(code, name, grade) },
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
            ) {
                Text("Ku Dar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Ka noqo") }
        }
    )
}
