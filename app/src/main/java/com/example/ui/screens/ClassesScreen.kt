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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SchoolClass
import com.example.data.User
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassesScreen(
    classes: List<SchoolClass>,
    currentUser: User? = null,
    onAddClassClick: (String, String, String, String) -> Unit,
    onDeleteClassClick: (Long) -> Unit,
    onBackClick: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var classToDelete by remember { mutableStateOf<SchoolClass?>(null) }
    val isAdmin = currentUser == null || currentUser.role == "ADMIN"
    val isCashier = currentUser?.role == "CASHIER"
    val isTeacher = currentUser?.role == "TEACHER"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("🏫 School Classes", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            text = if (isAdmin) "Admin Access • Manage All Classes" else if (isCashier) "Cashier View • All Classes (Read-Only)" else "Teacher View • Assigned Classes",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                },
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
            if (isAdmin) {
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = TealPrimary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Class")
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isTeacher) {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = TealDark, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Fasallada hoose: Waxaad kaliya maamuli kartaa fasalka laguu qoondeeyay. Macalinku fasal cusub ma dari karo waxna ma tirtiri karo.",
                            fontSize = 11.sp,
                            color = DarkText,
                            lineHeight = 15.sp
                        )
                    }
                }
            } else if (isCashier) {
                Card(
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = TealContainer.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = TealDark, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Muuqaalka Cashier-ka: Waxaad arki kartaa dhammaan fasallada dugsiga iyo ardayda si aad u maamusho lacag-bixinta. Cashier-ku fasal cusub ma diiwaan gelin karo.",
                            fontSize = 11.sp,
                            color = DarkText,
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            if (classes.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No classes added yet. Tap '+' to create a class.", color = MutedText, fontSize = 13.sp)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(classes) { cls ->
                        val isAssignedToMe = isTeacher && (currentUser?.getAssignedClassIdSet()?.contains(cls.id) == true)

                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isAssignedToMe) TealContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
                            ),
                            border = if (isAssignedToMe) androidx.compose.foundation.BorderStroke(1.5.dp, TealPrimary) else null,
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = cls.name,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = TealPrimary
                                        )
                                        if (isAssignedToMe) {
                                            Surface(
                                                color = PassGreen,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "✓ Fasalkaaga",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    if (cls.inchargeTeacher.isNotBlank()) {
                                        Text(
                                            text = "👨‍🏫 Teacher: ${cls.inchargeTeacher}",
                                            fontSize = 12.sp,
                                            color = DarkText
                                        )
                                    }
                                    if (cls.startDate.isNotBlank() || cls.endDate.isNotBlank()) {
                                        Text(
                                            text = "📅 Duration: ${cls.startDate} - ${cls.endDate}",
                                            fontSize = 10.sp,
                                            color = MutedText
                                        )
                                    }
                                }

                                if (isAdmin) {
                                    IconButton(onClick = { classToDelete = cls }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = FailRed)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showAddDialog && isAdmin) {
            AddClassDialog(
                onDismiss = { showAddDialog = false },
                onSave = { name, teacher, start, end ->
                    onAddClassClick(name, teacher, start, end)
                    showAddDialog = false
                }
            )
        }

        classToDelete?.let { cls ->
            AlertDialog(
                onDismissRequest = { classToDelete = null },
                title = { Text("Tirtir Fasalka (Delete Class)?", fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Ma hubtaa inaad si rasmi ah u tirtirto fasalka '${cls.name}'? Xogta ardayda iyo diiwaanka ku jira fasalkan waxaa lagama maarmaan ah in la hubiyo.",
                        fontSize = 13.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            onDeleteClassClick(cls.id)
                            classToDelete = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = FailRed)
                    ) {
                        Text("Haa, Tirtir", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { classToDelete = null }) {
                        Text("Jooji")
                    }
                }
            )
        }
    }
}

@Composable
fun AddClassDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var incharge by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("01-09-2025") }
    var endDate by remember { mutableStateOf("30-06-2026") }

    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Class", fontWeight = FontWeight.Bold, color = TealPrimary) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Class Name *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = incharge,
                    onValueChange = { incharge = it },
                    label = { Text("Class Teacher") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = startDate,
                    onValueChange = { startDate = it },
                    label = { Text("Start Date") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = endDate,
                    onValueChange = { endDate = it },
                    label = { Text("End Date") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        focusManager.clearFocus()
                        if (name.isNotBlank()) onSave(name, incharge, startDate, endDate)
                    }),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onSave(name, incharge, startDate, endDate) },
                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
            ) {
                Text("SAVE CLASS")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("CANCEL") }
        }
    )
}
