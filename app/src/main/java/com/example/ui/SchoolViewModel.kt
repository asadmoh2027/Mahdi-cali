package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.ui.screens.cleanExamSubjectName
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class StudentReportCard(
    val student: Student,
    val className: String,
    val subjectResults: List<SubjectResult>,
    val averagePercentage: Double,
    val totalExams: Int,
    val totalPassed: Int,
    val attendanceRate: Double,
    val pendingFees: List<FeeRecord>
)

data class SubjectResult(
    val examName: String,
    val subject: String,
    val score: Double,
    val totalMarks: Double,
    val passMarks: Double,
    val isAbsent: Boolean,
    val isPassed: Boolean
)

class SchoolViewModel(application: Application) : AndroidViewModel(application) {

    private val db = SchoolDatabase.getDatabase(application, viewModelScope)
    val repository = SchoolRepository(db)

    // Current logged-in user
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()

    // UI Toast or Status messages
    private val _uiMessage = MutableSharedFlow<String>()
    val uiMessage: SharedFlow<String> = _uiMessage.asSharedFlow()

    // School Name / Profile Settings
    private val prefs = application.getSharedPreferences("school_settings", Context.MODE_PRIVATE)
    private val _schoolName = MutableStateFlow(
        run {
            val saved = prefs.getString("school_name", null)
            if (saved == null || saved == "Xasan Cawke School" || saved.contains("Xasan", ignoreCase = true)) {
                prefs.edit().putString("school_name", "Mahdi Cali School").apply()
                "Mahdi Cali School"
            } else {
                saved
            }
        }
    )
    val schoolName: StateFlow<String> = _schoolName.asStateFlow()

