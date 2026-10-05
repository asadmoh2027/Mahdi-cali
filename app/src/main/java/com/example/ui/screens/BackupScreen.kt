package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import com.example.data.*
import com.example.ui.SchoolViewModel
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(
    viewModel: SchoolViewModel? = null,
    classes: List<SchoolClass> = emptyList(),
    lastCloudSyncTime: String = "Not Synced Yet",
    autoCloudSyncEnabled: Boolean = true,
    onToggleAutoCloudSync: (Boolean) -> Unit = {},
    onSyncToCloudClick: ((Boolean, String) -> Unit) -> Unit = {},
    onRestoreFromCloudClick: (String, (Boolean, String) -> Unit) -> Unit = { _, _ -> },
    onLoadStarterDataClick: (((Boolean, String) -> Unit) -> Unit)? = null,
    onSyncClassToCloudClick: (Long, (Boolean, String) -> Unit) -> Unit = { _, _ -> },
    onRestoreClassFromCloudClick: (Long, String, (Boolean, String) -> Unit) -> Unit = { _, _, _ -> },
    onExportBackupClick: () -> Unit = {},
    onExportClassBackupClick: (Long) -> Unit = {},
    onRestoreBackupClick: (String, (Boolean) -> Unit) -> Unit = { _, _ -> },
    onBackClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabTitles = listOf("📁 Google Drive & Script", "⚡ Firebase Sync", "🔄 Restore & Recovery", "🗑️ Qashin-qubka", "📊 Audit & History", "📤 Direct Export / Share")

    val backupRecords by (viewModel?.backupRecords ?: remember { MutableStateFlow(emptyList<BackupRecord>()) }).collectAsStateWithLifecycle(emptyList())
    val recycleItems by (viewModel?.recycleBinItems ?: remember { MutableStateFlow(emptyList<RecycleBinItem>()) }).collectAsStateWithLifecycle(emptyList())
    val markHistories by (viewModel?.markHistories ?: remember { MutableStateFlow(emptyList<MarkChangeHistory>()) }).collectAsStateWithLifecycle(emptyList())
    val auditLogs by (viewModel?.auditLogs ?: remember { MutableStateFlow(emptyList<AuditLog>()) }).collectAsStateWithLifecycle(emptyList())

    val isFirebaseConnected by CloudSync.isConnected.collectAsStateWithLifecycle()
    val isDriveConnected by (viewModel?.isDriveScriptConnected ?: remember { MutableStateFlow(false) }).collectAsStateWithLifecycle()
    val lastDriveTime by (viewModel?.lastDriveSyncTime ?: remember { MutableStateFlow(GoogleDriveSync.getLastSyncTime(context)) }).collectAsStateWithLifecycle()

    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isOperating by remember { mutableStateOf(false) }

    // Dialog state for Google Apps Script Config & Admin Password Protection
    var showScriptConfigDialog by remember { mutableStateOf(false) }
    var showAdminAuthDialog by remember { mutableStateOf(false) }
    var adminPasswordInput by remember { mutableStateOf("") }
    var adminPasswordError by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }
    var scriptUrlInput by remember { mutableStateOf(viewModel?.getGoogleScriptUrl() ?: GoogleDriveSync.getScriptUrl(context)) }

    // Dialog state for Firebase Config
    var showFirebaseConfigDialog by remember { mutableStateOf(false) }
    var showFirebaseSqlDialog by remember { mutableStateOf(false) }

    // Dialog state for Creating Enterprise Backup
    var showCreateBackupDialog by remember { mutableStateOf(false) }
    var backupNotesInput by remember { mutableStateOf("") }

    // Dialog state for Restoring a BackupRecord
    var backupToRestore by remember { mutableStateOf<BackupRecord?>(null) }
    val selectedRestoreCategories = remember { mutableStateMapOf(
        "CLASSES" to true,
        "STUDENTS" to true,
        "USERS" to true,
        "EXAMS" to true,
        "MARKS" to true,
        "ATTENDANCE" to true,
        "FEES" to true
    ) }

    // Dialog state for Permanent Delete item
    var itemToDeletePermanently by remember { mutableStateOf<RecycleBinItem?>(null) }
    var showEmptyTrashDialog by remember { mutableStateOf(false) }

    // Storage Access Framework (SAF) Launcher: Direct Save to Google Drive
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            isOperating = true
            viewModel?.saveBackupToUri(it) { success, msg ->
                isOperating = false
                statusMessage = msg
            }
        }
    }

    // Storage Access Framework (SAF) Launcher: Direct Open/Restore from Google Drive
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            isOperating = true
            viewModel?.restoreBackupFromUri(it) { success, msg ->
                isOperating = false
                statusMessage = msg
            }
        }
    }

    // Generic JSON File Picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(it)
                val jsonString = inputStream?.bufferedReader()?.use { reader -> reader.readText() }
                if (!jsonString.isNullOrBlank()) {
                    isOperating = true
                    onRestoreBackupClick(jsonString) { success ->
                        isOperating = false
                        statusMessage = if (success) "✅ Xogta si buuxda ayaa looga soo celiyay faylka JSON!" else "❌ Soo celinta faylka JSON waa fashilantay."
                    }
                }
            } catch (e: Exception) {
                statusMessage = "❌ Khalad ayaa dhacay: ${e.localizedMessage}"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Google Drive & Cloud Backups", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showAdminAuthDialog = true }) {
                        Icon(Icons.Default.CloudQueue, contentDescription = "Google Drive Script Settings", tint = Color.White)
                    }
                    IconButton(onClick = { showFirebaseConfigDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Firebase Settings", tint = Color.White)
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
            // Horizontal Scrollable Navigation Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                edgePadding = 12.dp,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 13.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) TealPrimary else DarkText
                            )
                        }
                    )
                }
            }

            // Global Status Banner
            AnimatedVisibility(visible = statusMessage != null) {
                Surface(
                    color = if (statusMessage?.startsWith("✅") == true) TealContainer else GoldContainer,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = statusMessage ?: "",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (statusMessage?.startsWith("✅") == true) TealDark else DarkText,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { statusMessage = null }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = DarkText, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Operating Progress Bar
            if (isOperating) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = TealPrimary)
            }

            // Tab Content
            when (selectedTab) {
                0 -> GoogleDriveAndScriptTab(
                    isDriveConnected = isDriveConnected,
                    lastDriveTime = lastDriveTime,
                    scriptUrl = scriptUrlInput,
                    isOperating = isOperating,
                    backupRecords = backupRecords,
                    autoCloudSyncEnabled = autoCloudSyncEnabled,
                    onToggleAutoCloudSync = onToggleAutoCloudSync,
                    onOpenScriptSettings = { showAdminAuthDialog = true },
                    onSyncToScriptClick = {
                        isOperating = true
                        viewModel?.syncToGoogleDriveScript { success, msg ->
                            isOperating = false
                            statusMessage = msg
                        }
                    },
                    onRestoreFromScriptClick = {
                        isOperating = true
                        viewModel?.restoreFromGoogleDriveScript { success, msg ->
                            isOperating = false
                            statusMessage = msg
                        }
                    },
                    onTestScriptClick = {
                        isOperating = true
                        viewModel?.testGoogleDriveScriptConnection { success, msg ->
                            isOperating = false
                            statusMessage = msg
                        }
                    },
                    onDirectSaveToDrive = {
                        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        createDocumentLauncher.launch("Mahdi_Cali_School_Backup_$dateStr.json")
                    },
                    onDirectRestoreFromDrive = {
                        openDocumentLauncher.launch(arrayOf("application/json", "*/*"))
                    },
                    onShareToDriveApp = {
                        viewModel?.exportBackupToGoogleDrive(context)
                    },
                    onCreateEnterpriseBackupClick = { showCreateBackupDialog = true },
                    onVerifyBackup = { record ->
                        viewModel?.verifyBackupRecord(record) { _, msg ->
                            statusMessage = msg
                        }
                    },
                    onRestoreBackupRecord = { record ->
                        backupToRestore = record
                    }
                )
                1 -> FirebaseSyncTab(
                    isConnected = isFirebaseConnected,
                    lastCloudSyncTime = lastCloudSyncTime,
                    autoCloudSyncEnabled = autoCloudSyncEnabled,
                    isOperating = isOperating,
                    onToggleAutoCloudSync = onToggleAutoCloudSync,
                    onSyncToFirebaseClick = {
                        isOperating = true
                        viewModel?.forceSyncToFirebase { _, msg ->
                            isOperating = false
                            statusMessage = msg
                        }
                    },
                    onRestoreFromFirebaseClick = {
                        isOperating = true
                        viewModel?.forceSyncFromFirebase { _, msg ->
                            isOperating = false
                            statusMessage = msg
                        }
                    },
                    onTestFirebaseClick = {
                        isOperating = true
                        viewModel?.testFirebaseConnection { _, msg ->
                            isOperating = false
                            statusMessage = msg
                        }
                    },
                    onOpenFirebaseConfig = { showFirebaseConfigDialog = true }
                )
                2 -> RestoreAndRecoveryTab(
                    onRestoreFromScriptClick = {
                        isOperating = true
                        viewModel?.restoreFromGoogleDriveScript { _, msg ->
                            isOperating = false
                            statusMessage = msg
                        }
                    },
                    onDirectRestoreFromDrive = {
                        openDocumentLauncher.launch(arrayOf("application/json", "*/*"))
                    },
                    onRestoreFromFirebaseClick = {
                        isOperating = true
                        viewModel?.forceSyncFromFirebase { _, msg ->
                            isOperating = false
                            statusMessage = msg
                        }
                    },
                    onPickLocalJsonFile = { filePickerLauncher.launch("application/json") },
                    onLoadStarterOfflineData = {
                        isOperating = true
                        onLoadStarterDataClick?.invoke { _, msg ->
                            isOperating = false
                            statusMessage = msg
                        }
                    }
                )
                3 -> RecycleBinTab(
                    items = recycleItems,
                    onRestoreItem = { item ->
                        viewModel?.restoreRecycleBinItem(item) { success ->
                            statusMessage = if (success) "✅ Xogta '${item.itemName}' dib ayaa loo soo celiyay!" else "❌ Soo celintu way fashilantay"
                        }
                    },
                    onPermanentDeleteItem = { item ->
                        itemToDeletePermanently = item
                    },
                    onEmptyTrash = { showEmptyTrashDialog = true }
                )
                4 -> AuditAndGradeHistoryTab(
                    markHistories = markHistories,
                    auditLogs = auditLogs
                )
                5 -> DirectExportTab(
                    onDirectSaveToDrive = {
                        val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        createDocumentLauncher.launch("Mahdi_Cali_School_Backup_$dateStr.json")
                    },
                    onShareToDriveApp = { viewModel?.exportBackupToGoogleDrive(context) },
                    onShareViaGmail = { viewModel?.sendBackupViaGmail(context) },
                    onExportFullJson = onExportBackupClick,
                    onPickLocalJsonFile = { filePickerLauncher.launch("application/json") }
                )
            }
        }
    }

    // Dialog: Admin Security Password Gate for Server URL (password: admin2536)
    if (showAdminAuthDialog) {
        AlertDialog(
            onDismissRequest = {
                showAdminAuthDialog = false
                adminPasswordInput = ""
                adminPasswordError = null
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = TealPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Amniga Admin-ka (Server Config)", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TealDark)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Qaybtan waxa gali kara oo Server URL-ka wax ka beddeli kara Maamulaha (Admin) oo keliya. Fadlan geli furahaaga sirta ah.",
                        fontSize = 12.sp,
                        color = DarkText
                    )

                    OutlinedTextField(
                        value = adminPasswordInput,
                        onValueChange = {
                            adminPasswordInput = it
                            adminPasswordError = null
                        },
                        label = { Text("Furaha Sirta ah (Password)") },
                        placeholder = { Text("Geli password-ka admin-ka") },
                        singleLine = true,
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = adminPasswordError != null,
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Qari password" else "Muuji password"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (adminPasswordError != null) {
                        Text(
                            text = adminPasswordError ?: "",
                            color = MaterialTheme.colorScheme.error,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (adminPasswordInput.trim() == "admin2536") {
                            showAdminAuthDialog = false
                            adminPasswordInput = ""
                            adminPasswordError = null
                            showScriptConfigDialog = true
                        } else {
                            adminPasswordError = "❌ Furaha sirta ah waa khalad! Fadlan hubi password-kaaga."
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    Text("Xaqiiji & Gal")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAdminAuthDialog = false
                        adminPasswordInput = ""
                        adminPasswordError = null
                    }
                ) {
                    Text("Ka Noqo")
                }
            }
        )
    }

    // Dialog: Google Drive Central Server Configuration (Editable & Secure)
    if (showScriptConfigDialog) {
        val clipboard = LocalClipboardManager.current
        var currentUrlInput by remember { mutableStateOf(scriptUrlInput) }
        var copySuccess by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showScriptConfigDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color(0xFF1E88E5))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("⚙️ Google Drive Server URL", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TealPrimary)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Geli Google Apps Script Web App URL-ka gaarka u ah Mahdi Cali School si xogta loogu kaydiyo Google Drive-kaaga rasmiga ah.",
                        fontSize = 11.sp,
                        color = DarkText
                    )

                    OutlinedTextField(
                        value = currentUrlInput,
                        onValueChange = { currentUrlInput = it },
                        label = { Text("Web App URL (script.google.com)") },
                        placeholder = { Text("https://script.google.com/macros/s/.../exec") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (currentUrlInput.isNotBlank()) {
                                IconButton(onClick = { currentUrlInput = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel?.saveGoogleScriptUrl(currentUrlInput)
                                scriptUrlInput = currentUrlInput
                                statusMessage = "URL-ka server-ka si guul leh ayaa loo keydiyay!"
                                showScriptConfigDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Keydi URL-ka", fontSize = 12.sp)
                        }

                        if (currentUrlInput.isNotBlank()) {
                            OutlinedButton(
                                onClick = {
                                    isOperating = true
                                    viewModel?.testGoogleDriveScriptConnection { success, msg ->
                                        isOperating = false
                                        statusMessage = msg
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Tijaabi", fontSize = 12.sp)
                            }
                        }
                    }

                    HorizontalDivider()

                    Text(
                        "Sidee loo samaystaa URL cusub? (1 Daqiiqo):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TealDark
                    )

                    Text(
                        "1. Gal script.google.com adigoo ku jira akoonka Google ee dugsiga.\n" +
                        "2. Riix 'New project', tirtir waxa ku jira, kuna dheji koodhkan hoose.\n" +
                        "3. Riix 'Deploy' -> 'New deployment' -> Nooca: 'Web app'.\n" +
                        "4. Execute as: 'Me' iyo Who has access: 'Anyone'.\n" +
                        "5. Nuqul ka qaado Web app URL-ka, kor ku dheji oo Save dheh.",
                        fontSize = 10.sp,
                        color = DarkText
                    )

                    Button(
                        onClick = {
                            clipboard.setText(AnnotatedString(GoogleDriveSync.DEFAULT_SCRIPT_TEMPLATE))
                            copySuccess = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = if (copySuccess) Color(0xFF10B981) else Color(0xFF1E88E5)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(if (copySuccess) Icons.Default.Check else Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (copySuccess) "Waa La Koobiyay Koodhka! ✓" else "Koobiyi Koodhka Google Script-ka", fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showScriptConfigDialog = false }) {
                    Text("Xir")
                }
            }
        )
    }

    // Dialog: Firebase Configuration Viewer
    if (showFirebaseConfigDialog) {
        AlertDialog(
            onDismissRequest = { showFirebaseConfigDialog = false },
            title = { Text("⚡ Firebase Configuration", fontWeight = FontWeight.Bold, color = TealPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Faahfaahinta xiriirka Firebase Cloud Database (Alternative Server):", fontSize = 12.sp, color = DarkText)

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("• Project ID: winter-territory-mq6d2", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TealPrimary)
                            Text("• Storage: winter-territory-mq6d2.firebasestorage.app", fontSize = 11.sp, color = DarkText)
                            Text("• Auth Domain: winter-territory-mq6d2.firebaseapp.com", fontSize = 11.sp, color = DarkText)
                            Text("• Status: 🟢 Connected via Official SDK", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color(0xFF2E7D32))
                        }
                    }
                    Text("Waxaad u isticmaali kartaa Firebase keyd labaad oo degdeg ah.", fontSize = 11.sp, color = Color.Gray)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        isOperating = true
                        viewModel?.testFirebaseConnection { success, msg ->
                            isOperating = false
                            statusMessage = if (success) "✅ $msg" else "❌ $msg"
                            showFirebaseConfigDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    Text("Tijaabi Xiriirka")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFirebaseConfigDialog = false }) {
                    Text("Xidh")
                }
            }
        )
    }

    // Dialog: Create Enterprise Versioned Backup
    if (showCreateBackupDialog) {
        AlertDialog(
            onDismissRequest = { showCreateBackupDialog = false },
            title = { Text("💾 Samee Version Backup Cusub", fontWeight = FontWeight.Bold, color = TealPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Geli qoraal ku saabsan sababta keydka loo qaadayo (tusaale: 'Imtixaanka 2026'):", fontSize = 12.sp, color = DarkText)
                    OutlinedTextField(
                        value = backupNotesInput,
                        onValueChange = { backupNotesInput = it },
                        label = { Text("Qoraalka Keydka / Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCreateBackupDialog = false
                        viewModel?.createEnterpriseBackup(
                            notes = backupNotesInput.ifBlank { "Manual Admin Backup" }
                        ) { _, msg ->
                            statusMessage = msg
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    Text("Abuur Keydka")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateBackupDialog = false }) {
                    Text("Ka Noqo")
                }
            }
        )
    }

    // Dialog: Partial / Entity Category Restore from Backup Record
    if (backupToRestore != null) {
        val record = backupToRestore!!
        AlertDialog(
            onDismissRequest = { backupToRestore = null },
            title = { Text("🔄 Soo Celi: ${record.backupId}", fontWeight = FontWeight.Bold, color = TealPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Dooro qaybaha aad rabto inaad dib u soo celiso:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    selectedRestoreCategories.keys.forEach { cat ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = selectedRestoreCategories[cat] == true,
                                onCheckedChange = { selectedRestoreCategories[cat] = it }
                            )
                            Text(cat, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val activeCats = selectedRestoreCategories.filter { it.value }.keys.toSet()
                        backupToRestore = null
                        viewModel?.restorePartialBackupRecord(record, activeCats) { _, msg ->
                            statusMessage = msg
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary)
                ) {
                    Text("Xaqiiji Soo Celinta")
                }
            },
            dismissButton = {
                TextButton(onClick = { backupToRestore = null }) {
                    Text("Ka Noqo")
                }
            }
        )
    }
}

// ----------------------------------------------------
// TAB 0: GOOGLE DRIVE & SCRIPT CLOUD BACKUP
// ----------------------------------------------------
@Composable
private fun GoogleDriveAndScriptTab(
    isDriveConnected: Boolean,
    lastDriveTime: String,
    scriptUrl: String,
    isOperating: Boolean,
    backupRecords: List<BackupRecord>,
    autoCloudSyncEnabled: Boolean,
    onToggleAutoCloudSync: (Boolean) -> Unit,
    onOpenScriptSettings: () -> Unit,
    onSyncToScriptClick: () -> Unit,
    onRestoreFromScriptClick: () -> Unit,
    onTestScriptClick: () -> Unit,
    onDirectSaveToDrive: () -> Unit,
    onDirectRestoreFromDrive: () -> Unit,
    onShareToDriveApp: () -> Unit,
    onCreateEnterpriseBackupClick: () -> Unit,
    onVerifyBackup: (BackupRecord) -> Unit,
    onRestoreBackupRecord: (BackupRecord) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Card 1: Google Drive Cloud Script Engine (Recommended & 100% Reliable)
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("☁️ Google Drive Server", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E88E5))
                        }
                        Surface(
                            color = if (scriptUrl.isNotBlank()) TealContainer else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (scriptUrl.isNotBlank()) "URL Diyaar Ah 🟢" else "Lama Galin URL ⚠️",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (scriptUrl.isNotBlank()) TealDark else MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text(
                        if (scriptUrl.isNotBlank()) "Server-ka Google Apps Script wuxuu si toos ah xogta ugu keydiyaa Google Drive-ka rasmiga ah ee dugsiga Mahdi Cali School."
                        else "Server URL-ka dugsiga lama gelin weli. Fadlan guji badhanka hoose si aad u geliso ama u cusboonaysiiso URL-kaaga cusub ee Google Apps Script.",
                        fontSize = 11.sp,
                        color = DarkText
                    )
                    Text("Keydkii u dambeeyay: $lastDriveTime", fontSize = 11.sp, color = MutedText)

                    OutlinedButton(
                        onClick = onOpenScriptSettings,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (scriptUrl.isNotBlank()) "⚙️ Bedel ama Tijaabi Server URL-ka" else "⚙️ Geli Server URL-ka Cusub", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Toos u Keydi (Auto-Sync to Drive)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text("Isbeddel kasta oo cusub u dir Google Drive Script", fontSize = 10.sp, color = MutedText)
                        }
                        Switch(
                            checked = autoCloudSyncEnabled,
                            onCheckedChange = onToggleAutoCloudSync
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onSyncToScriptClick,
                            enabled = !isOperating,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("⚡ KEYDI DRIVE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Button(
                            onClick = onRestoreFromScriptClick,
                            enabled = !isOperating,
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("📥 SOO DEJI DRIVE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    OutlinedButton(
                        onClick = onTestScriptClick,
                        enabled = !isOperating,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Tijaabi Xiriirka Google Drive Server", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Card 2: Native Android Direct Save & Restore to Google Drive (SAF)
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DriveFolderUpload, contentDescription = null, tint = TealPrimary)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("💾 Google Drive Direct File (Save & Open)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                    }

                    Text("Ku keydi ama ka soo celi faylka keydka (.json) adigoo toos uga dooranaya Google Drive-kaaga taleefanka ku jira:", fontSize = 11.sp, color = DarkText)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onDirectSaveToDrive,
                            enabled = !isOperating,
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Keydi Google Drive", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onDirectRestoreFromDrive,
                            enabled = !isOperating,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Ka Soo Celi Drive", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Button(
                        onClick = onShareToDriveApp,
                        enabled = !isOperating,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("La Wadaag Google Drive App / Gmail / WhatsApp", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Card 3: Enterprise Versioned Backups
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = TealContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("🏛️ Enterprise Versioned Backups", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TealDark)
                        Text("Keydi snapshots leh checksum SHA-256 oo dib loo soo celin karo qayb-qayb", fontSize = 11.sp, color = DarkText)
                    }
                    Button(
                        onClick = onCreateEnterpriseBackupClick,
                        colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Abuur Keyd", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // List of Snapshot Records
        if (backupRecords.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Wali ma jiraan Versioned Backups la keydiyay.", fontSize = 12.sp, color = MutedText)
                }
            }
        } else {
            items(backupRecords) { record ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = TealContainer,
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = "v${record.versionNumber}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TealDark,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(record.backupId, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(record.createdAt, fontSize = 10.sp, color = MutedText)
                        }

                        Text("Checksum SHA-256: ${record.checksum.take(16)}...", fontSize = 10.sp, color = MutedText)
                        Text("Abuuray: ${record.createdBy} • Cabirka: ${record.recordCount} xaraf", fontSize = 11.sp, color = DarkText)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { onVerifyBackup(record) }) {
                                Icon(Icons.Default.Verified, contentDescription = null, modifier = Modifier.size(14.dp), tint = TealPrimary)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tijaabi Integrity", fontSize = 11.sp, color = TealPrimary)
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            Button(
                                onClick = { onRestoreBackupRecord(record) },
                                colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text("Soo Celi Qayb", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// TAB 1: FIREBASE CLOUD SYNC (ALTERNATIVE)
// ----------------------------------------------------
@Composable
private fun FirebaseSyncTab(
    isConnected: Boolean,
    lastCloudSyncTime: String,
    autoCloudSyncEnabled: Boolean,
    isOperating: Boolean,
    onToggleAutoCloudSync: (Boolean) -> Unit,
    onSyncToFirebaseClick: () -> Unit,
    onRestoreFromFirebaseClick: () -> Unit,
    onTestFirebaseClick: () -> Unit,
    onOpenFirebaseConfig: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚡ Firebase Cloud Server (Alternative)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                        Surface(
                            color = if (isConnected) TealContainer else GoldContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isConnected) "Xidhan 🟢" else "Offline 📱",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isConnected) TealDark else DarkText,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Text("Project: winter-territory-mq6d2 • Firestore & Realtime DB", fontSize = 11.sp, color = DarkText)
                    Text("Keydkii Firebase: $lastCloudSyncTime", fontSize = 11.sp, color = MutedText)

                    HorizontalDivider()

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onSyncToFirebaseClick,
                            enabled = !isOperating,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("KEYDI FIREBASE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }

                        Button(
                            onClick = onRestoreFromFirebaseClick,
                            enabled = !isOperating,
                            colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("SOO DEJI FIREBASE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onTestFirebaseClick,
                            enabled = !isOperating,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Tijaabi Firebase", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onOpenFirebaseConfig,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Settings", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// TAB 2: RESTORE & RECOVERY
// ----------------------------------------------------
@Composable
private fun RestoreAndRecoveryTab(
    onRestoreFromScriptClick: () -> Unit,
    onDirectRestoreFromDrive: () -> Unit,
    onRestoreFromFirebaseClick: () -> Unit,
    onPickLocalJsonFile: () -> Unit,
    onLoadStarterOfflineData: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Option 1: Restore from Google Drive Script
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFF1E88E5))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("1. Ka Soo Celi Google Drive Script (Lagu Talagalay)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E88E5))
                }
                Text("Toos uga soo deji nuqulkii ugu dambeeyay Google Drive-kaaga adoo adeegsanaya Google Apps Script.", fontSize = 12.sp, color = DarkText)
                Button(
                    onClick = onRestoreFromScriptClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📥 Soo Deji Keydka Google Drive", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Option 2: Direct Google Drive File Picker (SAF)
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TealPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("2. Dooro Fayl Google Drive / Taleefanka (.json)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                }
                Text("Dooro faylka keydka ee ku jira Google Drive-kaaga ama Downloads si aad u soo celiso.", fontSize = 12.sp, color = DarkText)
                Button(
                    onClick = onDirectRestoreFromDrive,
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Dooro Fayl Google Drive (.json)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Option 3: Firebase Restore
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudSync, contentDescription = null, tint = Color(0xFF10B981))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("3. Ka Soo Celi Firebase Server", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                }
                Text("Xogta dugsiga ka soo deji Firebase Cloud Server.", fontSize = 12.sp, color = DarkText)
                OutlinedButton(
                    onClick = onRestoreFromFirebaseClick,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Ka Soo Celi Firebase", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Option 4: Starter Data Recovery
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = TealPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("4. Dib u Soo Celi Xogta Bilowga ah (Starter Data)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                }
                Text("Haddii aad rabto inaad nidaamka ku shubto xogta tusaalaha ah ee fasallada, ardayda, iyo buundooyinka.", fontSize = 12.sp, color = DarkText)
                Button(
                    onClick = onLoadStarterOfflineData,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Shub Xogta Bilowga ah (Starter Data)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ----------------------------------------------------
// TAB 3: RECYCLE BIN (QASHIN-QUBKA)
// ----------------------------------------------------
@Composable
private fun RecycleBinTab(
    items: List<RecycleBinItem>,
    onRestoreItem: (RecycleBinItem) -> Unit,
    onPermanentDeleteItem: (RecycleBinItem) -> Unit,
    onEmptyTrash: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("🗑️ Qashin-qubka (Recycle Bin)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                Text("Xogta la tirtiray halkan ayaa lagu hayaa si loo badbaadiyo", fontSize = 11.sp, color = MutedText)
            }
            if (items.isNotEmpty()) {
                TextButton(onClick = onEmptyTrash) {
                    Text("Faaruqi Dhammaan", color = FailRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (items.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Qashin-qubku waa faaruq. Wax xog ah oo la tirtiray ma jirto.", fontSize = 12.sp, color = MutedText)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(items) { item ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        color = GoldContainer,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = item.entityType,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = DarkText,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(item.itemName, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Text("Tirtiray: ${item.deletedBy} • ${item.deletedAt}", fontSize = 11.sp, color = MutedText)
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { onRestoreItem(item) },
                                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("Soo Celi", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }

                                IconButton(
                                    onClick = { onPermanentDeleteItem(item) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.DeleteForever, contentDescription = "Permanent Delete", tint = FailRed)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// TAB 4: AUDIT & GRADE CHANGE HISTORY
// ----------------------------------------------------
@Composable
private fun AuditAndGradeHistoryTab(
    markHistories: List<MarkChangeHistory>,
    auditLogs: List<AuditLog>
) {
    var subTab by remember { mutableIntStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TabRow(selectedTabIndex = subTab) {
            Tab(selected = subTab == 0, onClick = { subTab = 0 }) {
                Text("📈 Isbedelka Buundooyinka (${markHistories.size})", fontSize = 12.sp, modifier = Modifier.padding(8.dp))
            }
            Tab(selected = subTab == 1, onClick = { subTab = 1 }) {
                Text("📜 Diiwaanka Hawlaha (${auditLogs.size})", fontSize = 12.sp, modifier = Modifier.padding(8.dp))
            }
        }

        if (subTab == 0) {
            if (markHistories.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Wali ma jiro wax buundooyin ah oo wax laga beddelay.", fontSize = 12.sp, color = MutedText)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(markHistories) { h ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(2.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(h.studentName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                                    Text(h.changedAt, fontSize = 10.sp, color = MutedText)
                                }
                                Text("Maadada: ${h.subject} (${h.examName})", fontSize = 11.sp, color = DarkText)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Buundadii Hore: ${if (h.oldScore < 0) "Maqan" else h.oldScore}", fontSize = 11.sp, color = FailRed, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("➔", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Buundada Cusub: ${if (h.newScore < 0) "Maqan" else h.newScore}", fontSize = 11.sp, color = TealDark, fontWeight = FontWeight.Bold)
                                }
                                Text("Beddelay: ${h.changedBy} • Sababta: ${h.reason}", fontSize = 10.sp, color = DarkText)
                            }
                        }
                    }
                }
            }
        } else {
            if (auditLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Wali wax diiwaan hawleed ah lama hayo.", fontSize = 12.sp, color = MutedText)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(auditLogs) { log ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(1.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(log.title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                                    Text(log.timestamp, fontSize = 9.sp, color = MutedText)
                                }
                                Text("${log.userName} (${log.userRole}) • ${log.details}", fontSize = 11.sp, color = DarkText)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// TAB 5: DIRECT EXPORT / SHARE
// ----------------------------------------------------
@Composable
private fun DirectExportTab(
    onDirectSaveToDrive: () -> Unit,
    onShareToDriveApp: () -> Unit,
    onShareViaGmail: () -> Unit,
    onExportFullJson: () -> Unit,
    onPickLocalJsonFile: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("💾 Keydi Fayl Toos ah (Direct Storage / Google Drive)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                Text("Dooro meel kasta oo aad rabto inaad ku keydiso (Google Drive, SD Card, Downloads).", fontSize = 12.sp, color = DarkText)
                Button(
                    onClick = onDirectSaveToDrive,
                    colors = ButtonDefaults.buttonColors(containerColor = TealPrimary),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DriveFolderUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Keydi Google Drive / Taleefanka", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📤 La Wadaag Google Drive App & Gmail", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E88E5))
                Text("Si toos ah ugu dir Google Drive app, Gmail ama WhatsApp si aad meel ammaan ah ugu haysato.", fontSize = 12.sp, color = DarkText)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onShareToDriveApp,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Google Drive", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onShareViaGmail,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Gmail", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("📥 Soo Deji Xog (Import JSON)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TealPrimary)
                Text("Dooro fayl .json ah si aad ugu shubto database-ka.", fontSize = 12.sp, color = DarkText)
                OutlinedButton(
                    onClick = onPickLocalJsonFile,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Dooro Faylka (.json)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("🐙 GitHub Source & Codebase Backup", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = DarkText)
                Text("Codka iyo kaydka application-ka waxaa si toos ah loogu kaydiyay GitHub Repository-ga si xogta iyo dhismaha barnaamijka looma waayo.", fontSize = 12.sp, color = DarkText)
            }
        }
    }
}
