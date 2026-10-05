package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.SchoolClass
import com.example.data.User
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserManagementScreen(
    currentUser: User?,
    users: List<User>,
    classes: List<SchoolClass>,
    onChangePasswordClick: (String, String, (Boolean, String) -> Unit) -> Unit,
    onCreateTeacherClick: (String, String, String, List<Long>, (Boolean, String) -> Unit) -> Unit,
    onCreateCashierClick: (String, String, String, (Boolean, String) -> Unit) -> Unit,
    onResetPasswordClick: (Long, String, (Boolean, String) -> Unit) -> Unit = { _, _, _ -> },
    onToggleUserLock: (Long, Boolean) -> Unit,
    onDeleteUserClick: (Long) -> Unit,
    onBackClick: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val isAdmin = currentUser?.role == "ADMIN"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("User & Security Desk", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White) },
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
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = TealPrimary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Change Password", fontWeight = FontWeight.Bold) }
                )
                if (isAdmin) {
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Staff Accounts", fontWeight = FontWeight.Bold) }
                    )
                }
            }

            Box(modifier = Modifier.padding(16.dp)) {
                if (selectedTab == 0) {
                    ChangePasswordTab(onChangePasswordClick = onChangePasswordClick)
                } else if (isAdmin) {
                    StaffAccountsTab(
                        currentUser = currentUser,
                        users = users,
                        classes = classes,
                        onCreateTeacherClick = onCreateTeacherClick,
                        onCreateCashierClick = onCreateCashierClick,
                        onResetPasswordClick = onResetPasswordClick,
                        onToggleUserLock = onToggleUserLock,
                        onDeleteUserClick = onDeleteUserClick
                    )
                }
            }
        }
    }
}