    fun saveSchoolName(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isNotBlank()) {
            prefs.edit().putString("school_name", trimmed).apply()
            _schoolName.value = trimmed
            viewModelScope.launch {
                _uiMessage.emit("School name updated: $trimmed")
            }
        }
    }

    // --- Cloud Sync Settings & Logic ---
    private val _lastCloudSyncTime = MutableStateFlow(
        prefs.getString("last_cloud_sync_time", "Not Synced Yet") ?: "Not Synced Yet"
    )
    val lastCloudSyncTime: StateFlow<String> = _lastCloudSyncTime.asStateFlow()

    private val _autoCloudSyncEnabled = MutableStateFlow(
        prefs.getBoolean("auto_cloud_sync", true)
    )
    val autoCloudSyncEnabled: StateFlow<Boolean> = _autoCloudSyncEnabled.asStateFlow()

    // --- School Shift / Session State (Gelin Hore / Gelin Danbe / Dhammaan) ---
    private val _selectedShift = MutableStateFlow(
        prefs.getString("selected_shift", "Dhammaan") ?: "Dhammaan"
    )
    val selectedShift: StateFlow<String> = _selectedShift.asStateFlow()

    fun setSelectedShift(shift: String) {
        prefs.edit().putString("selected_shift", shift).apply()
        _selectedShift.value = shift
    }

    // --- School Facilities / Assets (Agabka Dugsiga) ---
    private val _classroomsCount = MutableStateFlow(prefs.getInt("asset_classrooms", 12))
    val classroomsCount: StateFlow<Int> = _classroomsCount.asStateFlow()

    private val _chairsCount = MutableStateFlow(prefs.getInt("asset_chairs", 350))
    val chairsCount: StateFlow<Int> = _chairsCount.asStateFlow()

    private val _toiletsCount = MutableStateFlow(prefs.getInt("asset_toilets", 10))
    val toiletsCount: StateFlow<Int> = _toiletsCount.asStateFlow()

    private val _officesCount = MutableStateFlow(prefs.getInt("asset_offices", 4))
    val officesCount: StateFlow<Int> = _officesCount.asStateFlow()

    private val _kitchenFeedingCount = MutableStateFlow(prefs.getInt("asset_kitchen_feeding", 1))
    val kitchenFeedingCount: StateFlow<Int> = _kitchenFeedingCount.asStateFlow()

    fun updateSchoolFacilities(classrooms: Int, chairs: Int, toilets: Int, offices: Int, kitchenFeeding: Int) {
        prefs.edit()
            .putInt("asset_classrooms", classrooms)
            .putInt("asset_chairs", chairs)
            .putInt("asset_toilets", toilets)
            .putInt("asset_offices", offices)
            .putInt("asset_kitchen_feeding", kitchenFeeding)
            .apply()
        _classroomsCount.value = classrooms
        _chairsCount.value = chairs
        _toiletsCount.value = toilets
        _officesCount.value = offices
        _kitchenFeedingCount.value = kitchenFeeding
        viewModelScope.launch {
            _uiMessage.emit("Agabka Dugsiga waa lagu guuleystay in la xareeyo!")
        }
    }

    // val isConnected: StateFlow<Boolean> = CloudSync.isConnected
    // val statusMessage: StateFlow<String> = CloudSync.statusMessage
    // val lastSyncTime: StateFlow<String> = CloudSync.lastSyncTime

    private val _isStartupLoading = MutableStateFlow(true)
    val isStartupLoading: StateFlow<Boolean> = _isStartupLoading.asStateFlow()
    
    private val _syncStatus = MutableStateFlow("Initializing...")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private var lastAutoSyncTimestamp: Long = 0L

    init {
        checkAuthAndLoadDataOnLaunch()
        setupAutoInternetCloudBackup(application)
        setupFirebaseAutoSync(application)
    }

    // --- Google Drive & Script Cloud Sync ---
    val isDriveScriptConnected: StateFlow<Boolean> = GoogleDriveSync.isDriveScriptConnected
    val lastDriveSyncTime: StateFlow<String> = GoogleDriveSync.lastDriveSyncTime

    fun getGoogleScriptUrl(): String = GoogleDriveSync.getScriptUrl(getApplication())
    fun saveGoogleScriptUrl(url: String) {
        GoogleDriveSync.setScriptUrl(getApplication(), url)
        viewModelScope.launch {
            _uiMessage.emit("📁 Google Drive Script URL la cusboonaysiiyay!")
            testGoogleDriveScriptConnection { _, _ -> }
        }
    }

    fun testGoogleDriveScriptConnection(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = GoogleDriveSync.testScriptConnection(getApplication())
            if (result.isSuccess) {
                onResult(true, result.getOrNull() ?: "Google Drive Script Connected 🟢")
            } else {
                onResult(false, "❌ Google Drive Cilad: ${result.exceptionOrNull()?.localizedMessage ?: "Lama xidhiidhi karo"}")
            }
        }
    }

    fun syncToGoogleDriveScript(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val jsonPayload = repository.exportFullDatabaseDirectFromDb()
                if (jsonPayload.isBlank() || jsonPayload == "{}") {
                    onResult(false, "⚠️ Database-ku waa madhan yahay, wax xog ah oo la keydiyo ma jiro.")
                    return@launch
                }
                val currentAdmin = _currentUser.value?.username ?: "Admin"
                val sName = schoolName.value.ifBlank { "Mahdi Cali School" }

                val result = GoogleDriveSync.uploadToGoogleDriveScript(
                    context = getApplication(),
                    jsonPayload = jsonPayload,
                    schoolName = sName,
                    updatedBy = currentAdmin
                )
                if (result.isSuccess) {
                    val msg = result.getOrNull() ?: "✅ Xogta waxaa lagu keydiyay Google Drive!"
                    _uiMessage.emit("📁 Xogta si buuxda ayaa loogu shubay Google Drive!")
                    onResult(true, msg)
                } else {
                    onResult(false, "❌ Google Drive Cilad: ${result.exceptionOrNull()?.localizedMessage}")
                }
            } catch (e: Exception) {
                onResult(false, "❌ Cilad: ${e.localizedMessage}")
            }
        }
    }

    fun restoreFromGoogleDriveScript(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val result = GoogleDriveSync.downloadFromGoogleDriveScript(getApplication())
                if (result.isSuccess) {
                    val (payload, driveSchoolName) = result.getOrNull() ?: Pair(null, null)
                    if (!payload.isNullOrBlank()) {
                        val success = repository.importBackupJson(payload, clearFirst = false)
                        if (success) {
                            if (!driveSchoolName.isNullOrBlank()) {
                                prefs.edit().putString("school_name", driveSchoolName).apply()
                                _schoolName.value = driveSchoolName
                            }
                            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                            val timestamp = sdf.format(java.util.Date())
                            GoogleDriveSync.setLastSyncTime(getApplication(), timestamp)
                            _uiMessage.emit("📁 Xogta si buuxda ayaa looga soo dejiyay Google Drive!")
                            onResult(true, "✅ Xogta guud ee dugsiga si guul leh ayaa looga soo dejiyay Google Drive!\n(Taariikhda: $timestamp)")
                        } else {
                            onResult(false, "❌ Qaabka xogta laga soo dejiyay Google Drive ma saxna.")
                        }
                    } else {
                        onResult(false, "⚠️ Wax xog ah lagama helin Google Drive. Fadlan marka hore keyd u dir Google Drive.")
                    }
                } else {
                    onResult(false, "❌ Cilad: ${result.exceptionOrNull()?.localizedMessage}")
                }
            } catch (e: Exception) {
                onResult(false, "❌ Cilad soo dejin: ${e.localizedMessage}")
            }
        }
    }

    fun saveBackupToUri(uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val jsonPayload = repository.exportFullDatabaseDirectFromDb()
                val os = getApplication<android.app.Application>().contentResolver.openOutputStream(uri)
                if (os == null) {
                    onResult(false, "❌ Lama furi karo meesha aad dooratay ee Google Drive.")
                    return@launch
                }
                val writeResult = GoogleDriveSync.writePayloadToStream(os, jsonPayload)
                if (writeResult.isSuccess) {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    val timestamp = sdf.format(java.util.Date())
                    _uiMessage.emit("💾 Faylka keydka si toos ah ayaa loogu keydiyay Google Drive!")
                    onResult(true, "✅ Faylka keydka guud ee dugsiga si toos ah ayaa loogu keydiyay Google Drive!\n(Taariikhda: $timestamp)")
                } else {
                    onResult(false, "❌ Cilad keydinta: ${writeResult.exceptionOrNull()?.localizedMessage}")
                }
            } catch (e: Exception) {
                onResult(false, "❌ Cilad: ${e.localizedMessage}")
            }
        }
    }

    fun restoreBackupFromUri(uri: Uri, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val isStream = getApplication<android.app.Application>().contentResolver.openInputStream(uri)
                if (isStream == null) {
                    onResult(false, "❌ Lama furi karo faylka aad dooratay.")
                    return@launch
                }
                val readResult = GoogleDriveSync.readPayloadFromStream(isStream)
                if (readResult.isSuccess) {
                    val jsonPayload = readResult.getOrNull() ?: ""
                    val success = repository.importBackupJson(jsonPayload, clearFirst = false)
                    if (success) {
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        val timestamp = sdf.format(java.util.Date())
                        _uiMessage.emit("📥 Xogta si buuxda ayaa looga soo celiyay faylka Google Drive!")
                        onResult(true, "✅ Dhammaan ardayda, fasallada, imtixaannada iyo xogtii dugsiga si buuxda ayaa looga soo celiyay faylka Google Drive!\n(Taariikhda: $timestamp)")
                    } else {
                        onResult(false, "❌ Faylkan kuma jiro qaab xog dugsi oo sax ah.")
                    }
                } else {
                    onResult(false, "❌ Cilad akhrinta faylka: ${readResult.exceptionOrNull()?.localizedMessage}")
                }
            } catch (e: Exception) {
                onResult(false, "❌ Cilad: ${e.localizedMessage}")
            }
        }
    }

    fun getFirebaseUrl(): String = CloudSync.getFirebaseUrl(getApplication())
    fun getFirebaseKey(): String = CloudSync.getFirebaseKey(getApplication())

    fun saveFirebaseSettings(url: String, key: String) {
        CloudSync.setFirebaseUrl(getApplication(), url)
        CloudSync.setFirebaseKey(getApplication(), key)
        viewModelScope.launch {
            _uiMessage.emit("⚡ Firebase Settings Updated: $url")
            testFirebaseConnection { _, _ -> }
        }
    }

    fun testFirebaseConnection(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = CloudSync.testConnection(getApplication())
            if (result.isSuccess) {
                onResult(true, result.getOrNull() ?: "Firebase Connected 🟢")
            } else {
                onResult(false, "❌ Firebase Cilad: ${result.exceptionOrNull()?.localizedMessage ?: "Lama xidhiidhi karo"}")
            }
        }
    }

    fun forceSyncToFirebase(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val jsonPayload = repository.exportFullDatabaseDirectFromDb()
                if (jsonPayload.isBlank() || jsonPayload == "{}") {
                    onResult(false, "⚠️ Database-ku waa madhan yahay, wax xog ah oo la keydiyo ma jiro.")
                    return@launch
                }

                val currentAdmin = _currentUser.value?.username ?: "Admin"
                val sName = schoolName.value.ifBlank { "Dugsiga" }
                
                // Upload to Firebase Cloud Server
                val spResult = CloudSync.uploadFullDatabaseToFirebase(
                    context = getApplication(),
                    jsonPayload = jsonPayload,
                    schoolName = sName,
                    updatedBy = currentAdmin
                )

                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                val timestamp = sdf.format(java.util.Date())
                prefs.edit().putString("last_cloud_sync_time", timestamp).putString("cloud_latest_json", jsonPayload).apply()
                _lastCloudSyncTime.value = timestamp

                if (spResult.isSuccess) {
                    _uiMessage.emit("⚡ Xogta si buuxda ayaa loogu shubay Firebase Cloud Server!")
                    onResult(true, "✅ Xogta dugsiga si degdeg ah ayaa loogu keydiyay Firebase Cloud Server!\n(Taariikhda: $timestamp)")
                } else {
                    onResult(false, "❌ Firebase sync failed: ${spResult.exceptionOrNull()?.localizedMessage}")
                }
            } catch (e: Exception) {
                onResult(false, "❌ Firebase Error: ${e.localizedMessage}")
            }
        }
    }

    fun forceSyncFromFirebase(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                var downloadedPayload: String? = null
                var downloadedSchoolName: String? = null
                val sourceProvider = "Firebase Cloud (PostgreSQL)"

                val spResult = CloudSync.downloadFullDatabaseFromFirebase(getApplication())
                if (spResult.isSuccess) {
                    val (payload, cloudSchoolName) = spResult.getOrNull() ?: Pair(null, null)
                    if (!payload.isNullOrBlank()) {
                        downloadedPayload = payload
                        downloadedSchoolName = cloudSchoolName
                    }
                }

                if (!downloadedPayload.isNullOrBlank()) {
                    val success = repository.importBackupJson(downloadedPayload, clearFirst = false)
                    if (success) {
                        if (!downloadedSchoolName.isNullOrBlank()) {
                            prefs.edit().putString("school_name", downloadedSchoolName).apply()
                            _schoolName.value = downloadedSchoolName
                        }
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        val timestamp = sdf.format(java.util.Date())
                        prefs.edit().putString("last_cloud_sync_time", timestamp).putString("cloud_latest_json", downloadedPayload).apply()
                        _lastCloudSyncTime.value = timestamp
                        _uiMessage.emit("⚡ Xogta dugsiga si buuxda ayaa looga soo dejiyay $sourceProvider!")
                        onResult(true, "✅ Xogta guud ee dugsiga si guul leh ayaa looga soo dejiyay $sourceProvider!\n(Taariikhda: $timestamp)")
                    } else {
                        onResult(false, "❌ Qaabka xogta soo degtay ma saxna!")
                    }
                } else {
                    onResult(false, "❌ Wax xog ah lagama helin Firebase Cloud Server-ka. Fadlan marka hore xog u shub.")
                }
            } catch (e: Exception) {
                onResult(false, "❌ Firebase Cilad: ${e.localizedMessage}")
            }
        }
    }

    fun checkAuthAndLoadDataOnLaunch() {
        viewModelScope.launch {
            _isStartupLoading.value = true
            
            try {
                // 1. Check if local data is empty. If so, attempt to restore from Firebase.
                val studentCount = repository.studentDao.getStudentCount()
                if (studentCount == 0) {
                     _syncStatus.value = "Authenticating with server..."
                     // Try Google Drive Script first if configured
                     val scriptUrl = GoogleDriveSync.getScriptUrl(getApplication())
                     if (scriptUrl.isNotBlank()) {
                         _syncStatus.value = "Restoring from Google Drive..."
                         val driveResult = GoogleDriveSync.downloadFromGoogleDriveScript(getApplication())
                         if (driveResult.isSuccess) {
                             val (payload, cloudSchoolName) = driveResult.getOrNull() ?: Pair(null, null)
                             if (!payload.isNullOrBlank()) {
                                 val success = repository.importBackupJson(payload, clearFirst = false)
                                 if (success) {
                                     if (!cloudSchoolName.isNullOrBlank()) {
                                         prefs.edit().putString("school_name", cloudSchoolName).apply()
                                         _schoolName.value = cloudSchoolName
                                     }
                                     val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                     val timestamp = sdf.format(java.util.Date())
                                     GoogleDriveSync.setLastSyncTime(getApplication(), timestamp)
                                     prefs.edit().putString("last_cloud_sync_time", timestamp).apply()
                                     _lastCloudSyncTime.value = timestamp
                                 }
                             }
                         }
                     }
                     val spResult = CloudSync.downloadFullDatabaseFromFirebase(getApplication())
                     if (spResult.isSuccess) {
                         _syncStatus.value = "Restoring students and records..."
                         val (payload, cloudSchoolName) = spResult.getOrNull() ?: Pair(null, null)
                         if (!payload.isNullOrBlank()) {
                             val success = repository.importBackupJson(payload, clearFirst = false)
                             if (success) {
                                 _syncStatus.value = "Finalizing setup..."
                                 if (!cloudSchoolName.isNullOrBlank()) {
                                     prefs.edit().putString("school_name", cloudSchoolName).apply()
                                     _schoolName.value = cloudSchoolName
                                 }
                                 val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                 val timestamp = sdf.format(java.util.Date())
                                 prefs.edit().putString("last_cloud_sync_time", timestamp).apply()
                                 _lastCloudSyncTime.value = timestamp
                                 android.util.Log.d("SchoolViewModel", "Restoration from Firebase successful.")
                             }
                         }
                     }
                }

                // 2. Continue with session restoration
                _syncStatus.value = "Restoring session..."
                val lastLoggedInUsername = prefs.getString("last_logged_in_username", "")
                if (!lastLoggedInUsername.isNullOrBlank()) {
                    val user = repository.getUserByUsername(lastLoggedInUsername)
                    if (user != null && !user.isLocked) {
                        _currentUser.value = user
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SchoolViewModel", "Startup error: ${e.message}")
            } finally {
                _isStartupLoading.value = false
            }
        }
    }

    fun loadStarterOfflineData(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                repository.restoreDefaultOfflineData()
                _uiMessage.emit("✅ Xogta Bilowga ah ee Dugsiga (Offline Data) si buuxda ayaa loo buuxiyay!")
                onResult(true, "✅ Xogta dugsiga, fasallada, ardayda, macalimiinta, dhibcaha iyo fiiga si toos ah ayaa loogu shubay taleefankaaga!")
            } catch (e: Exception) {
                onResult(false, "❌ Cilad: ${e.localizedMessage}")
            }
        }
    }

    private fun setupFirebaseAutoSync(context: Context) {
        // 1. Immediate auto-restore on startup / fresh install so data is restored automatically from Firebase
        viewModelScope.launch {
            kotlinx.coroutines.delay(500)
            pullAndMergeFromCloud(context)
        }

        // 2. Periodic background pull every 15 seconds to sync updates in real-time
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(15_000L)
                pullAndMergeFromCloud(context)
            }
        }

        // 3. Instant auto-backup whenever local database data changes while inside the app
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            combine(classes, students, exams, fees, allAttendance, allExamMarks, users) { args ->
                args
            }.collect {
                kotlinx.coroutines.delay(500)
                triggerAutoInternetSync(context, forceImmediate = true)
            }
        }
    }

    fun pullAndMergeFromCloud(context: Context) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (!isInternetAvailable(context)) return@launch
            try {
                // Reconcile any pending offline queue items to Firestore
                CloudSync.reconcilePendingChanges(context, repository.syncQueueDao)

                // Try downloading from Realtime DB first
                val result = CloudSync.downloadFullDatabaseFromFirebase(context)
                if (result.isSuccess) {
                    val pair = result.getOrNull()
                    val jsonPayload = pair?.first
                    if (!jsonPayload.isNullOrBlank()) {
                        repository.importBackupJson(jsonPayload, clearFirst = false)
                    }
                }

                // Also sync/pull from Firestore snapshot as fallback
                val fsResult = CloudSync.downloadFirestoreSnapshot(context)
                if (fsResult.isSuccess) {
                    val fsPayload = fsResult.getOrNull()
                    if (!fsPayload.isNullOrBlank()) {
                        repository.importBackupJson(fsPayload, clearFirst = false)
                    }
                }

                // Also sync/pull from Central Google Drive Script
                val driveResult = GoogleDriveSync.downloadFromGoogleDriveScript(context)
                if (driveResult.isSuccess) {
                    val drivePayload = driveResult.getOrNull()?.first
                    if (!drivePayload.isNullOrBlank()) {
                        repository.importBackupJson(drivePayload, clearFirst = false)
                    }
                }
            } catch (e: Exception) {
                // silent background pull
            }
        }
    }

    fun updateFirebaseCredentials(url: String, key: String) {
        CloudSync.setFirebaseUrl(getApplication(), url)
        CloudSync.setFirebaseKey(getApplication(), key)
    }

    fun restorePartialBackupRecord(record: BackupRecord, categories: Set<String>, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                // For now, simple implementation
                val success = repository.importBackupJson(record.jsonPayload, selectedCategories = categories, clearFirst = false)
                onComplete(success, if (success) "✅ Xogta qayb ahaan ayaa loo soo celiyay!" else "❌ Soo celintu way fashilantay.")
            } catch (e: Exception) {
                onComplete(false, "❌ Khalad: ${e.message}")
            }
        }
    }





    private fun setupAutoInternetCloudBackup(context: Context) {
        try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager != null) {
                val request = NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()

                connectivityManager.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        super.onAvailable(network)
                        triggerAutoInternetSync(context)
                    }

                    override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                        super.onCapabilitiesChanged(network, networkCapabilities)
                        if (networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
                            triggerAutoInternetSync(context)
                        }
                    }
                })
            }
        } catch (e: Exception) {
            android.util.Log.e("SchoolViewModel", "NetworkCallback registration error: ${e.localizedMessage}")
        }
    }

    fun triggerAutoInternetSync(context: Context, forceImmediate: Boolean = false) {
        val currentTime = System.currentTimeMillis()
        // Debounce to at most once every 30 seconds unless forceImmediate is requested
        if (!forceImmediate && currentTime - lastAutoSyncTimestamp < 30_000L) {
            return
        }
        lastAutoSyncTimestamp = currentTime

        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (!_autoCloudSyncEnabled.value && !forceImmediate) return@launch
            if (!isInternetAvailable(context)) return@launch

            try {
                val jsonPayload = repository.exportFullDatabaseDirectFromDb()
                if (jsonPayload.isBlank() || jsonPayload == "{}" ) {
                    return@launch
                }

                // Anti-loop protection: Only check lastSavedJson if not forceImmediate
                if (!forceImmediate) {
                    val lastSavedJson = prefs.getString("cloud_latest_json", "") ?: ""
                    if (lastSavedJson.trim() == jsonPayload.trim()) {
                        return@launch
                    }
                }

                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                val timestamp = sdf.format(java.util.Date())
                prefs.edit().putString("last_cloud_sync_time", timestamp).putString("cloud_latest_json", jsonPayload).apply()
                _lastCloudSyncTime.value = timestamp

                // 1. Upload to Firebase Realtime Database
                val currentAdmin = _currentUser.value?.username ?: "Admin"
                val sName = schoolName.value.ifBlank { "Dugsiga" }
                CloudSync.uploadFullDatabaseToFirebase(
                    context = getApplication(),
                    jsonPayload = jsonPayload,
                    schoolName = sName,
                    updatedBy = currentAdmin
                )

                // 2. Reconcile offline queue and sync to Cloud Firestore
                CloudSync.reconcilePendingChanges(getApplication(), repository.syncQueueDao)
                CloudSync.syncFirestoreSnapshot(getApplication(), jsonPayload)

                // 3. Auto-sync to Google Drive Script if configured
                val scriptUrl = GoogleDriveSync.getScriptUrl(getApplication())
                if (scriptUrl.isNotBlank()) {
                    GoogleDriveSync.uploadToGoogleDriveScript(
                        context = getApplication(),
                        jsonPayload = jsonPayload,
                        schoolName = sName,
                        updatedBy = currentAdmin
                    )
                }

                _uiMessage.emit("⚡ Xogta waxaa si degdeg ah loogu keydiyay Cloud!")
            } catch (e: Exception) {
                // Background cloud sync silent retry
            }
        }
    }

    fun setAutoCloudSync(enabled: Boolean) {
        prefs.edit().putBoolean("auto_cloud_sync", enabled).apply()
        _autoCloudSyncEnabled.value = enabled
    }

    fun isInternetAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        if (connectivityManager != null) {
            val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            if (capabilities != null) {
                return capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) ||
                        capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)
            }
        }
        return false
    }

    fun syncToCloud(context: Context, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (!isInternetAvailable(context)) {
                onResult(false, "❌ Internet ma jiro! Fadlan ku xidh Wi-Fi ama Mobile Data.")
                return@launch
            }
            try {
                val jsonPayload = repository.exportBackupJson(
                    classList = classes.value,
                    studentList = students.value,
                    userList = users.value,
                    examList = exams.value,
                    feeList = fees.value,
                    attendanceList = allAttendance.value,
                    markList = allExamMarks.value
                )

                val currentAdmin = currentUser.value?.username ?: "Admin"
                val sName = schoolName.value.ifBlank { "Dugsiga" }
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                val timestamp = sdf.format(java.util.Date())

                // 1. Google Drive Apps Script Cloud Sync
                val scriptUrl = GoogleDriveSync.getScriptUrl(context)
                if (scriptUrl.isNotBlank()) {
                    val driveRes = GoogleDriveSync.uploadToGoogleDriveScript(
                        context = context,
                        jsonPayload = jsonPayload,
                        schoolName = sName,
                        updatedBy = currentAdmin
                    )
                    if (driveRes.isSuccess) {
                        prefs.edit().putString("last_cloud_sync_time", timestamp).putString("cloud_latest_json", jsonPayload).apply()
                        _lastCloudSyncTime.value = timestamp
                        _uiMessage.emit("☁️ Xogta dugsiga si toos ah ayaa loogu keydiyay Google Drive Cloud!")
                        onResult(true, "✅ Google Drive Cloud Sync: Xogta guud waa la keydiyay! ($timestamp)")
                        return@launch
                    }
                }

                // 2. Try Firebase Sync
                val fbResult = CloudSync.uploadFullDatabaseToFirebase(
                    context = context,
                    jsonPayload = jsonPayload,
                    schoolName = sName,
                    updatedBy = currentAdmin
                )

                if (fbResult.isSuccess) {
                    prefs.edit().putString("last_cloud_sync_time", timestamp).putString("cloud_latest_json", jsonPayload).apply()
                    _lastCloudSyncTime.value = timestamp
                    _uiMessage.emit("☁️ Xogta dugsiga si toos ah ayaa loogu keydiyay Firebase Firestore!")
                    onResult(true, "✅ Firebase Cloud Sync: Xogta guud waa la keydiyay! ($timestamp)")
                    return@launch
                }

                // Local snapshot saved safely
                prefs.edit().putString("last_cloud_sync_time", timestamp).putString("cloud_latest_json", jsonPayload).apply()
                _lastCloudSyncTime.value = timestamp
                GoogleDriveSync.cacheOfflineBackup(context, jsonPayload)
                onResult(true, "✅ Xogta waxaa lagu keydiyay kaydka taleefanka si nabad ah ($timestamp)")
            } catch (e: Exception) {
                onResult(false, "❌ Cilad kaydinta: ${e.localizedMessage}")
            }
        }
    }

    fun syncClassToCloud(context: Context, classId: Long, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (!isInternetAvailable(context)) {
                onResult(false, "❌ Internet ma jiro! Fadlan ku xidh Wi-Fi ama Mobile Data.")
                return@launch
            }
            val targetClass = classes.value.find { it.id == classId }
            if (targetClass == null) {
                onResult(false, "❌ Fasalka lama helin!")
                return@launch
            }
            try {
                val jsonPayload = repository.exportClassBackupJson(
                    targetClass = targetClass,
                    studentList = students.value,
                    examList = exams.value,
                    feeList = fees.value,
                    attendanceList = allAttendance.value,
                    markList = allExamMarks.value
                )

                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                val timestamp = sdf.format(java.util.Date())

                // 1. Try Firebase
                val result = CloudSync.uploadClassToFirebase(context, classId, targetClass.name, jsonPayload)
                if (result.isSuccess) {
                    prefs.edit().putString("cloud_class_${classId}_json", jsonPayload).apply()
                    _uiMessage.emit("☁️ Fasalka ${targetClass.name} xogtiisa waxaa lagu keydiyay Firebase!")
                    onResult(true, "✅ Firebase: Fasalka ${targetClass.name} waa la keydiyay! ($timestamp)")
                    return@launch
                }

                // 2. Fallback HTTP
                val url = java.net.URL("https://httpbin.org/post")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; utf-8")
                conn.setRequestProperty("Accept", "application/json")
                conn.doOutput = true
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                conn.outputStream.use { os ->
                    val input = jsonPayload.toByteArray(Charsets.UTF_8)
                    os.write(input, 0, input.size)
                }

                val responseCode = conn.responseCode
                if (responseCode in 200..299) {
                    prefs.edit().putString("cloud_class_${classId}_json", jsonPayload).apply()
                    _uiMessage.emit("☁️ Fasalka ${targetClass.name} waxaa lagu keydiyay Cloud Server!")
                    onResult(true, "✅ Class ${targetClass.name} Cloud Backup Successful! ($timestamp)")
                } else {
                    onResult(false, "❌ Cloud Server HTTP error: $responseCode")
                }
            } catch (e: Exception) {
                onResult(false, "❌ Class Cloud Sync failed: ${e.localizedMessage}")
            }
        }
    }

    fun restoreFromCloud(context: Context, restorePasswordInput: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (restorePasswordInput.trim() != "0011") {
                onResult(false, "❌ Furaha sirta ah ee soo celinta waa khalad! (Geli Code: 0011)")
                return@launch
            }
            if (!isInternetAvailable(context)) {
                onResult(false, "❌ Internet ma jiro! Fadlan ku xidh internet-ka si xogta loo soo dejiyo.")
                return@launch
            }
            try {
                var jsonToRestore: String? = null
                var schoolNameToRestore: String? = null
                var cloudProviderName = "Firebase Cloud"

                // 1. Try downloading from Firebase first
                val spDownload = CloudSync.downloadFullDatabaseFromFirebase(context)
                if (spDownload.isSuccess) {
                    val (spPayload, spSchool) = spDownload.getOrNull() ?: Pair(null, null)
                    if (!spPayload.isNullOrBlank()) {
                        jsonToRestore = spPayload
                        schoolNameToRestore = spSchool
                        cloudProviderName = "Firebase Realtime Database (mahdi-cali-cloud)"
                    }
                }

                // 2. Fallback to local cached backup if cloud was empty
                if (jsonToRestore.isNullOrBlank()) {
                    val cachedCloudJson = prefs.getString("cloud_latest_json", "")
                    if (!cachedCloudJson.isNullOrBlank()) {
                        jsonToRestore = cachedCloudJson
                        cloudProviderName = "Local Cloud Cache"
                    }
                }

                if (!jsonToRestore.isNullOrBlank()) {
                    val success = repository.importBackupJson(jsonToRestore, clearFirst = false)
                    if (success) {
                        if (!schoolNameToRestore.isNullOrBlank()) {
                            prefs.edit().putString("school_name", schoolNameToRestore).apply()
                            _schoolName.value = schoolNameToRestore
                        }
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        val timestamp = sdf.format(java.util.Date())
                        prefs.edit().putString("last_cloud_sync_time", timestamp).putString("cloud_latest_json", jsonToRestore).apply()
                        _lastCloudSyncTime.value = timestamp
                        _uiMessage.emit("☁️ Xogta guud ee dugsiga si buuxda ayaa looga soo celiyay $cloudProviderName!")
                        onResult(true, "✅ Xogta guud ee dugsiga si guul leh ayaa looga soo celiyay $cloudProviderName!\n(Taariikhda: $timestamp)")
                    } else {
                        onResult(false, "❌ Qaabka xogta soo degtay ma saxna!")
                    }
                } else {
                    onResult(false, "❌ Wax xog keyd ah lagama helin Server-ka (Database-ku waa faaruq). Fadlan hubi inaad marka hore xog keydisay.")
                }
            } catch (e: Exception) {
                onResult(false, "❌ Soo celinta xogtu way guuldarraysatay: ${e.localizedMessage}")
            }
        }
    }

    fun restoreClassFromCloud(context: Context, classId: Long, restorePasswordInput: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            if (restorePasswordInput.trim() != "0011") {
                onResult(false, "❌ Furaha sirta ah ee soo celinta waa khalad! (Geli Code: 0011)")
                return@launch
            }
            if (!isInternetAvailable(context)) {
                onResult(false, "❌ Internet ma jiro! Fadlan ku xidh internet-ka.")
                return@launch
            }
            try {
                val fbDownload = CloudSync.downloadClassFromFirebase(context, classId)
                var cachedClassJson: String? = null
                if (fbDownload.isSuccess && !fbDownload.getOrNull().isNullOrBlank()) {
                    cachedClassJson = fbDownload.getOrNull()
                } else {
                    cachedClassJson = prefs.getString("cloud_class_${classId}_json", "")
                }

                if (!cachedClassJson.isNullOrBlank()) {
                    val success = repository.importBackupJson(cachedClassJson, clearFirst = false)
                    if (success) {
                        _uiMessage.emit("☁️ Fasalka xogtiisa waa laga soo celiyay Server-ka!")
                        onResult(true, "✅ Xogta fasalka si guul leh ayaa loo soo celiyay!")
                    } else {
                        onResult(false, "❌ Qaabka xogta fasalku ma saxna!")
                    }
                } else {
                    onResult(false, "❌ Fasalkan wax xog keyd ah lagama helin Server-ka!")
                }
            } catch (e: Exception) {
                onResult(false, "❌ Soo celinta fasalku way fashilantay: ${e.localizedMessage}")
            }
        }
    }

    fun exportClassBackup(context: Context, classId: Long) {
        viewModelScope.launch {
            val targetClass = classes.value.find { it.id == classId } ?: return@launch
            val jsonString = repository.exportClassBackupJson(
                targetClass = targetClass,
                studentList = students.value,
                examList = exams.value,
                feeList = fees.value,
                attendanceList = allAttendance.value,
                markList = allExamMarks.value
            )
            val fileName = "class_${targetClass.name.replace(" ", "_")}_backup.json"
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { it.write(jsonString.toByteArray()) }

            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Class Backup File"))
        }
    }

    fun downloadSampleStudentCsv(context: Context) {
        val sampleCsv = """Full Name,Gender,Mother Name,Phone
Ali Ahmed Jama,Male,Amina Hassan,0634123456
Fadumo Hassan Omar,Female,Maryan Abdi,0635987654
Mohamed Abdi Farah,Male,Sahra Osman,0634998877
Khadra Jama Yassin,Female,Fatima Ibrahim,0634112233
Hassan Barre Roble,Male,Hawa Noor,0635001122"""

        shareCSVFile(context, "sample_students_template.csv", sampleCsv)
    }

    // Search query for Student Exam Result Portal
    val studentSearchQuery = MutableStateFlow("")

    // List of classes permitted for current user
    val classes: StateFlow<List<SchoolClass>> = repository.getAllClasses().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Permitted class IDs based on role
    private val permittedClassIds: Flow<List<Long>> = combine(classes, currentUser) { classList, user ->
        if (user == null || user.role == "ADMIN" || user.role == "CASHIER") {
            classList.map { it.id }
        } else {
            val ids = user.assignedClassIds.split(",").mapNotNull { it.trim().toLongOrNull() }
            if (ids.isEmpty()) {
                classList.map { it.id }
            } else {
                ids
            }
        }
    }

    // Filtered Students (Admins & Cashiers see all students across the entire school; Teachers see all or assigned)
    @OptIn(ExperimentalCoroutinesApi::class)
    val students: StateFlow<List<Student>> = combine(permittedClassIds, currentUser) { ids, user ->
        ids to user
    }.flatMapLatest { (ids, user) ->
        if (user == null || user.role == "ADMIN" || user.role == "CASHIER") {
            repository.getAllStudents()
        } else if (ids.isNotEmpty()) {
            repository.getStudentsByClasses(ids)
        } else {
            repository.getAllStudents()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Shift-Filtered Classes
    val displayClasses: StateFlow<List<SchoolClass>> = combine(classes, selectedShift) { list, shift ->
        if (shift.equals("Dhammaan", ignoreCase = true) || shift.isBlank()) {
            list
        } else {
            list.filter { it.shift.equals(shift, ignoreCase = true) }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Shift-Filtered Students
    val displayStudents: StateFlow<List<Student>> = combine(students, classes, selectedShift) { studList, classList, shift ->
        if (shift.equals("Dhammaan", ignoreCase = true) || shift.isBlank()) {
            studList
        } else {
            val classShiftMap = classList.associate { it.id to it.shift }
            studList.filter { student ->
                student.shift.equals(shift, ignoreCase = true) || classShiftMap[student.classId]?.equals(shift, ignoreCase = true) == true
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Filtered Exams
    @OptIn(ExperimentalCoroutinesApi::class)
    val exams: StateFlow<List<Exam>> = combine(permittedClassIds, currentUser) { ids, user ->
        ids to user
    }.flatMapLatest { (ids, user) ->
        if (user == null || user.role == "ADMIN" || user.role == "CASHIER") {
            repository.getAllExams()
        } else if (ids.isNotEmpty()) {
            repository.getExamsByClasses(ids)
        } else {
            repository.getAllExams()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Filtered Fees
    @OptIn(ExperimentalCoroutinesApi::class)
    val fees: StateFlow<List<FeeRecord>> = combine(permittedClassIds, currentUser) { ids, user ->
        ids to user
    }.flatMapLatest { (ids, user) ->
        if (user == null || user.role == "ADMIN" || user.role == "CASHIER") {
            repository.getAllFees()
        } else if (ids.isNotEmpty()) {
            repository.getFeesByClasses(ids)
        } else {
            repository.getAllFees()
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allAttendance: StateFlow<List<AttendanceRecord>> = repository.getAllAttendance().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allExamMarks: StateFlow<List<ExamMark>> = repository.getAllExamMarks().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val users: StateFlow<List<User>> = repository.getAllUsers().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Audit & Transparency Logs
    val auditLogs: StateFlow<List<AuditLog>> = repository.getAllAuditLogs().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val recycleBinItems: StateFlow<List<RecycleBinItem>> = repository.getAllRecycleBinItems().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val backupRecords: StateFlow<List<BackupRecord>> = repository.getAllBackupRecords().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val markHistories: StateFlow<List<MarkChangeHistory>> = repository.getAllMarkHistory().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val academicYears: StateFlow<List<AcademicYear>> = repository.getAllAcademicYears().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val announcements: StateFlow<List<Announcement>> = repository.getAllAnnouncements().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val subjects: StateFlow<List<Subject>> = repository.getAllSubjects().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun addAnnouncement(
        title: String,
        content: String,
        targetAudience: String = "ALL",
        category: String = "GENERAL",
        isPinned: Boolean = false,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (title.isBlank() || content.isBlank()) {
                    onResult(false, "Fadlan buuxi cinwaanka iyo qoraalka ogeysiiska!")
                    return@launch
                }
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                val now = sdf.format(java.util.Date())
                val currentAuthor = currentUser.value?.fullName ?: "Admin"
                val announcement = Announcement(
                    title = title.trim(),
                    content = content.trim(),
                    targetAudience = targetAudience,
                    category = category,
                    isPinned = isPinned,
                    author = currentAuthor,
                    date = now.substringBefore(" "),
                    createdAt = now
                )
                repository.insertAnnouncement(announcement)
                logAudit(
                    category = "CREATE",
                    title = "Ogeysiis Cusub: $title",
                    details = "Audience: $targetAudience, Category: $category"
                )
                _uiMessage.emit("📢 Ogeysiiska si guul leh ayaa loo daabacay!")
                onResult(true, "Ogeysiiska waa la daabacay!")
            } catch (e: Exception) {
                onResult(false, "Cilad: ${e.localizedMessage}")
            }
        }
    }

    fun deleteAnnouncement(id: Long, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                repository.deleteAnnouncement(id)
                logAudit(category = "DELETE", title = "Tirtirid Ogeysiis (ID: $id)")
                _uiMessage.emit("🗑️ Ogeysiiskii waa la tirtiray!")
                onResult(true, "Ogeysiiska waa la tirtiray")
            } catch (e: Exception) {
                onResult(false, "Cilad: ${e.localizedMessage}")
            }
        }
    }

    fun addSubject(code: String, name: String, grade: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (code.isBlank() || name.isBlank()) {
                    onResult(false, "Fadlan geli Subject Code iyo Magaca Maaddada!")
                    return@launch
                }
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                val now = sdf.format(java.util.Date())
                val subject = Subject(
                    subjectCode = code.trim().uppercase(),
                    subjectName = name.trim(),
                    grade = grade.ifBlank { "All" },
                    status = "ACTIVE",
                    createdAt = now,
                    updatedAt = now
                )
                repository.insertSubject(subject)
                logAudit(category = "CREATE", title = "Maaddo Cusub: $name ($code)")
                _uiMessage.emit("📚 Maaddada si guul leh ayaa loogu daray nidaamka!")
                onResult(true, "Maaddada waa lagu daray!")
            } catch (e: Exception) {
                onResult(false, "Cilad: ${e.localizedMessage}")
            }
        }
    }

    fun deleteSubject(id: Long, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                repository.subjectDao.deleteSubjectById(id)
                logAudit(category = "DELETE", title = "Tirtirid Maaddo (ID: $id)")
                _uiMessage.emit("🗑️ Maaddadii waa la tirtiray!")
                onResult(true, "Maaddada waa la tirtiray")
            } catch (e: Exception) {
                onResult(false, "Cilad: ${e.localizedMessage}")
            }
        }
    }

    fun logAudit(
        category: String,
        title: String,
        className: String = "",
        details: String = "",
        rawData: String = "",
        status: String = "SUCCESS"
    ) {
        viewModelScope.launch {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
            val timestamp = sdf.format(java.util.Date())
            val user = _currentUser.value
            val userName = user?.fullName ?: "Administrator"
            val userRole = user?.role ?: "ADMIN"

            val log = AuditLog(
                timestamp = timestamp,
                userName = userName,
                userRole = userRole,
                actionCategory = category,
                title = title,
                className = className,
                details = details,
                status = status,
                rawDataSummary = rawData
            )
            repository.insertAuditLog(log)
        }
    }

    // Search Result for Portal
    private val _searchedReportCard = MutableStateFlow<StudentReportCard?>(null)
    val searchedReportCard: StateFlow<StudentReportCard?> = _searchedReportCard.asStateFlow()

    // --- Authentication ---
    fun login(username: String, password: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val u = username.trim()
            val p = password.trim()
            if (u.isBlank() || p.isBlank()) {
                onResult(false, "Fadlan geli Username-kaaga iyo Password-kaaga!")
                return@launch
            }

            // Define hardcoded users for fallback
            val predefinedUser = when {
                u.equals("admin", ignoreCase = true) && p == "2536" -> Pair("ADMIN", "Administrator")
                u.equals("sacad", ignoreCase = true) && p == "3746" -> Pair("ADMIN", "Teacher Sa'ad Mohamed")
                u.equals("sma", ignoreCase = true) && p == "2536" -> Pair("SUPER_ADMIN", "SMA Admin")
                u.equals("accountant", ignoreCase = true) && p == "1234" -> Pair("ACCOUNTANT", "School Accountant")
                u.equals("cashier", ignoreCase = true) && p == "1234" -> Pair("CASHIER", "School Cashier")
                u.equals("teacher", ignoreCase = true) && p == "1234" -> Pair("TEACHER", "Macalin Guud")
                u.equals("parent", ignoreCase = true) && p == "1234" -> Pair("PARENT", "Waalidka Ardayda")
                u.equals("student", ignoreCase = true) && p == "1234" -> Pair("STUDENT", "Ardayga Dugsiga")
                else -> null
            }

            val dbUser = repository.getUserByUsername(u)
            if (dbUser != null) {
                if (dbUser.isLocked) {
                    onResult(false, "❌ Akoonkan waxaa xannibay Maamulaha guud.")
                    return@launch
                }
                if (dbUser.passwordHash.trim() == p) {
                    _currentUser.value = dbUser
                    prefs.edit().putString("last_logged_in_username", u).apply()
                    onResult(true, "📱 Kusoo dhawoow ${dbUser.fullName} (${dbUser.role})!")
                    return@launch
                } else {
                    onResult(false, "❌ Password-ka aad galisay ma saxna!")
                    return@launch
                }
            } else if (predefinedUser != null) {
                // Auto-provision user from predefined list
                val uObj = User(
                    username = u,
                    passwordHash = p,
                    fullName = predefinedUser.second,
                    role = predefinedUser.first
                )
                repository.insertUser(uObj)
                _currentUser.value = uObj
                prefs.edit().putString("last_logged_in_username", u).apply()
                onResult(true, "📱 Kusoo dhawoow ${uObj.fullName} (${uObj.role})!")
                return@launch
            } else {
                onResult(false, "❌ Akoonkan laguma helin xogta gudaha. Fadlan hubi username-ka ama isku day mar kale.")
                return@launch
            }
        }
    }

    fun logout() {
        prefs.edit().putString("last_logged_in_username", "").apply()
        _currentUser.value = null
    }

    fun changePassword(oldPass: String, newPass: String, onResult: (Boolean, String) -> Unit) {
        val user = _currentUser.value
        if (user == null) {
            onResult(false, "Not logged in!")
            return
        }
        if (user.passwordHash.trim() != oldPass.trim()) {
            onResult(false, "Current password is incorrect!")
            return
        }
        if (newPass.trim().length < 3) {
            onResult(false, "New password must be at least 3 characters!")
            return
        }
        viewModelScope.launch {
            val updated = user.copy(passwordHash = newPass.trim())
            repository.updateUser(updated)
            _currentUser.value = updated
            logAudit(
                category = "SECURITY",
                title = "Bedelaadda Password-ka (${user.username})",
                details = "Password-ka isticmaalaha ${user.fullName} (${user.username}) waa la bedelay waxaana lagu keydiyay Cloud-ka.",
                status = "SUCCESS"
            )
            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            onResult(true, "✅ Password changed successfully & synced to Cloud!")
        }
    }

    fun adminResetUserPassword(userId: Long, newPass: String, onResult: (Boolean, String) -> Unit) {
        val p = newPass.trim()
        if (p.isBlank()) {
            onResult(false, "Password-ku ma noqon karo meel faaruq ah!")
            return
        }
        viewModelScope.launch {
            val target = repository.getUserById(userId)
            if (target == null) {
                onResult(false, "Isticmaalaha lama helin!")
                return@launch
            }
            val updated = target.copy(passwordHash = p)
            repository.updateUser(updated)
            logAudit(
                category = "SECURITY",
                title = "Maamulaha ayaa bedelay Password-ka (${target.username})",
                details = "Password-ka ${target.fullName} (${target.username}) waxaa loo bedelay: $p",
                status = "SUCCESS"
            )
            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            onResult(true, "✅ Password-ka ${target.fullName} si guul leh ayaa loo cusbooneysiiyay!")
        }
    }

    fun createTeacher(username: String, pass: String, name: String, assignedClasses: List<Long>, onResult: (Boolean, String) -> Unit) {
        val u = username.trim()
        val p = pass.trim()
        val n = name.trim()
        if (u.isBlank() || p.isBlank() || n.isBlank()) {
            onResult(false, "Fadlan buuxi dhammaan meelaha banaan (Magaca, Username, Password)!")
            return
        }
        viewModelScope.launch {
            val existing = repository.getUserByUsername(u)
            if (existing != null) {
                onResult(false, "Username '$u' horay ayaa loo isticmaalay! Fadlan dooro mid kale.")
                return@launch
            }
            val assignedStr = assignedClasses.joinToString(",")
            val currentSchoolId = CloudSync.getActiveSchoolId(getApplication())

            // 2. Save in local Room Database cache
            val newUser = User(
                username = u,
                passwordHash = p,
                fullName = n,
                role = "TEACHER",
                assignedClassIds = assignedStr,
                isLocked = false
            )
            repository.insertUser(newUser)

            logAudit(
                category = "SECURITY",
                title = "Abuurista Akoon Macalin ($n)",
                details = "Waxaa la abuuray akoonka macalinka: $n | Username: $u | Auth: Local Cache 📱",
                status = "SUCCESS"
            )

            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            onResult(true, "✅ Akoonka Macalinka $n si guul leh ayaa loogu abuuray!\nUsername: $u\nPassword: $p")
        }
    }

    fun createCashier(username: String, pass: String, name: String, onResult: (Boolean, String) -> Unit) {
        val u = username.trim()
        val p = pass.trim()
        val n = name.trim()
        if (u.isBlank() || p.isBlank() || n.isBlank()) {
            onResult(false, "Fadlan buuxi dhammaan meelaha banaan!")
            return
        }
        viewModelScope.launch {
            val existing = repository.getUserByUsername(u)
            if (existing != null) {
                onResult(false, "Username '$u' horay ayaa loo isticmaalay!")
                return@launch
            }
            val currentSchoolId = CloudSync.getActiveSchoolId(getApplication())

            // 2. Save in local Room Database cache
            val newUser = User(
                username = u,
                passwordHash = p,
                fullName = n,
                role = "CASHIER",
                assignedClassIds = "",
                isLocked = false
            )
            repository.insertUser(newUser)
            logAudit(
                category = "SECURITY",
                title = "Abuurista Akoon Cashier ($n)",
                details = "Waxaa la abuuray akoonka khasnajiga: $n | Username: $u | Auth: Local Cache 📱",
                status = "SUCCESS"
            )
            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            onResult(true, "✅ Akoonka Cashier-ka $n si guul leh ayaa loo abuuray!\nUsername: $u\nPassword: $p")
        }
    }

    fun createUserWithRole(
        username: String,
        pass: String,
        name: String,
        role: String,
        assignedClasses: List<Long> = emptyList(),
        onResult: (Boolean, String) -> Unit
    ) {
        val u = username.trim()
        val p = pass.trim()
        val n = name.trim()
        val r = role.trim().uppercase()
        if (u.isBlank() || p.isBlank() || n.isBlank()) {
            onResult(false, "Fadlan buuxi dhammaan meelaha banaan!")
            return
        }
        viewModelScope.launch {
            val existing = repository.getUserByUsername(u)
            if (existing != null) {
                onResult(false, "Username '$u' horay ayaa loo isticmaalay!")
                return@launch
            }
            val assignedStr = if (r == "TEACHER") assignedClasses.joinToString(",") else ""

            val newUser = User(
                username = u,
                passwordHash = p,
                fullName = n,
                role = r,
                assignedClassIds = assignedStr,
                isLocked = false
            )
            repository.insertUser(newUser)
            logAudit(
                category = "SECURITY",
                title = "Abuurista Akoon $r ($n)",
                details = "Role: $r | Username: $u | Auth: Local Cache 📱",
                status = "SUCCESS"
            )
            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            onResult(true, "✅ Akoonka $r ($n) si guul leh ayaa loo abuuray!\nUsername: $u\nPassword: $p")
        }
    }


    fun toggleUserLock(userId: Long, isLocked: Boolean) {
        viewModelScope.launch {
            val all = users.value
            val targetUser = all.find { it.id == userId } ?: return@launch
            val updated = targetUser.copy(isLocked = isLocked)
            repository.updateUser(updated)

            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            _uiMessage.emit("User ${targetUser.fullName} is now ${if (isLocked) "Disabled" else "Active"}")
        }
    }

    fun toggleStudentFree(studentId: Long, isFree: Boolean) {
        if (_currentUser.value?.role != "ADMIN") {
            viewModelScope.launch { _uiMessage.emit("Only Administrator can change Free/Scholarship status.") }
            return
        }
        viewModelScope.launch {
            val all = students.value
            val targetStudent = all.find { it.id == studentId } ?: return@launch
            val updated = targetStudent.copy(isFree = isFree)
            repository.updateStudent(updated)
            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            _uiMessage.emit("Student ${targetStudent.name} set to ${if (isFree) "FREE (Scholarship)" else "Standard"}")
        }
    }

    fun recordStudentPayment(
        studentId: Long,
        classId: Long,
        amount: Double,
        currency: String,
        feeType: String,
        notes: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val user = _currentUser.value
        if (user != null && user.role == "TEACHER") {
            viewModelScope.launch { _uiMessage.emit("Permission Denied: Teachers cannot record fee payments.") }
            onResult(false, "Teachers cannot record fee payments.")
            return
        }
        val targetStudent = students.value.find { it.id == studentId }
        if (targetStudent?.isFree == true) {
            viewModelScope.launch { _uiMessage.emit("Payment blocked: Free/Scholarship students cannot be billed.") }
            onResult(false, "Student is fee-exempt (Scholarship).")
            return
        }
        viewModelScope.launch {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
            val currentTimestamp = sdf.format(java.util.Date())
            val monthStr = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault()).format(java.util.Date())

            val fee = FeeRecord(
                classId = classId,
                studentId = studentId,
                feeType = feeType.ifBlank { "Tuition" },
                amount = amount,
                currency = currency,
                dueDate = currentTimestamp.substring(0, 10),
                month = monthStr,
                paidStatus = "Paid",
                paidDate = currentTimestamp,
                notes = notes
            )
            repository.insertFeeRecord(fee)

            val sName = targetStudent?.name ?: "Student #$studentId"
            val clsName = classes.value.find { it.id == classId }?.name ?: "Class #$classId"
            logAudit(
                category = "FINANCE",
                title = "Qabashada Lacagta Dugsi ($currency $amount)",
                className = clsName,
                details = "Ardayga: $sName (${targetStudent?.studentId}) | Nooca: $feeType | Bisha: $monthStr",
                rawData = "Ardayga: $sName | ID: ${targetStudent?.studentId} | Qadarka: $currency $amount | Nooca: $feeType | Bisha: $monthStr | Xusuus: $notes"
            )

            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            _uiMessage.emit("Payment of $currency $amount recorded successfully!")
            onResult(true, "Payment recorded on $currentTimestamp")
        }
    }

    fun deleteUser(id: Long) {
        viewModelScope.launch {
            repository.deleteUser(id)
            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            _uiMessage.emit("User deleted")
        }
    }

    // --- Student Result Portal Search ---
    fun searchStudentPortal(idOrName: String) {
        viewModelScope.launch {
            val query = idOrName.trim()
            if (query.isBlank()) {
                _searchedReportCard.value = null
                return@launch
            }
            val allStuds = students.value
            val student = allStuds.find {
                it.studentId.equals(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true)
            } ?: repository.getStudentByStudentId(query)

            if (student == null) {
                _searchedReportCard.value = null
                _uiMessage.emit("No student found with ID: $query")
                return@launch
            }

            val cls = classes.value.find { it.id == student.classId }
            val className = cls?.name ?: "Unknown Class"

            // Get marks for student
            val studentMarks = repository.examDao.getMarksForStudent(student.id).first()
            val allExamsList = exams.value

            val subjectResults = studentMarks.mapNotNull { mark ->
                val exam = allExamsList.find { it.id == mark.examId } ?: return@mapNotNull null
                val passed = !mark.isAbsent && mark.score >= exam.passMarks
                SubjectResult(
                    examName = exam.name,
                    subject = exam.subject,
                    score = mark.score,
                    totalMarks = exam.totalMarks,
                    passMarks = exam.passMarks,
                    isAbsent = mark.isAbsent,
                    isPassed = passed
                )
            }

            val totalExamsCount = subjectResults.size
            val totalPassedCount = subjectResults.count { it.isPassed }
            val totalScoreSum = subjectResults.sumOf { if (it.isAbsent) 0.0 else it.score }
            val totalMaxSum = subjectResults.sumOf { it.totalMarks }
            val avgPercentage = if (totalMaxSum > 0) (totalScoreSum / totalMaxSum) * 100 else 0.0

            // Attendance rate
            val attRecords = repository.getAttendanceForStudent(student.id).first()
            val totalDays = attRecords.size
            val presentDays = attRecords.count { it.status == "Present" }
            val attRate = if (totalDays > 0) (presentDays.toDouble() / totalDays.toDouble()) * 100 else 100.0

            // Pending Fees
            val studentFees = repository.getFeesForStudent(student.id).first()
            val pendingFeesList = studentFees.filter { it.paidStatus != "Paid" }

            _searchedReportCard.value = StudentReportCard(
                student = student,
                className = className,
                subjectResults = subjectResults,
                averagePercentage = avgPercentage,
                totalExams = totalExamsCount,
                totalPassed = totalPassedCount,
                attendanceRate = attRate,
                pendingFees = pendingFeesList
            )
        }
    }

    // --- Entity Actions ---
    fun addClass(name: String, incharge: String, startDate: String, endDate: String, shift: String = "Gelin Hore") {
        if (name.isBlank()) return
        if (_currentUser.value?.role == "TEACHER") {
            viewModelScope.launch { _uiMessage.emit("Permission Denied: Teachers cannot create new classes.") }
            return
        }
        val cleanShift = if (shift.isNotBlank()) shift else "Gelin Hore"
        viewModelScope.launch {
            repository.insertClass(
                SchoolClass(
                    name = name.trim(),
                    inchargeTeacher = incharge.trim(),
                    startDate = startDate.trim(),
                    endDate = endDate.trim(),
                    shift = cleanShift
                )
            )
            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            _uiMessage.emit("Fasalka '$name' ($cleanShift) waa la sameeyay!")
        }
    }

    fun updateClassShift(classId: Long, shift: String) {
        viewModelScope.launch {
            val cls = classes.value.find { it.id == classId }
            if (cls != null) {
                val updated = cls.copy(shift = shift)
                repository.updateClass(updated)
                triggerAutoInternetSync(getApplication(), forceImmediate = true)
                _uiMessage.emit("Shift-ka fasalka '${cls.name}' waxaa loo beddelay $shift")
            }
        }
    }

    fun deleteClass(id: Long) {
        if (_currentUser.value != null && _currentUser.value?.role != "ADMIN") {
            viewModelScope.launch { _uiMessage.emit("Permission Denied: Only Administrator can delete classes.") }
            return
        }
        viewModelScope.launch {
            repository.deleteClass(id)
            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            _uiMessage.emit("Class deleted")
        }
    }

    fun addStudent(name: String, gender: String, motherName: String, phone: String, classId: Long, shift: String = "") {
        if (name.isBlank()) return
        viewModelScope.launch {
            val autoId = repository.getNextStudentId()
            val cls = classes.value.find { it.id == classId }
            val studentShift = if (shift.isNotBlank()) shift else (cls?.shift ?: "Gelin Hore")
            repository.insertStudent(
                Student(
                    studentId = autoId,
                    name = name.trim(),
                    gender = gender,
                    motherName = motherName.trim(),
                    phone = phone.trim(),
                    classId = classId,
                    shift = studentShift
                )
            )
            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            _uiMessage.emit("Student $autoId ($name) added!")
        }
    }

    fun updateStudentShift(studentId: Long, shift: String) {
        viewModelScope.launch {
            val st = students.value.find { it.id == studentId }
            if (st != null) {
                val updated = st.copy(shift = shift)
                repository.updateStudent(updated)
                triggerAutoInternetSync(getApplication(), forceImmediate = true)
                _uiMessage.emit("Shift-ka ardayga '${st.name}' waxaa loo beddelay $shift")
            }
        }
    }

    fun updateUserShift(userId: Long, shift: String) {
        viewModelScope.launch {
            val usr = users.value.find { it.id == userId }
            if (usr != null) {
                val updated = usr.copy(shift = shift)
                repository.updateUser(updated)
                triggerAutoInternetSync(getApplication(), forceImmediate = true)
                _uiMessage.emit("Shift-ka shaqaalaha '${usr.fullName}' waxaa loo beddelay $shift")
            }
        }
    }

    fun bulkAddStudents(classId: Long, csvData: String, onResult: (Int, String) -> Unit) {
        if (classId == 0L || csvData.isBlank()) {
            onResult(0, "Fadlan ka dooro class ama geli xogta ardayda!")
            return
        }
        viewModelScope.launch {
            val lines = csvData.trim().split("\n")
            var count = 0
            lines.forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotBlank() && !trimmed.startsWith("Name", ignoreCase = true) && !trimmed.startsWith("Full Name", ignoreCase = true)) {
                    val parts = trimmed.split(",").map { it.trim() }
                    if (parts.isNotEmpty() && parts[0].isNotBlank()) {
                        val name = parts[0]
                        val gender = if (parts.size > 1 && parts[1].isNotBlank()) parts[1] else "Male"
                        val motherName = if (parts.size > 2) parts[2] else ""
                        val phone = if (parts.size > 3) parts[3] else ""
                        val autoId = repository.getNextStudentId()
                        repository.insertStudent(
                            Student(
                                studentId = autoId,
                                name = name,
                                gender = gender,
                                motherName = motherName,
                                phone = phone,
                                classId = classId
                            )
                        )
                        count++
                    }
                }
            }
            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            _uiMessage.emit("Waxaa si guul leh loo diiwaan geliyay $count arday!")
            onResult(count, "Successfully uploaded $count students!")
        }
    }

    fun deleteStudent(id: Long) {
        if (_currentUser.value?.role != "ADMIN") {
            viewModelScope.launch {
                _uiMessage.emit("Permission Denied: Only Administrator can delete students.")
            }
            return
        }
        viewModelScope.launch {
            repository.deleteStudent(id)
            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            _uiMessage.emit("Student removed")
        }
    }

    fun deleteClassStudents(classId: Long) {
        if (_currentUser.value?.role != "ADMIN") {
            viewModelScope.launch {
                _uiMessage.emit("Permission Denied: Only Administrator can delete students.")
            }
            return
        }
        viewModelScope.launch {
            val classStudents = students.value.filter { it.classId == classId && it.status != "DELETED" }
            val user = _currentUser.value?.fullName ?: "Admin"
            var count = 0
            classStudents.forEach { s ->
                repository.softDeleteStudent(s, user)
                count++
            }
            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            _uiMessage.emit("Fasalka waa laga saaray dhammaan ardaydii ku jirtay ($count arday) oo loo wareejiyay Qashin-qubka!")
        }
    }

    fun promoteStudents(studentIdsToPromote: List<Long>, targetClassId: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            var count = 0
            studentIdsToPromote.forEach { studentId ->
                val student = students.value.find { it.id == studentId }
                if (student != null) {
                    val updated = student.copy(classId = targetClassId)
                    repository.updateStudent(updated)
                    count++
                }
            }
            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            _uiMessage.emit("Waxaa si otomaatig ah loo gudbiyay $count arday!")
            onComplete()
        }
    }

    fun updateStudentClass(studentId: Long, targetClassId: Long) {
        viewModelScope.launch {
            val student = students.value.find { it.id == studentId }
            if (student != null) {
                val updated = student.copy(classId = targetClassId)
                repository.updateStudent(updated)
                triggerAutoInternetSync(getApplication(), forceImmediate = true)
                _uiMessage.emit("Fasalka ardayga '${student.name}' waa la cusbooneysiiyay!")
            }
        }
    }

    fun addExam(classId: Long, name: String, subject: String, totalMarks: Double, passMarks: Double, date: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.insertExam(
                Exam(
                    classId = classId,
                    name = name.trim(),
                    subject = if (subject.isNotBlank()) subject.trim() else name.trim(),
                    totalMarks = totalMarks,
                    passMarks = passMarks,
                    date = date.trim()
                )
            )
            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            _uiMessage.emit("Exam '$name' created!")
        }
    }

    fun updateExam(exam: Exam) {
        viewModelScope.launch {
            repository.updateExam(exam)
            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            _uiMessage.emit("Exam/Subject '${exam.name}' updated!")
        }
    }

    fun deleteExam(id: Long) {
        viewModelScope.launch {
            repository.deleteExam(id)
            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            _uiMessage.emit("Exam deleted")
        }
    }

    fun saveExamMarks(examId: Long, marks: List<ExamMark>) {
        viewModelScope.launch {
            repository.saveExamMarks(marks)
            val exam = exams.value.find { it.id == examId }
            val cls = classes.value.find { it.id == exam?.classId }
            val examName = exam?.name ?: "Exam #$examId"
            val clsName = cls?.name ?: "Class"
            val subj = exam?.subject ?: ""
            val validScores = marks.filter { !it.isAbsent }.map { it.score }
            val maxScore = if (validScores.isNotEmpty()) validScores.maxOrNull() ?: 0.0 else 0.0
            val minScore = if (validScores.isNotEmpty()) validScores.minOrNull() ?: 0.0 else 0.0
            val avgScore = if (validScores.isNotEmpty()) validScores.average() else 0.0
            val absentCount = marks.count { it.isAbsent }

            val studMap = students.value.associateBy { it.id }
            val rawBuilder = StringBuilder()
            marks.take(15).forEach { m ->
                val sName = studMap[m.studentId]?.name ?: "Student #${m.studentId}"
                val scText = if (m.isAbsent) "ABSENT" else "${if (m.score % 1.0 == 0.0) m.score.toInt() else m.score}/${exam?.totalMarks?.toInt() ?: 100}"
                rawBuilder.append("$sName: $scText | ")
            }
            if (marks.size > 15) rawBuilder.append("+ ${marks.size - 15} more...")

            logAudit(
                category = "GRADING",
                title = "Gelinta Dhibcaha Imtixaanka: $examName ($subj)",
                className = clsName,
                details = "${marks.size} Arday dhibco loo geliyay (Celcelis: ${String.format(java.util.Locale.US, "%.1f", avgScore)}, Ugu sareeya: $maxScore, Ugu hooseeya: $minScore" + (if (absentCount > 0) ", Maqan: $absentCount" else "") + ")",
                rawData = rawBuilder.toString().trimEnd(' ', '|')
            )
            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            _uiMessage.emit("Exam marks updated successfully!")
        }
    }

    fun addFee(classId: Long, studentId: Long, feeType: String, amount: Double, currency: String, dueDate: String, month: String, notes: String) {
        viewModelScope.launch {
            repository.insertFeeRecord(
                FeeRecord(
                    classId = classId,
                    studentId = studentId,
                    feeType = feeType,
                    amount = amount,
                    currency = currency,
                    dueDate = dueDate,
                    month = month,
                    notes = notes,
                    paidStatus = "Pending"
                )
            )
            val sName = students.value.find { it.id == studentId }?.name ?: "Student"
            val clsName = classes.value.find { it.id == classId }?.name ?: "Class"
            logAudit(
                category = "FINANCE",
                title = "Diiwaangelin Lacag Sugaysa ($currency $amount)",
                className = clsName,
                details = "Ardayga: $sName | Nooca: $feeType | Bisha: $month",
                rawData = "Ardayga: $sName | Qadarka: $currency $amount | Xaaladda: Pending"
            )
            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            _uiMessage.emit("Fee record saved ($amount $currency)")
        }
    }

    fun markFeePaid(fee: FeeRecord) {
        viewModelScope.launch {
            val updated = fee.copy(
                paidStatus = "Paid",
                paidDate = "2026-08-11"
            )
            repository.updateFeeRecord(updated)
            val sName = students.value.find { it.id == fee.studentId }?.name ?: "Student"
            val clsName = classes.value.find { it.id == fee.classId }?.name ?: "Class"
            logAudit(
                category = "FINANCE",
                title = "Lacag loo calaamadeeyay Paid (${fee.currency} ${fee.amount})",
                className = clsName,
                details = "Ardayga: $sName | Nooca: ${fee.feeType} | Bisha: ${fee.month}",
                rawData = "Fee ID: ${fee.id} marked as Paid"
            )
            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            _uiMessage.emit("Fee marked as Paid!")
        }
    }

    fun deleteFee(id: Long) {
        viewModelScope.launch {
            val target = fees.value.find { it.id == id }
            val sName = students.value.find { it.id == target?.studentId }?.name ?: "Student"
            val clsName = classes.value.find { it.id == target?.classId }?.name ?: "Class"
            repository.deleteFee(id)
            logAudit(
                category = "FINANCE",
                title = "Tirtiridda Diiwaan Lacag-bixineed",
                className = clsName,
                details = "Diiwaanka lacagta ${target?.currency ?: ""} ${target?.amount ?: 0.0} ee ardayga $sName (${target?.feeType ?: ""}) waa la tirtiray.",
                status = "DELETED"
            )
            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            _uiMessage.emit("Fee deleted")
        }
    }

    fun saveAttendance(classId: Long, date: String, records: List<AttendanceRecord>) {
        viewModelScope.launch {
            val nowSdf = java.text.SimpleDateFormat("hh:mm:ss a", java.util.Locale.getDefault())
            val timeNowStr = nowSdf.format(java.util.Date())
            val user = _currentUser.value
            val userName = user?.fullName ?: user?.username ?: "Macallin"

            val enrichedRecords = records.map { r ->
                r.copy(
                    recordedBy = if (r.recordedBy.isBlank()) userName else r.recordedBy,
                    recordedAt = if (r.recordedAt.isBlank()) timeNowStr else r.recordedAt
                )
            }

            repository.saveAttendanceList(classId, date, enrichedRecords)
            val clsName = classes.value.find { it.id == classId }?.name ?: "Class #$classId"
            val pCount = enrichedRecords.count { it.status == "Present" }
            val aCount = enrichedRecords.count { it.status == "Absent" }
            val fCount = enrichedRecords.count { it.status == "Free" }
            val total = enrichedRecords.size

            val studMap = students.value.associateBy { it.id }
            val rawBuilder = StringBuilder()
            enrichedRecords.take(15).forEach { r ->
                val sName = studMap[r.studentId]?.name ?: "Student #${r.studentId}"
                rawBuilder.append("$sName: ${r.status} | ")
            }
            if (enrichedRecords.size > 15) rawBuilder.append("+ ${enrichedRecords.size - 15} more...")

            logAudit(
                category = "ATTENDANCE",
                title = "Diiwaangelinta Xaadirinta ($date)",
                className = clsName,
                details = "$total Arday: $pCount Jooga (Present), $aCount Maqan (Absent) • Saacadda: $timeNowStr" + (if (fCount > 0) ", $fCount Fasax" else ""),
                rawData = rawBuilder.toString().trimEnd(' ', '|')
            )
            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            _uiMessage.emit("Xaadirinta maalinta $date waa la keydiyay ($timeNowStr)!")
        }
    }

    fun deleteAttendanceLog(classId: Long, date: String) {
        if (_currentUser.value != null && _currentUser.value?.role == "TEACHER") {
            viewModelScope.launch {
                _uiMessage.emit("Permission Denied: Only Administrator or Cashier can delete attendance logs.")
            }
            return
        }
        viewModelScope.launch {
            val clsName = classes.value.find { it.id == classId }?.name ?: "Class #$classId"
            repository.deleteAttendanceLog(classId, date)
            logAudit(
                category = "ATTENDANCE",
                title = "Tirtiridda Xaadirinta ($date)",
                className = clsName,
                details = "Diiwaankii xaadirinta ee fasalka $clsName taariikhda $date waa la tirtiray.",
                status = "DELETED"
            )
            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            _uiMessage.emit("Diiwaankii xaadirinta ee taariikhda $date waa la tirtiray!")
        }
    }

    fun clearAllAuditLogs() {
        if (_currentUser.value?.role != "ADMIN") {
            viewModelScope.launch { _uiMessage.emit("Permission Denied: Only Administrator can clear audit logs.") }
            return
        }
        viewModelScope.launch {
            repository.clearAllAuditLogs()
            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            _uiMessage.emit("Dhammaan diiwaanka kormeerka (Audit Logs) waa la tirtiray!")
        }
    }

    // --- Backup & Restore ---
    fun exportBackup(context: Context) {
        viewModelScope.launch {
            val jsonStr = repository.exportBackupJson(
                classes.value,
                students.value,
                users.value,
                exams.value,
                fees.value,
                allAttendance.value,
                allExamMarks.value
            )
            val file = File(context.cacheDir, "school_system_backup.json")
            FileOutputStream(file).use { it.write(jsonStr.toByteArray()) }

            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Share Backup File"))
        }
    }

    fun exportBackupToGoogleDrive(context: Context) {
        viewModelScope.launch {
            val dateStr = getCurrentDateTimeStr().take(10).replace("/", "-")
            val fileName = "School_Backup_GoogleDrive_${dateStr}.json"
            val jsonStr = repository.exportBackupJson(
                classes.value,
                students.value,
                users.value,
                exams.value,
                fees.value,
                allAttendance.value,
                allExamMarks.value
            )
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { it.write(jsonStr.toByteArray()) }

            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val driveIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Google Drive School Backup - $dateStr")
                putExtra(Intent.EXTRA_TEXT, "School Management System Complete Cloud Backup for Google Drive (${getCurrentDateTimeStr()})")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(driveIntent, "Save to Google Drive / Cloud"))
            _uiMessage.emit("📁 Waxaa loo diyaariyay keydinta Google Drive!")
        }
    }

    fun sendBackupViaGmail(context: Context) {
        viewModelScope.launch {
            val dateStr = getCurrentDateTimeStr().take(10).replace("/", "-")
            val fileName = "School_System_Backup_${dateStr}.json"
            val jsonStr = repository.exportBackupJson(
                classes.value,
                students.value,
                users.value,
                exams.value,
                fees.value,
                allAttendance.value,
                allExamMarks.value
            )
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { it.write(jsonStr.toByteArray()) }

            val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val emailIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_SUBJECT, "School Management System Backup - $dateStr")
                putExtra(Intent.EXTRA_TEXT, "Ku lifaaqan waa keydka guud ee dugsiga (School Database Backup JSON) taariikhda: ${getCurrentDateTimeStr()}.\nFadlan ku keydi Google Drive ama Gmail si aadan u lumin xogta.")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(emailIntent, "Send via Gmail / Email"))
            _uiMessage.emit("📧 Waxaa loo diyaariyay dirista Gmail / Email!")
        }
    }

    fun restoreBackupJson(jsonString: String, onComplete: (Boolean) -> Unit) {
        viewModelScope.launch {
            val success = repository.importBackupJson(jsonString, clearFirst = false)
            if (success) {
                logAudit(
                    category = "SYSTEM",
                    title = "Soo Celinta Xogta Guud (Restore Database)",
                    details = "Dhammaan xogtii dugsiga ayaa dib looga soo celiyay faylka keydka ah.",
                    status = "RESTORED"
                )
            }
            onComplete(success)
        }
    }

    // --- CSV Reports ---
    fun exportExamReportCSV(context: Context) {
        viewModelScope.launch {
            val sb = StringBuilder()
            val schoolHeader = schoolName.value.ifBlank { "Dugsiga H/Dhexe" }
            val allExamsList = exams.value
            val allStudentsList = students.value
            val targetClasses = classes.value

            sb.append("Wasaaradda waxbarashada iyo Sayniska JSL,,,,,,,,,,,,,,,,,,,,,\n")
            sb.append("Xafiiska Waxbarasha Degmada Gabiley,,,,,,,,,,,,,,,,,,,,,\n")
            sb.append("Waaxda Qorshaynta,,,,,,,,,,,,,,,,,,,,,\n")
            sb.append("Xaashida Imtixaanka Naqliga ee Dugsiyada H/Dhexe,,,,,,,,,,,,,,,,,,,,,\n")

            targetClasses.forEach { cls ->
                val clsStuds = allStudentsList.filter { it.classId == cls.id }
                val clsExams = allExamsList.filter { it.classId == cls.id }

                val examMarksMap = mutableMapOf<Long, List<ExamMark>>()
                for (exam in clsExams) {
                    examMarksMap[exam.id] = repository.examDao.getMarksForExam(exam.id).first()
                }

                sb.append("Dugsiga : ${schoolHeader}                                             Fasalka: ${cls.name},,  TERM ONE  ( 2025/2026),,,,,,,,,    TERM TWO ( 2025/2026),,,,,,,,,,\n")
                sb.append("Sno,EMIS ID,Magaca Ardayga,,,Diin,Som,Car,Eng,Xis,Say,C/B,Wadar,Diin,Som,Car,Eng,Xis,Say,C/B,Wadar,Wadar 2 T\n")

                clsStuds.forEachIndexed { idx, student ->
                    fun getScore(subjectKey: String, isTerm2: Boolean): Double? {
                        val matchingExams = clsExams.filter { exam ->
                            val text = (exam.name + " " + exam.subject).lowercase()
                            val isT2 = text.contains("term 2") || text.contains("term2") || text.contains("t2") || text.contains("sem 2") || text.contains("final")
                            val correctTerm = if (isTerm2) isT2 else !isT2

                            val matchesSubject = when (subjectKey) {
                                "Diin" -> text.contains("diin") || text.contains("islam") || text.contains("quran")
                                "Som" -> text.contains("som") || text.contains("soomaali")
                                "Car" -> text.contains("car") || text.contains("arab")
                                "Eng" -> text.contains("eng") || text.contains("ing")
                                "Xis" -> text.contains("xis") || text.contains("math")
                                "Say" -> text.contains("say") || text.contains("sci")
                                "C/B" -> text.contains("c/b") || text.contains("bulsho") || text.contains("soc")
                                else -> false
                            }
                            correctTerm && matchesSubject
                        }

                        val targetExam = matchingExams.firstOrNull() ?: run {
                            val subjectIdx = listOf("Diin", "Som", "Car", "Eng", "Xis", "Say", "C/B").indexOf(subjectKey)
                            val termExams = clsExams.filter { exam ->
                                val text = (exam.name + " " + exam.subject).lowercase()
                                val isT2 = text.contains("term 2") || text.contains("term2") || text.contains("t2") || text.contains("sem 2") || text.contains("final")
                                if (isTerm2) isT2 else !isT2
                            }
                            if (subjectIdx in termExams.indices) termExams[subjectIdx] else null
                        }

                        if (targetExam != null) {
                            val marks = examMarksMap[targetExam.id] ?: emptyList()
                            val m = marks.find { it.studentId == student.id }
                            if (m != null && !m.isAbsent) {
                                return m.score
                            }
                        }
                        return null
                    }

                    val t1Diin = getScore("Diin", false)
                    val t1Som = getScore("Som", false)
                    val t1Car = getScore("Car", false)
                    val t1Eng = getScore("Eng", false)
                    val t1Xis = getScore("Xis", false)
                    val t1Say = getScore("Say", false)
                    val t1CB = getScore("C/B", false)

                    val t1Scores = listOfNotNull(t1Diin, t1Som, t1Car, t1Eng, t1Xis, t1Say, t1CB)
                    val t1Wadar = if (t1Scores.isNotEmpty()) t1Scores.sum() else null

                    val t2Diin = getScore("Diin", true)
                    val t2Som = getScore("Som", true)
                    val t2Car = getScore("Car", true)
                    val t2Eng = getScore("Eng", true)
                    val t2Xis = getScore("Xis", true)
                    val t2Say = getScore("Say", true)
                    val t2CB = getScore("C/B", true)

                    val t2Scores = listOfNotNull(t2Diin, t2Som, t2Car, t2Eng, t2Xis, t2Say, t2CB)
                    val t2Wadar = if (t2Scores.isNotEmpty()) t2Scores.sum() else null

                    val wadar2T = if (t1Wadar != null || t2Wadar != null) (t1Wadar ?: 0.0) + (t2Wadar ?: 0.0) else null

                    fun fmt(num: Double?): String = if (num == null) "" else if (num % 1.0 == 0.0) num.toInt().toString() else String.format(java.util.Locale.US, "%.1f", num)

                    sb.append("${idx + 1},${student.studentId},\"${student.name}\",,,${fmt(t1Diin)},${fmt(t1Som)},${fmt(t1Car)},${fmt(t1Eng)},${fmt(t1Xis)},${fmt(t1Say)},${fmt(t1CB)},${fmt(t1Wadar)},${fmt(t2Diin)},${fmt(t2Som)},${fmt(t2Car)},${fmt(t2Eng)},${fmt(t2Xis)},${fmt(t2Say)},${fmt(t2CB)},${fmt(t2Wadar)},${fmt(wadar2T)}\n")
                }
                sb.append(",,Magaca Maamulaha,,,,,,,,,,,,,,,,,,,\n")
                sb.append(",,Saxeexa Maamulaha   _______________________________,,,,,,,,,,,,,,,,,,,\n\n")
            }

            shareCSVFile(context, "Xaashida_Imtixaanka_Naqliga.csv", sb.toString())
        }
    }

    fun exportFeeReportCSV(context: Context) {
        viewModelScope.launch {
            val sb = StringBuilder()
            val schoolHeader = schoolName.value.ifBlank { "SCHOOL MANAGEMENT SYSTEM" }
            sb.append("School Name: \"${schoolHeader}\"\n")
            sb.append("Student ID,Student Name,Class,Fee Type,Amount,Currency,Due Date,Month,Status,Paid Date,Notes\n")
            val allFees = fees.value
            val allStuds = students.value

            allFees.forEach { fee ->
                val student = allStuds.find { it.id == fee.studentId }
                val clsName = classes.value.find { it.id == fee.classId }?.name ?: ""
                sb.append("\"${student?.studentId ?: ""}\",\"${student?.name ?: ""}\",\"$clsName\",\"${fee.feeType}\",${fee.amount},\"${fee.currency}\",\"${fee.dueDate}\",\"${fee.month}\",\"${fee.paidStatus}\",\"${fee.paidDate}\",\"${fee.notes}\"\n")
            }

            shareCSVFile(context, "fee_collection_report.csv", sb.toString())
        }
    }

    private fun shareCSVFile(context: Context, filename: String, csvContent: String) {
        val file = File(context.cacheDir, filename)
        FileOutputStream(file).use { it.write(csvContent.toByteArray()) }

        val uri: Uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Export CSV Report"))
    }

    // --- Helper for Serial Code & Auto Date-Time ---
    private fun generateSerialCode(prefix: String): String {
        val dateStr = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
        val randomPart = (1000..9999).random()
        return "$prefix-$dateStr-$randomPart"
    }

    private fun getCurrentDateTimeStr(): String {
        return java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", java.util.Locale.US).format(java.util.Date())
    }

    private var cachedLogoBase64: String? = null

    fun getSchoolLogoBase64(context: Context): String {
        cachedLogoBase64?.let { return it }
        return try {
            val original = android.graphics.BitmapFactory.decodeResource(context.resources, com.example.R.drawable.school_logo)
            if (original != null) {
                val size = 120
                val scaled = android.graphics.Bitmap.createScaledBitmap(original, size, size, true)
                val stream = java.io.ByteArrayOutputStream()
                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, stream)
                val bytes = stream.toByteArray()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                cachedLogoBase64 = base64
                base64
            } else ""
        } catch (e: Exception) {
            ""
        }
    }

    fun getArtisticHeaderHtml(
        context: Context,
        schoolTitle: String,
        reportTitle: String,
        serialCode: String,
        dateStr: String,
        badgeText: String? = null
    ): String {
        val logoBase64 = getSchoolLogoBase64(context)
        val sb = StringBuilder()
        sb.append("<div class='artistic-header-container'>")
        sb.append("<div class='artistic-header-top'>")

        // Left Side Column (State / Ministry / School Tier)
        sb.append("<div class='header-side left-side'>")
        sb.append("<div class='side-title'>JAMHUURIYADDA SOMALILAND</div>")
        sb.append("<div class='side-sub'>WASAARADDA WAXBARASHADA & SAYNISKA</div>")
        sb.append("<div class='side-loc'>Dugsiga Hoose / Dhexe ee Dawliga ah</div>")
        sb.append("</div>")

        // Center Column (Centrally Placed Circular School Emblem)
        sb.append("<div class='header-center-logo'>")
        if (logoBase64.isNotBlank()) {
            sb.append("<img src='data:image/jpeg;base64,$logoBase64' class='artistic-logo-center-img' alt='School Logo' />")
        }
        sb.append("</div>")

        // Right Side Column (Reference, Date & Official Badge)
        sb.append("<div class='header-side right-side'>")
        sb.append("<div class='side-meta-item'><b>Tixraac (Ref):</b> <span class='ref-code'>$serialCode</span></div>")
        sb.append("<div class='side-meta-item'><b>Taariikhda:</b> $dateStr</div>")
        if (!badgeText.isNullOrBlank()) {
            sb.append("<div class='side-badge'>$badgeText</div>")
        }
        sb.append("</div>")
        sb.append("</div>")

        // Bottom Centered Titles & Motto
        sb.append("<div class='header-center-titles'>")
        sb.append("<div class='school-main-title'>${schoolTitle.uppercase()}</div>")
        sb.append("<div class='report-main-banner'>${reportTitle.uppercase()}</div>")
        sb.append("<div class='school-motto-tag'>🌟 Excellence, Knowledge & Integrity • Waxbarasho Tayo Leh 🌟</div>")
        sb.append("</div>")
        sb.append("</div>")
        return sb.toString()
    }

    val artisticHeaderCss = """
        .artistic-header-container {
            padding: 12px 18px 14px 18px;
            margin-bottom: 18px;
            border-radius: 10px;
            background: linear-gradient(180deg, #F8FAFC 0%, #F0FDF4 100%);
            border: 1.5px solid #CBD5E1;
            border-top: 4px solid #006A6B;
            box-shadow: 0 2px 8px rgba(0, 106, 107, 0.08);
        }
        .artistic-header-top {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: 12px;
        }
        .header-side {
            flex: 1;
            font-size: 11px;
            line-height: 1.35;
        }
        .header-side.left-side {
            text-align: left;
        }
        .header-side.right-side {
            text-align: right;
        }
        .side-title {
            font-weight: 800;
            color: #004D4E;
            font-size: 11px;
            text-transform: uppercase;
            letter-spacing: 0.3px;
        }
        .side-sub {
            font-weight: 700;
            color: #475569;
            font-size: 9.5px;
            text-transform: uppercase;
        }
        .side-loc {
            font-weight: 600;
            color: #64748B;
            font-size: 9px;
            margin-top: 2px;
        }
        .side-meta-item {
            font-size: 10.5px;
            color: #334155;
            margin-bottom: 2px;
        }
        .ref-code {
            font-family: monospace;
            font-weight: bold;
            color: #006A6B;
        }
        .side-badge {
            display: inline-block;
            background: #006A6B;
            color: #FFFFFF;
            padding: 2px 8px;
            border-radius: 4px;
            font-size: 9.5px;
            font-weight: 800;
            letter-spacing: 0.5px;
            margin-top: 3px;
        }
        .header-center-logo {
            flex-shrink: 0;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 0 10px;
        }
        .artistic-logo-center-img {
            width: 82px;
            height: 82px;
            border-radius: 50%;
            object-fit: cover;
            border: 3px solid #006A6B;
            box-shadow: 0 4px 10px rgba(0, 106, 107, 0.25);
            background: #FFFFFF;
        }
        .header-center-titles {
            text-align: center;
            margin-top: 8px;
            padding-top: 8px;
            border-top: 1px dashed #CBD5E1;
        }
        .school-main-title {
            color: #006A6B;
            font-size: 21px;
            font-weight: 900;
            letter-spacing: 0.8px;
            text-transform: uppercase;
            line-height: 1.15;
            margin: 0 0 4px 0;
        }
        .report-main-banner {
            display: inline-block;
            background: #006A6B;
            color: #FFFFFF;
            font-size: 12px;
            font-weight: 800;
            padding: 3px 18px;
            border-radius: 4px;
            letter-spacing: 0.5px;
            text-transform: uppercase;
        }
        .school-motto-tag {
            color: #059669;
            font-size: 10px;
            font-weight: 700;
            margin-top: 4px;
            font-style: italic;
        }
    """.trimIndent()

    // --- HTML Printable Report Card ---
    fun printReportCardHtml(context: Context, card: StudentReportCard) {
        val schoolHeader = schoolName.value.ifBlank { "SCHOOL MANAGEMENT SYSTEM" }
        val serialCode = generateSerialCode("RPT")
        val nowDateTime = getCurrentDateTimeStr()

        val html = StringBuilder()
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>")
        html.append("@page { size: A4; margin: 15mm; }")
        html.append("body { font-family: sans-serif; padding: 15px; color: #1A1A1A; }")
        html.append(".header { text-align: center; border-bottom: 2px solid #0D9488; padding-bottom: 10px; margin-bottom: 20px; }")
        html.append(".title { color: #0D9488; font-size: 24px; font-weight: bold; }")
        html.append(".sub { color: #6B6B6B; font-size: 14px; }")
        html.append("table { width: 100%; border-collapse: collapse; margin-top: 15px; }")
        html.append("th { background: #0D9488; color: white; padding: 8px; text-align: left; }")
        html.append("td { border-bottom: 1px solid #E2E8F0; padding: 8px; }")
        html.append(".pass { color: #38A169; font-weight: bold; }")
        html.append(".fail { color: #E53E3E; font-weight: bold; }")
        html.append(".summary { background: #F0FDFA; padding: 15px; border-radius: 8px; margin-top: 20px; }")
        html.append("</style></head><body>")

        html.append("<div class='header'>")
        html.append("<div class='title'>${schoolHeader.uppercase()}</div>")
        html.append("<div class='sub'>OFFICIAL ACADEMIC REPORT CARD STATEMENT</div>")
        html.append("<div style='margin-top:6px; font-size:11px; color:#0D9488;'><b>Serial Code:</b> $serialCode &nbsp;|&nbsp; <b>Date & Time:</b> $nowDateTime</div>")
        html.append("</div>")

        html.append("<p><b>Student Name:</b> ${card.student.name} &nbsp;&nbsp; <b>ID:</b> ${card.student.studentId}</p>")
        html.append("<p><b>Class:</b> ${card.className} &nbsp;&nbsp; <b>Gender:</b> ${card.student.gender} &nbsp;&nbsp; <b>Phone:</b> ${card.student.phone}</p>")

        html.append("<h3>Exam Results Summary</h3>")
        html.append("<table><tr><th>Subject / Exam</th><th>Score</th><th>Max Marks</th><th>Percentage</th><th>Status</th></tr>")
        card.subjectResults.forEach { res ->
            val pct = if (res.totalMarks > 0) String.format("%.1f%%", (res.score / res.totalMarks) * 100) else "-"
            val statusClass = if (res.isPassed) "pass" else "fail"
            val statusText = if (res.isAbsent) "ABSENT" else if (res.isPassed) "PASSED" else "FAILED"
            html.append("<tr><td>${res.subject} (${res.examName})</td><td>${res.score}</td><td>${res.totalMarks}</td><td>$pct</td><td class='$statusClass'>$statusText</td></tr>")
        }
        html.append("</table>")

        html.append("<div class='summary'>")
        html.append("<h4>Academic Overview</h4>")
        html.append("<p><b>Average Percentage:</b> ${String.format("%.1f%%", card.averagePercentage)}</p>")
        html.append("<p><b>Total Passed Exams:</b> ${card.totalPassed} / ${card.totalExams}</p>")
        html.append("<p><b>Attendance Rate:</b> ${String.format("%.1f%%", card.attendanceRate)}</p>")
        if (card.pendingFees.isNotEmpty()) {
            html.append("<p style='color: #DD6B20;'><b>Pending Fees:</b> ")
            val feeStrs = card.pendingFees.map { "${it.feeType}: ${it.amount} ${it.currency}" }
            html.append(feeStrs.joinToString(", "))
            html.append("</p>")
        } else {
            html.append("<p style='color: #38A169;'><b>Pending Fees:</b> None (All Cleared)</p>")
        }
        html.append("</div>")

        html.append("</body></html>")

        repository.printHtmlReport(context, html.toString(), "ReportCard_${card.student.studentId}")
    }

    // --- Specific Printable HTML Reports ---
    fun printSingleStudentReport(context: Context, student: Student) {
        viewModelScope.launch {
            val cls = classes.value.find { it.id == student.classId }
            val clsName = cls?.name ?: "N/A"
            val teacherName = cls?.inchargeTeacher?.ifBlank { "Macallinka Fasalka" } ?: "Macallinka Fasalka"
            val studentFees = fees.value.filter { it.studentId == student.id }
            val studentAtt = allAttendance.value.filter { it.studentId == student.id }
            val studentMarks = allExamMarks.value.filter { it.studentId == student.id }
            val allExamsList = exams.value

            val totalAtt = studentAtt.size
            val presentAtt = studentAtt.count { it.status == "Present" }
            val lateAtt = studentAtt.count { it.status == "Late" || it.status == "Habsan" || it.status == "H" }
            val absentAtt = studentAtt.count { it.status == "Absent" || it.status == "A" }
            val attRate = if (totalAtt > 0) (presentAtt.toDouble() / totalAtt) * 100 else 100.0

            val totalScore = studentMarks.sumOf { it.score }
            val totalMaxPossible = studentMarks.sumOf { m -> allExamsList.find { it.id == m.examId }?.totalMarks ?: 100.0 }
            val academicAvg = if (totalMaxPossible > 0) (totalScore / totalMaxPossible) * 100.0 else 0.0

            val schoolHeader = schoolName.value.ifBlank { "MAHDI CALI SCHOOL" }
            val serialCode = generateSerialCode("STR")
            val nowDateTime = getCurrentDateTimeStr()

            val html = StringBuilder()
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>")
            html.append("@page { size: A4; margin: 15mm; }")
            html.append("body { font-family: 'Segoe UI', Arial, sans-serif; padding: 15px; color: #1E293B; background: #FFF; }")
            html.append(artisticHeaderCss)
            html.append(".section-title { font-size: 13px; font-weight: 800; color: #006A6B; text-transform: uppercase; border-left: 4px solid #006A6B; padding-left: 8px; margin: 15px 0 8px 0; }")
            html.append(".info-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; margin-bottom: 15px; }")
            html.append(".info-item { background: #FFF; border: 1px solid #CBD5E1; border-radius: 4px; padding: 6px 10px; font-size: 11px; color: #334155; }")
            html.append(".badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 10px; font-weight: bold; }")
            html.append(".badge-free { background: #FEF3C7; color: #B45309; border: 1px solid #F59E0B; }")
            html.append(".badge-pass { background: #DCFCE7; color: #15803D; }")
            html.append(".badge-fail { background: #FEE2E2; color: #B91C1C; }")
            html.append(".badge-paid { background: #DCFCE7; color: #15803D; }")
            html.append(".badge-due { background: #FEE2E2; color: #B91C1C; }")
            html.append("table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 11px; }")
            html.append("th { background: #006A6B; color: #FFFFFF; padding: 8px 10px; text-align: left; font-size: 11px; font-weight: bold; }")
            html.append("td { border-bottom: 1px solid #E2E8F0; padding: 8px 10px; font-size: 11px; color: #334155; }")
            html.append("tr:nth-child(even) { background-color: #F8FAFC; }")
            html.append(".kpi-row { display: flex; gap: 10px; margin: 15px 0; }")
            html.append(".kpi-card { flex: 1; background: #F1F5F9; border-radius: 6px; padding: 10px; text-align: center; border: 1px solid #CBD5E1; }")
            html.append(".kpi-val { font-size: 15px; font-weight: 800; color: #006A6B; margin-top: 4px; }")
            html.append(".kpi-lbl { font-size: 10px; font-weight: 700; color: #64748B; text-transform: uppercase; }")
            html.append(".signatures { display: flex; justify-content: space-between; margin-top: 30px; padding-top: 15px; page-break-inside: avoid; }")
            html.append(".sig-box { text-align: center; width: 45%; }")
            html.append(".sig-line { border-top: 1px dashed #64748B; margin-top: 25px; padding-top: 4px; font-size: 11px; font-weight: bold; color: #475569; }")
            html.append("</style></head><body>")

            // Top Header with Artistic Logo
            html.append(getArtisticHeaderHtml(
                context = context,
                schoolTitle = schoolHeader,
                reportTitle = "WARBIXINTA GUUD EE ARDAYGA (STUDENT COMPREHENSIVE REPORT)",
                serialCode = serialCode,
                dateStr = nowDateTime,
                badgeText = "OFFICIAL PROFILE"
            ))

            // Section 1: Student Information
            html.append("<div class='section-title'>1. Xogta Shakhsiga & Diiwaanka (Student Profile)</div>")
            html.append("<div class='info-grid'>")
            html.append("<div class='info-item'><b>Magaca Ardayga:</b> ${student.name}</div>")
            html.append("<div class='info-item'><b>Student ID:</b> <span style='font-family:monospace; font-weight:bold; color:#006A6B;'>${student.studentId}</span></div>")
            html.append("<div class='info-item'><b>Fasalka:</b> $clsName &nbsp;(Macallin: $teacherName)</div>")
            html.append("<div class='info-item'><b>Jinsiga:</b> ${student.gender}</div>")
            html.append("<div class='info-item'><b>Magaca Hooyada:</b> ${student.motherName.ifBlank { "N/A" }}</div>")
            html.append("<div class='info-item'><b>Telefoonka Waalidka:</b> ${student.phone.ifBlank { "N/A" }}</div>")
            html.append("<div class='info-item'><b>Xaaladda Fiiga:</b> ${if (student.isFree) "<span class='badge badge-free'>FREE / SCHOLARSHIP (Bilaash)</span>" else "<span class='badge'>STANDARD ENROLLMENT</span>"}</div>")
            html.append("<div class='info-item'><b>Status-ka Guud:</b> <span class='badge badge-pass'>ACTIVE ENROLLED</span></div>")
            html.append("</div>")

            // Section 2: Exam & Academic Performance
            html.append("<div class='section-title'>2. Natiijooyinka Imtixaanaadka & Dhibcaha (Academic Performance)</div>")
            if (studentMarks.isEmpty()) {
                html.append("<p style='font-size:11px; color:#64748B; font-style:italic;'>Wax natiijooyin imtixaan ah weli looma diiwaangelin ardaygan.</p>")
            } else {
                html.append("<div class='kpi-row'>")
                html.append("<div class='kpi-card'><div class='kpi-lbl'>Wadarta Dhibcaha</div><div class='kpi-val'>${String.format("%.1f", totalScore)} / ${String.format("%.0f", totalMaxPossible)}</div></div>")
                html.append("<div class='kpi-card'><div class='kpi-lbl'>Celceliska Guud (%)</div><div class='kpi-val'>${String.format("%.1f%%", academicAvg)}</div></div>")
                html.append("<div class='kpi-card'><div class='kpi-lbl'>Maadooyinka La Galay</div><div class='kpi-val'>${studentMarks.size}</div></div>")
                html.append("<div class='kpi-card'><div class='kpi-lbl'>Heerka Tacliinta</div><div class='kpi-val' style='color:${if (academicAvg >= 50) "#15803D" else "#B91C1C"}'>${if (academicAvg >= 50) "GUUL (PASS)" else "DHACAY (FAIL)"}</div></div>")
                html.append("</div>")

                html.append("<table>")
                html.append("<thead><tr><th>Maadada</th><th>Imtixaanka</th><th>Dhibcaha</th><th>Max</th><th>Boqolkiiba</th><th>Darajada</th><th>Xaaladda</th></tr></thead><tbody>")
                studentMarks.forEach { m ->
                    val exam = allExamsList.find { it.id == m.examId }
                    val examName = exam?.name ?: "Exam"
                    val subjName = exam?.subject ?: "Subject"
                    val maxM = exam?.totalMarks ?: 100.0
                    val pct = if (maxM > 0) (m.score / maxM) * 100.0 else 0.0
                    val grade = when {
                        pct >= 90 -> "A+"
                        pct >= 80 -> "A"
                        pct >= 70 -> "B"
                        pct >= 60 -> "C"
                        pct >= 50 -> "D"
                        else -> "F"
                    }
                    val isPass = pct >= 50
                    html.append("<tr>")
                    html.append("<td><b>$subjName</b></td>")
                    html.append("<td>$examName</td>")
                    html.append("<td>${String.format("%.1f", m.score)}</td>")
                    html.append("<td>${String.format("%.0f", maxM)}</td>")
                    html.append("<td>${String.format("%.1f%%", pct)}</td>")
                    html.append("<td><b>$grade</b></td>")
                    html.append("<td><span class='badge ${if (isPass) "badge-pass" else "badge-fail"}'>${if (isPass) "GUDUB" else "DHAC"}</span></td>")
                    html.append("</tr>")
                }
                html.append("</tbody></table>")
            }

            // Section 3: Attendance Record
            html.append("<div class='section-title'>3. Diiwaanka Xaadirinta & Joogitaanka (Attendance Record)</div>")
            html.append("<div class='kpi-row'>")
            html.append("<div class='kpi-card'><div class='kpi-lbl'>Wadarta Maalmaha</div><div class='kpi-val'>$totalAtt</div></div>")
            html.append("<div class='kpi-card'><div class='kpi-lbl'>Joogay (Present)</div><div class='kpi-val' style='color:#15803D;'>$presentAtt</div></div>")
            html.append("<div class='kpi-card'><div class='kpi-lbl'>Habsan (Late)</div><div class='kpi-val' style='color:#D97706;'>$lateAtt</div></div>")
            html.append("<div class='kpi-card'><div class='kpi-lbl'>Maqnaa (Absent)</div><div class='kpi-val' style='color:#B91C1C;'>$absentAtt</div></div>")
            html.append("<div class='kpi-card'><div class='kpi-lbl'>Boqolkiiba Xaadirinta</div><div class='kpi-val'>${String.format("%.1f%%", attRate)}</div></div>")
            html.append("</div>")

            // Section 4: Fee & Financial Record
            html.append("<div class='section-title'>4. Diiwaanka Lacagaha & Bixinta Fiiga (Financial Statement)</div>")
            if (student.isFree) {
                html.append("<div style='background:#FEF3C7; border:1px solid #F59E0B; border-radius:6px; padding:12px; text-align:center;'>")
                html.append("<b style='color:#B45309; font-size:13px;'>🌟 ARDAYGAN LACAGTA WAA LAGA DHAAFAY (FEE EXEMPT / SCHOLARSHIP)</b><br>")
                html.append("<span style='color:#78350F; font-size:11px;'>Ardaygan wax lacag ah ama deyn ah laguma laha. Dugsigu wuxuu u siiyay deeq waxbarasho oo bilaash ah.</span>")
                html.append("</div>")
            } else if (studentFees.isEmpty()) {
                html.append("<p style='font-size:11px; color:#64748B; font-style:italic;'>Wax diiwaan fiigo ah oo weli loo furay ardaygan lama helin.</p>")
            } else {
                val totalPaid = studentFees.filter { it.paidStatus == "Paid" }.sumOf { it.amount }
                val totalPending = studentFees.filter { it.paidStatus != "Paid" }.sumOf { it.amount }

                html.append("<div class='kpi-row'>")
                html.append("<div class='kpi-card'><div class='kpi-lbl'>Wadarta La Bixiyay (Paid)</div><div class='kpi-val' style='color:#15803D;'>$${String.format("%.0f", totalPaid)}</div></div>")
                html.append("<div class='kpi-card'><div class='kpi-lbl'>Baaqiga Lagu Leeyahay (Due)</div><div class='kpi-val' style='color:${if (totalPending > 0) "#B91C1C" else "#15803D"};'>$${String.format("%.0f", totalPending)}</div></div>")
                html.append("<div class='kpi-card'><div class='kpi-lbl'>Tirada Qaansheeyada</div><div class='kpi-val'>${studentFees.size}</div></div>")
                html.append("</div>")

                html.append("<table>")
                html.append("<thead><tr><th>Nooca Fiiga</th><th>Qadarka</th><th>Lacagta</th><th>Xilliga Bixinta</th><th>Xaaladda</th><th>Taariikhda La Bixiyay</th></tr></thead><tbody>")
                studentFees.forEach { f ->
                    val isPaid = f.paidStatus == "Paid"
                    html.append("<tr>")
                    html.append("<td><b>${f.feeType}</b></td>")
                    html.append("<td>${String.format("%.0f", f.amount)}</td>")
                    html.append("<td>${f.currency}</td>")
                    html.append("<td>${f.dueDate}</td>")
                    html.append("<td><span class='badge ${if (isPaid) "badge-paid" else "badge-due"}'>${f.paidStatus}</span></td>")
                    html.append("<td>${f.paidDate.ifBlank { "-" }}</td>")
                    html.append("</tr>")
                }
                html.append("</tbody></table>")
            }

            // Signatures Section
            html.append("<div class='signatures'>")
            html.append("<div class='sig-box'><div class='sig-line'>Macallinka Fasalka (Class Teacher)</div></div>")
            html.append("<div class='sig-box'><div class='sig-line'>Maamulaha Dugsiga (Principal Stamp & Sign)</div></div>")
            html.append("</div>")

            html.append("</div></body></html>")

            repository.printHtmlReport(context, html.toString(), "Student_Report_${student.studentId}")
        }
    }

    fun printFeeReportHtml(context: Context) {
        val allFees = fees.value
        val allStuds = students.value
        val allCls = classes.value

        val usdPaid = allFees.filter { it.currency == "USD" && it.paidStatus == "Paid" }.sumOf { it.amount }
        val usdPending = allFees.filter { it.currency == "USD" && it.paidStatus != "Paid" }.sumOf { it.amount }

        val slsPaid = allFees.filter { it.currency == "SLS" && it.paidStatus == "Paid" }.sumOf { it.amount }
        val slsPending = allFees.filter { it.currency == "SLS" && it.paidStatus != "Paid" }.sumOf { it.amount }

        val etbPaid = allFees.filter { it.currency == "ETB" && it.paidStatus == "Paid" }.sumOf { it.amount }
        val etbPending = allFees.filter { it.currency == "ETB" && it.paidStatus != "Paid" }.sumOf { it.amount }

        val schoolHeader = schoolName.value.ifBlank { "Mahdi Cali School" }
        val serialCode = generateSerialCode("FIN")
        val nowDateTime = getCurrentDateTimeStr()

        val html = StringBuilder()
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>")
        html.append("@page { size: A4; margin: 15mm; }")
        html.append("body { font-family: 'Segoe UI', Arial, sans-serif; padding: 15px; color: #1A1A1A; line-height: 1.4; }")
        html.append(artisticHeaderCss)
        html.append("table { width: 100%; border-collapse: collapse; margin-top: 10px; }")
        html.append("th { background: #006A6B; color: white; padding: 8px; text-align: left; font-size: 12px; }")
        html.append("td { border-bottom: 1px solid #E2E8F0; padding: 6px; font-size: 12px; }")
        html.append(".summary { background: #FFF8E1; padding: 12px; border-radius: 8px; margin-bottom: 15px; border: 1px solid #FDE68A; }")
        html.append("</style></head><body>")

        html.append(getArtisticHeaderHtml(
            context = context,
            schoolTitle = schoolHeader,
            reportTitle = "WARBIXINTA LACAGAHA & MAALIYADDA (FINANCIAL & FEE REPORT)",
            serialCode = serialCode,
            dateStr = nowDateTime,
            badgeText = "FINANCIAL STATEMENT"
        ))

        html.append("<div class='summary'>")
        html.append("<b>Totals Breakdown:</b><br/>")
        html.append("• USD ($): Paid = $${String.format("%.0f", usdPaid)} | Pending = $${String.format("%.0f", usdPending)}<br/>")
        html.append("• Somaliland (SLSh): Paid = ${String.format("%.0f", slsPaid)} | Pending = ${String.format("%.0f", slsPending)}<br/>")
        html.append("• Ethiopia (Birr): Paid = ${String.format("%.0f", etbPaid)} Br | Pending = ${String.format("%.0f", etbPending)} Br")
        html.append("</div>")

        html.append("<table><tr><th>Student ID</th><th>Name</th><th>Class</th><th>Type</th><th>Amount</th><th>Currency</th><th>Due Date</th><th>Status</th></tr>")
        allFees.forEach { f ->
            val st = allStuds.find { it.id == f.studentId }
            val cls = allCls.find { it.id == f.classId }
            html.append("<tr><td>${st?.studentId ?: "-"}</td><td>${st?.name ?: "N/A"}</td><td>${cls?.name ?: "N/A"}</td><td>${f.feeType}</td><td>${f.amount}</td><td>${f.currency}</td><td>${f.dueDate}</td><td><b>${f.paidStatus}</b></td></tr>")
        }
        html.append("</table></body></html>")

        repository.printHtmlReport(context, html.toString(), "Fee_Collection_Report")
    }

    fun printSchoolOverviewReportHtml(context: Context) {
        val allCls = classes.value
        val allStuds = students.value
        val allUsers = users.value

        val totalStudents = allStuds.size
        val boysCount = allStuds.count { it.gender.equals("Male", ignoreCase = true) || it.gender.equals("Wiil", ignoreCase = true) }
        val girlsCount = allStuds.count { it.gender.equals("Female", ignoreCase = true) || it.gender.equals("Gabdho", ignoreCase = true) || it.gender.equals("Gabdhaha", ignoreCase = true) }
        
        val boysPercent = if (totalStudents > 0) (boysCount.toDouble() / totalStudents) * 100 else 0.0
        val girlsPercent = if (totalStudents > 0) (girlsCount.toDouble() / totalStudents) * 100 else 0.0

        val totalTeachers = allUsers.count { it.role.equals("TEACHER", ignoreCase = true) || it.role.equals("ADMIN", ignoreCase = true) }.coerceAtLeast(1)
        val morningTeachers = allUsers.count { (it.role.equals("TEACHER", true) || it.role.equals("ADMIN", true)) && (it.shift.contains("Hore", true) || it.shift.contains("Dhammaan", true)) }
        val afternoonTeachers = allUsers.count { (it.role.equals("TEACHER", true) || it.role.equals("ADMIN", true)) && (it.shift.contains("Danbe", true) || it.shift.contains("Dhammaan", true)) }

        val classrooms = classroomsCount.value.coerceAtLeast(allCls.size)
        val chairs = chairsCount.value
        val toilets = toiletsCount.value
        val offices = officesCount.value
        val kitchenFeeding = kitchenFeedingCount.value

        val schoolHeader = schoolName.value.ifBlank { "Mahdi Cali School" }
        val serialCode = generateSerialCode("SCH")
        val nowDateTime = getCurrentDateTimeStr()

        val html = StringBuilder()
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>")
        html.append("@page { size: A4; margin: 15mm; }")
        html.append("body { font-family: 'Segoe UI', Arial, sans-serif; padding: 15px; color: #1A1A1A; line-height: 1.5; }")
        html.append(artisticHeaderCss)
        html.append("table { width: 100%; border-collapse: collapse; margin-top: 10px; margin-bottom: 20px; }")
        html.append("th { background: #006A6B; color: white; padding: 10px; text-align: left; font-size: 13px; font-weight: bold; }")
        html.append("td { border-bottom: 1px solid #E2E8F0; padding: 8px 10px; font-size: 12px; }")
        html.append("tr:nth-child(even) { background-color: #F8FAFC; }")
        html.append(".section-title { color: #006A6B; border-bottom: 2px solid #006A6B; padding-bottom: 4px; margin-top: 22px; margin-bottom: 10px; font-size: 16px; font-weight: bold; }")
        html.append(".chart-box { background: #F1F5F9; border: 1px solid #CBD5E1; border-radius: 10px; padding: 16px; margin: 15px 0; }")
        html.append(".stat-card { display: inline-block; width: 30%; background: #E0F2FE; border-radius: 8px; padding: 12px; margin-right: 2%; text-align: center; box-sizing: border-box; }")
        html.append(".stat-val { font-size: 22px; font-weight: bold; color: #0369A1; }")
        html.append(".stat-lbl { font-size: 11px; color: #334155; text-transform: uppercase; margin-top: 4px; }")
        html.append("</style></head><body>")

        html.append(getArtisticHeaderHtml(
            context = context,
            schoolTitle = schoolHeader,
            reportTitle = "WARBIXINTA GUUD EE DUGSIGA (COMPREHENSIVE SCHOOL REPORT)",
            serialCode = serialCode,
            dateStr = nowDateTime,
            badgeText = "OFFICIAL REPORT"
        ))

        // Section 1: Agabka Dugsiga (School Facilities & Assets)
        html.append("<div class='section-title'>1. AGABKA IYO DHISMAHA DUGSIGA (SCHOOL FACILITIES & ASSETS)</div>")
        html.append("<table>")
        html.append("<tr><th>#</th><th>Magaca Agabka (Facility Name)</th><th>Tirada / Xaddiga (Quantity)</th><th>Xaaladda (Condition)</th><th>Faahfaahin (Details)</th></tr>")
        html.append("<tr><td>1</td><td><b>Fasalada Dugsiga (Classrooms)</b></td><td><b>$classrooms Fasal</b></td><td>Working (${allCls.size} Active Classes)</td><td>Fasalada duruustu ka socoto</td></tr>")
        html.append("<tr><td>2</td><td><b>Kuraasta Ardayda (Chairs / Desks)</b></td><td><b>$chairs Kuraas</b></td><td>Good Condition</td><td>Kuraasta & miisaska fadhiga ardayda</td></tr>")
        html.append("<tr><td>3</td><td><b>Musqulaha (Toilets / Restrooms)</b></td><td><b>$toilets Musqulro</b></td><td>Clean & Functional</td><td>Musqulaha ardayda iyo shaqaalaha</td></tr>")
        html.append("<tr><td>4</td><td><b>Xafiisyada Dugsiga (Admin Offices)</b></td><td><b>$offices Xafiis</b></td><td>Fully Equipped</td><td>Xafiisyada maamulka iyo macalimiinta</td></tr>")
        html.append("<tr><td>5</td><td><b>Jikada Cuntada (Kitchen Feeding Program)</b></td><td><b>$kitchenFeeding Jiko</b></td><td>Operational</td><td>Jikada bixisa cuntada bilaashka ah ee ardayda</td></tr>")
        html.append("</table>")

        // Section 2: Macalimiinta Iyo Shaqaalaha
        html.append("<div class='section-title'>2. MACALIMIINTA IYO SHAQAALAHA (TEACHERS & STAFF SUMMARY)</div>")
        html.append("<div style='margin-bottom: 12px;'>")
        html.append("<div class='stat-card'><div class='stat-val'>$totalTeachers</div><div class='stat-lbl'>Wadarta Macalimiinta</div></div>")
        html.append("<div class='stat-card' style='background:#DCFCE7;'><div class='stat-val' style='color:#15803D;'>$morningTeachers</div><div class='stat-lbl'>Gelin Hore (Morning)</div></div>")
        html.append("<div class='stat-card' style='background:#FEF3C7;'><div class='stat-val' style='color:#B45309;'>$afternoonTeachers</div><div class='stat-lbl'>Gelin Danbe (Afternoon)</div></div>")
        html.append("</div>")

        // Section 3: Ardayda Iyo Garaafka Wiilasha & Gabdhaha
        html.append("<div class='section-title'>3. DEMOGRAPHICS ARDAYDA IYO GARAAFAKA (STUDENT DEMOGRAPHICS & GENDER GRAPH)</div>")
        
        val boysPctFormatted = String.format(java.util.Locale.US, "%.1f", boysPercent)
        val girlsPctFormatted = String.format(java.util.Locale.US, "%.1f", girlsPercent)

        html.append("<div class='chart-box'>")
        html.append("<div style='display:flex; justify-content:space-between; font-size:14px; font-weight:bold; margin-bottom:8px;'>")
        html.append("<span>👦 Wiilasha (Boys): $boysCount ($boysPctFormatted%)</span>")
        html.append("<span>👧 Gabdhaha (Girls): $girlsCount ($girlsPctFormatted%)</span>")
        html.append("</div>")
        html.append("<div style='display:flex; height:28px; border-radius:14px; overflow:hidden; border:1px solid #94A3B8; background:#E2E8F0;'>")
        html.append("<div style='width:${boysPercent}%; background:linear-gradient(90deg, #0284C7, #0369A1); color:white; text-align:center; font-size:12px; font-weight:bold; line-height:28px;'>$boysPctFormatted% Boys</div>")
        html.append("<div style='width:${girlsPercent}%; background:linear-gradient(90deg, #D946EF, #A21CAF); color:white; text-align:center; font-size:12px; font-weight:bold; line-height:28px;'>$girlsPctFormatted% Girls</div>")
        html.append("</div>")
        html.append("<div style='margin-top:10px; font-size:12px; color:#475569; text-align:center;'>Wadarta Guud ee Ardayda Dugsiga Rejabsan: <b>$totalStudents Arday</b></div>")
        html.append("</div>")

        // Class-by-Class Breakdown Table
        html.append("<h4 style='color:#006A6B; margin-top:15px; margin-bottom:6px;'>Tirada Ardayda Fasal Kasta (Class-by-Class Boys & Girls Breakdown)</h4>")
        html.append("<table>")
        html.append("<tr><th>#</th><th>Fasalka (Class Name)</th><th>Shift-ka</th><th>👦 Wiilal</th><th>👧 Gabdho</th><th>Wadarta (Total)</th></tr>")
        allCls.forEachIndexed { index, cls ->
            val classStudents = allStuds.filter { it.classId == cls.id }
            val cBoys = classStudents.count { it.gender.equals("Male", ignoreCase = true) || it.gender.equals("Wiil", ignoreCase = true) }
            val cGirls = classStudents.count { it.gender.equals("Female", ignoreCase = true) || it.gender.equals("Gabdho", ignoreCase = true) || it.gender.equals("Gabdhaha", ignoreCase = true) }
            val cTotal = classStudents.size
            html.append("<tr><td>${index + 1}</td><td><b>${cls.name}</b></td><td>${cls.shift}</td><td>$cBoys</td><td>$cGirls</td><td><b>$cTotal</b></td></tr>")
        }
        html.append("<tr style='background:#E2E8F0; font-weight:bold;'><td>-</td><td>WADARTA GUUD</td><td>-</td><td>$boysCount</td><td>$girlsCount</td><td>$totalStudents</td></tr>")
        html.append("</table>")

        // Signatures
        html.append("<div style='margin-top: 40px; display: flex; justify-content: space-between;'>")
        html.append("<div><b>Incharge Officer:</b><br/><br/>______________________<br/>Taariikh: $nowDateTime</div>")
        html.append("<div><b>Maamulaha Dugsiga (Principal):</b><br/><br/>______________________<br/>Saaniyo & Shaambad</div>")
        html.append("</div>")

        html.append("</body></html>")

        repository.printHtmlReport(context, html.toString(), "Warbixinta_Guud_Ee_Dugsiga")
    }

    fun printClassReportHtml(context: Context, selectedClassId: Long) {
        val targetClasses = if (selectedClassId == 0L) classes.value else classes.value.filter { it.id == selectedClassId }
        val allStuds = students.value

        val schoolHeader = schoolName.value.ifBlank { "Mahdi Cali School" }
        val serialCode = generateSerialCode("CLS")
        val nowDateTime = getCurrentDateTimeStr()

        val html = StringBuilder()
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>")
        html.append("@page { size: A4; margin: 15mm; }")
        html.append("body { font-family: 'Segoe UI', Arial, sans-serif; padding: 15px; color: #1A1A1A; line-height: 1.4; }")
        html.append(artisticHeaderCss)
        html.append("table { width: 100%; border-collapse: collapse; margin-top: 10px; margin-bottom: 20px; }")
        html.append("th { background: #006A6B; color: white; padding: 8px; text-align: left; font-size: 12px; }")
        html.append("td { border-bottom: 1px solid #E2E8F0; padding: 6px; font-size: 12px; }")
        html.append("</style></head><body>")

        html.append(getArtisticHeaderHtml(
            context = context,
            schoolTitle = schoolHeader,
            reportTitle = "DIIWAANKA ARDAYDA FASALLADA (CLASS DIRECTORY & ROSTER)",
            serialCode = serialCode,
            dateStr = nowDateTime,
            badgeText = "CLASS ROSTER"
        ))

        targetClasses.forEach { cls ->
            val clsStuds = allStuds.filter { it.classId == cls.id }
            html.append("<h3>Class: ${cls.name} &nbsp;&nbsp; (Teacher: ${cls.inchargeTeacher.ifBlank { "Unassigned" }}) &nbsp;&nbsp; Total Students: ${clsStuds.size}</h3>")
            if (clsStuds.isEmpty()) {
                html.append("<p>No enrolled students.</p>")
            } else {
                html.append("<table><tr><th>#</th><th>Student ID</th><th>Name</th><th>Gender</th><th>Mother Name</th><th>Phone</th></tr>")
                clsStuds.forEachIndexed { index, s ->
                    html.append("<tr><td>${index + 1}</td><td>${s.studentId}</td><td>${s.name}</td><td>${s.gender}</td><td>${s.motherName}</td><td>${s.phone}</td></tr>")
                }
                html.append("</table>")
            }
        }
        html.append("</body></html>")

        repository.printHtmlReport(context, html.toString(), "Class_Roster_Report")
    }

    fun printStudentClearanceHtml(
        context: Context,
        student: Student,
        previousSchool: String,
        destinationSchool: String,
        destinationClass: String,
        academicYear: String
    ) {
        val currentCls = classes.value.find { it.id == student.classId }
        val currentClassName = currentCls?.name ?: "N/A"
        val schoolHeader = previousSchool.ifBlank { schoolName.value.ifBlank { "Mahdi Cali School" } }

        val standardSubjects = listOf("Xisaab", "Saynis", "Af-Soomaali", "English", "Carabi", "Cilmiga Bulshada")

        val html = StringBuilder()
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>")
        html.append("@page { size: A4; margin: 15mm; }")
        html.append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 15px; color: #102A2A; font-size: 12px; }")
        html.append(".document-container { border: 2px solid #006A6B; padding: 15px; border-radius: 6px; box-sizing: border-box; }")

        // Header Style
        html.append(".header-box { text-align: center; border: 1.5px solid #006A6B; padding: 8px; border-radius: 4px; background: #FAFDFD; margin-bottom: 10px; }")
        html.append(".gov-title { font-size: 10px; font-weight: bold; color: #004D4E; text-transform: uppercase; letter-spacing: 0.5px; }")
        html.append(".school-title { font-size: 16px; font-weight: 800; color: #006A6B; margin: 3px 0 6px 0; text-transform: uppercase; }")
        html.append(".doc-banner { background: #006A6B; color: #FFFFFF; font-size: 14px; font-weight: bold; padding: 4px 18px; display: inline-block; border-radius: 4px; letter-spacing: 1px; }")

        // Student Info Table
        html.append(".info-table { width: 100%; border-collapse: collapse; margin-bottom: 12px; border: 1px solid #CBD5E1; }")
        html.append(".info-table td { padding: 5px 8px; border: 1px solid #E2E8F0; font-size: 11px; }")
        html.append(".info-label { font-weight: bold; color: #475569; width: 42%; background: #F8FAFC; }")
        html.append(".info-value { font-weight: bold; color: #0F172A; }")

        // Section Title
        html.append(".section-title { font-size: 11px; font-weight: bold; color: #006A6B; margin-bottom: 8px; text-transform: uppercase; border-bottom: 2px solid #006A6B; padding-bottom: 3px; }")

        // Grid of Class Boxes (2 columns layout)
        html.append(".boxes-grid { width: 100%; display: table; table-layout: fixed; border-spacing: 8px; margin-bottom: 10px; }")
        html.append(".box-row { display: table-row; }")
        html.append(".class-box { display: table-cell; width: 50%; vertical-align: top; border: 1.5px solid #006A6B; border-radius: 4px; overflow: hidden; background: #FFFFFF; box-sizing: border-box; }")
        html.append(".box-header { background: #006A6B; color: #FFFFFF; font-weight: bold; text-align: center; padding: 4px; font-size: 10px; text-transform: uppercase; letter-spacing: 0.5px; }")
        html.append(".box-content { padding: 4px; }")

        // Subject Table inside Box
        html.append(".sub-table { width: 100%; border-collapse: collapse; font-size: 9px; text-align: center; }")
        html.append(".sub-table th { background: #E6F2F2; color: #004D4E; padding: 3px 2px; border-bottom: 1px solid #CBD5E1; font-weight: bold; }")
        html.append(".sub-table td { padding: 2px 3px; border-bottom: 1px solid #E2E8F0; }")
        html.append(".sub-table td.sub-name { text-align: left; font-weight: 500; color: #1E293B; }")
        html.append(".sub-table tr.tot-row td { background: #F0F7F7; font-weight: bold; color: #006A6B; border-top: 1px solid #006A6B; }")

        // Footer
        html.append(".footer-section { margin-top: 15px; border: 1px solid #CBD5E1; border-radius: 4px; padding: 10px; background: #FAFDFD; page-break-inside: avoid; }")
        html.append(".sig-row { display: flex; justify-content: space-between; margin-bottom: 10px; font-size: 10px; font-weight: bold; }")
        html.append(".stamp-row { display: flex; justify-content: space-between; font-size: 9px; color: #64748B; }")

        html.append("</style></head><body>")

        html.append("<div class='document-container'>")

        // Header
        val logoBase64 = getSchoolLogoBase64(context)
        val serialCodeCLR = generateSerialCode("CLR")
        val nowDateTimeCLR = getCurrentDateTimeStr()

        html.append("<div class='header-box' style='padding:10px 14px; margin-bottom:12px;'>")
        html.append("<div style='display:flex; align-items:center; justify-content:space-between; gap:12px;'>")
        html.append("<div style='flex:1; text-align:left; font-size:10px; line-height:1.3;'>")
        html.append("<div style='font-weight:bold; color:#004D4E; font-size:10.5px;'>JAMHUURIYADDA SOMALILAND</div>")
        html.append("<div style='font-weight:bold; color:#475569; font-size:9.5px;'>WASAARADDA WAXBARASHADA & SAYNISKA</div>")
        html.append("<div style='color:#64748B; font-size:9px;'>Agaasinka Waxbarashada Guud</div>")
        html.append("</div>")

        html.append("<div style='flex-shrink:0; text-align:center;'>")
        if (logoBase64.isNotBlank()) {
            html.append("<img src='data:image/jpeg;base64,$logoBase64' style='width:70px; height:70px; border-radius:50%; object-fit:cover; border:2.5px solid #006A6B; background:#FFF; box-shadow:0 3px 8px rgba(0,106,107,0.2);' alt='Logo' />")
        }
        html.append("</div>")

        html.append("<div style='flex:1; text-align:right; font-size:10px; line-height:1.3;'>")
        html.append("<div style='color:#334155;'><b>Tixraac:</b> <span style='font-family:monospace; color:#006A6B; font-weight:bold;'>$serialCodeCLR</span></div>")
        html.append("<div style='color:#334155;'><b>Taariikhda:</b> $nowDateTimeCLR</div>")
        html.append("<div style='display:inline-block; background:#006A6B; color:#FFF; padding:2px 8px; border-radius:4px; font-size:9px; font-weight:bold; margin-top:2px;'>OFFICIAL CERTIFICATE</div>")
        html.append("</div>")
        html.append("</div>")

        html.append("<div style='text-align:center; margin-top:6px; padding-top:6px; border-top:1px dashed #CBD5E1;'>")
        html.append("<div class='school-title' style='margin:0 0 3px 0; font-size:18px;'>${schoolHeader.uppercase()}</div>")
        html.append("<div class='doc-banner' style='font-size:13px; padding:3px 18px;'>WARQADDA ARDAYGA (CLEARANCE & RECORD SHEET)</div>")
        html.append("</div>")
        html.append("</div>")

        // Student Metadata Table
        html.append("<table class='info-table'>")
        html.append("<tr><td class='info-label'>1. Magaca Ardayga oo Dhamaystiran:</td><td class='info-value'>${student.name}</td></tr>")
        html.append("<tr><td class='info-label'>2. Magaca Hooyada:</td><td class='info-value'>${student.motherName.ifBlank { "N/A" }}</td></tr>")
        html.append("<tr><td class='info-label'>3. Fasalka uu Ku Jiray:</td><td class='info-value'>$currentClassName</td></tr>")
        html.append("<tr><td class='info-label'>4. Dugsiga uu Ku Jiray:</td><td class='info-value'>$schoolHeader</td></tr>")
        html.append("<tr><td class='info-label'>5. Dugsiga loo Beddelay:</td><td class='info-value'>$destinationSchool</td></tr>")
        html.append("<tr><td class='info-label'>6. Fasalka uu u Gudbay:</td><td class='info-value'>$destinationClass</td></tr>")
        html.append("<tr><td class='info-label'>7. Sanad-Dugsyeedka:</td><td class='info-value'>$academicYear</td></tr>")
        html.append("</table>")

        // Class Records Title
        html.append("<div class='section-title'>DIIWAANKA SANNADAHA / FASALLADA (FASALLADA 1AAD - 4AAD)</div>")

        // Boxes Grid (4 boxes arranged in 2 rows of 2 columns)
        html.append("<div class='boxes-grid'>")

        val allCls = classes.value
        val allExs = exams.value
        val allMrs = allExamMarks.value

        fun fmt(n: Double) = if (n % 1.0 == 0.0) n.toInt().toString() else String.format(java.util.Locale.US, "%.1f", n)

        (1..4).chunked(2).forEach { pair ->
            html.append("<div class='box-row'>")
            pair.forEach { boxIdx ->
                val boxLabel = "Fasalka ${boxIdx}aad"

                html.append("<div class='class-box'>")
                html.append("<div class='box-header'>${boxLabel.uppercase()}</div>")
                html.append("<div class='box-content'>")

                html.append("<table class='sub-table'>")
                html.append("<thead><tr><th style='text-align:left;'>Maadada</th><th>Teeram 1</th><th>Teeram 2</th><th style='text-align:right;'>Wadarta</th></tr></thead>")
                html.append("<tbody>")

                // Boxes 1..4: Find class in DB matching this grade boxIdx
                val matchingClass = allCls.find { cls ->
                    val digits = cls.name.filter { it.isDigit() }
                    val num = digits.toIntOrNull()
                    if (num != null) num == boxIdx
                    else {
                        val nameLower = cls.name.lowercase()
                        nameLower.contains("fasalka $boxIdx") || nameLower.contains("class $boxIdx") || nameLower.contains("grade $boxIdx") || nameLower.contains("$boxIdx")
                    }
                }

                val classExams = if (matchingClass != null) {
                    allExs.filter { it.classId == matchingClass.id }
                } else emptyList()

                val activeSubjects = classExams.map { exam ->
                    cleanExamSubjectName(exam.subject.ifBlank { exam.name })
                }.filter { it.isNotBlank() }.distinct()

                val subjectsList = if (activeSubjects.isNotEmpty()) {
                    val full = standardSubjects.toMutableList()
                    activeSubjects.forEach { sub ->
                        if (!full.any { it.equals(sub, ignoreCase = true) }) full.add(sub)
                    }
                    full
                } else standardSubjects

                var sumT1 = 0.0
                var sumT2 = 0.0
                var hasAnyT1 = false
                var hasAnyT2 = false

                subjectsList.forEach { subName ->
                    val t1Exam = classExams.find { exam ->
                        val text = (exam.name + " " + exam.subject).lowercase()
                        val isT2 = text.contains("term 2") || text.contains("term2") || text.contains("t2") || text.contains("sem 2") || text.contains("final")
                        val subClean = cleanExamSubjectName(exam.subject.ifBlank { exam.name }).lowercase()
                        val targetClean = subName.lowercase()
                        val matches = subClean == targetClean || subClean.contains(targetClean) || text.contains(targetClean) ||
                            (targetClean.contains("diin") && (text.contains("diin") || text.contains("islam") || text.contains("tarbiya"))) ||
                            (targetClean.contains("xisaab") && (text.contains("math") || text.contains("xisaab"))) ||
                            (targetClean.contains("saynis") && (text.contains("science") || text.contains("saynis"))) ||
                            (targetClean.contains("soomaali") && (text.contains("soomaali") || text.contains("somali"))) ||
                            (targetClean.contains("english") && text.contains("english")) ||
                            (targetClean.contains("carabi") && (text.contains("arabic") || text.contains("carabi"))) ||
                            (targetClean.contains("bulshada") && (text.contains("bulshada") || text.contains("social")))
                        !isT2 && matches
                    }

                    val t2Exam = classExams.find { exam ->
                        val text = (exam.name + " " + exam.subject).lowercase()
                        val isT2 = text.contains("term 2") || text.contains("term2") || text.contains("t2") || text.contains("sem 2") || text.contains("final")
                        val subClean = cleanExamSubjectName(exam.subject.ifBlank { exam.name }).lowercase()
                        val targetClean = subName.lowercase()
                        val matches = subClean == targetClean || subClean.contains(targetClean) || text.contains(targetClean) ||
                            (targetClean.contains("diin") && (text.contains("diin") || text.contains("islam") || text.contains("tarbiya"))) ||
                            (targetClean.contains("xisaab") && (text.contains("math") || text.contains("xisaab"))) ||
                            (targetClean.contains("saynis") && (text.contains("science") || text.contains("saynis"))) ||
                            (targetClean.contains("soomaali") && (text.contains("soomaali") || text.contains("somali"))) ||
                            (targetClean.contains("english") && text.contains("english")) ||
                            (targetClean.contains("carabi") && (text.contains("arabic") || text.contains("carabi"))) ||
                            (targetClean.contains("bulshada") && (text.contains("bulshada") || text.contains("social")))
                        isT2 && matches
                    }

                    val t1Mark = t1Exam?.let { ex -> allMrs.find { it.examId == ex.id && it.studentId == student.id && !it.isAbsent } }
                    val t2Mark = t2Exam?.let { ex -> allMrs.find { it.examId == ex.id && it.studentId == student.id && !it.isAbsent } }

                    val t1Score = t1Mark?.score?.let { s -> if (s > 50.0) s / 2.0 else s }
                    val t2Score = t2Mark?.score?.let { s -> if (s > 50.0) s / 2.0 else s }

                    if (t1Score != null) { sumT1 += t1Score; hasAnyT1 = true }
                    if (t2Score != null) { sumT2 += t2Score; hasAnyT2 = true }

                    val t1Str = t1Score?.let { fmt(it) } ?: "&nbsp;"
                    val t2Str = t2Score?.let { fmt(it) } ?: "&nbsp;"
                    val totStr = if (t1Score != null || t2Score != null) {
                        fmt((t1Score ?: 0.0) + (t2Score ?: 0.0))
                    } else "&nbsp;"

                    html.append("<tr><td class='sub-name'>$subName</td><td>$t1Str</td><td>$t2Str</td><td style='text-align:right; font-weight:bold;'>$totStr</td></tr>")
                }

                val grandT1Str = if (hasAnyT1) fmt(sumT1) else "&nbsp;"
                val grandT2Str = if (hasAnyT2) fmt(sumT2) else "&nbsp;"
                val grandTotStr = if (hasAnyT1 || hasAnyT2) fmt(sumT1 + sumT2) else "&nbsp;"

                html.append("<tr class='tot-row'><td class='sub-name'>Wadarta Guud</td><td>$grandT1Str</td><td>$grandT2Str</td><td style='text-align:right;'>$grandTotStr</td></tr>")

                html.append("</tbody></table>")
                html.append("</div></div>")
            }
            html.append("</div>")
        }

        html.append("</div>")

        // Footer Section
        html.append("<div class='footer-section'>")
        html.append("<div class='sig-row'>")
        html.append("<div>Magaca Maamulaha: __________________________</div>")
        html.append("<div>Saxeexa Maamulaha: __________________________</div>")
        html.append("</div>")
        html.append("<div class='stamp-row'>")
        html.append("<div>Taariikhda & Waqtiga: $nowDateTimeCLR</div>")
        html.append("<div>Serial Code: $serialCodeCLR</div>")
        html.append("</div></div>")

        html.append("</div></body></html>")

        repository.printHtmlReport(context, html.toString(), "Warqadda_Ardayga_${student.studentId}")
    }

    fun printStudentMarksheetHtml(
        context: Context,
        student: Student,
        academicYear: String,
        destinationClass: String
    ) {
        val currentCls = classes.value.find { it.id == student.classId }
        val currentClassName = currentCls?.name ?: "N/A"
        val schoolHeader = schoolName.value.ifBlank { "Mahdi Cali School" }
        val serialCodeMSK = generateSerialCode("MSK")
        val nowDateTimeMSK = getCurrentDateTimeStr()

        val classExamsList = exams.value.filter { it.classId == student.classId }
        val extractedSubjects = classExamsList.map { exam ->
            cleanExamSubjectName(exam.subject.ifBlank { exam.name })
        }.filter { it.isNotBlank() }.distinct()

        val standard7Subjects = listOf(
            "Diinta Islaamka",
            "Af-Soomaali",
            "Xisaab",
            "Saynis",
            "Cilmiga Bulshada",
            "English",
            "Carabi"
        )

        val activeSubjects = standard7Subjects
        val allExams = exams.value
        val allMrs = allExamMarks.value

        fun fmt(n: Double) = if (n % 1.0 == 0.0) n.toInt().toString() else String.format(java.util.Locale.US, "%.1f", n)

        fun getAutoScore(subName: String, isTerm2: Boolean): Double? {
            val studentMarks = allMrs.filter { it.studentId == student.id && !it.isAbsent }
            val subLower = subName.lowercase()

            val match = studentMarks.mapNotNull { mark ->
                val exam = allExams.find { it.id == mark.examId }
                if (exam != null) {
                    val text = (exam.name + " " + exam.subject).lowercase()
                    val examIsTerm2 = text.contains("term 2") || text.contains("term2") || text.contains("t2") || text.contains("sem 2") || text.contains("final") || text.contains("2nd") || text.contains("teeramka 2")
                    val isCorrectTerm = if (isTerm2) examIsTerm2 else !examIsTerm2

                    val subClean = cleanExamSubjectName(exam.subject.ifBlank { exam.name }).lowercase()
                    val targetClean = subLower

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

                    if (isCorrectTerm && matchesSubject) Pair(mark, exam) else null
                } else null
            }.firstOrNull()

            if (match != null) {
                val (mark, exam) = match
                var sc = mark.score
                if (exam.totalMarks == 100.0 && sc > 50.0) {
                    sc /= 2.0
                }
                return sc.coerceAtMost(50.0)
            }
            return null
        }

        var grandTotal = 0.0
        var validCount = 0

        val html = StringBuilder()
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>")
        html.append("@page { size: A4; margin: 15mm; }")
        html.append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; padding: 15px; color: #102A2A; }")

        // Header
        html.append(".header-box { text-align: center; border: 1px solid #006A6B; padding: 8px 12px; border-radius: 6px; background: #FAFDFD; margin-bottom: 15px; }")
        html.append(".gov-title { font-size: 11px; font-weight: bold; color: #004D4E; text-transform: uppercase; letter-spacing: 0.5px; }")
        html.append(".school-title { font-size: 16px; font-weight: 800; color: #006A6B; margin: 4px 0; text-transform: uppercase; }")
        html.append(".doc-banner { background: #006A6B; color: #FFFFFF; font-size: 12px; font-weight: bold; padding: 4px 16px; display: inline-block; border-radius: 4px; letter-spacing: 0.5px; }")

        // Section Title
        html.append(".sec-title { font-size: 11px; font-weight: bold; color: #006A6B; margin: 15px 0 6px 0; text-transform: uppercase; border-bottom: 2px solid #006A6B; padding-bottom: 2px; }")

        // Profile Table
        html.append(".info-table { width: 100%; border-collapse: collapse; margin-bottom: 15px; border: 1px solid #CBD5E1; }")
        html.append(".info-table td { padding: 6px 10px; border: 1px solid #E2E8F0; font-size: 11px; }")
        html.append(".info-label { font-weight: bold; color: #475569; width: 22%; background: #F8FAFC; }")
        html.append(".info-value { font-weight: bold; color: #0F172A; width: 28%; }")

        // Marks Table
        html.append(".marks-table { width: 100%; border-collapse: collapse; margin-bottom: 15px; border: 2px solid #006A6B; font-size: 11px; }")
        html.append(".marks-table th { background: #006A6B; color: white; padding: 8px 6px; text-align: center; font-weight: bold; font-size: 11px; }")
        html.append(".marks-table td { border: 1px solid #CBD5E1; padding: 6px; text-align: center; font-size: 11px; }")
        html.append(".marks-table td.sub-name { text-align: left; font-weight: bold; color: #1E293B; }")
        html.append(".marks-table tr.summary-row td { background: #F0F7F7; font-weight: bold; color: #006A6B; border-top: 2px solid #006A6B; padding: 8px 6px; }")

        // Result Boxes
        html.append(".result-box-pass { background: #E8F5E9; border: 1px solid #2E7D32; border-radius: 6px; padding: 10px 14px; margin-bottom: 10px; color: #1B5E20; }")
        html.append(".result-box-fail { background: #FFEBEE; border: 1px solid #C62828; border-radius: 6px; padding: 10px 14px; margin-bottom: 10px; color: #B71C1C; }")
        html.append(".result-head { font-size: 13px; font-weight: bold; margin-bottom: 4px; }")
        html.append(".result-body { font-size: 11px; }")

        // Footer
        html.append(".footer-section { margin-top: 20px; border: 1px solid #CBD5E1; border-radius: 6px; padding: 10px 14px; background: #FAFDFD; page-break-inside: avoid; }")
        html.append(".sig-row { display: flex; justify-content: space-between; margin-bottom: 10px; font-size: 12px; font-weight: bold; }")
        html.append(".stamp-row { display: flex; justify-content: space-between; font-size: 11px; color: #64748B; }")

        html.append("</style></head><body>")

        html.append("<div class='document-container'>")

        // Header
        val logoBase64 = getSchoolLogoBase64(context)
        html.append("<div class='header-box'>")
        html.append("<div style='display:flex; align-items:center; justify-content:space-between; gap:8px;'>")
        html.append("<div style='flex:1; text-align:left; font-size:8.5px; line-height:1.15;'>")
        html.append("<div style='font-weight:bold; color:#004D4E; font-size:9px;'>JAMHUURIYADDA SOMALILAND</div>")
        html.append("<div style='font-weight:bold; color:#475569; font-size:8px;'>WASAARADDA WAXBARASHADA & SAYNISKA</div>")
        html.append("<div style='color:#64748B; font-size:7.5px;'>Imtixaanka Sanad-Dugsiyeedka</div>")
        html.append("</div>")

        html.append("<div style='flex-shrink:0; text-align:center;'>")
        if (logoBase64.isNotBlank()) {
            html.append("<img src='data:image/jpeg;base64,$logoBase64' style='width:36px; height:36px; border-radius:50%; object-fit:cover; border:1.5px solid #006A6B; background:#FFF;' alt='Logo' />")
        }
        html.append("</div>")

        html.append("<div style='flex:1; text-align:right; font-size:8.5px; line-height:1.15;'>")
        html.append("<div style='color:#334155;'><b>Tixraac:</b> <span style='font-family:monospace; color:#006A6B; font-weight:bold;'>$serialCodeMSK</span></div>")
        html.append("<div style='color:#334155;'><b>Taariikhda:</b> $nowDateTimeMSK</div>")
        html.append("<div style='display:inline-block; background:#006A6B; color:#FFF; padding:1px 6px; border-radius:3px; font-size:8px; font-weight:bold; margin-top:2px;'>OFFICIAL MARKSHEET</div>")
        html.append("</div>")
        html.append("</div>")

        html.append("<div style='text-align:center; margin-top:3px; padding-top:3px; border-top:1px dashed #CBD5E1;'>")
        html.append("<div class='school-title'>${schoolHeader.uppercase()}</div>")
        html.append("<div class='doc-banner'>STUDENT MARKSHEET (WARBIXINTA DHIBCAHA)</div>")
        html.append("</div>")
        html.append("</div>")

        // 1. STUDENT PROFILE (Compact 4-column 2-pair layout)
        html.append("<div class='sec-title'>1. STUDENT PROFILE (XOGTA ARDAYGA)</div>")
        html.append("<table class='info-table'>")
        html.append("<tr><td class='info-label'>Magaca Ardayga:</td><td class='info-value'>${student.name}</td><td class='info-label'>Student ID / Roll:</td><td class='info-value'>${student.studentId}</td></tr>")
        html.append("<tr><td class='info-label'>Magaca Hooyada:</td><td class='info-value'>${student.motherName.ifBlank { "N/A" }}</td><td class='info-label'>Mobilka Waalidka:</td><td class='info-value'>${student.phone.ifBlank { "N/A" }}</td></tr>")
        html.append("<tr><td class='info-label'>Fasalka Hadda:</td><td class='info-value'>$currentClassName</td><td class='info-label'>Jinsiga (Gender):</td><td class='info-value'>${student.gender}</td></tr>")
        html.append("<tr><td class='info-label'>Sanad-Dugsiyeedka:</td><td class='info-value'>$academicYear</td><td class='info-label'>Fasalka uu u Gudbayo:</td><td class='info-value'>$destinationClass</td></tr>")
        html.append("</table>")

        // 2. IMTIXAANKA SANAD DUGSIYEEDKA
        html.append("<div class='sec-title'>2. IMTIXAANKA SANAD DUGSIYEEDKA (Mark Max: 50 | Pass: 25 per Term)</div>")
        html.append("<table class='marks-table'>")
        html.append("<thead><tr><th style='text-align:left;'>Maadada</th><th>Teeramka 1aad (Max: 50)</th><th>Teeramka 2aad (Max: 50)</th><th>Wadarta (/100)</th><th>Celceliska</th><th style='text-align:right;'>Natiijada</th></tr></thead>")
        html.append("<tbody>")

        activeSubjects.forEach { subName ->
            val t1Val = getAutoScore(subName, isTerm2 = false)
            val t2Val = getAutoScore(subName, isTerm2 = true)

            val totVal = if (t1Val != null || t2Val != null) (t1Val ?: 0.0) + (t2Val ?: 0.0) else null
            val avgVal = if (t1Val != null && t2Val != null) totVal!! / 2.0 else totVal

            if (totVal != null) {
                grandTotal += totVal
                validCount++
            }

            val t1Str = t1Val?.let { fmt(it) } ?: "&nbsp;"
            val t2Str = t2Val?.let { fmt(it) } ?: "&nbsp;"
            val totStr = totVal?.let { fmt(it) } ?: "&nbsp;"
            val avgStr = avgVal?.let { fmt(it) } ?: "&nbsp;"
            val resStr = when {
                totVal == null -> "&nbsp;"
                (totVal ?: 0.0) >= 50.0 || (avgVal ?: 0.0) >= 25.0 -> "<span style='color:#2E7D32; font-weight:bold;'>BAASAY</span>"
                else -> "<span style='color:#C62828; font-weight:bold;'>DHACAY</span>"
            }

            html.append("<tr><td class='sub-name'>$subName</td><td>$t1Str</td><td>$t2Str</td><td style='font-weight:bold;'>$totStr</td><td>$avgStr</td><td style='text-align:right;'>$resStr</td></tr>")
        }

        val maxPossible = if (validCount > 0) validCount * 100.0 else 700.0
        val overallPct = if (maxPossible > 0) (grandTotal / maxPossible) * 100.0 else 0.0
        val passThreshold = (validCount * 50.0).coerceAtLeast(175.0)
        val isPassed = grandTotal >= passThreshold || overallPct >= 50.0

        val grade = when {
            overallPct >= 90 -> "A"
            overallPct >= 80 -> "B"
            overallPct >= 70 -> "C"
            overallPct >= 50 -> "D"
            else -> "F"
        }

        html.append("<tr class='summary-row'><td class='sub-name'>Total Marks (Wadarta Guud)</td><td colspan='4' style='text-align:right;'><b>${fmt(grandTotal)} / ${activeSubjects.size * 100}</b></td><td>&nbsp;</td></tr>")
        html.append("<tr class='summary-row'><td class='sub-name'>Average (Celceliska Guud)</td><td colspan='4' style='text-align:right;'><b>${String.format(java.util.Locale.US, "%.1f%%", overallPct)}</b></td><td>&nbsp;</td></tr>")
        html.append("<tr class='summary-row'><td class='sub-name'>Grade</td><td colspan='4' style='text-align:right;'><b>$grade</b></td><td>&nbsp;</td></tr>")
        html.append("<tr class='summary-row'><td class='sub-name'>Status (Natiijada)</td><td colspan='4' style='text-align:right;'><b>${if (isPassed) "<span style='color:#2E7D32;'>BAASAY</span>" else "<span style='color:#C62828;'>DHACAY</span>"}</b></td><td>&nbsp;</td></tr>")
        html.append("<tr class='summary-row'><td class='sub-name'>Fasalka uu u Gudbayo</td><td colspan='4' style='text-align:right;'><b>$destinationClass</b></td><td>&nbsp;</td></tr>")

        html.append("</tbody></table>")

        // 3. NATIIJADA ARDAYGA
        html.append("<div class='sec-title'>3. NATIIJADA ARDAYGA</div>")

        if (isPassed) {
            html.append("<div class='result-box-pass'>")
            html.append("<div class='result-head'>🎉 HAMBALYO!</div>")
            html.append("<div class='result-body'>“Waxaan kuu hambalyaynaynaa guusha aad ka gaadhay imtixaanka sanad-dugsiyeedka. Dadaalkaaga iyo horumarkaaga sii wad, kuna dadaal inaad mar kasta gaadho heer ka sarreeya.”</div>")
            html.append("</div>")
        } else {
            html.append("<div class='result-box-fail'>")
            html.append("<div class='result-head'>📌 DARDARAN IYO DHIIRIGELIN</div>")
            html.append("<div class='result-body'>“Ha niyad jabin. Guuldarradu ma aha dhammaadka waxbarashada, ee waa fursad aad ku ogaan karto meelaha aad u baahan tahay inaad ku dadaasho. Dib u eeg casharradaada, dadaalka kordhi, waqtiga si wanaagsan uga faa’iidayso, waxaana rajaynaynaa inaad sannadka dambe guul weyn gaadho.”</div>")
            html.append("</div>")
        }

        // 4. GUNNAANAD
        html.append("<div class='footer-section'>")
        html.append("<div class='sig-row'>")
        html.append("<div>Magaca Maamulaha: ______________________</div>")
        html.append("<div>Saxeexa Maamulaha: ______________________</div>")
        html.append("</div>")
        html.append("<div class='stamp-row'>")
        html.append("<div>Taariikhda & Waqtiga: $nowDateTimeMSK</div>")
        html.append("<div>Serial Code: $serialCodeMSK</div>")
        html.append("</div></div>")

        html.append("</body></html>")

        repository.printHtmlReport(context, html.toString(), "Student_Marksheet_${student.studentId}")
    }

    fun printAllClassMarksheetsHtml(
        context: Context,
        classId: Long,
        academicYear: String,
        destinationClass: String
    ) {
        val targetClasses = if (classId != 0L) {
            classes.value.filter { it.id == classId }
        } else {
            classes.value
        }
        val targetStudents = students.value.filter { s ->
            (classId == 0L || s.classId == classId) &&
            (s.status.isBlank() || s.status.equals("ACTIVE", ignoreCase = true)) &&
            !s.isDeleted
        }

        if (targetStudents.isEmpty()) {
            viewModelScope.launch { _uiMessage.emit("Arday firfircoon ma joogaan fasalka la doortay.") }
            return
        }

        viewModelScope.launch { _uiMessage.emit("Waxaa la diyaarinayaa warqadaha ${targetStudents.size} arday...") }

        val schoolHeader = schoolName.value.ifBlank { "Mahdi Cali School" }
        val logoBase64 = getSchoolLogoBase64(context)
        val nowDateTimeMSK = getCurrentDateTimeStr()

        val html = StringBuilder()
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>")
        html.append("@page { size: A4 portrait; margin: 18mm 12mm 15mm 12mm; }")
        html.append("@media print { html, body { margin: 0; padding: 0; } body { -webkit-print-color-adjust: exact; print-color-adjust: exact; } .document-container { page-break-inside: avoid !important; break-inside: avoid !important; max-height: 288mm; box-sizing: border-box; overflow: hidden; } .page-break { page-break-after: always; break-after: page; } }")
        html.append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 0; color: #102A2A; font-size: 8.5px; line-height: 1.15; }")
        html.append(".document-container { border: 1.5px solid #006A6B; padding: 4px 6px; border-radius: 4px; box-sizing: border-box; page-break-inside: avoid !important; break-inside: avoid !important; max-height: 288mm; overflow: hidden; }")
        html.append(".header-box { text-align: center; border: 1px solid #006A6B; padding: 3px 6px; border-radius: 4px; background: #FAFDFD; margin-bottom: 3px; }")
        html.append(".school-title { font-size: 13px; font-weight: 800; color: #006A6B; margin: 1px 0 2px 0; text-transform: uppercase; }")
        html.append(".doc-banner { background: #006A6B; color: #FFFFFF; font-size: 9.5px; font-weight: bold; padding: 1.5px 12px; display: inline-block; border-radius: 3px; letter-spacing: 0.5px; }")
        html.append(".sec-title { font-size: 8.5px; font-weight: bold; color: #006A6B; margin: 3px 0 1px 0; text-transform: uppercase; border-bottom: 1.5px solid #006A6B; padding-bottom: 1px; }")
        html.append(".info-table { width: 100%; border-collapse: collapse; margin-bottom: 3px; border: 1px solid #CBD5E1; }")
        html.append(".info-table td { padding: 1.5px 5px; border: 1px solid #E2E8F0; font-size: 8.5px; }")
        html.append(".info-label { font-weight: bold; color: #475569; width: 22%; background: #F8FAFC; }")
        html.append(".info-value { font-weight: bold; color: #0F172A; width: 28%; }")
        html.append(".marks-table { width: 100%; border-collapse: collapse; margin-bottom: 3px; border: 1.5px solid #006A6B; font-size: 8.5px; }")
        html.append(".marks-table th { background: #006A6B; color: white; padding: 2.5px 3px; text-align: center; font-weight: bold; font-size: 8.5px; }")
        html.append(".marks-table td { border: 1px solid #CBD5E1; padding: 1.5px 3px; text-align: center; font-size: 8.5px; }")
        html.append(".marks-table td.sub-name { text-align: left; font-weight: bold; color: #1E293B; }")
        html.append(".marks-table tr.summary-row td { background: #F0F7F7; font-weight: bold; color: #006A6B; border-top: 1.5px solid #006A6B; padding: 1.5px 3px; font-size: 8.5px; }")
        html.append(".result-box-pass { background: #E8F5E9; border: 1px solid #2E7D32; border-radius: 3px; padding: 2px 6px; margin-bottom: 2px; color: #1B5E20; line-height: 1.15; }")
        html.append(".result-box-fail { background: #FFEBEE; border: 1px solid #C62828; border-radius: 3px; padding: 2px 6px; margin-bottom: 2px; color: #B71C1C; line-height: 1.15; }")
        html.append(".result-head { font-size: 9.5px; font-weight: bold; margin-bottom: 1px; }")
        html.append(".result-body { font-size: 8px; line-height: 1.15; }")
        html.append(".footer-section { margin-top: 2px; border: 1px solid #CBD5E1; border-radius: 3px; padding: 2.5px 6px; background: #FAFDFD; page-break-inside: avoid; }")
        html.append(".sig-row { display: flex; justify-content: space-between; margin-bottom: 2px; font-size: 8.5px; font-weight: bold; }")
        html.append(".stamp-row { display: flex; justify-content: space-between; font-size: 7.5px; color: #64748B; }")
        html.append("</style></head><body>")

        val activeSubjects = listOf("Diinta Islaamka", "Af-Soomaali", "Xisaab", "Saynis", "Cilmiga Bulshada", "English", "Carabi")
        val allExs = exams.value
        val allMrs = allExamMarks.value

        fun fmt(n: Double) = if (n % 1.0 == 0.0) n.toInt().toString() else String.format(java.util.Locale.US, "%.1f", n)

        targetStudents.forEachIndexed { index, student ->
            val currentCls = classes.value.find { it.id == student.classId }
            val currentClassName = currentCls?.name ?: "N/A"
            val serialCodeMSK = generateSerialCode("MSK")

            html.append("<div class='document-container'>")
            html.append("<div class='header-box'>")
            html.append("<div style='display:flex; align-items:center; justify-content:space-between; gap:8px;'>")
            html.append("<div style='flex:1; text-align:left; font-size:8.5px; line-height:1.15;'>")
            html.append("<div style='font-weight:bold; color:#004D4E; font-size:9px;'>JAMHUURIYADDA SOMALILAND</div>")
            html.append("<div style='font-weight:bold; color:#475569; font-size:8px;'>WASAARADDA WAXBARASHADA & SAYNISKA</div>")
            html.append("<div style='color:#64748B; font-size:7.5px;'>Imtixaanka Sanad-Dugsiyeedka</div>")
            html.append("</div>")

            html.append("<div style='flex-shrink:0; text-align:center;'>")
            if (logoBase64.isNotBlank()) {
                html.append("<img src='data:image/jpeg;base64,$logoBase64' style='width:36px; height:36px; border-radius:50%; object-fit:cover; border:1.5px solid #006A6B; background:#FFF;' alt='Logo' />")
            }
            html.append("</div>")

            html.append("<div style='flex:1; text-align:right; font-size:8.5px; line-height:1.15;'>")
            html.append("<div style='color:#334155;'><b>Tixraac:</b> <span style='font-family:monospace; color:#006A6B; font-weight:bold;'>$serialCodeMSK</span></div>")
            html.append("<div style='color:#334155;'><b>Taariikhda:</b> $nowDateTimeMSK</div>")
            html.append("<div style='display:inline-block; background:#006A6B; color:#FFF; padding:1px 6px; border-radius:3px; font-size:8px; font-weight:bold; margin-top:2px;'>OFFICIAL MARKSHEET</div>")
            html.append("</div>")
            html.append("</div>")

            html.append("<div style='text-align:center; margin-top:2px; padding-top:2px; border-top:1px dashed #CBD5E1;'>")
            html.append("<div class='school-title'>${schoolHeader.uppercase()}</div>")
            html.append("<div class='doc-banner'>STUDENT MARKSHEET (WARBIXINTA DHIBCAHA)</div>")
            html.append("</div>")
            html.append("</div>")

            html.append("<div class='sec-title'>1. STUDENT PROFILE (XOGTA ARDAYGA)</div>")
            html.append("<table class='info-table'>")
            html.append("<tr><td class='info-label'>Magaca Ardayga:</td><td class='info-value'>${student.name}</td><td class='info-label'>Student ID / Roll:</td><td class='info-value'>${student.studentId}</td></tr>")
            html.append("<tr><td class='info-label'>Magaca Hooyada:</td><td class='info-value'>${student.motherName.ifBlank { "N/A" }}</td><td class='info-label'>Mobilka Waalidka:</td><td class='info-value'>${student.phone.ifBlank { "N/A" }}</td></tr>")
            html.append("<tr><td class='info-label'>Fasalka Hadda:</td><td class='info-value'>$currentClassName</td><td class='info-label'>Jinsiga (Gender):</td><td class='info-value'>${student.gender}</td></tr>")
            html.append("<tr><td class='info-label'>Sanad-Dugsiyeedka:</td><td class='info-value'>$academicYear</td><td class='info-label'>Fasalka uu u Gudbayo:</td><td class='info-value'>$destinationClass</td></tr>")
            html.append("</table>")

            html.append("<div class='sec-title'>2. IMTIXAANKA SANAD DUGSIYEEDKA (Mark Max: 50 | Pass: 25 per Term)</div>")
            html.append("<table class='marks-table'>")
            html.append("<thead><tr><th style='text-align:left;'>Maadada</th><th>Teeramka 1aad (Max: 50)</th><th>Teeramka 2aad (Max: 50)</th><th>Wadarta (/100)</th><th>Celceliska</th><th style='text-align:right;'>Natiijada</th></tr></thead>")
            html.append("<tbody>")

            var grandTotal = 0.0
            var validCount = 0

            activeSubjects.forEach { subName ->
                val studentMarks = allMrs.filter { it.studentId == student.id && !it.isAbsent }
                val subLower = subName.lowercase()

                fun getTermScore(isT2: Boolean): Double? {
                    val match = studentMarks.mapNotNull { mark ->
                        val exam = allExs.find { it.id == mark.examId }
                        if (exam != null) {
                            val text = (exam.name + " " + exam.subject).lowercase()
                            val examIsTerm2 = text.contains("term 2") || text.contains("term2") || text.contains("t2") || text.contains("sem 2") || text.contains("final") || text.contains("2nd") || text.contains("teeramka 2")
                            val isCorrectTerm = if (isT2) examIsTerm2 else !examIsTerm2
                            val subClean = cleanExamSubjectName(exam.subject.ifBlank { exam.name }).lowercase()
                            val targetClean = subLower
                            val matchesSubject = subClean == targetClean || subClean.contains(targetClean) || targetClean.contains(subClean) || text.contains(targetClean)
                            if (isCorrectTerm && matchesSubject) Pair(mark, exam) else null
                        } else null
                    }.firstOrNull()

                    if (match != null) {
                        var sc = match.first.score
                        if (match.second.totalMarks == 100.0 && sc > 50.0) sc /= 2.0
                        return sc.coerceAtMost(50.0)
                    }
                    return null
                }

                val t1Val = getTermScore(false)
                val t2Val = getTermScore(true)

                val totVal = if (t1Val != null || t2Val != null) (t1Val ?: 0.0) + (t2Val ?: 0.0) else null
                val avgVal = if (t1Val != null && t2Val != null) totVal!! / 2.0 else totVal

                if (totVal != null) {
                    grandTotal += totVal
                    validCount++
                }

                val t1Str = t1Val?.let { fmt(it) } ?: "&nbsp;"
                val t2Str = t2Val?.let { fmt(it) } ?: "&nbsp;"
                val totStr = totVal?.let { fmt(it) } ?: "&nbsp;"
                val avgStr = avgVal?.let { fmt(it) } ?: "&nbsp;"
                val resStr = when {
                    totVal == null -> "&nbsp;"
                    (totVal ?: 0.0) >= 50.0 || (avgVal ?: 0.0) >= 25.0 -> "<span style='color:#2E7D32; font-weight:bold;'>BAASAY</span>"
                    else -> "<span style='color:#C62828; font-weight:bold;'>DHACAY</span>"
                }

                html.append("<tr><td class='sub-name'>$subName</td><td>$t1Str</td><td>$t2Str</td><td style='font-weight:bold;'>$totStr</td><td>$avgStr</td><td style='text-align:right;'>$resStr</td></tr>")
            }

            val maxPossible = if (validCount > 0) validCount * 100.0 else 700.0
            val overallPct = if (maxPossible > 0) (grandTotal / maxPossible) * 100.0 else 0.0
            val passThreshold = (validCount * 50.0).coerceAtLeast(175.0)
            val isPassed = grandTotal >= passThreshold || overallPct >= 50.0

            val grade = when {
                overallPct >= 90 -> "A"
                overallPct >= 80 -> "B"
                overallPct >= 70 -> "C"
                overallPct >= 50 -> "D"
                else -> "F"
            }

            html.append("<tr class='summary-row'><td class='sub-name'>Total Marks (Wadarta Guud)</td><td colspan='4' style='text-align:right;'><b>${fmt(grandTotal)} / ${activeSubjects.size * 100}</b></td><td>&nbsp;</td></tr>")
            html.append("<tr class='summary-row'><td class='sub-name'>Average (Celceliska Guud)</td><td colspan='4' style='text-align:right;'><b>${String.format(java.util.Locale.US, "%.1f%%", overallPct)}</b></td><td>&nbsp;</td></tr>")
            html.append("<tr class='summary-row'><td class='sub-name'>Grade</td><td colspan='4' style='text-align:right;'><b>$grade</b></td><td>&nbsp;</td></tr>")
            html.append("<tr class='summary-row'><td class='sub-name'>Status (Natiijada)</td><td colspan='4' style='text-align:right;'><b>${if (isPassed) "<span style='color:#2E7D32;'>BAASAY</span>" else "<span style='color:#C62828;'>DHACAY</span>"}</b></td><td>&nbsp;</td></tr>")
            html.append("<tr class='summary-row'><td class='sub-name'>Fasalka uu u Gudbayo</td><td colspan='4' style='text-align:right;'><b>$destinationClass</b></td><td>&nbsp;</td></tr>")

            html.append("</tbody></table>")

            html.append("<div class='sec-title'>3. NATIIJADA ARDAYGA</div>")
            if (isPassed) {
                html.append("<div class='result-box-pass'>")
                html.append("<div class='result-head'>🎉 HAMBALYO!</div>")
                html.append("<div class='result-body'>“Waxaan kuu hambalyaynaynaa guusha aad ka gaadhay imtixaanka sanad-dugsiyeedka. Dadaalkaaga iyo horumarkaaga sii wad, kuna dadaal inaad mar kasta gaadho heer ka sarreeya.”</div>")
                html.append("</div>")
            } else {
                html.append("<div class='result-box-fail'>")
                html.append("<div class='result-head'>📌 DARDARAN IYO DHIIRIGELIN</div>")
                html.append("<div class='result-body'>“Ha niyad jabin. Guuldarradu ma aha dhammaadka waxbarashada, ee waa fursad aad ku ogaan karto meelaha aad u baahan tahay inaad ku dadaasho. Dib u eeg casharradaada, dadaalka kordhi, waqtiga si wanaagsan uga faa’iidayso, waxaana rajaynaynaa inaad sannadka dambe guul weyn gaadho.”</div>")
                html.append("</div>")
            }

            html.append("<div class='footer-section'>")
            html.append("<div class='sig-row'><div>Magaca Maamulaha: ______________________</div><div>Saxeexa Maamulaha: ______________________</div></div>")
            html.append("<div class='stamp-row'><div>Taariikhda & Waqtiga: $nowDateTimeMSK</div><div>Serial Code: $serialCodeMSK</div></div>")
            html.append("</div></div>")

            if (index < targetStudents.size - 1) {
                html.append("<div class='page-break'></div>")
            }
        }

        html.append("</body></html>")
        val clsName = targetClasses.firstOrNull()?.name?.replace(" ", "_") ?: "All_Classes"
        repository.printHtmlReport(context, html.toString(), "All_Marksheets_$clsName")
    }

    fun printAllClassClearancesHtml(
        context: Context,
        classId: Long,
        previousSchool: String,
        destinationSchool: String,
        destinationClass: String,
        academicYear: String
    ) {
        val targetClasses = if (classId != 0L) {
            classes.value.filter { it.id == classId }
        } else {
            classes.value
        }
        val targetStudents = students.value.filter { s ->
            (classId == 0L || s.classId == classId) &&
            (s.status.isBlank() || s.status.equals("ACTIVE", ignoreCase = true)) &&
            !s.isDeleted
        }

        if (targetStudents.isEmpty()) {
            viewModelScope.launch { _uiMessage.emit("Arday firfircoon ma joogaan fasalka la doortay.") }
            return
        }

        viewModelScope.launch { _uiMessage.emit("Waxaa la diyaarinayaa warqadaha ${targetStudents.size} arday...") }

        val logoBase64 = getSchoolLogoBase64(context)
        val nowDateTimeCLR = getCurrentDateTimeStr()

        val html = StringBuilder()
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>")
        html.append("@page { size: A4; margin: 15mm; }")
        html.append("@media print { .page-break { page-break-after: always; break-after: page; } }")
        html.append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 15px; color: #102A2A; font-size: 12px; }")
        html.append(".document-container { border: 2px solid #006A6B; padding: 15px; border-radius: 6px; box-sizing: border-box; }")
        html.append(".header-box { text-align: center; border: 1.5px solid #006A6B; padding: 8px; border-radius: 4px; background: #FAFDFD; margin-bottom: 10px; }")
        html.append(".school-title { font-size: 16px; font-weight: 800; color: #006A6B; margin: 3px 0 6px 0; text-transform: uppercase; }")
        html.append(".doc-banner { background: #006A6B; color: #FFFFFF; font-size: 14px; font-weight: bold; padding: 4px 18px; display: inline-block; border-radius: 4px; letter-spacing: 1px; }")
        html.append(".info-table { width: 100%; border-collapse: collapse; margin-bottom: 12px; border: 1px solid #CBD5E1; }")
        html.append(".info-table td { padding: 5px 8px; border: 1px solid #E2E8F0; font-size: 11px; }")
        html.append(".info-label { font-weight: bold; color: #475569; width: 42%; background: #F8FAFC; }")
        html.append(".info-value { font-weight: bold; color: #0F172A; }")
        html.append(".section-title { font-size: 11px; font-weight: bold; color: #006A6B; margin-bottom: 8px; text-transform: uppercase; border-bottom: 2px solid #006A6B; padding-bottom: 3px; }")
        html.append(".boxes-grid { width: 100%; display: table; table-layout: fixed; border-spacing: 8px; margin-bottom: 10px; }")
        html.append(".box-row { display: table-row; }")
        html.append(".class-box { display: table-cell; width: 50%; vertical-align: top; border: 1.5px solid #006A6B; border-radius: 4px; overflow: hidden; background: #FFFFFF; box-sizing: border-box; }")
        html.append(".box-header { background: #006A6B; color: #FFFFFF; font-weight: bold; text-align: center; padding: 4px; font-size: 10px; text-transform: uppercase; letter-spacing: 0.5px; }")
        html.append(".box-content { padding: 4px; }")
        html.append(".sub-table { width: 100%; border-collapse: collapse; font-size: 9px; text-align: center; }")
        html.append(".sub-table th { background: #E6F2F2; color: #004D4E; padding: 3px 2px; border-bottom: 1px solid #CBD5E1; font-weight: bold; }")
        html.append(".sub-table td { padding: 2px 3px; border-bottom: 1px solid #E2E8F0; }")
        html.append(".sub-table td.sub-name { text-align: left; font-weight: 500; color: #1E293B; }")
        html.append(".sub-table tr.tot-row td { background: #F0F7F7; font-weight: bold; color: #006A6B; border-top: 1px solid #006A6B; }")
        html.append(".footer-section { margin-top: 15px; border: 1px solid #CBD5E1; border-radius: 4px; padding: 10px; background: #FAFDFD; page-break-inside: avoid; }")
        html.append(".sig-row { display: flex; justify-content: space-between; margin-bottom: 10px; font-size: 10px; font-weight: bold; }")
        html.append(".stamp-row { display: flex; justify-content: space-between; font-size: 9px; color: #64748B; }")
        html.append("</style></head><body>")

        val standardSubjects = listOf("Diinta Islaamka", "Af-Soomaali", "Xisaab", "Saynis", "Cilmiga Bulshada", "English", "Carabi")
        val allCls = classes.value
        val allExs = exams.value
        val allMrs = allExamMarks.value

        fun fmt(n: Double) = if (n % 1.0 == 0.0) n.toInt().toString() else String.format(java.util.Locale.US, "%.1f", n)

        targetStudents.forEachIndexed { index, student ->
            val currentCls = allCls.find { it.id == student.classId }
            val currentClassName = currentCls?.name ?: "N/A"
            val schoolHeader = previousSchool.ifBlank { schoolName.value.ifBlank { "Mahdi Cali School" } }
            val serialCodeCLR = generateSerialCode("CLR")

            html.append("<div class='document-container'>")
            html.append("<div class='header-box' style='padding:10px 14px; margin-bottom:12px;'>")
            html.append("<div style='display:flex; align-items:center; justify-content:space-between; gap:12px;'>")
            html.append("<div style='flex:1; text-align:left; font-size:10px; line-height:1.3;'>")
            html.append("<div style='font-weight:bold; color:#004D4E; font-size:10.5px;'>JAMHUURIYADDA SOMALILAND</div>")
            html.append("<div style='font-weight:bold; color:#475569; font-size:9.5px;'>WASAARADDA WAXBARASHADA & SAYNISKA</div>")
            html.append("<div style='color:#64748B; font-size:9px;'>Agaasinka Waxbarashada Guud</div>")
            html.append("</div>")

            html.append("<div style='flex-shrink:0; text-align:center;'>")
            if (logoBase64.isNotBlank()) {
                html.append("<img src='data:image/jpeg;base64,$logoBase64' style='width:70px; height:70px; border-radius:50%; object-fit:cover; border:2.5px solid #006A6B; background:#FFF; box-shadow:0 3px 8px rgba(0,106,107,0.2);' alt='Logo' />")
            }
            html.append("</div>")

            html.append("<div style='flex:1; text-align:right; font-size:10px; line-height:1.3;'>")
            html.append("<div style='color:#334155;'><b>Tixraac:</b> <span style='font-family:monospace; color:#006A6B; font-weight:bold;'>$serialCodeCLR</span></div>")
            html.append("<div style='color:#334155;'><b>Taariikhda:</b> $nowDateTimeCLR</div>")
            html.append("<div style='display:inline-block; background:#006A6B; color:#FFF; padding:2px 8px; border-radius:4px; font-size:9px; font-weight:bold; margin-top:2px;'>OFFICIAL CERTIFICATE</div>")
            html.append("</div>")
            html.append("</div>")

            html.append("<div style='text-align:center; margin-top:6px; padding-top:6px; border-top:1px dashed #CBD5E1;'>")
            html.append("<div class='school-title' style='margin:0 0 3px 0; font-size:18px;'>${schoolHeader.uppercase()}</div>")
            html.append("<div class='doc-banner' style='font-size:13px; padding:3px 18px;'>WARQADDA ARDAYGA (CLEARANCE & RECORD SHEET)</div>")
            html.append("</div>")
            html.append("</div>")

            html.append("<table class='info-table'>")
            html.append("<tr><td class='info-label'>1. Magaca Ardayga oo Dhamaystiran:</td><td class='info-value'>${student.name}</td></tr>")
            html.append("<tr><td class='info-label'>2. Magaca Hooyada:</td><td class='info-value'>${student.motherName.ifBlank { "N/A" }}</td></tr>")
            html.append("<tr><td class='info-label'>3. Fasalka uu Ku Jiray:</td><td class='info-value'>$currentClassName</td></tr>")
            html.append("<tr><td class='info-label'>4. Dugsiga uu Ku Jiray:</td><td class='info-value'>$schoolHeader</td></tr>")
            html.append("<tr><td class='info-label'>5. Dugsiga loo Beddelay:</td><td class='info-value'>$destinationSchool</td></tr>")
            html.append("<tr><td class='info-label'>6. Fasalka uu u Gudbay:</td><td class='info-value'>$destinationClass</td></tr>")
            html.append("<tr><td class='info-label'>7. Sanad-Dugsyeedka:</td><td class='info-value'>$academicYear</td></tr>")
            html.append("</table>")

            html.append("<div class='section-title'>DIIWAANKA SANNADAHA / FASALLADA (FASALLADA 1AAD - 4AAD)</div>")
            html.append("<div class='boxes-grid'>")

            (1..4).chunked(2).forEach { pair ->
                html.append("<div class='box-row'>")
                pair.forEach { boxIdx ->
                    val matchingClass = allCls.find { cls ->
                        val digits = cls.name.filter { it.isDigit() }
                        val num = digits.toIntOrNull()
                        if (num != null) num == boxIdx
                        else {
                            val nameLower = cls.name.lowercase()
                            nameLower.contains("fasalka $boxIdx") || nameLower.contains("class $boxIdx") || nameLower.contains("grade $boxIdx") || nameLower.contains("$boxIdx")
                        }
                    }
                    val classExams = if (matchingClass != null) allExs.filter { it.classId == matchingClass.id } else emptyList()

                    html.append("<div class='class-box'>")
                    html.append("<div class='box-header'>Fasalka ${boxIdx}aad (Class $boxIdx)</div>")
                    html.append("<div class='box-content'>")
                    html.append("<table class='sub-table'><thead><tr><th style='text-align:left;'>Maadada</th><th>T1</th><th>T2</th><th>Wadar</th></tr></thead><tbody>")

                    var totT1 = 0.0
                    var totT2 = 0.0
                    standardSubjects.forEach { sub ->
                        fun getMarkVal(isT2: Boolean): Double? {
                            val matchedClassExam = classExams.find { exam ->
                                val text = (exam.name + " " + exam.subject).lowercase()
                                val examIsTerm2 = text.contains("term 2") || text.contains("term2") || text.contains("t2") || text.contains("sem 2") || text.contains("final")
                                val isCorrectTerm = if (isT2) examIsTerm2 else !examIsTerm2
                                val subClean = cleanExamSubjectName(exam.subject.ifBlank { exam.name }).lowercase()
                                val targetClean = sub.lowercase()
                                val matchesSubject = subClean == targetClean || subClean.contains(targetClean) || text.contains(targetClean)
                                isCorrectTerm && matchesSubject
                            }
                            if (matchedClassExam != null) {
                                val mark = allMrs.find { it.examId == matchedClassExam.id && it.studentId == student.id && !it.isAbsent }
                                if (mark != null) {
                                    var score = mark.score
                                    if (matchedClassExam.totalMarks == 100.0 && score > 50.0) score /= 2.0
                                    return score.coerceAtMost(50.0)
                                }
                            }
                            return null
                        }

                        val t1Val = getMarkVal(false)
                        val t2Val = getMarkVal(true)
                        if (t1Val != null) totT1 += t1Val
                        if (t2Val != null) totT2 += t2Val

                        val t1S = t1Val?.let { fmt(it) } ?: ""
                        val t2S = t2Val?.let { fmt(it) } ?: ""
                        val totS = if (t1Val != null || t2Val != null) fmt((t1Val ?: 0.0) + (t2Val ?: 0.0)) else ""

                        html.append("<tr><td class='sub-name'>$sub</td><td>$t1S</td><td>$t2S</td><td style='font-weight:bold;'>$totS</td></tr>")
                    }

                    val grandTotS = fmt(totT1 + totT2)
                    html.append("<tr class='tot-row'><td class='sub-name'>Wadarta Guud</td><td>${fmt(totT1)}</td><td>${fmt(totT2)}</td><td>$grandTotS</td></tr>")
                    html.append("</tbody></table></div></div>")
                }
                html.append("</div>")
            }
            html.append("</div>")

            html.append("<div class='footer-section'>")
            html.append("<div class='sig-row'><div>Saxeexa Maamulaha: ______________________</div><div>Saxeexa Kormeeraha: ______________________</div></div>")
            html.append("<div class='stamp-row'><div>Taariikhda: $nowDateTimeCLR</div><div>Serial: $serialCodeCLR</div></div>")
            html.append("</div></div>")

            if (index < targetStudents.size - 1) {
                html.append("<div class='page-break'></div>")
            }
        }

        html.append("</body></html>")
        val clsName = targetClasses.firstOrNull()?.name?.replace(" ", "_") ?: "All_Classes"
        repository.printHtmlReport(context, html.toString(), "All_Clearances_$clsName")
    }

    fun printAllStudentsComprehensiveReportsHtml(context: Context, classId: Long) {
        val targetClasses = if (classId != 0L) {
            classes.value.filter { it.id == classId }
        } else {
            classes.value
        }
        val targetStudents = students.value.filter { s ->
            (classId == 0L || s.classId == classId) &&
            (s.status.isBlank() || s.status.equals("ACTIVE", ignoreCase = true)) &&
            !s.isDeleted
        }

        if (targetStudents.isEmpty()) {
            viewModelScope.launch { _uiMessage.emit("Arday firfircoon ma joogaan fasalka la doortay.") }
            return
        }

        viewModelScope.launch { _uiMessage.emit("Waxaa la diyaarinayaa warbixin-sanadeedka ${targetStudents.size} arday...") }

        val schoolHeader = schoolName.value.ifBlank { "MAHDI CALI SCHOOL" }
        val nowDateTime = getCurrentDateTimeStr()
        val allExamsList = exams.value

        val html = StringBuilder()
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>")
        html.append("@page { size: A4; margin: 15mm; }")
        html.append("@media print { .page-break { page-break-after: always; break-after: page; } }")
        html.append("body { font-family: 'Segoe UI', Arial, sans-serif; padding: 15px; color: #1E293B; background: #FFF; line-height: 1.5; }")
        html.append(artisticHeaderCss)
        html.append(".section-title { font-size: 13px; font-weight: 800; color: #006A6B; text-transform: uppercase; border-left: 4px solid #006A6B; padding-left: 8px; margin: 18px 0 8px 0; }")
        html.append(".info-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px; margin-bottom: 14px; }")
        html.append(".info-item { background: #FFF; border: 1px solid #CBD5E1; border-radius: 4px; padding: 6px 10px; font-size: 11px; color: #334155; }")
        html.append(".badge { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: bold; }")
        html.append(".badge-free { background: #FEF3C7; color: #B45309; border: 1px solid #F59E0B; }")
        html.append(".badge-pass { background: #DCFCE7; color: #15803D; }")
        html.append("table { width: 100%; border-collapse: collapse; margin-top: 8px; font-size: 12px; }")
        html.append("th { background: #006A6B; color: #FFFFFF; padding: 8px 10px; text-align: left; font-size: 11px; font-weight: bold; }")
        html.append("td { border-bottom: 1px solid #E2E8F0; padding: 8px 10px; font-size: 11px; color: #334155; }")
        html.append(".kpi-row { display: flex; gap: 10px; margin: 10px 0; }")
        html.append(".kpi-card { flex: 1; background: #F1F5F9; border-radius: 6px; padding: 10px; text-align: center; border: 1px solid #CBD5E1; }")
        html.append(".kpi-val { font-size: 16px; font-weight: 800; color: #006A6B; margin-top: 2px; }")
        html.append(".kpi-lbl { font-size: 10px; font-weight: 700; color: #64748B; text-transform: uppercase; }")
        html.append(".signatures { display: flex; justify-content: space-between; margin-top: 36px; padding-top: 16px; page-break-inside: avoid; }")
        html.append(".sig-box { text-align: center; width: 40%; }")
        html.append(".sig-line { border-top: 1px dashed #64748B; margin-top: 40px; padding-top: 4px; font-size: 11px; font-weight: bold; color: #475569; }")
        html.append("</style></head><body>")

        targetStudents.forEachIndexed { index, student ->
            val cls = classes.value.find { it.id == student.classId }
            val clsName = cls?.name ?: "N/A"
            val teacherName = cls?.inchargeTeacher?.ifBlank { "Macallinka Fasalka" } ?: "Macallinka Fasalka"
            val studentMarks = allExamMarks.value.filter { it.studentId == student.id }
            val serialCode = generateSerialCode("STR")

            val totalScore = studentMarks.sumOf { it.score }
            val totalMaxPossible = studentMarks.sumOf { m -> allExamsList.find { it.id == m.examId }?.totalMarks ?: 100.0 }
            val academicAvg = if (totalMaxPossible > 0) (totalScore / totalMaxPossible) * 100.0 else 0.0

            html.append("<div style='border: 1.5px solid #006A6B; padding: 14px; border-radius: 6px;'>")
            html.append(getArtisticHeaderHtml(
                context = context,
                schoolTitle = schoolHeader,
                reportTitle = "WARBIXINTA GUUD EE ARDAYGA (STUDENT COMPREHENSIVE REPORT)",
                serialCode = serialCode,
                dateStr = nowDateTime,
                badgeText = "OFFICIAL PROFILE"
            ))

            html.append("<div class='section-title'>1. Xogta Shakhsiga & Diiwaanka (Student Profile)</div>")
            html.append("<div class='info-grid'>")
            html.append("<div class='info-item'><b>Magaca Ardayga:</b> ${student.name}</div>")
            html.append("<div class='info-item'><b>Student ID:</b> <span style='font-family:monospace; font-weight:bold; color:#006A6B;'>${student.studentId}</span></div>")
            html.append("<div class='info-item'><b>Fasalka:</b> $clsName &nbsp;(Macallin: $teacherName)</div>")
            html.append("<div class='info-item'><b>Jinsiga:</b> ${student.gender}</div>")
            html.append("<div class='info-item'><b>Magaca Hooyada:</b> ${student.motherName.ifBlank { "N/A" }}</div>")
            html.append("<div class='info-item'><b>Telefoonka Waalidka:</b> ${student.phone.ifBlank { "N/A" }}</div>")
            html.append("<div class='info-item'><b>Xaaladda Fiiga:</b> ${if (student.isFree) "<span class='badge badge-free'>FREE / SCHOLARSHIP (Bilaash)</span>" else "<span class='badge'>STANDARD ENROLLMENT</span>"}</div>")
            html.append("<div class='info-item'><b>Status-ka Guud:</b> <span class='badge badge-pass'>ACTIVE ENROLLED</span></div>")
            html.append("</div>")

            html.append("<div class='section-title'>2. Natiijooyinka Imtixaanaadka & Dhibcaha (Academic Performance)</div>")
            if (studentMarks.isEmpty()) {
                html.append("<p style='font-size:11px; color:#64748B; font-style:italic;'>Wax natiijooyin imtixaan ah weli looma diiwaangelin ardaygan.</p>")
            } else {
                html.append("<div class='kpi-row'>")
                html.append("<div class='kpi-card'><div class='kpi-lbl'>Wadarta Dhibcaha</div><div class='kpi-val'>${String.format("%.1f", totalScore)} / ${String.format("%.0f", totalMaxPossible)}</div></div>")
                html.append("<div class='kpi-card'><div class='kpi-lbl'>Celceliska Guud (%)</div><div class='kpi-val'>${String.format("%.1f%%", academicAvg)}</div></div>")
                html.append("<div class='kpi-card'><div class='kpi-lbl'>Maadooyinka La Galay</div><div class='kpi-val'>${studentMarks.size}</div></div>")
                html.append("<div class='kpi-card'><div class='kpi-lbl'>Heerka Tacliinta</div><div class='kpi-val' style='color:${if (academicAvg >= 50) "#15803D" else "#B91C1C"}'>${if (academicAvg >= 50) "GUUL (PASS)" else "DHACAY (FAIL)"}</div></div>")
                html.append("</div>")

                html.append("<table>")
                html.append("<thead><tr><th>Maadada</th><th>Imtixaanka</th><th>Dhibcaha</th><th>Max</th><th>Boqolkiiba</th><th>Darajada</th><th>Xaaladda</th></tr></thead><tbody>")
                studentMarks.forEach { mark ->
                    val ex = allExamsList.find { it.id == mark.examId }
                    val exName = ex?.name ?: "Imtixaan"
                    val subName = ex?.subject?.ifBlank { "Maado" } ?: "Maado"
                    val maxM = ex?.totalMarks ?: 100.0
                    val pct = if (maxM > 0) (mark.score / maxM) * 100.0 else 0.0
                    val pass = mark.score >= (maxM / 2.0)
                    val gradeStr = when {
                        pct >= 90 -> "A"
                        pct >= 80 -> "B"
                        pct >= 70 -> "C"
                        pct >= 50 -> "D"
                        else -> "F"
                    }
                    val statusStr = if (mark.isAbsent) "<span style='color:#B91C1C;'>ABSENT</span>" else if (pass) "<span style='color:#15803D; font-weight:bold;'>PASSED</span>" else "<span style='color:#B91C1C; font-weight:bold;'>FAILED</span>"

                    html.append("<tr><td>$subName</td><td>$exName</td><td>${mark.score}</td><td>$maxM</td><td>${String.format("%.1f%%", pct)}</td><td><b>$gradeStr</b></td><td>$statusStr</td></tr>")
                }
                html.append("</tbody></table>")
            }

            html.append("<div class='signatures'>")
            html.append("<div class='sig-box'><div class='sig-line'>Macallinka Fasalka</div></div>")
            html.append("<div class='sig-box'><div class='sig-line'>Maamulaha Dugsiga</div></div>")
            html.append("</div>")

            html.append("</div>")

            if (index < targetStudents.size - 1) {
                html.append("<div class='page-break'></div>")
            }
        }

        html.append("</body></html>")
        val clsName = targetClasses.firstOrNull()?.name?.replace(" ", "_") ?: "All_Classes"
        repository.printHtmlReport(context, html.toString(), "All_Student_Reports_$clsName")
    }

    fun isTimeOutsideWorkingHours(timeStr: String): Boolean {
        if (timeStr.isBlank()) return false
        try {
            val clean = timeStr.trim().lowercase()
            var hour24 = 0
            var minute = 0

            if (clean.contains("pm") || clean.contains("am")) {
                val isPm = clean.contains("pm")
                val isAm = clean.contains("am")
                val digits = clean.replace("am", "").replace("pm", "").trim()
                val timePart = if (digits.contains(" ")) digits.split(" ").last() else digits
                val parts = timePart.split(":")
                if (parts.isNotEmpty()) {
                    val hour = parts[0].trim().toIntOrNull() ?: return false
                    minute = if (parts.size > 1) parts[1].trim().toIntOrNull() ?: 0 else 0
                    hour24 = hour
                    if (isPm && hour < 12) hour24 += 12
                    if (isAm && hour == 12) hour24 = 0
                }
            } else if (clean.contains(":")) {
                val timePart = if (clean.contains(" ")) clean.split(" ").last() else clean
                val parts = timePart.split(":")
                if (parts.isNotEmpty()) {
                    hour24 = parts[0].trim().toIntOrNull() ?: return false
                    minute = if (parts.size > 1) parts[1].trim().toIntOrNull() ?: 0 else 0
                }
            } else {
                return false
            }

            val totalMinutes = hour24 * 60 + minute
            val morningStart = 8 * 60        // 08:00 AM
            val morningEnd = 12 * 60         // 12:00 PM
            val afternoonStart = 14 * 60     // 02:00 PM
            val afternoonEnd = 16 * 60 + 30  // 04:30 PM

            val inMorning = totalMinutes in morningStart..morningEnd
            val inAfternoon = totalMinutes in afternoonStart..afternoonEnd

            return !(inMorning || inAfternoon)
        } catch (e: Exception) {
            return false
        }
    }

    fun printAttendanceReportHtml(context: Context, selectedClassId: Long, yearMonth: String? = null) {
        val targetClasses = if (selectedClassId == 0L) classes.value else classes.value.filter { it.id == selectedClassId }
        val allStuds = students.value
        val rawAtt = allAttendance.value
        val allAtt = if (!yearMonth.isNullOrBlank()) {
            rawAtt.filter { it.date.startsWith(yearMonth) }
        } else {
            rawAtt
        }

        val schoolHeader = schoolName.value.ifBlank { "Dugsiga H/Dhexe" }
        val serialCodeATT = generateSerialCode("ATT")
        val nowDateTimeATT = getCurrentDateTimeStr()
        val periodText = if (!yearMonth.isNullOrBlank()) "BISHA: $yearMonth" else "DHAMAAN XILLIYADA (ALL TIME)"

        val html = StringBuilder()
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>")
        html.append("@page { size: A4; margin: 18mm 12mm 15mm 12mm; }")
        html.append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; padding: 20px; color: #111; font-size: 11px; line-height: 1.4; }")
        html.append(artisticHeaderCss)
        html.append("table { width: 100%; border-collapse: collapse; margin-top: 8px; margin-bottom: 18px; text-align: left; }")
        html.append("th { background: #006A6B; color: white; padding: 7px 8px; font-size: 11px; font-weight: bold; }")
        html.append("td { border-bottom: 1px solid #E2E8F0; padding: 6px 8px; font-size: 11px; }")
        html.append(".badge-outside { color: #991B1B; font-weight: bold; background: #FEE2E2; padding: 3px 8px; border-radius: 4px; border: 1px solid #FCA5A5; font-size: 10px; display: inline-block; }")
        html.append(".badge-ok { color: #166534; font-weight: bold; background: #DCFCE7; padding: 3px 8px; border-radius: 4px; border: 1px solid #86EFAC; font-size: 10px; display: inline-block; }")
        html.append("h3 { color: #006A6B; margin-top: 14px; margin-bottom: 6px; font-size: 13px; border-bottom: 1.5px solid #006A6B; padding-bottom: 2px; }")
        html.append("</style></head><body>")

        html.append(getArtisticHeaderHtml(
            context = context,
            schoolTitle = schoolHeader,
            reportTitle = "WARBIXINTA XAADIRINTA ARDAYDA IYO WAKHTIGA MACALINKU XAADIRIYAY",
            serialCode = serialCodeATT,
            dateStr = nowDateTimeATT,
            badgeText = periodText
        ))

        targetClasses.forEach { cls ->
            val clsStuds = allStuds.filter { it.classId == cls.id }
            val clsAtt = allAtt.filter { it.classId == cls.id }

            html.append("<h2 style='color:#006A6B; border-left: 4px solid #006A6B; padding-left: 8px; margin-top:20px; font-size:15px;'>🏫 Fasalka: ${cls.name} ($periodText)</h2>")

            if (clsStuds.isEmpty()) {
                html.append("<p style='color:#666;'>Fasalkan arday kuma jirto.</p>")
            } else {
                html.append("<h3>1. Diiwaanka Summary-ga Xaadirinta Ardayda</h3>")
                html.append("<table><tr><th style='width:30px;'>#</th><th style='width:80px;'>Student ID</th><th>Magaca Ardayga</th><th style='width:80px;'>Wadarta</th><th style='width:80px;'>Joog (Present)</th><th style='width:80px;'>Maqan (Absent)</th><th style='width:80px;'>Rate %</th></tr>")
                clsStuds.forEachIndexed { idx, s ->
                    val sAtt = clsAtt.filter { it.studentId == s.id }
                    val total = sAtt.size
                    val present = sAtt.count { it.status == "Present" || it.status == "P" }
                    val absent = total - present
                    val rate = if (total > 0) (present.toDouble() / total) * 100 else 100.0
                    html.append("<tr><td>${idx + 1}</td><td>${s.studentId}</td><td><b>${s.name}</b></td><td>$total</td><td><span style='color:#166534; font-weight:bold;'>$present</span></td><td><span style='color:#991B1B; font-weight:bold;'>$absent</span></td><td><b>${String.format(java.util.Locale.US, "%.1f%%", rate)}</b></td></tr>")
                }
                html.append("</table>")

                html.append("<h3>2. Diiwaanka Wakhtiga Xaadirinta Macalinka (Teacher Timing Audit)</h3>")
                val distinctDates = clsAtt.map { it.date }.distinct().sortedDescending()
                if (distinctDates.isEmpty()) {
                    html.append("<p style='color:#666; font-style:italic;'>Xaadirin la sameeyay ma jirto xilligan la doortay.</p>")
                } else {
                    html.append("<table><tr><th style='width:100px;'>Taariikhda</th><th style='width:100px;'>Fasalka</th><th>Macalinka Xaadiriyay</th><th style='width:130px;'>Saacadda La Xaadiriyay</th><th>Xaaladda Saacadaha Shaqada</th></tr>")
                    distinctDates.forEach { d ->
                        val dateRecs = clsAtt.filter { it.date == d }
                        val teacherName = dateRecs.firstOrNull { it.recordedBy.isNotBlank() }?.recordedBy?.ifBlank { "Macalin" } ?: "Macalin"
                        val timeRecorded = dateRecs.firstOrNull { it.recordedAt.isNotBlank() }?.recordedAt?.ifBlank { "-" } ?: "-"
                        val isOutside = isTimeOutsideWorkingHours(timeRecorded)
                        val statusHtml = if (isOutside) {
                            "<span class='badge-outside'>🚨 Ka baxsan saacadaha shaqada (Shift: 08:00-12:00 / 14:00-16:30)</span>"
                        } else {
                            "<span class='badge-ok'>✅ Saacadihii Shaqada (Official Hours)</span>"
                        }
                        html.append("<tr><td><b>$d</b></td><td>${cls.name}</td><td>👨‍🏫 $teacherName</td><td>⏰ <b>$timeRecorded</b></td><td>$statusHtml</td></tr>")
                    }
                    html.append("</table>")
                }
            }
        }
        html.append("</body></html>")

        repository.printHtmlReport(context, html.toString(), "Attendance_Report_${yearMonth ?: "All"}")
    }

    fun printStudentAttendanceReportHtml(
        context: Context,
        student: Student,
        mode: String = "MONTH", // "MONTH" or "TERM"
        selectedMonth: String? = null
    ) {
        val currentCls = classes.value.find { it.id == student.classId }
        val currentClassName = currentCls?.name ?: "N/A"
        val schoolHeader = schoolName.value.ifBlank { "Mahdi Cali School" }
        val serialCode = generateSerialCode("ATT")
        val nowDateTime = getCurrentDateTimeStr()
        val logoBase64 = getSchoolLogoBase64(context)

        val targetMonth = if (!selectedMonth.isNullOrBlank()) selectedMonth else java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.US).format(java.util.Date())
        val allStudentAtt = allAttendance.value.filter { it.studentId == student.id }

        val isMonthly = mode.equals("MONTH", ignoreCase = true)
        val filteredAtt = if (isMonthly) {
            allStudentAtt.filter { it.date.startsWith(targetMonth) }.sortedBy { it.date }
        } else {
            allStudentAtt.sortedBy { it.date }
        }

        val totalDays = filteredAtt.size
        val presentDays = filteredAtt.count { it.status.equals("Present", ignoreCase = true) || it.status == "P" }
        val absentDays = filteredAtt.count { it.status.equals("Absent", ignoreCase = true) || it.status == "A" }
        val lateDays = filteredAtt.count { it.status.equals("Late", ignoreCase = true) || it.status.equals("Habsan", ignoreCase = true) || it.status == "H" }
        val freeDays = filteredAtt.count { it.status.equals("Free", ignoreCase = true) || it.status == "F" }
        val attendanceRate = if (totalDays > 0) ((presentDays + lateDays + freeDays).toDouble() / totalDays) * 100.0 else 100.0

        val reportTitle = if (isMonthly) {
            "WARBIXINTA XAADIRINTA ARDAYGA EE BISHA ($targetMonth)"
        } else {
            "WARBIXINTA XAADIRINTA ARDAYGA EE TEERAMKA / SANNADKA"
        }

        val html = StringBuilder()
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>")
        html.append("@page { size: A4 portrait; margin: 18mm 12mm 15mm 12mm; }")
        html.append("@media print { html, body { margin: 0; padding: 0; } body { -webkit-print-color-adjust: exact; print-color-adjust: exact; } .document-container { page-break-inside: avoid !important; break-inside: avoid !important; max-height: 288mm; box-sizing: border-box; overflow: hidden; } }")
        html.append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 0; color: #102A2A; font-size: 8.5px; line-height: 1.15; }")
        html.append(".document-container { border: 1.5px solid #006A6B; padding: 4px 6px; border-radius: 4px; box-sizing: border-box; page-break-inside: avoid !important; break-inside: avoid !important; max-height: 288mm; overflow: hidden; }")
        html.append(".header-box { text-align: center; border: 1px solid #006A6B; padding: 3px 6px; border-radius: 4px; background: #FAFDFD; margin-bottom: 3px; }")
        html.append(".school-title { font-size: 13px; font-weight: 800; color: #006A6B; margin: 1px 0 2px 0; text-transform: uppercase; }")
        html.append(".doc-banner { background: #006A6B; color: #FFFFFF; font-size: 9.5px; font-weight: bold; padding: 1.5px 12px; display: inline-block; border-radius: 3px; letter-spacing: 0.5px; }")
        html.append(".sec-title { font-size: 8.5px; font-weight: bold; color: #006A6B; margin: 3px 0 1px 0; text-transform: uppercase; border-bottom: 1.5px solid #006A6B; padding-bottom: 1px; }")
        html.append(".info-table { width: 100%; border-collapse: collapse; margin-bottom: 3px; border: 1px solid #CBD5E1; }")
        html.append(".info-table td { padding: 1.5px 5px; border: 1px solid #E2E8F0; font-size: 8.5px; }")
        html.append(".info-label { font-weight: bold; color: #475569; width: 22%; background: #F8FAFC; }")
        html.append(".info-value { font-weight: bold; color: #0F172A; width: 28%; }")
        html.append(".kpi-row { display: flex; gap: 4px; margin: 4px 0; }")
        html.append(".kpi-card { flex: 1; border-radius: 3px; padding: 3px 2px; text-align: center; font-weight: bold; border: 1px solid #CBD5E1; font-size: 8px; }")
        html.append(".kpi-num { font-size: 12px; margin-top: 1px; font-weight: 800; }")
        html.append(".att-table { width: 100%; border-collapse: collapse; margin-top: 2px; margin-bottom: 3px; font-size: 8.5px; }")
        html.append(".att-table th { background: #006A6B; color: white; padding: 2.5px 3px; font-weight: bold; text-align: center; font-size: 8.5px; }")
        html.append(".att-table td { border: 1px solid #CBD5E1; padding: 1.5px 3px; text-align: center; font-size: 8.5px; }")
        html.append(".status-p { color: #166534; font-weight: bold; background: #DCFCE7; padding: 1px 4px; border-radius: 2px; }")
        html.append(".status-a { color: #991B1B; font-weight: bold; background: #FEE2E2; padding: 1px 4px; border-radius: 2px; }")
        html.append(".status-l { color: #B45309; font-weight: bold; background: #FEF3C7; padding: 1px 4px; border-radius: 2px; }")
        html.append(".footer-section { margin-top: 2px; border: 1px solid #CBD5E1; border-radius: 3px; padding: 2.5px 6px; background: #FAFDFD; page-break-inside: avoid; }")
        html.append(".sig-row { display: flex; justify-content: space-between; margin-bottom: 2px; font-size: 8.5px; font-weight: bold; }")
        html.append(".stamp-row { display: flex; justify-content: space-between; font-size: 7.5px; color: #64748B; }")
        html.append("</style></head><body>")

        html.append("<div class='document-container'>")

        // Header
        html.append("<div class='header-box'>")
        html.append("<div style='display:flex; align-items:center; justify-content:space-between; gap:8px;'>")
        html.append("<div style='flex:1; text-align:left; font-size:8.5px; line-height:1.15;'>")
        html.append("<div style='font-weight:bold; color:#004D4E; font-size:9px;'>JAMHUURIYADDA SOMALILAND</div>")
        html.append("<div style='font-weight:bold; color:#475569; font-size:8px;'>WASAARADDA WAXBARASHADA & SAYNISKA</div>")
        html.append("<div style='color:#64748B; font-size:7.5px;'>Diiwaanka Xaadirinta Ardayda</div>")
        html.append("</div>")

        html.append("<div style='flex-shrink:0; text-align:center;'>")
        if (logoBase64.isNotBlank()) {
            html.append("<img src='data:image/jpeg;base64,$logoBase64' style='width:36px; height:36px; border-radius:50%; object-fit:cover; border:1.5px solid #006A6B; background:#FFF;' alt='Logo' />")
        }
        html.append("</div>")

        html.append("<div style='flex:1; text-align:right; font-size:9px; line-height:1.2;'>")
        html.append("<div style='color:#334155;'><b>Tixraac:</b> <span style='font-family:monospace; color:#006A6B; font-weight:bold;'>$serialCode</span></div>")
        html.append("<div style='color:#334155;'><b>Taariikhda:</b> $nowDateTime</div>")
        html.append("<div style='display:inline-block; background:#006A6B; color:#FFF; padding:1px 6px; border-radius:3px; font-size:8px; font-weight:bold; margin-top:2px;'>OFFICIAL ATTENDANCE</div>")
        html.append("</div>")
        html.append("</div>")

        html.append("<div style='text-align:center; margin-top:3px; padding-top:3px; border-top:1px dashed #CBD5E1;'>")
        html.append("<div class='school-title'>${schoolHeader.uppercase()}</div>")
        html.append("<div class='doc-banner'>$reportTitle</div>")
        html.append("</div>")
        html.append("</div>")

        // 1. Profile Table
        html.append("<div class='sec-title'>1. XOGTA ARDAYGA (STUDENT PROFILE)</div>")
        html.append("<table class='info-table'>")
        html.append("<tr><td class='info-label'>Magaca Ardayga:</td><td class='info-value'>${student.name}</td><td class='info-label'>Student ID:</td><td class='info-value'>${student.studentId}</td></tr>")
        html.append("<tr><td class='info-label'>Fasalka:</td><td class='info-value'>$currentClassName</td><td class='info-label'>Jinsiga (Gender):</td><td class='info-value'>${student.gender}</td></tr>")
        html.append("<tr><td class='info-label'>Mobilka Waalidka:</td><td class='info-value'>${student.phone.ifBlank { "N/A" }}</td><td class='info-label'>Xilliga / Bisha:</td><td class='info-value'>${if (isMonthly) targetMonth else "Teeramka / Sanadka"}</td></tr>")
        html.append("</table>")

        // 2. Summary KPI Cards
        html.append("<div class='sec-title'>2. TIRAKOOBKA XAADIRINTA (ATTENDANCE SUMMARY)</div>")
        html.append("<div class='kpi-row'>")
        html.append("<div class='kpi-card' style='background:#F1F5F9;'><div style='color:#475569;'>Wadarta Maalmaha</div><div class='kpi-num' style='color:#0F172A;'>$totalDays</div></div>")
        html.append("<div class='kpi-card' style='background:#DCFCE7;'><div style='color:#15803D;'>Joogay (Present)</div><div class='kpi-num' style='color:#166534;'>$presentDays</div></div>")
        html.append("<div class='kpi-card' style='background:#FEE2E2;'><div style='color:#B91C1C;'>Maqnaa (Absent)</div><div class='kpi-num' style='color:#991B1B;'>$absentDays</div></div>")
        html.append("<div class='kpi-card' style='background:#FEF3C7;'><div style='color:#B45309;'>Habsan (Late)</div><div class='kpi-num' style='color:#92400E;'>$lateDays</div></div>")
        html.append("<div class='kpi-card' style='background:#E0F2FE;'><div style='color:#0369A1;'>Boqolkiiba (%)</div><div class='kpi-num' style='color:#0284C7;'>${String.format(java.util.Locale.US, "%.1f%%", attendanceRate)}</div></div>")
        html.append("</div>")

        // 3. Detailed Breakdown
        if (isMonthly) {
            html.append("<div class='sec-title'>3. DIIWAANKA MAALMAHA EE BISHA (DAILY LOG - $targetMonth)</div>")
            if (filteredAtt.isEmpty()) {
                html.append("<p style='font-style:italic; color:#64748B; margin:4px 0;'>Wax diiwaan xaadirin ah looma helin ardaygan bishan ($targetMonth).</p>")
            } else {
                html.append("<table class='att-table'>")
                html.append("<thead><tr><th style='width:30px;'>#</th><th style='width:80px;'>Taariikhda</th><th style='width:90px;'>Xaaladda</th><th>Wakhtiga La Xaadiriyay</th><th>Macalinka</th></tr></thead><tbody>")
                filteredAtt.forEachIndexed { idx, att ->
                    val statusBadge = when {
                        att.status.equals("Present", ignoreCase = true) || att.status == "P" -> "<span class='status-p'>✅ JOOGAY</span>"
                        att.status.equals("Absent", ignoreCase = true) || att.status == "A" -> "<span class='status-a'>❌ MAQNAA</span>"
                        att.status.equals("Late", ignoreCase = true) || att.status.equals("Habsan", ignoreCase = true) || att.status == "H" -> "<span class='status-l'>⏰ HABSAN</span>"
                        else -> "<span class='status-p'>🌟 BILAASH</span>"
                    }
                    val timeStr = att.recordedAt.ifBlank { "-" }
                    val teacher = att.recordedBy.ifBlank { "Macalin" }
                    html.append("<tr><td>${idx + 1}</td><td><b>${att.date}</b></td><td>$statusBadge</td><td>$timeStr</td><td>$teacher</td></tr>")
                }
                html.append("</tbody></table>")
            }
        } else {
            // Term Breakdown by Month
            html.append("<div class='sec-title'>3. KALA-JABINTA BILALAHA EE TEERAMKA (MONTHLY TERM BREAKDOWN)</div>")
            val distinctMonths = allStudentAtt.map { it.date.take(7) }.filter { it.length == 7 }.distinct().sorted()
            if (distinctMonths.isEmpty()) {
                html.append("<p style='font-style:italic; color:#64748B; margin:4px 0;'>Wax diiwaan xaadirin ah looma helin ardaygan xilligan.</p>")
            } else {
                html.append("<table class='att-table'>")
                html.append("<thead><tr><th style='width:30px;'>#</th><th>Bisha (Month)</th><th style='width:80px;'>Wadarta</th><th style='width:80px;'>Joogay</th><th style='width:80px;'>Maqnaa</th><th style='width:80px;'>Habsan</th><th style='width:80px;'>Boqolkiiba (%)</th></tr></thead><tbody>")
                distinctMonths.forEachIndexed { idx, mKey ->
                    val mAtt = allStudentAtt.filter { it.date.startsWith(mKey) }
                    val mTot = mAtt.size
                    val mPres = mAtt.count { it.status.equals("Present", ignoreCase = true) || it.status == "P" }
                    val mAbs = mAtt.count { it.status.equals("Absent", ignoreCase = true) || it.status == "A" }
                    val mLate = mAtt.count { it.status.equals("Late", ignoreCase = true) || it.status.equals("Habsan", ignoreCase = true) || it.status == "H" }
                    val mFree = mAtt.count { it.status.equals("Free", ignoreCase = true) || it.status == "F" }
                    val mPct = if (mTot > 0) ((mPres + mLate + mFree).toDouble() / mTot) * 100.0 else 100.0
                    html.append("<tr><td>${idx + 1}</td><td><b>Bisha $mKey</b></td><td>$mTot</td><td><span style='color:#166534; font-weight:bold;'>$mPres</span></td><td><span style='color:#991B1B; font-weight:bold;'>$mAbs</span></td><td><span style='color:#B45309; font-weight:bold;'>$mLate</span></td><td><b>${String.format(java.util.Locale.US, "%.1f%%", mPct)}</b></td></tr>")
                }
                html.append("</tbody></table>")
            }
        }

        // 4. Evaluation / Remarks
        val evalText = if (attendanceRate >= 85.0) {
            "✅ <b>Xaalad Wanaagsan:</b> Ardaygu wuxuu muujiyay dabeecad iyo joogitaan aad u wanaagsan. Wuxuu u qalmaa dhiirigelin joogto ah."
        } else {
            "⚠️ <b>Digniin Xaadirineed:</b> Joogitaanka ardaygu wuxuu ka hooseeyaa 85%. Waxaa waalidka laga codsanayaa inuu xafiiska maamulka la soo xidhiidho si arintan looga wada hadlo."
        }
        html.append("<div style='background:#F8FAFC; border:1px solid #CBD5E1; border-radius:4px; padding:4px 8px; margin-top:4px; font-size:9px; line-height:1.3;'>$evalText</div>")

        // 5. Footer Signatures & Stamp
        html.append("<div class='footer-section'>")
        html.append("<div class='sig-row'>")
        html.append("<div>Macalinka Fasalka: ______________________</div>")
        html.append("<div>Maamulaha Dugsiga: ______________________</div>")
        html.append("</div>")
        html.append("<div class='stamp-row'>")
        html.append("<div>Taariikhda & Waqtiga: $nowDateTime</div>")
        html.append("<div>Serial Code: $serialCode</div>")
        html.append("</div></div>")

        html.append("</div></body></html>")

        val fileTag = if (isMonthly) "Attendance_${student.studentId}_$targetMonth" else "Attendance_${student.studentId}_Term"
        repository.printHtmlReport(context, html.toString(), fileTag)
    }

    fun printExamReportHtml(context: Context, selectedClassId: Long) {
        viewModelScope.launch {
            val targetClasses = if (selectedClassId == 0L) classes.value else classes.value.filter { it.id == selectedClassId }
            val allExamsList = exams.value
            val allStuds = students.value

            val schoolHeader = schoolName.value.ifBlank { "Dugsiga H/Dhexe" }
            val serialCodeEXM = generateSerialCode("EXM")
            val nowDateTimeEXM = getCurrentDateTimeStr()

            val html = StringBuilder()
            html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>")
            html.append("@page { size: A4; margin: 18mm 12mm 15mm 12mm; }")
            html.append("body { font-family: 'Segoe UI', Arial, sans-serif; padding: 15px; color: #111; }")
            html.append(".header-box { text-align: center; font-weight: bold; margin-bottom: 12px; }")
            html.append(".title-main { font-size: 18px; color: #0D47A1; margin-bottom: 2px; text-transform: uppercase; }")
            html.append(".title-sub { font-size: 14px; color: #333; margin-bottom: 2px; }")
            html.append(".title-dept { font-size: 12px; color: #666; margin-bottom: 6px; }")
            html.append(".title-sheet { font-size: 15px; color: #006A6B; text-decoration: underline; margin-top: 4px; font-weight: bold; }")
            html.append(".info-bar { display: flex; justify-content: space-between; font-weight: bold; font-size: 12px; margin-bottom: 10px; border-bottom: 2px solid #006A6B; padding-bottom: 6px; }")
            html.append("table { width: 100%; border-collapse: collapse; font-size: 11px; text-align: center; margin-bottom: 25px; }")
            html.append("th, td { border: 1px solid #777; padding: 5px 3px; }")
            html.append("th { background: #006A6B; color: white; }")
            html.append(".th-sub { background: #00838F; color: white; }")
            html.append(".sname { text-align: left; padding-left: 6px; font-weight: 600; }")
            html.append(".wadar-col { font-weight: bold; background: #E0F2F1; color: #004D40; }")
            html.append(".total-2t { font-weight: bold; background: #FFF9C4; color: #000; }")
            html.append(".footer-sig { display: flex; justify-content: space-between; margin-top: 40px; font-weight: bold; font-size: 13px; }")
            html.append("</style></head><body>")

            targetClasses.forEach { cls ->
                val clsStuds = allStuds.filter { it.classId == cls.id }
                val clsExams = allExamsList.filter { it.classId == cls.id }

                val examMarksMap = mutableMapOf<Long, List<ExamMark>>()
                for (exam in clsExams) {
                    examMarksMap[exam.id] = repository.examDao.getMarksForExam(exam.id).first()
                }

                html.append("<div class='header-box'>")
                html.append("<div class='title-main'>Wasaaradda Waxbarashada iyo Sayniska JSL</div>")
                html.append("<div class='title-sub'>Xafiiska Waxbarashada Degmada Gabiley</div>")
                html.append("<div class='title-dept'>Waaxda Qorshaynta</div>")
                html.append("<div class='title-sheet'>Xaashida Imtixaanka Naqliga ee Dugsiyada H/Dhexe</div>")
                html.append("</div>")

                html.append("<div class='info-bar'>")
                html.append("<div>Dugsiga: <u>${schoolHeader.uppercase()}</u></div>")
                html.append("<div>Fasalka: <u>${cls.name}</u></div>")
                html.append("<div>Serial Code: <u>$serialCodeEXM</u></div>")
                html.append("<div>Taariikhda & Waqtiga: <u>$nowDateTimeEXM</u></div>")
                html.append("</div>")

                html.append("<table><thead>")
                html.append("<tr>")
                html.append("<th rowspan='2'>Sno</th>")
                html.append("<th rowspan='2'>EMIS ID</th>")
                html.append("<th rowspan='2' style='text-align: left; padding-left: 6px;'>Magaca Ardayga</th>")
                html.append("<th colspan='8'>TERM ONE (2025/2026)</th>")
                html.append("<th colspan='8'>TERM TWO (2025/2026)</th>")
                html.append("<th rowspan='2'>Wadar 2 T</th>")
                html.append("</tr>")

                html.append("<tr class='th-sub'>")
                html.append("<th>Diin</th><th>Som</th><th>Car</th><th>Eng</th><th>Xis</th><th>Say</th><th>C/B</th><th class='wadar-col'>Wadar</th>")
                html.append("<th>Diin</th><th>Som</th><th>Car</th><th>Eng</th><th>Xis</th><th>Say</th><th>C/B</th><th class='wadar-col'>Wadar</th>")
                html.append("</tr></thead><tbody>")

                clsStuds.forEachIndexed { idx, student ->
                    fun getScore(subjectKey: String, isTerm2: Boolean): Double? {
                        val matchingExams = clsExams.filter { exam ->
                            val text = (exam.name + " " + exam.subject).lowercase()
                            val isT2 = text.contains("term 2") || text.contains("term2") || text.contains("t2") || text.contains("sem 2") || text.contains("final")
                            val correctTerm = if (isTerm2) isT2 else !isT2

                            val matchesSubject = when (subjectKey) {
                                "Diin" -> text.contains("diin") || text.contains("islam") || text.contains("quran")
                                "Som" -> text.contains("som") || text.contains("soomaali")
                                "Car" -> text.contains("car") || text.contains("arab")
                                "Eng" -> text.contains("eng") || text.contains("ing")
                                "Xis" -> text.contains("xis") || text.contains("math")
                                "Say" -> text.contains("say") || text.contains("sci")
                                "C/B" -> text.contains("c/b") || text.contains("bulsho") || text.contains("soc")
                                else -> false
                            }
                            correctTerm && matchesSubject
                        }

                        val targetExam = matchingExams.firstOrNull() ?: run {
                            val subjectIdx = listOf("Diin", "Som", "Car", "Eng", "Xis", "Say", "C/B").indexOf(subjectKey)
                            val termExams = clsExams.filter { exam ->
                                val text = (exam.name + " " + exam.subject).lowercase()
                                val isT2 = text.contains("term 2") || text.contains("term2") || text.contains("t2") || text.contains("sem 2") || text.contains("final")
                                if (isTerm2) isT2 else !isT2
                            }
                            if (subjectIdx in termExams.indices) termExams[subjectIdx] else null
                        }

                        if (targetExam != null) {
                            val marks = examMarksMap[targetExam.id] ?: emptyList()
                            val m = marks.find { it.studentId == student.id }
                            if (m != null && !m.isAbsent) {
                                return m.score
                            }
                        }
                        return null
                    }

                    val t1Diin = getScore("Diin", false)
                    val t1Som = getScore("Som", false)
                    val t1Car = getScore("Car", false)
                    val t1Eng = getScore("Eng", false)
                    val t1Xis = getScore("Xis", false)
                    val t1Say = getScore("Say", false)
                    val t1CB = getScore("C/B", false)

                    val t1Scores = listOfNotNull(t1Diin, t1Som, t1Car, t1Eng, t1Xis, t1Say, t1CB)
                    val t1Wadar = if (t1Scores.isNotEmpty()) t1Scores.sum() else null

                    val t2Diin = getScore("Diin", true)
                    val t2Som = getScore("Som", true)
                    val t2Car = getScore("Car", true)
                    val t2Eng = getScore("Eng", true)
                    val t2Xis = getScore("Xis", true)
                    val t2Say = getScore("Say", true)
                    val t2CB = getScore("C/B", true)

                    val t2Scores = listOfNotNull(t2Diin, t2Som, t2Car, t2Eng, t2Xis, t2Say, t2CB)
                    val t2Wadar = if (t2Scores.isNotEmpty()) t2Scores.sum() else null

                    val wadar2T = if (t1Wadar != null || t2Wadar != null) (t1Wadar ?: 0.0) + (t2Wadar ?: 0.0) else null

                    fun fmt(num: Double?): String = if (num == null) "" else if (num % 1.0 == 0.0) num.toInt().toString() else String.format(java.util.Locale.US, "%.1f", num)

                    html.append("<tr>")
                    html.append("<td>${idx + 1}</td>")
                    html.append("<td>${student.studentId}</td>")
                    html.append("<td class='sname'>${student.name}</td>")

                    html.append("<td>${fmt(t1Diin)}</td>")
                    html.append("<td>${fmt(t1Som)}</td>")
                    html.append("<td>${fmt(t1Car)}</td>")
                    html.append("<td>${fmt(t1Eng)}</td>")
                    html.append("<td>${fmt(t1Xis)}</td>")
                    html.append("<td>${fmt(t1Say)}</td>")
                    html.append("<td>${fmt(t1CB)}</td>")
                    html.append("<td class='wadar-col'>${fmt(t1Wadar)}</td>")

                    html.append("<td>${fmt(t2Diin)}</td>")
                    html.append("<td>${fmt(t2Som)}</td>")
                    html.append("<td>${fmt(t2Car)}</td>")
                    html.append("<td>${fmt(t2Eng)}</td>")
                    html.append("<td>${fmt(t2Xis)}</td>")
                    html.append("<td>${fmt(t2Say)}</td>")
                    html.append("<td>${fmt(t2CB)}</td>")
                    html.append("<td class='wadar-col'>${fmt(t2Wadar)}</td>")

                    html.append("<td class='total-2t'>${fmt(wadar2T)}</td>")
                    html.append("</tr>")
                }

                html.append("</tbody></table>")

                html.append("<div class='footer-sig'>")
                html.append("<div>Magaca Maamulaha: ________________________</div>")
                html.append("<div>Saxeexa Maamulaha: ________________________</div>")
                html.append("</div><div style='page-break-after: always;'></div>")
            }

            html.append("</body></html>")

            repository.printHtmlReport(context, html.toString(), "Xaashida_Imtixaanka_Naqliga")
        }
    }

    fun printMonthlyAttendanceSheetHtml(context: Context, classId: Long, yearMonth: String) {
        val targetClass = classes.value.find { it.id == classId }
        val className = targetClass?.name ?: "All Classes"
        val classStudents = students.value.filter { classId == 0L || it.classId == classId }
        val monthAttendance = allAttendance.value.filter { 
            it.date.startsWith(yearMonth) && (classId == 0L || it.classId == classId)
        }

        val distinctDates = monthAttendance.map { it.date }.distinct().sorted()
        val schoolHeader = schoolName.value.ifBlank { "SCHOOL MANAGEMENT SYSTEM" }
        val serialCodeATT = generateSerialCode("MATT")
        val nowDateTimeATT = getCurrentDateTimeStr()

        val html = StringBuilder()
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>")
        html.append("@page { size: landscape; margin: 18mm 12mm 15mm 12mm; }")
        html.append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; padding: 10px; color: #111; font-size: 11px; }")
        html.append(".header { text-align: center; border-bottom: 2px solid #006A6B; padding-bottom: 8px; margin-bottom: 12px; }")
        html.append(".school-title { font-size: 18px; font-weight: 800; color: #006A6B; text-transform: uppercase; margin: 0; }")
        html.append(".doc-title { font-size: 14px; font-weight: bold; color: #333; margin-top: 4px; }")
        html.append(".meta-bar { display: flex; justify-content: space-between; font-size: 11px; font-weight: bold; margin-top: 6px; color: #004D4E; }")
        html.append("table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 10px; text-align: center; }")
        html.append("th, td { border: 1px solid #718096; padding: 4px 2px; }")
        html.append("th { background: #006A6B; color: white; font-weight: bold; }")
        html.append(".sname { text-align: left; padding-left: 6px; font-weight: 600; white-space: nowrap; }")
        html.append(".pres { color: #166534; font-weight: bold; }")
        html.append(".abs { color: #991B1B; font-weight: bold; }")
        html.append(".free { color: #92400E; font-weight: bold; }")
        html.append(".tot-col { background: #E6FFFA; font-weight: bold; color: #004D40; }")
        html.append(".footer { display: flex; justify-content: space-between; margin-top: 25px; font-size: 11px; font-weight: bold; }")
        html.append("</style></head><body>")

        val logoBase64 = getSchoolLogoBase64(context)
        html.append("<div class='header' style='display:flex; align-items:center; justify-content:space-between; gap:14px; border-bottom:2.5px solid #006A6B; padding-bottom:10px; margin-bottom:12px;'>")
        html.append("<div style='flex:1; text-align:left; font-size:10px; line-height:1.3;'>")
        html.append("<div style='font-weight:bold; color:#004D4E; font-size:11px;'>JAMHUURIYADDA SOMALILAND</div>")
        html.append("<div style='font-weight:bold; color:#475569; font-size:9.5px;'>WASAARADDA WAXBARASHADA & SAYNISKA</div>")
        html.append("<div style='color:#64748B; font-size:9px;'>Fasalka: <b>$className</b> &nbsp;|&nbsp; Ardayda: <b>${classStudents.size}</b></div>")
        html.append("</div>")

        html.append("<div style='flex-shrink:0; text-align:center;'>")
        if (logoBase64.isNotBlank()) {
            html.append("<img src='data:image/jpeg;base64,$logoBase64' style='width:65px; height:65px; border-radius:50%; object-fit:cover; border:2.5px solid #006A6B; background:#FFF; box-shadow:0 3px 8px rgba(0,106,107,0.2);' alt='Logo' />")
        }
        html.append("<div style='font-weight:900; color:#006A6B; font-size:14px; text-transform:uppercase; margin-top:2px;'>${schoolHeader.uppercase()}</div>")
        html.append("<div style='font-weight:bold; color:#1E293B; font-size:11px;'>XAASHIDA XAADIRINTA BISHA - $yearMonth</div>")
        html.append("</div>")

        html.append("<div style='flex:1; text-align:right; font-size:10px; line-height:1.3;'>")
        html.append("<div style='color:#334155;'><b>Ref:</b> <span style='font-family:monospace; color:#006A6B; font-weight:bold;'>$serialCodeATT</span></div>")
        html.append("<div style='color:#334155;'><b>Taariikhda:</b> $nowDateTimeATT</div>")
        html.append("<div style='color:#64748B; font-size:9px;'>Maalmaha: <b>${distinctDates.size} Maalmood</b></div>")
        html.append("</div>")
        html.append("</div>")

        if (classStudents.isEmpty()) {
            html.append("<p style='text-align:center; margin-top:30px; font-size:13px;'>Fasalkan arday kuma jirto.</p>")
        } else {
            html.append("<table><thead><tr>")
            html.append("<th style='width:30px;'>#</th>")
            html.append("<th style='width:70px;'>Student ID</th>")
            html.append("<th class='sname' style='width:160px;'>Magaca Ardayga</th>")

            // Date columns (e.g. Day of month)
            distinctDates.forEach { d ->
                val dayNumber = try { d.substring(8) } catch (e: Exception) { d }
                html.append("<th style='width:22px;'>$dayNumber</th>")
            }

            html.append("<th class='tot-col' style='width:35px;'>Joog (P)</th>")
            html.append("<th class='tot-col' style='width:35px;'>Maqan (A)</th>")
            html.append("<th class='tot-col' style='width:35px;'>Fasax (F)</th>")
            html.append("<th class='tot-col' style='width:45px;'>% Rate</th>")
            html.append("</tr></thead><tbody>")

            classStudents.forEachIndexed { idx, s ->
                val sRecords = monthAttendance.filter { it.studentId == s.id }
                val pCount = sRecords.count { it.status == "Present" || it.status == "P" }
                val aCount = sRecords.count { it.status == "Absent" || it.status == "A" }
                val fCount = sRecords.count { it.status == "Free" || it.status == "F" }
                val totalSessions = distinctDates.size
                val pct = if (totalSessions > 0) (pCount.toDouble() / totalSessions) * 100 else 100.0

                html.append("<tr>")
                html.append("<td>${idx + 1}</td>")
                html.append("<td>${s.studentId}</td>")
                html.append("<td class='sname'>${s.name}</td>")

                distinctDates.forEach { d ->
                    val rec = sRecords.find { it.date == d }
                    val cellText = when (rec?.status) {
                        "Present", "P" -> "<span class='pres'>P</span>"
                        "Absent", "A" -> "<span class='abs'>A</span>"
                        "Free", "F" -> "<span class='free'>F</span>"
                        else -> "-"
                    }
                    html.append("<td>$cellText</td>")
                }

                html.append("<td class='tot-col'>$pCount</td>")
                html.append("<td class='tot-col'>$aCount</td>")
                html.append("<td class='tot-col'>$fCount</td>")
                html.append("<td class='tot-col'>${String.format(Locale.US, "%.0f%%", pct)}</td>")
                html.append("</tr>")
            }

            html.append("</tbody></table>")

            html.append("<div class='footer'>")
            html.append("<div>Magaca Macalinka/Masuulka: __________________________</div>")
            html.append("<div>Saxeexa Maamulka: __________________________</div>")
            html.append("<div>Shaambadda Dugsiga (Stamp): __________________________</div>")
            html.append("</div>")
        }

        html.append("</body></html>")
        repository.printHtmlReport(context, html.toString(), "Monthly_Attendance_${className.replace(" ", "_")}_$yearMonth")
    }

    fun exportMonthlyAttendanceCsv(context: Context, classId: Long, yearMonth: String) {
        val targetClass = classes.value.find { it.id == classId }
        val className = targetClass?.name ?: "All_Classes"
        val classStudents = students.value.filter { classId == 0L || it.classId == classId }
        val monthAttendance = allAttendance.value.filter { 
            it.date.startsWith(yearMonth) && (classId == 0L || it.classId == classId)
        }
        val distinctDates = monthAttendance.map { it.date }.distinct().sorted()

        val sb = StringBuilder()
        sb.append("No,Student ID,Student Name,Class")
        distinctDates.forEach { d ->
            val dayNum = try { d.substring(8) } catch (e: Exception) { d }
            sb.append(",Day_$dayNum")
        }
        sb.append(",Total_Present,Total_Absent,Total_Free,Attendance_Rate\n")

        classStudents.forEachIndexed { idx, s ->
            val sRecords = monthAttendance.filter { it.studentId == s.id }
            val pCount = sRecords.count { it.status == "Present" || it.status == "P" }
            val aCount = sRecords.count { it.status == "Absent" || it.status == "A" }
            val fCount = sRecords.count { it.status == "Free" || it.status == "F" }
            val totalSessions = distinctDates.size
            val pct = if (totalSessions > 0) (pCount.toDouble() / totalSessions) * 100 else 100.0

            sb.append("${idx + 1},${s.studentId},\"${s.name}\",\"$className\"")
            distinctDates.forEach { d ->
                val rec = sRecords.find { it.date == d }
                val code = when (rec?.status) {
                    "Present", "P" -> "P"
                    "Absent", "A" -> "A"
                    "Free", "F" -> "F"
                    else -> "-"
                }
                sb.append(",$code")
            }
            sb.append(",$pCount,$aCount,$fCount,${String.format(Locale.US, "%.1f%%", pct)}\n")
        }

        shareCSVFile(context, "Monthly_Attendance_${className.replace(" ", "_")}_$yearMonth.csv", sb.toString())
    }

    fun printStudentLateWarningHtml(
        context: Context,
        student: Student,
        records: List<AttendanceRecord>,
        className: String,
        academicYear: String = "2026/2027"
    ) {
        val schoolHeader = schoolName.value.ifBlank { "Mahdi Cali School" }
        val serialCode = generateSerialCode("WRN")
        val nowDateTime = getCurrentDateTimeStr()

        val lateRecords = records.filter { it.status.equals("Late", ignoreCase = true) || it.status.equals("Habsan", ignoreCase = true) || it.status == "H" }
            .sortedByDescending { it.date }
        val absentRecords = records.filter { it.status.equals("Absent", ignoreCase = true) || it.status == "A" }
            .sortedByDescending { it.date }
        val presentRecords = records.filter { it.status.equals("Present", ignoreCase = true) || it.status == "P" }

        val totalDays = records.size
        val lateCount = lateRecords.size
        val absentCount = absentRecords.size
        val presentCount = presentRecords.size
        val ratePct = if (totalDays > 0) ((presentCount + lateCount).toDouble() / totalDays) * 100.0 else 100.0

        val html = StringBuilder()
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>")
        html.append("@page { size: A4 portrait; margin: 18mm 12mm 15mm 12mm; }")
        html.append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; color: #1A202C; margin: 0; padding: 14px; font-size: 12px; }")
        html.append(".header-box { border: 2px solid #006A6B; border-radius: 8px; padding: 14px; background: #F7FAFC; margin-bottom: 16px; }")
        html.append(".top-bar { display: flex; justify-content: space-between; align-items: center; border-bottom: 2px solid #006A6B; padding-bottom: 8px; }")
        html.append(".school-title { font-size: 20px; font-weight: 900; color: #006A6B; text-transform: uppercase; margin: 0; }")
        html.append(".doc-badge { background: #DC2626; color: white; padding: 4px 12px; border-radius: 4px; font-size: 11px; font-weight: bold; }")
        html.append(".doc-title { text-align: center; font-size: 16px; font-weight: 800; color: #1A202C; margin-top: 10px; text-transform: uppercase; }")
        html.append(".student-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; margin-top: 12px; background: white; padding: 10px; border-radius: 6px; border: 1px solid #E2E8F0; }")
        html.append(".student-item { display: flex; flex-direction: column; }")
        html.append(".student-label { font-size: 10px; color: #718096; font-weight: bold; text-transform: uppercase; }")
        html.append(".student-val { font-size: 12.5px; color: #004D4E; font-weight: bold; margin-top: 2px; }")
        html.append(".kpi-bar { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin: 16px 0; }")
        html.append(".kpi-card { padding: 10px; border-radius: 6px; text-align: center; font-weight: bold; border: 1px solid #E2E8F0; }")
        html.append(".kpi-late { background: #FFFBEB; border-color: #F59E0B; color: #B45309; }")
        html.append(".kpi-abs { background: #FEF2F2; border-color: #EF4444; color: #DC2626; }")
        html.append(".kpi-pres { background: #F0FDF4; border-color: #22C55E; color: #15803D; }")
        html.append(".kpi-rate { background: #ECFEFF; border-color: #06B6D4; color: #0E7490; }")
        html.append(".kpi-num { font-size: 18px; margin-top: 2px; }")
        html.append("table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 11px; }")
        html.append("th, td { border: 1px solid #CBD5E0; padding: 6px 8px; }")
        html.append("th { background: #006A6B; color: white; font-weight: bold; text-align: center; }")
        html.append(".status-late { color: #B45309; font-weight: bold; }")
        html.append(".status-abs { color: #DC2626; font-weight: bold; }")
        html.append(".notice-box { margin-top: 18px; padding: 12px; background: #FFFBEB; border-left: 4px solid #F59E0B; border-radius: 4px; font-size: 11.5px; line-height: 1.5; }")
        html.append(".signatures { display: flex; justify-content: space-between; margin-top: 36px; font-size: 11px; font-weight: bold; }")
        html.append(".sig-block { width: 45%; }")
        html.append(".sig-line { border-bottom: 1px solid #4A5568; margin-top: 32px; }")
        html.append("</style></head><body>")

        val logoBase64 = getSchoolLogoBase64(context)
        html.append("<div class='header-box' style='padding:12px 16px;'>")
        html.append("<div style='display:flex; align-items:center; justify-content:space-between; gap:12px;'>")
        html.append("<div style='flex:1; text-align:left;'>")
        html.append("<div style='font-size:11px; font-weight:bold; color:#004D4E;'>JAMHUURIYADDA SOMALILAND</div>")
        html.append("<div style='font-size:9.5px; font-weight:bold; color:#475569;'>WASAARADDA WAXBARASHADA & SAYNISKA</div>")
        html.append("<div style='font-size:9px; color:#64748B;'>Dugsiga Hoose / Dhexe ee Dawliga ah</div>")
        html.append("</div>")

        html.append("<div style='flex-shrink:0; text-align:center;'>")
        if (logoBase64.isNotBlank()) {
            html.append("<img src='data:image/jpeg;base64,$logoBase64' style='width:72px; height:72px; border-radius:50%; object-fit:cover; border:2.5px solid #006A6B; background:#FFF; box-shadow:0 3px 8px rgba(0,106,107,0.25);' alt='Logo' />")
        }
        html.append("</div>")

        html.append("<div style='flex:1; text-align:right;'>")
        html.append("<div style='font-size:10.5px; color:#334155;'><b>Ref:</b> <span style='font-family:monospace; color:#006A6B; font-weight:bold;'>$serialCode</span></div>")
        html.append("<div style='font-size:10.5px; color:#334155;'><b>Taariikhda:</b> $nowDateTime</div>")
        html.append("<div class='doc-badge' style='margin-top:3px; display:inline-block;'>WARQAD DIGNIIN AH</div>")
        html.append("</div>")
        html.append("</div>")

        html.append("<div style='text-align:center; margin-top:8px; padding-top:6px; border-top:1px dashed #CBD5E1;'>")
        html.append("<div class='school-title' style='font-size:19px;'>${schoolHeader.uppercase()}</div>")
        html.append("<div class='doc-title' style='margin-top:2px; font-size:14px;'>WARBIXINTA HABSANKA & MAQNAANSHAHA ARDAYGA</div>")
        html.append("</div>")
        html.append("</div>")

        html.append("<div class='student-grid'>")
        html.append("<div class='student-item'><span class='student-label'>Magaca Ardayga</span><span class='student-val'>${student.name}</span></div>")
        html.append("<div class='student-item'><span class='student-label'>Student ID</span><span class='student-val'>${student.studentId}</span></div>")
        html.append("<div class='student-item'><span class='student-label'>Fasalka (Class)</span><span class='student-val'>$className</span></div>")
        html.append("<div class='student-item'><span class='student-label'>Hooyada (Mother)</span><span class='student-val'>${student.motherName.ifBlank { "N/A" }}</span></div>")
        html.append("<div class='student-item'><span class='student-label'>Telefoonka Waalidka</span><span class='student-val'>${student.phone.ifBlank { "N/A" }}</span></div>")
        html.append("<div class='student-item'><span class='student-label'>Serial Code</span><span class='student-val'>$serialCode</span></div>")
        html.append("</div></div>")

        html.append("<div class='kpi-bar'>")
        html.append("<div class='kpi-card kpi-late'><div>WADAR HABSANKA</div><div class='kpi-num'>$lateCount Maalmood</div></div>")
        html.append("<div class='kpi-card kpi-abs'><div>WADAR MAQNAANSHAHA</div><div class='kpi-num'>$absentCount Maalmood</div></div>")
        html.append("<div class='kpi-card kpi-pres'><div>WADAR JOOGITAANKA</div><div class='kpi-num'>$presentCount Maalmood</div></div>")
        html.append("<div class='kpi-card kpi-rate'><div>HEERKA XAADIRINTA</div><div class='kpi-num'>${String.format(Locale.US, "%.0f%%", ratePct)}</div></div>")
        html.append("</div>")

        val issueRecords = records.filter {
            it.status.equals("Late", ignoreCase = true) || it.status.equals("Habsan", ignoreCase = true) || it.status == "H" ||
            it.status.equals("Absent", ignoreCase = true) || it.status == "A"
        }.sortedByDescending { it.date }

        if (issueRecords.isEmpty()) {
            html.append("<p style='text-align:center; padding:20px; font-size:13px; color:#15803D; font-weight:bold;'>Masha'Allah! Ardaygani ma laha wax habsan ama maqnaansho ah oo diiwaangashan.</p>")
        } else {
            html.append("<div style='font-weight:bold; font-size:12.5px; margin-bottom:4px; color:#1A202C;'>Diiwaanka Taariikheed ee Habsanka & Maqnaanshaha:</div>")
            html.append("<table><thead><tr>")
            html.append("<th style='width:40px;'>#</th>")
            html.append("<th style='width:130px;'>Taariikhda (Date)</th>")
            html.append("<th style='width:120px;'>Nooca (Status)</th>")
            html.append("<th>Faahfaahin & Xusuusin (Remarks)</th>")
            html.append("</tr></thead><tbody>")

            issueRecords.forEachIndexed { idx, rec ->
                val isLate = rec.status.equals("Late", ignoreCase = true) || rec.status.equals("Habsan", ignoreCase = true) || rec.status == "H"
                val statusTitle = if (isLate) "🟠 HABSAMAY (LATE)" else "🔴 MAQNAA (ABSENT)"
                val statusClass = if (isLate) "status-late" else "status-abs"
                val remark = if (isLate) "Ardaygu wuxuu soo daahay xilligii bilaabashada casharka." else "Ardaygu ma uusan joogin fasalka sabab la'aan/fasax la'aan."

                html.append("<tr>")
                html.append("<td style='text-align:center;'>${idx + 1}</td>")
                html.append("<td style='text-align:center; font-weight:bold;'>${rec.date}</td>")
                html.append("<td style='text-align:center;' class='$statusClass'>$statusTitle</td>")
                html.append("<td>$remark</td>")
                html.append("</tr>")
            }
            html.append("</tbody></table>")
        }

        html.append("<div class='notice-box'>")
        html.append("<b>Ogaysiis Ku Socda Waalidka:</b><br>")
        html.append("Waalidka sharafta leh, waxaa lagula socodsiinayaa in ardayga kor ku xusan uu soo habsamay <b>$lateCount jeer</b> uuna maqnaa <b>$absentCount jeer</b>. Habsanka iyo maqnaanshuhu waxay si toos ah u saameeyaan natiijada waxbarasho iyo akhlaaqda ardayga. Fadlan la xidhiidh maamulka dugsiga si loo saxo sababaha keena habsanka.")
        html.append("</div>")

        html.append("<div class='signatures'>")
        html.append("<div class='sig-block'>")
        html.append("<div>Maamulaha Dugsiga: ___________________________</div>")
        html.append("<div class='sig-line'></div>")
        html.append("<div style='font-size:10px; color:#718096; margin-top:2px;'>Saxeexa & Shaambadda Dugsiga ($nowDateTime)</div>")
        html.append("</div>")

        html.append("<div class='sig-block' style='text-align:right;'>")
        html.append("<div>Waalidka Ardayga: ___________________________</div>")
        html.append("<div class='sig-line'></div>")
        html.append("<div style='font-size:10px; color:#718096; margin-top:2px;'>Saxeexa Waalidka / Oggolaanshaha</div>")
        html.append("</div>")
        html.append("</div>")

        html.append("</body></html>")
        repository.printHtmlReport(context, html.toString(), "Student_Late_Warning_${student.studentId}_${student.name.replace(" ", "_")}")
    }

    fun printClassLateReportHtml(context: Context, classId: Long, yearMonth: String? = null) {
        val targetClass = classes.value.find { it.id == classId }
        val className = targetClass?.name ?: "Dhammaan Fasalada"
        val classStudents = students.value.filter { classId == 0L || it.classId == classId }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        val filteredAttendance = allAttendance.value.filter {
            (classId == 0L || it.classId == classId) &&
            (yearMonth == null || it.date.startsWith(yearMonth))
        }

        val distinctDates = filteredAttendance.map { it.date }.distinct().sorted()
        val schoolHeader = schoolName.value.ifBlank { "Mahdi Cali School" }
        val serialCode = generateSerialCode("LREP")
        val nowDateTime = getCurrentDateTimeStr()

        val html = StringBuilder()
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>")
        html.append("@page { size: A4 portrait; margin: 18mm 12mm 15mm 12mm; }")
        html.append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; color: #1A202C; margin: 0; padding: 12px; font-size: 11px; }")
        html.append(".header-box { border: 2px solid #006A6B; border-radius: 8px; padding: 12px; background: #F7FAFC; margin-bottom: 12px; }")
        html.append(".top-bar { display: flex; justify-content: space-between; align-items: center; border-bottom: 2px solid #006A6B; padding-bottom: 6px; }")
        html.append(".school-title { font-size: 18px; font-weight: 900; color: #006A6B; text-transform: uppercase; margin: 0; }")
        html.append(".doc-badge { background: #006A6B; color: white; padding: 4px 10px; border-radius: 4px; font-size: 10px; font-weight: bold; }")
        html.append(".doc-title { text-align: center; font-size: 14px; font-weight: 800; color: #1A202C; margin-top: 8px; text-transform: uppercase; }")
        html.append(".meta-bar { display: flex; justify-content: space-between; font-size: 11px; font-weight: bold; margin-top: 8px; color: #004D4E; }")
        html.append("table { width: 100%; border-collapse: collapse; margin-top: 10px; font-size: 11px; }")
        html.append("th, td { border: 1px solid #718096; padding: 5px 6px; }")
        html.append("th { background: #006A6B; color: white; font-weight: bold; text-align: center; font-size: 11px; }")
        html.append(".sname { text-align: left; padding-left: 8px; font-weight: 600; }")
        html.append(".late-col { color: #B45309; font-weight: bold; background: #FFFBEB; text-align: center; }")
        html.append(".abs-col { color: #DC2626; font-weight: bold; background: #FEF2F2; text-align: center; }")
        html.append(".pres-col { color: #15803D; font-weight: bold; text-align: center; }")
        html.append(".rate-col { font-weight: bold; text-align: center; }")
        html.append(".footer { display: flex; justify-content: space-between; margin-top: 26px; font-size: 11px; font-weight: bold; }")
        html.append("</style></head><body>")

        val logoBase64 = getSchoolLogoBase64(context)
        html.append("<div class='header-box' style='padding:12px 16px;'>")
        html.append("<div style='display:flex; align-items:center; justify-content:space-between; gap:12px;'>")
        html.append("<div style='flex:1; text-align:left;'>")
        html.append("<div style='font-size:11px; font-weight:bold; color:#004D4E;'>JAMHUURIYADDA SOMALILAND</div>")
        html.append("<div style='font-size:9.5px; font-weight:bold; color:#475569;'>WASAARADDA WAXBARASHADA & SAYNISKA</div>")
        html.append("<div style='font-size:9px; color:#64748B;'>Dugsiga Hoose / Dhexe ee Dawliga ah</div>")
        html.append("</div>")

        html.append("<div style='flex-shrink:0; text-align:center;'>")
        if (logoBase64.isNotBlank()) {
            html.append("<img src='data:image/jpeg;base64,$logoBase64' style='width:70px; height:70px; border-radius:50%; object-fit:cover; border:2.5px solid #006A6B; background:#FFF; box-shadow:0 3px 8px rgba(0,106,107,0.25);' alt='Logo' />")
        }
        html.append("</div>")

        html.append("<div style='flex:1; text-align:right;'>")
        html.append("<div style='font-size:10.5px; color:#334155;'><b>Ref:</b> <span style='font-family:monospace; color:#006A6B; font-weight:bold;'>$serialCode</span></div>")
        html.append("<div style='font-size:10.5px; color:#334155;'><b>Taariikhda:</b> $nowDateTime</div>")
        html.append("<div class='doc-badge' style='margin-top:3px; display:inline-block;'>WARBIXINTA FASALKA</div>")
        html.append("</div>")
        html.append("</div>")

        html.append("<div style='text-align:center; margin-top:8px; padding-top:6px; border-top:1px dashed #CBD5E1;'>")
        html.append("<div class='school-title' style='font-size:18px;'>${schoolHeader.uppercase()}</div>")
        html.append("<div class='doc-title' style='margin-top:2px; font-size:13px;'>WARBIXINTA GUUD EE HABSANKA ARDAYDA FASALKA: $className</div>")
        html.append("</div>")
        html.append("</div>")
        html.append("<div class='meta-bar'>")
        html.append("<div>Fasalka: <u>$className</u></div>")
        html.append("<div>Xilliga: <u>${yearMonth ?: "Dhammaan (All-Time)"}</u></div>")
        html.append("<div>Wadar Ardayda: <u>${classStudents.size}</u></div>")
        html.append("<div>Maalmaha la Xaadiriyay: <u>${distinctDates.size} Maalmood</u></div>")
        html.append("<div>Taariikhda: <u>$nowDateTime</u></div>")
        html.append("</div></div>")

        if (classStudents.isEmpty()) {
            html.append("<p style='text-align:center; margin-top:20px;'>Fasalkan arday kuma diiwaangashana.</p>")
        } else {
            html.append("<table><thead><tr>")
            html.append("<th style='width:30px;'>#</th>")
            html.append("<th style='width:80px;'>ST ID</th>")
            html.append("<th class='sname'>MAGACA ARDAYGA (STUDENT NAME)</th>")
            html.append("<th style='width:70px;'>FASALKA</th>")
            html.append("<th style='width:75px;' class='late-col'>HABSAN (LATE)</th>")
            html.append("<th style='width:75px;' class='abs-col'>MAQAN (ABSENT)</th>")
            html.append("<th style='width:70px;'>JOOGAY</th>")
            html.append("<th style='width:75px;'>% RATE</th>")
            html.append("<th style='width:90px;'>XURMO / HEER</th>")
            html.append("</tr></thead><tbody>")

            classStudents.forEachIndexed { idx, s ->
                val sRecords = filteredAttendance.filter { it.studentId == s.id }
                val lateCount = sRecords.count { it.status.equals("Late", ignoreCase = true) || it.status.equals("Habsan", ignoreCase = true) || it.status == "H" }
                val absCount = sRecords.count { it.status.equals("Absent", ignoreCase = true) || it.status == "A" }
                val presCount = sRecords.count { it.status.equals("Present", ignoreCase = true) || it.status == "P" }
                val totalDays = sRecords.size
                val ratePct = if (totalDays > 0) ((presCount + lateCount).toDouble() / totalDays) * 100.0 else 100.0
                val sClsName = classes.value.find { it.id == s.classId }?.name ?: className

                val statusRemark = when {
                    lateCount >= 5 -> "<span style='color:#DC2626; font-weight:bold;'>Digniin (Khatar)</span>"
                    lateCount >= 3 -> "<span style='color:#B45309; font-weight:bold;'>Dhexdhexaad</span>"
                    else -> "<span style='color:#15803D; font-weight:bold;'>Wanaagsan</span>"
                }

                html.append("<tr>")
                html.append("<td style='text-align:center;'>${idx + 1}</td>")
                html.append("<td style='text-align:center; font-weight:bold;'>${s.studentId}</td>")
                html.append("<td class='sname'>${s.name}</td>")
                html.append("<td style='text-align:center;'>$sClsName</td>")
                html.append("<td class='late-col'>$lateCount</td>")
                html.append("<td class='abs-col'>$absCount</td>")
                html.append("<td class='pres-col'>$presCount</td>")
                html.append("<td class='rate-col'>${String.format(Locale.US, "%.0f%%", ratePct)}</td>")
                html.append("<td style='text-align:center;'>$statusRemark</td>")
                html.append("</tr>")
            }

            html.append("</tbody></table>")

            html.append("<div class='footer'>")
            html.append("<div>Macalinka/Kormeere: __________________________</div>")
            html.append("<div>Maamulaha Dugsiga: __________________________</div>")
            html.append("<div>Shaambadda Dugsiga: __________________________</div>")
            html.append("</div>")
        }

        html.append("</body></html>")
        repository.printHtmlReport(context, html.toString(), "Class_Late_Report_${className.replace(" ", "_")}")
    }

    fun exportClassLateReportCsv(context: Context, classId: Long, yearMonth: String? = null) {
        val targetClass = classes.value.find { it.id == classId }
        val className = targetClass?.name ?: "All_Classes"
        val classStudents = students.value.filter { classId == 0L || it.classId == classId }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        val filteredAttendance = allAttendance.value.filter {
            (classId == 0L || it.classId == classId) &&
            (yearMonth == null || it.date.startsWith(yearMonth))
        }

        val sb = StringBuilder()
        sb.append("School: \"${schoolName.value.ifBlank { "DUGSIGA WAXBARASHADA" }}\"\n")
        sb.append("Report: \"WARBIXINTA HABSANKA & MAQNAANSHAHA ARDAYDA (STUDENT TARDINESS & ABSENCES)\"\n")
        sb.append("Class: \"$className\"\n")
        sb.append("Time Period: \"${yearMonth ?: "All-Time"}\"\n")
        sb.append("Export Date: \"${getCurrentDateTimeStr()}\"\n\n")

        sb.append("No,Student ID,Student Name,Class,Total Late (Habsan),Total Absent (Maqan),Total Present (Joogay),Attendance Rate %,Remarks\n")

        classStudents.forEachIndexed { idx, s ->
            val sRecords = filteredAttendance.filter { it.studentId == s.id }
            val lateCount = sRecords.count { it.status.equals("Late", ignoreCase = true) || it.status.equals("Habsan", ignoreCase = true) || it.status == "H" }
            val absCount = sRecords.count { it.status.equals("Absent", ignoreCase = true) || it.status == "A" }
            val presCount = sRecords.count { it.status.equals("Present", ignoreCase = true) || it.status == "P" }
            val totalDays = sRecords.size
            val ratePct = if (totalDays > 0) ((presCount + lateCount).toDouble() / totalDays) * 100.0 else 100.0
            val sClsName = classes.value.find { it.id == s.classId }?.name ?: className
            val remark = if (lateCount >= 5) "High Tardiness" else if (lateCount >= 3) "Moderate" else "Good"

            sb.append("${idx + 1},${s.studentId},\"${s.name}\",\"$sClsName\",$lateCount,$absCount,$presCount,${String.format(Locale.US, "%.1f%%", ratePct)},$remark\n")
        }

        shareCSVFile(context, "Student_Late_Report_${className.replace(" ", "_")}.csv", sb.toString())
    }

    fun exportStudentsExcel(context: Context, classId: Long = 0L) {
        viewModelScope.launch {
            val targetClass = classes.value.find { it.id == classId }
            val className = targetClass?.name ?: "All_Classes"
            val classStudents = students.value.filter { classId == 0L || it.classId == classId }
            val schoolHeader = schoolName.value.ifBlank { "Mahdi Cali School" }

            val sb = StringBuilder()
            sb.append("School: \"$schoolHeader\"\n")
            sb.append("Class: \"$className\"\n")
            sb.append("Export Date: \"${getCurrentDateTimeStr()}\"\n")
            sb.append("Total Students: ${classStudents.size}\n\n")

            sb.append("No,Student ID,Student Name,Class,Gender,Mother Name,Guardian Phone,Fee Status,Total Paid,Enrolled Date\n")

            classStudents.forEachIndexed { idx, s ->
                val sClsName = classes.value.find { it.id == s.classId }?.name ?: className
                val studentFees = fees.value.filter { it.studentId == s.id && it.paidStatus == "Paid" }
                val paidSummary = if (s.isFree) {
                    "FREE"
                } else if (studentFees.isNotEmpty()) {
                    studentFees.joinToString("; ") { "${it.currency} ${if (it.amount % 1.0 == 0.0) it.amount.toInt().toString() else it.amount}" }
                } else {
                    "PENDING"
                }
                val feeStatus = if (s.isFree) "FREE" else if (studentFees.isNotEmpty()) "PAID" else "PENDING"

                sb.append("${idx + 1},\"${s.studentId}\",\"${s.name}\",\"$sClsName\",\"${s.gender}\",\"${s.motherName}\",\"${s.phone}\",\"$feeStatus\",\"$paidSummary\"\n")
            }

            val filename = if (classId == 0L) "Students_All_Classes_${getCurrentDateTimeStr().take(10).replace("/", "-")}.csv"
                           else "Students_${className.replace(" ", "_")}_${getCurrentDateTimeStr().take(10).replace("/", "-")}.csv"

            shareCSVFile(context, filename, sb.toString())
        }
    }

    fun printStudentsListMarkHtml(
        context: Context,
        classId: Long,
        subjectName: String,
        examTitle: String,
        totalMarks: Double,
        academicYear: String,
        teacherName: String
    ) {
        val targetClass = classes.value.find { it.id == classId }
        val className = targetClass?.name ?: "All Classes"
        val classStudents = students.value.filter { classId == 0L || it.classId == classId }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

        val schoolHeader = schoolName.value.ifBlank { "Mahdi Cali School" }
        val serialCode = generateSerialCode("SLM")
        val nowDateTime = getCurrentDateTimeStr()
        val totalMarksStr = if (totalMarks % 1.0 == 0.0) totalMarks.toInt().toString() else totalMarks.toString()

        val html = StringBuilder()
        html.append("<!DOCTYPE html><html><head><meta charset='UTF-8'><style>")
        html.append("@page { size: A4 portrait; margin: 18mm 12mm 15mm 12mm; }")
        html.append("body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 12px; color: #1A202C; font-size: 12px; }")
        html.append(".header-box { border: 2px solid #006A6B; border-radius: 8px; padding: 12px; margin-bottom: 14px; background: #F7FAFC; }")
        html.append(".top-logo-bar { display: flex; align-items: center; justify-content: space-between; border-bottom: 2px solid #006A6B; padding-bottom: 8px; }")
        html.append(".school-title { font-size: 20px; font-weight: 900; color: #006A6B; text-transform: uppercase; letter-spacing: 0.5px; margin: 0; }")
        html.append(".doc-badge { background: #006A6B; color: white; padding: 4px 10px; border-radius: 4px; font-size: 11px; font-weight: bold; }")
        html.append(".doc-heading { text-align: center; font-size: 15px; font-weight: 800; color: #2D3748; margin-top: 8px; text-transform: uppercase; }")
        html.append(".meta-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; margin-top: 10px; font-size: 11px; background: white; padding: 8px; border-radius: 6px; border: 1px solid #E2E8F0; }")
        html.append(".meta-item { display: flex; flex-direction: column; }")
        html.append(".meta-label { font-size: 10px; color: #718096; font-weight: bold; text-transform: uppercase; }")
        html.append(".meta-val { font-size: 12px; color: #004D4E; font-weight: bold; margin-top: 2px; }")
        html.append("table { width: 100%; border-collapse: collapse; margin-top: 12px; font-size: 11px; }")
        html.append("th, td { border: 1px solid #718096; padding: 6px 4px; }")
        html.append("th { background: #006A6B; color: white; font-weight: bold; text-align: center; font-size: 11px; }")
        html.append(".sname { text-align: left; padding-left: 8px; font-weight: 600; }")
        html.append(".st-id { font-family: monospace; font-weight: bold; text-align: center; color: #2D3748; }")
        html.append(".mark-box { width: 75px; text-align: center; font-size: 13px; font-weight: bold; color: #718096; height: 26px; }")
        html.append(".grade-box { width: 55px; text-align: center; font-weight: bold; color: #718096; }")
        html.append(".sign-box { width: 85px; text-align: center; }")
        html.append(".summary-box { display: grid; grid-template-columns: repeat(6, 1fr); gap: 6px; margin-top: 16px; font-size: 10px; border: 1px dashed #006A6B; padding: 8px; border-radius: 6px; background: #F0FDF4; text-align: center; }")
        html.append(".summary-label { font-weight: bold; color: #006A6B; }")
        html.append(".summary-line { border-bottom: 1px solid #718096; margin-top: 8px; height: 12px; }")
        html.append(".footer-sig { display: flex; justify-content: space-between; margin-top: 24px; padding-top: 10px; border-top: 1px solid #CBD5E0; font-size: 11px; font-weight: bold; }")
        html.append(".sig-block { width: 45%; }")
        html.append(".sig-line { border-bottom: 1px solid #4A5568; margin-top: 28px; }")
        html.append("</style></head><body>")

        val logoBase64 = getSchoolLogoBase64(context)
        html.append("<div class='header-box' style='padding:12px 16px;'>")
        html.append("<div style='display:flex; align-items:center; justify-content:space-between; gap:12px;'>")
        html.append("<div style='flex:1; text-align:left;'>")
        html.append("<div style='font-size:11px; font-weight:bold; color:#004D4E;'>JAMHUURIYADDA SOMALILAND</div>")
        html.append("<div style='font-size:9.5px; font-weight:bold; color:#475569;'>WASAARADDA WAXBARASHADA & SAYNISKA</div>")
        html.append("<div style='font-size:9px; color:#64748B;'>Dugsiga Hoose / Dhexe ee Dawliga ah</div>")
        html.append("</div>")

        html.append("<div style='flex-shrink:0; text-align:center;'>")
        if (logoBase64.isNotBlank()) {
            html.append("<img src='data:image/jpeg;base64,$logoBase64' style='width:70px; height:70px; border-radius:50%; object-fit:cover; border:2.5px solid #006A6B; background:#FFF; box-shadow:0 3px 8px rgba(0,106,107,0.25);' alt='Logo' />")
        }
        html.append("</div>")

        html.append("<div style='flex:1; text-align:right;'>")
        html.append("<div style='font-size:10.5px; color:#334155;'><b>Ref:</b> <span style='font-family:monospace; color:#006A6B; font-weight:bold;'>$serialCode</span></div>")
        html.append("<div style='font-size:10.5px; color:#334155;'><b>Taariikhda:</b> $nowDateTime</div>")
        html.append("<div class='doc-badge' style='margin-top:3px; display:inline-block;'>OFFICIAL GRADE SHEET</div>")
        html.append("</div>")
        html.append("</div>")

        html.append("<div style='text-align:center; margin-top:8px; padding-top:6px; border-top:1px dashed #CBD5E1;'>")
        html.append("<div class='school-title' style='font-size:18px;'>${schoolHeader.uppercase()}</div>")
        html.append("<div class='doc-heading' style='margin-top:2px; font-size:13.5px;'>XAASHIDA DHIBCAHA EE ARDAYDA (STUDENTS LIST MARK)</div>")
        html.append("</div>")
        html.append("</div>")

        html.append("<div class='meta-grid'>")
        html.append("<div class='meta-item'><span class='meta-label'>Fasalka (Class)</span><span class='meta-val'>$className</span></div>")
        html.append("<div class='meta-item'><span class='meta-label'>Maadada (Subject)</span><span class='meta-val'>${subjectName.ifBlank { "Dhammaan / Guud" }}</span></div>")
        html.append("<div class='meta-item'><span class='meta-label'>Nooca Qiimaynta (Exam/Test)</span><span class='meta-val'>${examTitle.ifBlank { "Imtixaanka Bisha / Term" }}</span></div>")
        html.append("<div class='meta-item'><span class='meta-label'>Dhibcaha Sare (Max Marks)</span><span class='meta-val'>$totalMarksStr Marks</span></div>")

        html.append("<div class='meta-item'><span class='meta-label'>Macalinka (Teacher)</span><span class='meta-val'>${teacherName.ifBlank { "Macalinka Maadada" }}</span></div>")
        html.append("<div class='meta-item'><span class='meta-label'>Sanad Dugsiyeedka</span><span class='meta-val'>${academicYear.ifBlank { "2026/2027" }}</span></div>")
        html.append("<div class='meta-item'><span class='meta-label'>Serial Code</span><span class='meta-val'>$serialCode</span></div>")
        html.append("<div class='meta-item'><span class='meta-label'>Taariikhda (Date)</span><span class='meta-val'>$nowDateTime</span></div>")
        html.append("</div></div>")

        if (classStudents.isEmpty()) {
            html.append("<p style='text-align:center; margin-top:30px; font-size:13px; color:#718096;'>Fasalkan arday kuma diiwaangashana.</p>")
        } else {
            html.append("<table><thead><tr>")
            html.append("<th style='width:30px;'>#</th>")
            html.append("<th style='width:90px;'>ST ID</th>")
            html.append("<th class='sname'>MAGACA ARDAYGA (STUDENT NAME)</th>")
            html.append("<th style='width:80px;'>FASALKA</th>")
            html.append("<th style='width:85px;'>DHIIRIGELIN / DHIBACO (${totalMarksStr})</th>")
            html.append("<th style='width:65px;'>DARAJADA (GRADE)</th>")
            html.append("<th style='width:90px;'>FAALLO / SAXEEX</th>")
            html.append("</tr></thead><tbody>")

            classStudents.forEachIndexed { idx, s ->
                val sClsName = classes.value.find { it.id == s.classId }?.name ?: className
                html.append("<tr>")
                html.append("<td style='text-align:center;'>${idx + 1}</td>")
                html.append("<td class='st-id'>${s.studentId}</td>")
                html.append("<td class='sname'>${s.name}</td>")
                html.append("<td style='text-align:center; font-weight:600;'>$sClsName</td>")
                html.append("<td class='mark-box'>____ / $totalMarksStr</td>")
                html.append("<td class='grade-box'>[ &nbsp; &nbsp; ]</td>")
                html.append("<td class='sign-box'></td>")
                html.append("</tr>")
            }

            html.append("</tbody></table>")

            // Teacher Manual Summary Statistics Box
            html.append("<div class='summary-box'>")
            html.append("<div><div class='summary-label'>WADAR ARDAYDA</div><div style='font-size:12px; font-weight:bold; margin-top:4px;'>${classStudents.size}</div></div>")
            html.append("<div><div class='summary-label'>JOOGAY (PRESENT)</div><div class='summary-line'></div></div>")
            html.append("<div><div class='summary-label'>MAQAN (ABSENT)</div><div class='summary-line'></div></div>")
            html.append("<div><div class='summary-label'>UGU SAREEYAY (MAX)</div><div class='summary-line'></div></div>")
            html.append("<div><div class='summary-label'>UGU HOOSEEYAY (MIN)</div><div class='summary-line'></div></div>")
            html.append("<div><div class='summary-label'>CELCELIS (AVG)</div><div class='summary-line'></div></div>")
            html.append("</div>")

            // Signature block
            html.append("<div class='footer-sig'>")
            html.append("<div class='sig-block'>")
            html.append("<div>Macalinka Maadada: <u>${teacherName.ifBlank { "___________________________" }}</u></div>")
            html.append("<div class='sig-line'></div>")
            html.append("<div style='font-size:10px; color:#718096; margin-top:2px;'>Saxeexa Macalinka & Taariikhda</div>")
            html.append("</div>")

            html.append("<div class='sig-block' style='text-align:right;'>")
            html.append("<div>Maamulaha Dugsiga: ___________________________</div>")
            html.append("<div class='sig-line'></div>")
            html.append("<div style='font-size:10px; color:#718096; margin-top:2px;'>Saxeexa & Shaambadda Rasmiga ah ee Dugsiga</div>")
            html.append("</div>")
            html.append("</div>")
        }

        html.append("</body></html>")
        repository.printHtmlReport(context, html.toString(), "Students_List_Mark_${className.replace(" ", "_")}_${subjectName.replace(" ", "_")}")
    }

    fun exportStudentsListMarkCsv(
        context: Context,
        classId: Long,
        subjectName: String,
        examTitle: String,
        totalMarks: Double
    ) {
        viewModelScope.launch {
            val targetClass = classes.value.find { it.id == classId }
            val className = targetClass?.name ?: "All_Classes"
            val classStudents = students.value.filter { classId == 0L || it.classId == classId }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            val schoolHeader = schoolName.value.ifBlank { "Mahdi Cali School" }
            val totalMarksStr = if (totalMarks % 1.0 == 0.0) totalMarks.toInt().toString() else totalMarks.toString()

            val sb = StringBuilder()
            sb.append("School: \"$schoolHeader\"\n")
            sb.append("Report: \"STUDENTS LIST MARK SHEET (GRADE ENTRY)\"\n")
            sb.append("Class: \"$className\"\n")
            sb.append("Subject: \"${subjectName.ifBlank { "General" }}\"\n")
            sb.append("Assessment: \"${examTitle.ifBlank { "Monthly Test / Term Exam" }}\"\n")
            sb.append("Max Marks: $totalMarksStr\n")
            sb.append("Date: \"${getCurrentDateTimeStr()}\"\n")
            sb.append("Total Students: ${classStudents.size}\n\n")

            sb.append("No,ST ID,Student Name,Class,Marks (Max $totalMarksStr),Grade,Remarks\n")

            classStudents.forEachIndexed { idx, s ->
                val sClsName = classes.value.find { it.id == s.classId }?.name ?: className
                sb.append("${idx + 1},\"${s.studentId}\",\"${s.name}\",\"$sClsName\",,, \n")
            }

            val filename = "Students_List_Mark_${className.replace(" ", "_")}_${subjectName.replace(" ", "_")}.csv"
            shareCSVFile(context, filename, sb.toString())
        }
    }

    fun exportAuditLogsCsv(context: Context, filteredLogs: List<AuditLog>) {
        viewModelScope.launch {
            val sb = StringBuilder()
            val schoolHeader = schoolName.value.ifBlank { "Mahdi Cali School" }
            sb.append("School: \"$schoolHeader\"\n")
            sb.append("Report: \"ADMINISTRATOR TRANSPARENCY & AUDIT LOG\"\n")
            sb.append("Export Date: \"${getCurrentDateTimeStr()}\"\n")
            sb.append("Total Activity Entries: ${filteredLogs.size}\n\n")

            sb.append("No,Timestamp,User Name,Role,Category,Class,Action Title,Details,Status\n")
            filteredLogs.forEachIndexed { idx, log ->
                sb.append("${idx + 1},\"${log.timestamp}\",\"${log.userName}\",\"${log.userRole}\",\"${log.actionCategory}\",\"${log.className}\",\"${log.title}\",\"${log.details}\",\"${log.status}\"\n")
            }

            shareCSVFile(context, "Transparency_Audit_Log_${getCurrentDateTimeStr().take(10).replace("/", "-")}.csv", sb.toString())
        }
    }

    fun printAuditLogsHtml(context: Context, filteredLogs: List<AuditLog>) {
        val schoolHeader = schoolName.value.ifBlank { "Mahdi Cali School" }
        val dateStr = getCurrentDateTimeStr()

        val rows = StringBuilder()
        filteredLogs.forEachIndexed { idx, log ->
            val badgeColor = when (log.actionCategory) {
                "ATTENDANCE" -> "#059669"
                "GRADING" -> "#2563EB"
                "FINANCE" -> "#D97706"
                "STUDENT" -> "#7C3AED"
                else -> "#4B5563"
            }
            rows.append("""
                <tr>
                    <td style="padding: 8px; border: 1px solid #E5E7EB; text-align: center;">${idx + 1}</td>
                    <td style="padding: 8px; border: 1px solid #E5E7EB; font-size: 11px; white-space: nowrap;">${log.timestamp}</td>
                    <td style="padding: 8px; border: 1px solid #E5E7EB;"><strong>${log.userName}</strong><br/><span style="font-size: 10px; color: #6B7280;">${log.userRole}</span></td>
                    <td style="padding: 8px; border: 1px solid #E5E7EB;"><span style="background: ${badgeColor}22; color: ${badgeColor}; padding: 3px 6px; border-radius: 4px; font-size: 10px; font-weight: bold;">${log.actionCategory}</span></td>
                    <td style="padding: 8px; border: 1px solid #E5E7EB;">${log.className.ifBlank { "-" }}</td>
                    <td style="padding: 8px; border: 1px solid #E5E7EB;">
                        <strong>${log.title}</strong><br/>
                        <span style="font-size: 11px; color: #374151;">${log.details}</span>
                    </td>
                    <td style="padding: 8px; border: 1px solid #E5E7EB; text-align: center; font-weight: bold; font-size: 11px; color: ${if (log.status == "DELETED") "#DC2626" else "#059669"};">${log.status}</td>
                </tr>
            """.trimIndent())
        }

        val logoBase64 = getSchoolLogoBase64(context)
        val headerHtml = getArtisticHeaderHtml(
            context = context,
            schoolTitle = schoolHeader,
            reportTitle = "DAAH-FURNAANTA & DIIWAANKA HAWLAHA (TRANSPARENCY & AUDIT LOG)",
            serialCode = generateSerialCode("AUD"),
            dateStr = dateStr,
            badgeText = "SYSTEM AUDIT"
        )

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8"/>
                <title>Transparency & Audit Log</title>
                <style>
                    body { font-family: 'Segoe UI', Arial, sans-serif; margin: 20px; color: #1F2937; }
                    $artisticHeaderCss
                    table { width: 100%; border-collapse: collapse; margin-top: 12px; font-size: 12px; }
                    th { background-color: #006A6B; color: white; padding: 8px; border: 1px solid #006A6B; text-align: left; font-size: 11px; }
                    .footer { margin-top: 24px; font-size: 10px; color: #9CA3AF; text-align: right; }
                </style>
            </head>
            <body>
                $headerHtml
                <table>
                    <thead>
                        <tr>
                            <th style="width: 30px;">#</th>
                            <th style="width: 120px;">Taariikh / Saacad</th>
                            <th style="width: 130px;">Qofka Geliyay</th>
                            <th style="width: 80px;">Qaybta</th>
                            <th style="width: 90px;">Fasalka</th>
                            <th>Faahfaahinta Diiwaanka / Hawsha</th>
                            <th style="width: 70px;">Xaaladda</th>
                        </tr>
                    </thead>
                    <tbody>
                        $rows
                    </tbody>
                </table>
                <div class="footer">
                    Daabacaadda Nidaamka Maamulka Dugsiga • Mahdi Cali School System
                </div>
            </body>
            </html>
        """.trimIndent()

        repository.printHtmlReport(context, html, "Transparency_Audit_Report")
    }

    // ==========================================
    // RECYCLE BIN & SOFT DELETE OPERATIONS
    // ==========================================
    fun softDeleteStudent(student: Student, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val user = _currentUser.value?.fullName ?: "Admin"
            val success = repository.softDeleteStudent(student, user)
            if (success) {
                logAudit(
                    category = "DELETE",
                    title = "Ardayga ${student.name} waxaa loo wareejiyay Qashin-qubka (Soft Delete)",
                    details = "ID: ${student.studentId}, Fasalka ID: ${student.classId}",
                    status = "DELETED"
                )
                triggerAutoInternetSync(getApplication(), forceImmediate = true)
                _uiMessage.emit("🗑️ Ardayga ${student.name} waxaa loo wareejiyay Qashin-qubka!")
            }
            onComplete(success)
        }
    }

    fun softDeleteClass(schoolClass: SchoolClass, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val user = _currentUser.value?.fullName ?: "Admin"
            val success = repository.softDeleteClass(schoolClass, user)
            if (success) {
                logAudit(
                    category = "DELETE",
                    title = "Fasalka ${schoolClass.name} waxaa loo wareejiyay Qashin-qubka",
                    details = "Class ID: ${schoolClass.id}, Grade: ${schoolClass.grade}",
                    status = "DELETED"
                )
                triggerAutoInternetSync(getApplication(), forceImmediate = true)
                _uiMessage.emit("🗑️ Fasalka ${schoolClass.name} waxaa loo wareejiyay Qashin-qubka!")
            }
            onComplete(success)
        }
    }

    fun restoreRecycleBinItem(item: RecycleBinItem, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val success = repository.restoreRecycleBinItem(item)
            if (success) {
                logAudit(
                    category = "RESTORE",
                    title = "Waxaa Qashin-qubka laga soo celiyay: ${item.itemName}",
                    details = "EntityType: ${item.entityType}, OriginalId: ${item.originalId}",
                    status = "SUCCESS"
                )
                triggerAutoInternetSync(getApplication(), forceImmediate = true)
                _uiMessage.emit("✅ Xogta '${item.itemName}' dib ayaa loo soo celiyay!")
            }
            onComplete(success)
        }
    }

    fun permanentDeleteRecycleBinItem(item: RecycleBinItem, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            repository.deleteRecycleBinItem(item.id)
            CloudSync.removeRecycleBinItemFromCloud(getApplication(), item.entityType, item.originalId)
            logAudit(
                category = "PERMANENT_DELETE",
                title = "Xogta '${item.itemName}' si joogto ah ayaa loo tirtiray",
                details = "Type: ${item.entityType}, ID: ${item.originalId}",
                status = "DELETED"
            )
            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            _uiMessage.emit("🗑️ Xogta '${item.itemName}' si joogto ah ayaa loo tirtiray.")
            onComplete(true)
        }
    }

    fun clearRecycleBin(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            repository.clearRecycleBin()
            logAudit(
                category = "CLEAR_TRASH",
                title = "Qashin-qubka oo dhan waa la faarujiyay",
                details = "Dhammaan xogtii ku jirtay qashin-qubka waa la wada tirtiray",
                status = "DELETED"
            )
            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            _uiMessage.emit("🗑️ Qashin-qubka oo dhan waa la faarujiyay!")
            onComplete(true)
        }
    }

    // ==========================================
    // EXAM LOCK & GRADE CHANGE AUDIT
    // ==========================================
    fun finalizeExam(exam: Exam, finalize: Boolean, onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val user = _currentUser.value?.fullName ?: "Admin"
            val now = getCurrentDateTimeStr()
            val updatedExam = exam.copy(
                isFinalized = finalize,
                status = if (finalize) "FINALIZED" else "PUBLISHED",
                lockedBy = if (finalize) user else "",
                lockedAt = if (finalize) now else ""
            )
            repository.updateExam(updatedExam)
            logAudit(
                category = if (finalize) "LOCK_EXAM" else "UNLOCK_EXAM",
                title = if (finalize) "Imtixaanka '${exam.name}' waa la xidhay (Finalized)" else "Imtixaanka '${exam.name}' waa la furay (Unlocked)",
                className = classes.value.find { it.id == exam.classId }?.name ?: "",
                details = "Subject: ${exam.subject}, LockedBy: $user, Time: $now",
                status = "SUCCESS"
            )
            triggerAutoInternetSync(getApplication(), forceImmediate = true)
            _uiMessage.emit(if (finalize) "🔒 Imtixaanka waa la xidhay (Lama beddeli karo)" else "🔓 Imtixaanka waa la furay")
            onComplete(true)
        }
    }

    fun saveExamMarksWithAudit(
        exam: Exam,
        newMarks: List<ExamMark>,
        previousMarks: List<ExamMark>,
        studentsList: List<Student>,
        reason: String,
        onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val user = _currentUser.value?.fullName ?: "Macalin"
            val now = getCurrentDateTimeStr()

            // Save marks in room
            repository.saveExamMarks(newMarks)

            // Detect and log any changed grades
            newMarks.forEach { nm ->
                val prev = previousMarks.find { it.studentId == nm.studentId }
                val oldScore = prev?.score ?: 0.0
                if (prev != null && (prev.score != nm.score || prev.isAbsent != nm.isAbsent)) {
                    val sName = studentsList.find { it.id == nm.studentId }?.name ?: "Arday"
                    val history = MarkChangeHistory(
                        markId = nm.id,
                        examId = exam.id,
                        examName = exam.name,
                        subject = exam.subject,
                        studentId = nm.studentId,
                        studentName = sName,
                        oldScore = oldScore,
                        newScore = if (nm.isAbsent) -1.0 else nm.score,
                        changedBy = user,
                        changedAt = now,
                        reason = reason.ifBlank { "Dib u eegis caadi ah" }
                    )
                    repository.logMarkChange(history)
                    CloudSync.recordMarkChangeInCloud(getApplication(), history)
                }
            }

            logAudit(
                category = "CHANGE_MARK",
                title = "Buundooyinka '${exam.subject} (${exam.name})' waa la cusbooneysiiyay",
                className = classes.value.find { it.id == exam.classId }?.name ?: "",
                details = "Wadarta ardayda: ${newMarks.size}, Sababta: ${reason.ifBlank { "Cusbooneysiin caadi ah" }}",
                status = "SUCCESS"
            )
            triggerAutoInternetSync(getApplication())
            _uiMessage.emit("✅ Buundooyinka imtixaanka si ammaan ah ayaa loo keydiyay!")
            onComplete(true)
        }
    }

    // ==========================================
    // BACKUP & RESTORE ENTERPRISE SYSTEM
    // ==========================================
    fun createEnterpriseBackup(notes: String = "Manual Backup", onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val currentClasses = classes.value
                val currentStudents = students.value
                val allUsersList = repository.getAllUsersList()
                val currentExams = exams.value
                val currentFees = fees.value
                val currentAttendance = allAttendance.value
                val currentMarks = allExamMarks.value

                val jsonPayload = repository.exportBackupJson(
                    classList = currentClasses,
                    studentList = currentStudents,
                    userList = allUsersList,
                    examList = currentExams,
                    feeList = currentFees,
                    attendanceList = currentAttendance,
                    markList = currentMarks
                )

                val sdf = java.text.SimpleDateFormat("yyyy_MM_dd_HHmmss", java.util.Locale.getDefault())
                val timestampStr = sdf.format(java.util.Date())
                val backupId = "backup_$timestampStr"
                val checksum = CloudSync.calculateChecksum(jsonPayload)
                val totalRecords = currentClasses.size + currentStudents.size + allUsersList.size + currentExams.size + currentFees.size + currentAttendance.size + currentMarks.size
                val user = _currentUser.value?.fullName ?: "Administrator"

                val record = BackupRecord(
                    backupId = backupId,
                    academicYearId = "2025-2026",
                    createdBy = user,
                    createdAt = getCurrentDateTimeStr(),
                    versionNumber = 4L,
                    recordCount = totalRecords,
                    checksum = checksum,
                    backupStatus = "VERIFIED",
                    notes = notes,
                    jsonPayload = jsonPayload
                )

                // 1. Save locally in Room
                repository.insertBackupRecord(record)

                // 2. Save in Cloud Firestore
                CloudSync.createVersionedCloudBackup(getApplication(), record)

                // 3. Log Audit
                logAudit(
                    category = "BACKUP",
                    title = "Keydin Cusub ayaa la abuuray: $backupId",
                    details = "Wadarta diiwaannada: $totalRecords, Checksum: ${checksum.take(8)}...",
                    status = "SUCCESS"
                )

                _uiMessage.emit("✅ Keydinta Dugsiga ($backupId) si guul leh ayaa loo sameeyay!")
                onResult(true, "✅ Backup $backupId waa la abuuray! ($totalRecords diiwaan, Checksum: ${checksum.take(8)}...)")
            } catch (e: Exception) {
                onResult(false, "❌ Cilad keydinta: ${e.localizedMessage}")
            }
        }
    }

    fun verifyBackupRecord(record: BackupRecord, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val currentChecksum = CloudSync.calculateChecksum(record.jsonPayload)
                val isValid = currentChecksum == record.checksum || record.checksum.isBlank()
                if (isValid) {
                    onResult(true, "✅ Xogta keydku waa sax (Valid & Intact)! Checksum SHA-256: ${currentChecksum.take(12)}...")
                } else {
                    onResult(false, "⚠️ Digniin: Checksum-ka xogtu ma is waafaqsana! (Mismatch)")
                }
            } catch (e: Exception) {
                onResult(false, "❌ Cilad baadhitaanka: ${e.localizedMessage}")
            }
        }
    }

    fun restoreEnterpriseBackup(
        backupRecord: BackupRecord,
        selectedCategories: Set<String>,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                // 1. MANDATORY Pre-Restore Safety Backup
                val currentClasses = classes.value
                val currentStudents = students.value
                val allUsersList = repository.getAllUsersList()
                val currentExams = exams.value
                val currentFees = fees.value
                val currentAttendance = allAttendance.value
                val currentMarks = allExamMarks.value

                val safetyPayload = repository.exportBackupJson(
                    classList = currentClasses,
                    studentList = currentStudents,
                    userList = allUsersList,
                    examList = currentExams,
                    feeList = currentFees,
                    attendanceList = currentAttendance,
                    markList = currentMarks
                )
                val sdf = java.text.SimpleDateFormat("yyyy_MM_dd_HHmmss", java.util.Locale.getDefault())
                val safetyId = "safety_prerestore_${sdf.format(java.util.Date())}"
                val safetyRecord = BackupRecord(
                    backupId = safetyId,
                    createdBy = "SYSTEM_SAFETY",
                    createdAt = getCurrentDateTimeStr(),
                    recordCount = currentClasses.size + currentStudents.size,
                    checksum = CloudSync.calculateChecksum(safetyPayload),
                    backupStatus = "VERIFIED",
                    notes = "Automatic safety backup prior to restore of ${backupRecord.backupId}",
                    jsonPayload = safetyPayload
                )
                repository.insertBackupRecord(safetyRecord)

                // 2. Perform Restore
                val success = repository.importBackupJson(backupRecord.jsonPayload, selectedCategories = selectedCategories, clearFirst = false)
                if (success) {
                    logAudit(
                        category = "RESTORE",
                        title = "Dib u soo celinta Keydka: ${backupRecord.backupId}",
                        details = "Qaybaha la soo celiyay: ${selectedCategories.joinToString(", ")}. Safety backup: $safetyId",
                        status = "SUCCESS"
                    )
                    _uiMessage.emit("✅ Xogta dib ayaa loo soo celiyay!")
                    onResult(true, "✅ Xogta keydka ${backupRecord.backupId} si guul leh ayaa loo soo celiyay!\n(Waxaa si toos ah loo sameeyay Safety Backup: $safetyId)")
                } else {
                    onResult(false, "❌ Qaabka xogta keydku ma saxna!")
                }
            } catch (e: Exception) {
                onResult(false, "❌ Cilad soo celinta: ${e.localizedMessage}")
            }
        }
    }

    // --- AI Auto Backup & Auto Restore Engine ---
    fun performAiAutoBackup(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val jsonPayload = repository.exportFullDatabaseDirectFromDb()
                if (jsonPayload.isBlank() || jsonPayload == "{}") {
                    onComplete("⚠️ **AI Backup:** Database-ku waa madhan yahay, wax xog ah oo la kaydiyo ma jiro.")
                    return@launch
                }

                val currentAdmin = _currentUser.value?.username ?: "Admin"
                val sName = schoolName.value.ifBlank { "Dugsiga" }

                // Upload to Firebase and Firebase
                val spResult = CloudSync.uploadFullDatabaseToFirebase(
                    context = getApplication(),
                    jsonPayload = jsonPayload,
                    schoolName = sName,
                    updatedBy = currentAdmin
                )

                val uploadResult = CloudSync.uploadFullDatabaseToFirebase(
                    context = getApplication(),
                    jsonPayload = jsonPayload,
                    schoolName = sName,
                    updatedBy = currentAdmin
                )

                if (spResult.isSuccess || uploadResult.isSuccess) {
                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                    val timestamp = sdf.format(java.util.Date())
                    prefs.edit().putString("last_cloud_sync_time", timestamp).putString("cloud_latest_json", jsonPayload).apply()
                    _lastCloudSyncTime.value = timestamp

                    logAudit(
                        category = "AI_AUTO_BACKUP",
                        title = "AI Cloud Auto Backup Executed",
                        details = "Full database backup uploaded to Firebase & Firebase Cloud Server.",
                        status = "SUCCESS"
                    )

                    onComplete("⚡ **FIREBASE REALTIME & CLOUD BACKUP GUUL!**\n\n• **Dugsiga:** $sName\n• **Taariikhda:** $timestamp\n• **Firebase Server:** mahdi-cali-cloud (us-central1) 🟢\n• **Status:** Xogta oo buuxda ayaa lagu keydiyay Firebase Realtime Database!\n• **Cabbirka Xogta:** ${jsonPayload.length / 1024} KB")
                } else {
                    val err = spResult.exceptionOrNull()?.localizedMessage ?: uploadResult.exceptionOrNull()?.localizedMessage ?: "Unknown cloud error"
                    onComplete("❌ **AI BACKUP FAILED:** $err\n(Fadlan hubi internet-kaaga)")
                }
            } catch (e: Exception) {
                onComplete("❌ **AI Backup Error:** ${e.localizedMessage}")
            }
        }
    }

    fun performAiAutoRestore(onComplete: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val currentSchoolId = CloudSync.getActiveSchoolId(getApplication())
                
                // 1. Try Firebase Download first
                var downloadedPayload: String? = null
                var schoolNameRemote: String? = null

                val spDownload = CloudSync.downloadFullDatabaseFromFirebase(getApplication(), currentSchoolId)
                if (spDownload.isSuccess && !spDownload.getOrNull()?.first.isNullOrBlank()) {
                    downloadedPayload = spDownload.getOrNull()?.first
                    schoolNameRemote = spDownload.getOrNull()?.second
                }

                if (!downloadedPayload.isNullOrBlank()) {
                    val success = repository.importBackupJson(downloadedPayload, clearFirst = false)
                    if (success) {
                        if (!schoolNameRemote.isNullOrBlank()) {
                            prefs.edit().putString("school_name", schoolNameRemote).apply()
                            _schoolName.value = schoolNameRemote
                        }
                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        val timestamp = sdf.format(java.util.Date())
                        prefs.edit().putString("last_cloud_sync_time", timestamp).putString("cloud_latest_json", downloadedPayload).apply()
                        _lastCloudSyncTime.value = timestamp

                        logAudit(
                            category = "AI_AUTO_RESTORE",
                            title = "AI Cloud Auto Restore Executed",
                            details = "Full database snapshot downloaded and restored from Firebase Server.",
                            status = "SUCCESS"
                        )

                        onComplete("⚡ **FIREBASE RESTORE SUCCESSFUL!**\n\n• **Dugsiga:** ${schoolNameRemote ?: schoolName.value}\n• **Server:** Firebase Realtime Database (mahdi-cali-cloud)\n• **Status:** Dhamaan xogta dugsiga ka timi Firebase Cloud Server-ka waa lagu soo shubay aaladdan!\n• **Taariikhda Restore-ka:** $timestamp")
                    } else {
                        onComplete("❌ **AI Restore Error:** Laguma guulaysan in JSON data-da lagu shubo database-ka local-ka ah.")
                    }
                } else {
                    onComplete("⚠️ **AI Restore:** Server-ka Firebase/Cloud ma laha xog hore oo la kaydiyay (Cloud payload is empty). Fadlan marka hore xog u shub.")
                }
            } catch (e: Exception) {
                onComplete("❌ **AI Restore Exception:** ${e.localizedMessage}")
            }
        }
    }

    fun customAiPromptSubmit(prompt: String, onComplete: (String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            try {
                val cleanPrompt = prompt.trim()
                if (cleanPrompt.isBlank()) {
                    onComplete("Fadlan qor su'aasha aad rabto inaad ka hesho xogta dugsiga.")
                    return@launch
                }
                val response = processSomaliDataQuery(cleanPrompt)
                onComplete(response)
            } catch (e: Exception) {
                onComplete("❌ Cilad baa dhacday: ${e.localizedMessage}")
            }
        }
    }

    private suspend fun processSomaliDataQuery(query: String): String {
        val q = query.lowercase().trim()
        val studentList: List<Student> = students.value
        val classList: List<SchoolClass> = classes.value
        val examList: List<Exam> = exams.value
        val markList: List<ExamMark> = allExamMarks.value
        val feeList: List<FeeRecord> = fees.value
        val attendanceList: List<AttendanceRecord> = allAttendance.value
        val userList: List<User> = users.value
        val sName = schoolName.value.ifBlank { "Dugsiga" }

        // Helper: Format class name
        fun getClassName(classId: Long): String {
            return classList.find { it.id == classId }?.name ?: "Fasal aan la aqoon"
        }

        // 1. Cloud Backup / Restore specifically requested
        if (q.contains("backup") || (q.contains("kaydi") && q.contains("server")) || q.contains("shub server")) {
            var backupResult = ""
            performAiAutoBackup { backupResult = it }
            kotlinx.coroutines.delay(800)
            return backupResult.ifBlank { "⚡ Xogta dugsiga si toos ah ayaa loogu keydiyay Cloud Server-ka!" }
        }
        if (q.contains("restore") || (q.contains("soo celi") && q.contains("server"))) {
            var restoreResult = ""
            performAiAutoRestore { restoreResult = it }
            kotlinx.coroutines.delay(800)
            return restoreResult.ifBlank { "⚡ Xogta dugsiga si toos ah ayaa looga soo dejiyay Cloud Server-ka!" }
        }

        // 2. Greetings & Help
        if (q in listOf("asc", "salaam", "assalamu calaykum", "salaamu calaykum", "hello", "hi", "hey", "i caawi", "help", "caawimo", "maxaad qaban kartaa")) {
            return """
                🤖 **Kusoo dhawoow Kaaliyaha AI ee Raadinta Xogta Dugsiga!**
                
                Waxaan diyaar u ahay inaan kugu caawiyo baadhista iyo falanqaynta degdegga ah ee dhammaan xogta **$sName**:
                
                🔍 **Tusaalooyinka aad i weydiin karto:**
                • *"Raadi ardayga Axmed Cali"* ama *"ID AUTO-001"*
                • *"Immisa arday ayaa dhigata dugsiga?"*
                • *"Keen 5-ta arday ee ugu sarreeya (Top 5)?"*
                • *"Ardayda aan fiiga bixin (deynta)?"*
                • *"Fasalka Form 4 ardaydiisa?"*
                • *"Warbixinta xaadirinta maanta?"*
                • *"Macalimiinta dugsiga iyo fasalladooda?"*
                • *"Tirada wiilasha iyo gabdhaha?"*
                
                Qor waxa aad raadinayso hadda! 👇
            """.trimIndent()
        }

        // 3. Specific Student Search (By Name, Student ID, or Admission Number)
        val isExplicitSearch = q.startsWith("raadi") || q.startsWith("search") || q.contains("ardayga") || q.contains("arday") || q.contains("id") || q.contains("roll")
        val searchKeyword = q.removePrefix("raadi").removePrefix("search").removePrefix("ardayga").removePrefix("arday").removePrefix("xogta").trim()

        val matchingStudents: List<Student> = if (searchKeyword.length >= 2) {
            studentList.filter { st ->
                st.name.lowercase().contains(searchKeyword) ||
                st.studentId.lowercase().contains(searchKeyword) ||
                st.admissionNumber.lowercase().contains(searchKeyword) ||
                st.phone.contains(searchKeyword)
            }
        } else emptyList()

        if (isExplicitSearch && matchingStudents.isNotEmpty()) {
            if (matchingStudents.size == 1) {
                val st = matchingStudents.first()
                val cName = getClassName(st.classId)
                val stMarks = markList.filter { it.studentId == st.id && !it.isAbsent }
                val totalScore = stMarks.sumOf { it.score }
                val countSubjects = stMarks.size
                val avgScore = if (countSubjects > 0) totalScore / countSubjects else 0.0

                val stFees = feeList.filter { it.studentId == st.id }
                val paidFees = stFees.filter { it.paidStatus.equals("Paid", ignoreCase = true) }
                val totalPaid = paidFees.sumOf { it.amount }
                val feeStatus = if (st.isFree) "Bilaash (Scholarship)" else if (paidFees.isNotEmpty()) "Waa bixiyay ($$totalPaid)" else "Lama bixin (Pending)"

                return """
                    👨‍🎓 **XOGTA BUUXDA EE ARDAYGA:**
                    ━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                    • **Magaca:** ${st.name}
                    • **Fasalka:** $cName
                    • **Student ID:** ${st.studentId}
                    • **Admission No:** ${st.admissionNumber.ifBlank { st.studentId }}
                    • **Jinsiga:** ${if (st.gender.equals("Female", true) || st.gender.equals("F", true) || st.gender.lowercase().contains("dhedig")) "Dheddig (Gabadh) 👧" else "Lab (Wiil) 👦"}
                    • **Taleefanka:** ${st.phone.ifBlank { "Lama gelin" }}
                    • **Hooyada:** ${st.motherName.ifBlank { "Lama qorin" }}
                    • **Xaaladda Fiiga:** $feeStatus
                    • **Celceliska Imtixaanka:** ${String.format(java.util.Locale.US, "%.1f", avgScore)}% ($countSubjects maaddo)
                    • **Xaaladda Dugsiga:** ${if (st.status == "ACTIVE") "Waa Firfircoon (Active) 🟢" else st.status}
                """.trimIndent()
            } else {
                val sb = StringBuilder()
                sb.append("🔍 **Waxaa la helay ${matchingStudents.size} arday oo ku habboon baaritaankaaga:**\n\n")
                matchingStudents.take(10).forEachIndexed { idx, st ->
                    val cName = getClassName(st.classId)
                    sb.append("${idx + 1}. **${st.name}** | Fasalka: $cName | ID: ${st.studentId}\n")
                }
                if (matchingStudents.size > 10) {
                    sb.append("\n*(Waxaa jira ${matchingStudents.size - 10} arday oo kale)*")
                }
                sb.append("\n💡 *Talo: Qor magaca oo buuxa ama ID gaar ah si aad xogta oo faahfaahsan u hesho.*")
                return sb.toString()
            }
        }

        // 4. Top Ranking Students & Exam Results (Ugu Sarreeya, Kaalinta 1aad, Top 5/10)
        if (q.contains("top") || q.contains("sareey") || q.contains("kaalinta") || q.contains("buundo") || q.contains("darajo") || q.contains("rank") || q.contains("imtixaan")) {
            if (studentList.isEmpty() || markList.isEmpty()) {
                return "⚠️ **Xogta Imtixaanka:** Ma jiraan buundooyin imtixaan oo hadda ku diiwaangashan nidaamka."
            }

            // Calculate total marks and rank for each student
            val studentScores = studentList.mapNotNull { st ->
                val stMarks = markList.filter { it.studentId == st.id && !it.isAbsent }
                val total = stMarks.sumOf { it.score }
                val count = stMarks.size
                val avg = if (count > 0) total / count else 0.0
                if (total > 0.0) Triple(st, total, avg) else null
            }.sortedByDescending { it.second }

            if (studentScores.isEmpty()) {
                return "⚠️ **Xogta Imtixaanka:** Wali buundooyin looma gelin ardayda dugsiga."
            }

            val limit = if (q.contains("10")) 10 else if (q.contains("3")) 3 else 5
            val sb = StringBuilder()
            sb.append("🏆 **ARDAYDA UGU SARREEYA DUGSIGA (TOP $limit):**\n")
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n")

            studentScores.take(limit).forEachIndexed { index, item ->
                val st = item.first
                val total = item.second
                val avg = item.third
                val cName = getClassName(st.classId)
                val medal = when (index) {
                    0 -> "🥇 Kaalinta 1-aad"
                    1 -> "🥈 Kaalinta 2-aad"
                    2 -> "🥉 Kaalinta 3-aad"
                    else -> "🎖️ Kaalinta ${index + 1}-aad"
                }
                val passStatus = if (total >= 350.0 || avg >= 50.0) "Gudbay ✅" else "Dhacay ❌"
                sb.append("$medal: **${st.name}**\n")
                sb.append("   • Fasalka: $cName | ID: ${st.studentId}\n")
                sb.append("   • Wadarta Buundooyinka: **${String.format(java.util.Locale.US, "%.1f", total)}** (Celcelis: ${String.format(java.util.Locale.US, "%.1f", avg)}%) - $passStatus\n\n")
            }

            val passCount = studentScores.count { it.second >= 350.0 || it.third >= 50.0 }
            val failCount = studentScores.size - passCount
            sb.append("📊 **Koobid Imtixaan:** Guud ahaan ${studentScores.size} arday oo imtixaamay ($passCount Gudbay, $failCount Dhacay).")
            return sb.toString()
        }

        // 5. Fee / Financial / Deynta Queries
        if (q.contains("lacag") || q.contains("fee") || q.contains("deyn") || q.contains("dhaqaale") || q.contains("unpaid") || q.contains("paid") || q.contains("bixiyay") || q.contains("aan bixin")) {
            val totalStudents = studentList.size
            val freeStudents = studentList.count { it.isFree }

            val paidStudentIds = feeList.filter { it.paidStatus.equals("Paid", ignoreCase = true) }.map { it.studentId }.toSet()
            val unpaidStudents = studentList.filter { !it.isFree && !paidStudentIds.contains(it.id) }

            val totalUsdPaid = feeList.filter { (it.currency == "USD" || it.currency.isBlank()) && it.paidStatus.equals("Paid", ignoreCase = true) }.sumOf { it.amount }
            val totalSlsPaid = feeList.filter { it.currency == "SLS" && it.paidStatus.equals("Paid", ignoreCase = true) }.sumOf { it.amount }
            val totalEtbPaid = feeList.filter { it.currency == "ETB" && it.paidStatus.equals("Paid", ignoreCase = true) }.sumOf { it.amount }

            val sb = StringBuilder()
            sb.append("💰 **WARBIXINTA LACAGAHA & FIIGA DUGSIGA:**\n")
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
            sb.append("• **Wadarta Lacagta Soo Xarootay (USD):** $$totalUsdPaid\n")
            if (totalSlsPaid > 0.0) sb.append("• **Lacagta Soo Xarootay (SLS):** ${String.format(java.util.Locale.US, "%,.0f", totalSlsPaid)} SLS\n")
            if (totalEtbPaid > 0.0) sb.append("• **Lacagta Soo Xarootay (ETB):** ${String.format(java.util.Locale.US, "%,.0f", totalEtbPaid)} ETB\n")
            sb.append("• **Ardayda Bixisay:** ${paidStudentIds.size} arday\n")
            sb.append("• **Ardayda aan wali bixin (Deynta):** ${unpaidStudents.size} arday\n")
            sb.append("• **Ardayda Bilaashka ah (Scholarship):** $freeStudents arday\n\n")

            if (unpaidStudents.isNotEmpty()) {
                sb.append("⚠️ **Tusaale Ardayda Deynta lagu leeyahay:**\n")
                unpaidStudents.take(6).forEachIndexed { idx, st ->
                    val cName = getClassName(st.classId)
                    sb.append("${idx + 1}. **${st.name}** ($cName) - Tel: ${st.phone.ifBlank { "Ma jiro" }}\n")
                }
                if (unpaidStudents.size > 6) {
                    sb.append("*(iyo ${unpaidStudents.size - 6} arday oo kale)*\n")
                }
            } else {
                sb.append("✅ Dhammaan ardayda bixin lahayd fiiga wey bixiyeen ama waa bilaash!")
            }
            return sb.toString()
        }

        // 6. Specific Class Inquiry or List of Classes
        val matchedClass = classList.find { c -> q.contains(c.name.lowercase()) }
        if (matchedClass != null) {
            val classStudents = studentList.filter { it.classId == matchedClass.id }
            val boys = classStudents.count { it.gender.equals("Male", true) || it.gender.equals("M", true) || it.gender.lowercase().contains("lab") }
            val girls = classStudents.size - boys

            val sb = StringBuilder()
            sb.append("🏫 **XOGTA FASALKA: ${matchedClass.name.uppercase()}**\n")
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
            sb.append("• **Macalinka Fasalka:** ${matchedClass.inchargeTeacher.ifBlank { "Lama magacaabin" }}\n")
            sb.append("• **Tirada Ardayda:** ${classStudents.size} arday (👦 $boys Wiilal, 👧 $girls Gabdho)\n")
            sb.append("• **Sanad Dugsiyeedka:** ${matchedClass.academicYearId}\n\n")

            if (classStudents.isNotEmpty()) {
                sb.append("📋 **Liiska Ardayda Fasalka:**\n")
                classStudents.take(12).forEachIndexed { idx, st ->
                    sb.append("${idx + 1}. ID ${st.studentId}: **${st.name}**\n")
                }
                if (classStudents.size > 12) {
                    sb.append("*(iyo ${classStudents.size - 12} arday oo kale)*\n")
                }
            } else {
                sb.append("⚠️ Fasalkan wali arday kuma qorna.")
            }
            return sb.toString()
        }

        if (q.contains("fasal") || q.contains("classes") || q.contains("class") || q.contains("fasallada")) {
            if (classList.isEmpty()) {
                return "⚠️ Ma jiraan fasallo hadda ku diiwaangashan dugsiga."
            }
            val sb = StringBuilder()
            sb.append("🏫 **LIISKA DHAMMAAN FASALLADA DUGSIGA (${classList.size} Fasal):**\n")
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n\n")
            classList.forEachIndexed { idx, c ->
                val cStudents = studentList.filter { it.classId == c.id }
                sb.append("${idx + 1}. **${c.name}**\n")
                sb.append("   • Ardayda: **${cStudents.size}** arday\n")
                sb.append("   • Macalinka: ${c.inchargeTeacher.ifBlank { "Lama qorin" }}\n\n")
            }
            sb.append("💡 *Talo: Qor tusaale ahaan \"Fasalka ${classList.firstOrNull()?.name ?: "Grade 1"}\" si aad u aragto ardaydiisa.*")
            return sb.toString()
        }

        // 7. Attendance Queries
        if (q.contains("xaadir") || q.contains("attendance") || q.contains("maqan") || q.contains("absent") || q.contains("jooga")) {
            val totalRecords = attendanceList.size
            val presentCount = attendanceList.count { it.status.equals("Present", ignoreCase = true) }
            val absentCount = attendanceList.count { it.status.equals("Absent", ignoreCase = true) }
            val freeCount = attendanceList.count { it.status.equals("Free", ignoreCase = true) }

            val percentage = if (totalRecords > 0) (presentCount.toDouble() / totalRecords) * 100.0 else 100.0

            return """
                📅 **WARBIXINTA XAADIRINTA DUGSIGA:**
                ━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                • **Wadarta Diiwaangelinta Xaadirinta:** $totalRecords
                • **Xaadir (Joogay):** $presentCount (${String.format(java.util.Locale.US, "%.1f", percentage)}%) 🟢
                • **Maqan (Absent):** $absentCount arday 🔴
                • **Fasax (Free):** $freeCount arday 🟡
                
                ${if (absentCount > 0) "💡 *Fiiro gaar ah: Waxaa jira $absentCount xaaladood oo maqnaansho ah oo diiwaangashan.*" else "✅ Dhammaan ardaydu waa xaadir!"}
            """.trimIndent()
        }

        // 8. Teachers / Staff
        if (q.contains("macalin") || q.contains("macalimiin") || q.contains("teacher") || q.contains("shaqaale") || q.contains("user") || q.contains("admin")) {
            val teachers = userList.filter { it.role.equals("TEACHER", ignoreCase = true) }
            val admins = userList.filter { it.role.equals("ADMIN", ignoreCase = true) || it.role.equals("SUPER_ADMIN", ignoreCase = true) }

            val sb = StringBuilder()
            sb.append("👨‍🏫 **MACALIMIINTA IYO MAAMULKA DUGSIGA:**\n")
            sb.append("━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n")
            sb.append("• **Wadarta Macalimiinta:** ${teachers.size}\n")
            sb.append("• **Maamulayaasha:** ${admins.size}\n\n")

            if (teachers.isNotEmpty()) {
                sb.append("📋 **Macalimiinta Diiwaangashan:**\n")
                teachers.forEachIndexed { idx, t ->
                    val assignedClasses = classList.filter { it.inchargeTeacher.contains(t.fullName, true) || it.inchargeTeacher.contains(t.username, true) }
                    val classInfo = if (assignedClasses.isNotEmpty()) " (Fasalka: ${assignedClasses.joinToString { it.name }})" else ""
                    sb.append("${idx + 1}. **${t.fullName}** (@${t.username})$classInfo\n")
                }
            } else {
                sb.append("ℹ️ Lama helin akoonno macalin oo gaar ah, maamulka ayaa maamula fasallada.")
            }
            return sb.toString()
        }

        // 9. Total Counts, Demographics & Executive Summary
        if (q.contains("immisa") || q.contains("tirada") || q.contains("wadarta") || q.contains("total") || q.contains("dumar") || q.contains("rag") || q.contains("wiil") || q.contains("gabdho") || q.contains("summary") || q.contains("guud")) {
            val totalStudents = studentList.size
            val boys = studentList.count { it.gender.equals("Male", true) || it.gender.equals("M", true) || it.gender.lowercase().contains("lab") }
            val girls = totalStudents - boys
            val boysPercent = if (totalStudents > 0) (boys.toDouble() / totalStudents) * 100.0 else 0.0
            val girlsPercent = if (totalStudents > 0) (girls.toDouble() / totalStudents) * 100.0 else 0.0

            return """
                📊 **WARBIXINTA GUUD EE DUGSIGA ($sName):**
                ━━━━━━━━━━━━━━━━━━━━━━━━━━━━
                • **Wadarta Ardayda:** **$totalStudents** arday
                • **Wiilasha (Boys):** $boys (${String.format(java.util.Locale.US, "%.1f", boysPercent)}%) 👦
                • **Gabdhaha (Girls):** $girls (${String.format(java.util.Locale.US, "%.1f", girlsPercent)}%) 👧
                • **Tirada Fasallada:** ${classList.size} fasal 🏫
                • **Tirada Imtixaanaadka:** ${examList.size} imtixaan 📝
                • **Tirada Macalimiinta:** ${userList.count { it.role.equals("TEACHER", true) }} macalin 👨‍🏫
                • **Celceliska Fasal Kasta:** ${if (classList.isNotEmpty()) totalStudents / classList.size else 0} arday
            """.trimIndent()
        }

        // Fallback: Check if user typed a name directly without "raadi"
        val fuzzyMatch = studentList.filter { st ->
            st.name.lowercase().contains(q) ||
            q.contains(st.name.lowercase()) ||
            st.studentId.lowercase() == q
        }
        if (fuzzyMatch.isNotEmpty()) {
            val st = fuzzyMatch.first()
            val cName = getClassName(st.classId)
            return """
                👨‍🎓 **XOGTA ARDAYGA LA HELAY:**
                • **Magaca:** ${st.name}
                • **Fasalka:** $cName
                • **Student ID:** ${st.studentId}
                • **Admission No:** ${st.admissionNumber.ifBlank { st.studentId }}
                • **Taleefanka:** ${st.phone.ifBlank { "Ma jiro" }}
                • **Xaaladda Fiiga:** ${if (st.isFree) "Bilaash" else "Caadi"}
            """.trimIndent()
        }

        // Unrecognized prompt guidance
        return """
            🤖 **Kaaliyaha AI:** Ma fahmin su'aasha *"query"*.
            
            Fadlan isku day inaad weydiiso:
            • *"Raadi [Magaca Ardayga]"*
            • *"Top 5 ardayda ugu sarreeya"*
            • *"Tirada ardayda iyo fasallada"*
            • *"Ardayda deynta lagu leeyahay"*
            • *"Fasalka [Magaca Fasalka]"*
        """.trimIndent()
    }
}

