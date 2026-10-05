package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
import com.example.data.SchoolClass
import com.example.data.User
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddStudentScreen(
    nextStudentId: String,
    classes: List<SchoolClass>,
    currentUser: User? = null,
    onSaveStudentClick: (String, String, String, String, Long) -> Unit,
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

    var name by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("Male") }
    var motherName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var selectedClassId by remember(accessibleClasses) { mutableLongStateOf(accessibleClasses.firstOrNull()?.id ?: 0L) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Register Student", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White) },
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
                    Text("Student Registration Form", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TealDark)

                    Surface(
                        color = TealContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Badge, contentDescription = null, tint = TealDark, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Assigned Student ID: $nextStudentId", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TealDark)
                        }
                    }

                    errorMessage?.let {
                        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; errorMessage = null },
                        label = { Text("Student Full Name *") },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Gender Selector
                    Column {
                        Text("Gender:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                            FilterChip(
                                selected = gender == "Male",
                                onClick = { gender = "Male" },
                                label = { Text("Boy (Male)") },
                                leadingIcon = { Icon(Icons.Default.Boy, contentDescription = null) }
                            )
                            FilterChip(
                                selected = gender == "Female",
                                onClick = { gender = "Female" },
                                label = { Text("Girl (Female)") },
                                leadingIcon = { Icon(Icons.Default.Girl, contentDescription = null) }
                            )
                        }
                    }

                    // Class Selector
                    Column {
                        Text("Assign to Class *:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        if (accessibleClasses.isEmpty()) {
                            Text(
                                if (currentUser?.role == "TEACHER") "Ma laguu qoondeyn fasal arday lagu daro. La xidhiidh Admin-ka."
                                else "No classes found. Please create a class first.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        } else {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                items(accessibleClasses) { cls ->
                                    FilterChip(
                                        selected = selectedClassId == cls.id,
                                        onClick = { selectedClassId = cls.id },
                                        label = { Text(cls.name, fontSize = 11.sp) }
                                    )
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = motherName,
                        onValueChange = { motherName = it },
                        label = { Text("Mother's Name") },
                        leadingIcon = { Icon(Icons.Default.Face, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Guardian Phone (Call)") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Button(
                        onClick = {
                            if (name.isBlank()) {
                                errorMessage = "Please enter student's full name"
                                return@Button
                            }
                            if (selectedClassId == 0L) {
                                errorMessage = "Please select a valid class"
                                return@Button
                            }
                            onSaveStudentClick(name, gender, motherName, phone, selectedClassId)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Save & Register Student", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
