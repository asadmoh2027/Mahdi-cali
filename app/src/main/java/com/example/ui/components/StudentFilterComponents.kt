package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentSearchFilterCard(
    students: List<Student>,
    classes: List<SchoolClass>,
    selectedStudentId: Long?,
    onStudentSelected: (Student) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var phoneQuery by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf("All") }
    var selectedClassId by remember { mutableLongStateOf(0L) }
    var isExpanded by remember { mutableStateOf(false) }
    var showAdvancedFilters by remember { mutableStateOf(false) }

    val filteredStudents = remember(students, searchQuery, phoneQuery, selectedGender, selectedClassId) {
        students.filter { student ->
            val matchesClass = selectedClassId == 0L || student.classId == selectedClassId
            val matchesName = searchQuery.isBlank() ||
                student.name.contains(searchQuery, ignoreCase = true) ||
                student.studentId.contains(searchQuery, ignoreCase = true) ||
                student.motherName.contains(searchQuery, ignoreCase = true)
            val matchesGender = selectedGender == "All" ||
                student.gender.equals(selectedGender, ignoreCase = true)
            val matchesPhone = phoneQuery.isBlank() ||
                student.phone.contains(phoneQuery, ignoreCase = true)

            matchesClass && matchesName && matchesGender && matchesPhone
        }
    }

    val currentSelectedStudent = students.find { it.id == selectedStudentId } ?: filteredStudents.firstOrNull()

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header Bar with Quick Action Icons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.FilterList, contentDescription = null, tint = TealPrimary, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Student Selection (${filteredStudents.size})",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TealPrimary
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = { showAdvancedFilters = !showAdvancedFilters },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = if (showAdvancedFilters) Icons.Default.Tune else Icons.Default.Search,
                            contentDescription = "Toggle Search Filters",
                            tint = TealPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (searchQuery.isNotBlank() || phoneQuery.isNotBlank() || selectedGender != "All" || selectedClassId != 0L) {
                        IconButton(
                            onClick = {
                                searchQuery = ""
                                phoneQuery = ""
                                selectedGender = "All"
                                selectedClassId = 0L
                            },
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = "Reset Filters", tint = FailRed, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Collapsible / Compact Search Inputs
            AnimatedVisibility(visible = showAdvancedFilters) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Name / ID", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            singleLine = true,
                            modifier = Modifier.weight(1.2f)
                        )

                        OutlinedTextField(
                            value = phoneQuery,
                            onValueChange = { phoneQuery = it },
                            placeholder = { Text("Phone", fontSize = 11.sp) },
                            leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.weight(0.8f)
                        )
                    }
                }
            }

            // Compact Filter Row: Gender Icons & Class Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    FilterChip(
                        selected = selectedGender == "All",
                        onClick = { selectedGender = "All" },
                        label = { Text("All", fontSize = 10.sp) }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedGender == "Male",
                        onClick = { selectedGender = "Male" },
                        label = { Text("Boys", fontSize = 10.sp) },
                        leadingIcon = { Icon(Icons.Default.Boy, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }
                item {
                    FilterChip(
                        selected = selectedGender == "Female",
                        onClick = { selectedGender = "Female" },
                        label = { Text("Girls", fontSize = 10.sp) },
                        leadingIcon = { Icon(Icons.Default.Girl, contentDescription = null, modifier = Modifier.size(14.dp)) }
                    )
                }

                item {
                    FilterChip(
                        selected = selectedClassId == 0L,
                        onClick = { selectedClassId = 0L },
                        label = { Text("All Classes", fontSize = 10.sp) }
                    )
                }

                items(classes) { cls ->
                    FilterChip(
                        selected = selectedClassId == cls.id,
                        onClick = { selectedClassId = cls.id },
                        label = { Text(cls.name, fontSize = 10.sp) }
                    )
                }
            }

            // Student Dropdown Selector
            ExposedDropdownMenuBox(
                expanded = isExpanded,
                onExpandedChange = { isExpanded = !isExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = if (currentSelectedStudent != null) {
                        "${currentSelectedStudent.name} • ID: ${currentSelectedStudent.studentId}"
                    } else "No matching students",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Select Student *", fontSize = 11.sp) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isExpanded) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TealPrimary,
                        unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = isExpanded,
                    onDismissRequest = { isExpanded = false }
                ) {
                    if (filteredStudents.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No matching students found", fontSize = 11.sp, color = FailRed) },
                            onClick = { isExpanded = false }
                        )
                    } else {
                        filteredStudents.forEach { st ->
                            val cls = classes.find { it.id == st.classId }?.name ?: ""
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("${st.name} • ID: ${st.studentId}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("Class: $cls • ${st.gender} • Tel: ${st.phone.ifBlank { "N/A" }}", fontSize = 10.sp, color = MutedText)
                                    }
                                },
                                onClick = {
                                    onStudentSelected(st)
                                    isExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