@Composable
fun ChangePasswordTab(
    onChangePasswordClick: (String, String, (Boolean, String) -> Unit) -> Unit
) {
    var oldPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    var oldVisible by remember { mutableStateOf(false) }
    var newVisible by remember { mutableStateOf(false) }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        Text("🔑 Security & Password Update", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
        Text("Updating your password will immediately invalidate the old password across the system.", fontSize = 12.sp, color = MutedText)

        AnimatedVisibility(visible = statusMessage != null) {
            Surface(
                color = if (isSuccess) GoldContainer else MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = statusMessage ?: "",
                    color = if (isSuccess) TealDark else MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }

        OutlinedTextField(
            value = oldPassword,
            onValueChange = { oldPassword = it },
            label = { Text("Current Password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { oldVisible = !oldVisible }) {
                    Icon(if (oldVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null)
                }
            },
            visualTransformation = if (oldVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = newPassword,
            onValueChange = { newPassword = it },
            label = { Text("New Password (Min 3 characters)") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
                IconButton(onClick = { newVisible = !newVisible }) {
                    Icon(if (newVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null)
                }
            },
            visualTransformation = if (newVisible) VisualTransformation.None else PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = confirmPassword,
            onValueChange = { confirmPassword = it },
            label = { Text("Confirm New Password") },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                focusManager.clearFocus()
                if (newPassword != confirmPassword) {
                    isSuccess = false
                    statusMessage = "❌ Passwords do not match!"
                    return@Button
                }
                onChangePasswordClick(oldPassword, newPassword) { success, msg ->
                    isSuccess = success
                    statusMessage = msg
                    if (success) {
                        oldPassword = ""
                        newPassword = ""
                        confirmPassword = ""
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
            shape = RoundedCornerShape(10.dp)
        ) {
            Text("Update Password", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffAccountsTab(
    currentUser: User?,
    users: List<User>,
    classes: List<SchoolClass>,
    onCreateTeacherClick: (String, String, String, List<Long>, (Boolean, String) -> Unit) -> Unit,
    onCreateCashierClick: (String, String, String, (Boolean, String) -> Unit) -> Unit,
    onResetPasswordClick: (Long, String, (Boolean, String) -> Unit) -> Unit = { _, _, _ -> },
    onToggleUserLock: (Long, Boolean) -> Unit,
    onDeleteUserClick: (Long) -> Unit
) {
    var accountTypeToCreate by remember { mutableStateOf("TEACHER") } // TEACHER or CASHIER
    var newUsername by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newFullName by remember { mutableStateOf("") }
    var selectedClassIds by remember { mutableStateOf(setOf<Long>()) }

    var formMessage by remember { mutableStateOf<String?>(null) }
    var isSuccess by remember { mutableStateOf(false) }

    // Dialog state for resetting a staff member's password
    var userToResetPassword by remember { mutableStateOf<User?>(null) }
    var resetNewPassText by remember { mutableStateOf("") }
    var resetPassMessage by remember { mutableStateOf<String?>(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
    ) {
        // Staff Creation Card
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("➕ Samee Akoon Cusub (User Registration)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TealDark)

                // Role Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val roles = listOf(
                        "TEACHER" to "Macalin",
                        "CASHIER" to "Khasnaji",
                        "PARENT" to "Waalid",
                        "STUDENT" to "Arday"
                    )
                    roles.forEach { (roleKey, roleLabel) ->
                        FilterChip(
                            selected = accountTypeToCreate == roleKey,
                            onClick = { accountTypeToCreate = roleKey },
                            label = { Text(roleLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                        )
                    }
                }

                AnimatedVisibility(visible = formMessage != null) {
                    Surface(
                        color = if (isSuccess) GoldContainer else MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = formMessage ?: "",
                            color = if (isSuccess) TealDark else MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = newFullName,
                    onValueChange = { newFullName = it },
                    label = { Text("Magaca Buuxa (Full Name)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = newUsername,
                    onValueChange = { newUsername = it },
                    label = { Text("Username (tusaale: cali ama cali12)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                var newPasswordVisible by remember { mutableStateOf(true) }
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Password (Qarsoodi)") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { newPasswordVisible = !newPasswordVisible }) {
                            Icon(if (newPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = "Toggle Password Visibility")
                        }
                    },
                    visualTransformation = if (newPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (accountTypeToCreate == "TEACHER") {
                    Text("Fasallada Macalinka loo fasaxay (Assigned Classes):", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TealDark)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        classes.forEach { cls ->
                            val isChecked = selectedClassIds.contains(cls.id)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = { checked ->
                                        selectedClassIds = if (checked) selectedClassIds + cls.id else selectedClassIds - cls.id
                                    }
                                )
                                Text(cls.name, fontSize = 12.sp)
                            }
                        }
                    }
                } else if (accountTypeToCreate == "PARENT") {
                    Text("ℹ️ Akoonka waalidku wuxuu xaq u leeyahay inuu arko buundooyinka iyo fiiga caruurtiisa.", fontSize = 11.sp, color = MutedText)
                } else if (accountTypeToCreate == "STUDENT") {
                    Text("ℹ️ Akoonka ardaygu wuxuu xaq u leeyahay inuu arko natiijooyinka imtixaanaadka.", fontSize = 11.sp, color = MutedText)
                } else {
                    Text("ℹ️ Akoonka khasnajigu wuxuu xaq u leeyahay qaadista lacagaha iyo diiwaanka.", fontSize = 11.sp, color = MutedText)
                }

                Button(
                    onClick = {
                        if (accountTypeToCreate == "TEACHER") {
                            onCreateTeacherClick(newUsername, newPassword, newFullName, selectedClassIds.toList()) { ok, msg ->
                                isSuccess = ok
                                formMessage = msg
                                if (ok) {
                                    newUsername = ""
                                    newPassword = ""
                                    newFullName = ""
                                    selectedClassIds = emptySet()
                                }
                            }
                        } else {
                            onCreateCashierClick(newUsername, newPassword, newFullName) { ok, msg ->
                                isSuccess = ok
                                formMessage = msg
                                if (ok) {
                                    newUsername = ""
                                    newPassword = ""
                                    newFullName = ""
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("ABUUR AKOONKA $accountTypeToCreate", fontWeight = FontWeight.Bold, color = Color.White)
                }

            }
        }

        // Filter out 'sma' internal admin account and hidden users from the visible staff list
        val isCurrentUserSacad = currentUser?.username?.lowercase() == "sacad"
        val visibleUsers = users.filter { u ->
            if (isCurrentUserSacad) {
                u.id == currentUser!!.id
            } else {
                u.username.lowercase() != "sma" && 
                u.username.lowercase() != "sacad" && 
                !u.fullName.contains("SMA Admin", ignoreCase = true) && 
                !u.isHidden
            }
        }

        // Existing Staff Accounts List
        Text("👥 Shaqaalaha & Macalimiinta Diiwaangashan (${visibleUsers.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkText)

        visibleUsers.forEach { u ->
            val roleBadgeColor = when (u.role) {
                "ADMIN" -> TealPrimary
                "CASHIER" -> Color(0xFF0288D1)
                else -> Color(0xFFE6A100)
            }

            var showPassInCard by remember { mutableStateOf(false) }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (u.isLocked) Color(0xFFFDE8E8) else MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = u.fullName, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = DarkText)
                                Surface(color = roleBadgeColor, shape = RoundedCornerShape(4.dp)) {
                                    Text(
                                        text = u.role,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                                if (u.isLocked) {
                                    Surface(color = FailRed, shape = RoundedCornerShape(4.dp)) {
                                        Text(
                                            text = "DISABLED",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(3.dp))
                            
                            // Username & Password display
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(text = "Username: ${u.username}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TealDark)
                                Text(
                                    text = if (showPassInCard) "Password: ${u.passwordHash}" else "Password: ••••••••",
                                    fontSize = 11.sp,
                                    color = MutedText
                                )
                                IconButton(
                                    onClick = { showPassInCard = !showPassInCard },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = if (showPassInCard) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Show password",
                                        modifier = Modifier.size(16.dp),
                                        tint = TealPrimary
                                    )
                                }
                            }
                        }

                        // Actions for staff: Reset Password, Lock toggle & Delete (if not admin)
                        if (u.role != "ADMIN") {
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                IconButton(
                                    onClick = {
                                        userToResetPassword = u
                                        resetNewPassText = ""
                                        resetPassMessage = null
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Key,
                                        contentDescription = "Bedel Password",
                                        tint = TealPrimary
                                    )
                                }

                                IconButton(
                                    onClick = { onToggleUserLock(u.id, !u.isLocked) }
                                ) {
                                    Icon(
                                        imageVector = if (u.isLocked) Icons.Default.LockOpen else Icons.Default.Lock,
                                        contentDescription = if (u.isLocked) "Enable Account" else "Disable Account",
                                        tint = if (u.isLocked) PassGreen else FailRed
                                    )
                                }

                                IconButton(
                                    onClick = { onDeleteUserClick(u.id) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Staff",
                                        tint = FailRed
                                    )
                                }
                            }
                        }
                    }

                    if (u.role == "TEACHER" && u.assignedClassIds.isNotBlank()) {
                        val assignedNames = u.assignedClassIds.split(",")
                            .mapNotNull { idStr -> classes.find { it.id.toString() == idStr.trim() }?.name }
                            .joinToString(", ")
                        Text("Fasallada loo xilsaaray: ${assignedNames.ifBlank { "Dhammaan Fasallada" }}", fontSize = 11.sp, color = TealDark)
                    }
                }
            }
        }
    }

    // Reset Password Dialog for Admin
    userToResetPassword?.let { targetUser ->
        AlertDialog(
            onDismissRequest = { userToResetPassword = null },
            title = { Text("🔑 Bedel Password-ka (${targetUser.fullName})", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Geli password-ka cusub ee macalinka '${targetUser.fullName}' (Username: ${targetUser.username}):", fontSize = 12.sp)

                    resetPassMessage?.let { msg ->
                        Text(msg, color = TealDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedTextField(
                        value = resetNewPassText,
                        onValueChange = { resetNewPassText = it },
                        label = { Text("Password-ka Cusub") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (resetNewPassText.isNotBlank()) {
                            onResetPasswordClick(targetUser.id, resetNewPassText) { success, msg ->
                                if (success) {
                                    userToResetPassword = null
                                } else {
                                    resetPassMessage = msg
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    Text("Cusbooneysii", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { userToResetPassword = null }) {
                    Text("Kansal")
                }
            }
        )
    }
}
