package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

object GoogleDriveSync {
    val isDriveScriptConnected = MutableStateFlow(false)
    val driveStatusMessage = MutableStateFlow("")
    val lastDriveSyncTime = MutableStateFlow("")

    private const val PREFS_NAME = "google_drive_sync_prefs"
    private const val KEY_SCRIPT_URL = "script_webhook_url"
    private const val KEY_LAST_SYNC_TIME = "last_drive_sync_time"

    // Default sample/fallback Apps Script template URL or user's custom URL
    const val DEFAULT_SCRIPT_TEMPLATE = """// ============================================================
// Google Apps Script: School Management System Backup Engine
// 1. Tag script.google.com -> Create New Project
// 2. Tirtir koodka hore, ku dheji koodkan (Paste this code)
// 3. Guji 'Deploy' -> 'New deployment' -> Select type: 'Web app'
// 4. Description: 'School Backup'
// 5. Execute as: 'Me' (Your Google Account)
// 6. Who has access: 'Anyone' (Fadlan dooro Anyone si toos ah)
// 7. Guji 'Deploy' -> Sii ruqsadda (Authorize) -> Nuuxi Web app URL
// ============================================================

function doPost(e) {
  var lock = LockService.getScriptLock();
  try {
    lock.waitLock(10000);
    var body = e.postData.contents;
    var data = JSON.parse(body);
    var folderName = "Mahdi_Cali_School_Backups";
    var folders = DriveApp.getFoldersByName(folderName);
    var folder = folders.hasNext() ? folders.next() : DriveApp.createFolder(folderName);

    if (data.action === "backup") {
      var latestFiles = folder.getFilesByName("latest_backup.json");
      var file = latestFiles.hasNext() ? latestFiles.next() : folder.createFile("latest_backup.json", "", MimeType.PLAIN_TEXT);
      file.setContent(body);

      // Permanent timestamped history
      var dateStr = Utilities.formatDate(new Date(), "GMT+3", "yyyy-MM-dd_HH-mm-ss");
      folder.createFile("backup_" + dateStr + ".json", body, MimeType.PLAIN_TEXT);

      return ContentService.createTextOutput(JSON.stringify({
        status: "success",
        message: "Xogta si buuxda ayaa loogu keydiyay Google Drive!",
        fileId: file.getId(),
        timestamp: new Date().toISOString()
      })).setMimeType(ContentService.MimeType.JSON);
    } 
    else if (data.action === "get_latest") {
      var files = folder.getFilesByName("latest_backup.json");
      if (files.hasNext()) {
        var content = files.next().getBlob().getDataAsString();
        return ContentService.createTextOutput(content).setMimeType(ContentService.MimeType.JSON);
      }
      return ContentService.createTextOutput(JSON.stringify({
        status: "empty",
        message: "Wax keyd ah lagama helin Google Drive."
      })).setMimeType(ContentService.MimeType.JSON);
    }
    
    return ContentService.createTextOutput(JSON.stringify({ status: "unknown_action" }))
      .setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({
      status: "error",
      message: err.toString()
    })).setMimeType(ContentService.MimeType.JSON);
  } finally {
    try { lock.releaseLock(); } catch(e) {}
  }
}

function doGet(e) {
  try {
    var folderName = "Mahdi_Cali_School_Backups";
    var folders = DriveApp.getFoldersByName(folderName);
    if (folders.hasNext()) {
      var folder = folders.next();
      var files = folder.getFilesByName("latest_backup.json");
      if (files.hasNext()) {
        var content = files.next().getBlob().getDataAsString();
        return ContentService.createTextOutput(content).setMimeType(ContentService.MimeType.JSON);
      }
    }
    return ContentService.createTextOutput(JSON.stringify({
      status: "empty",
      message: "No backup found in Google Drive folder yet."
    })).setMimeType(ContentService.MimeType.JSON);
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({
      status: "error",
      message: err.toString()
    })).setMimeType(ContentService.MimeType.JSON);
  }
}"""

    private val httpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(45, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    // Official Permanent Central Google Drive Script Server for Mahdi Cali School
    const val PERMANENT_CENTRAL_SCRIPT_URL = "https://script.google.com/macros/s/AKfycbyD90Hjfuc5BlgagYSC62Uu9qTleujMNMOHr7DE0xsml9NG4NTLruTLLh1Gj1GfYBwv/exec"

    fun getScriptUrl(context: Context? = null): String {
        if (context != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val custom = prefs.getString(KEY_SCRIPT_URL, null)
            if (!custom.isNullOrBlank()) {
                if (custom.contains("AKfycbySg4iCJ0TVe-FuOVEA3IlRFG_yMS_5-sy4mMxinKsBemAKVXJFnU9XWpT2zoGGNlAK")) {
                    prefs.edit().putString(KEY_SCRIPT_URL, PERMANENT_CENTRAL_SCRIPT_URL).apply()
                    return PERMANENT_CENTRAL_SCRIPT_URL
                }
                return custom
            }
        }
        return PERMANENT_CENTRAL_SCRIPT_URL
    }

    fun setScriptUrl(context: Context, url: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val trimmed = url.trim()
        if (trimmed.isBlank() || trimmed.contains("AKfycbySg4iCJ0TVe-FuOVEA3IlRFG_yMS_5-sy4mMxinKsBemAKVXJFnU9XWpT2zoGGNlAK")) {
            prefs.edit().putString(KEY_SCRIPT_URL, PERMANENT_CENTRAL_SCRIPT_URL).apply()
        } else {
            prefs.edit().putString(KEY_SCRIPT_URL, trimmed).apply()
        }
    }

    fun getLastSyncTime(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LAST_SYNC_TIME, "Lama xidhiidhin weli") ?: "Lama xidhiidhin weli"
    }

    fun setLastSyncTime(context: Context, timeStr: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LAST_SYNC_TIME, timeStr).apply()
        lastDriveSyncTime.value = timeStr
    }

    fun isNetworkAvailable(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork ?: return false
            val cap = cm.getNetworkCapabilities(network) ?: return false
            cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Exception) {
            true
        }
    }

    fun cacheOfflineBackup(context: Context, jsonPayload: String) {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString("offline_permanent_backup_json", jsonPayload).apply()
        } catch (e: Exception) {}
    }

    fun getCachedOfflineBackup(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString("offline_permanent_backup_json", null)
    }

    suspend fun uploadToGoogleDriveScript(
        context: Context,
        jsonPayload: String,
        schoolName: String,
        updatedBy: String
    ): Result<String> = withContext(Dispatchers.IO) {
        val scriptUrl = getScriptUrl(context)
        if (scriptUrl.isBlank()) {
            return@withContext Result.failure(Exception("Fadlan marka hore geli Google Apps Script Web App URL-ka ee Settings-ka."))
        }

        // Always cache payload locally on device first - ZERO data loss guarantee
        cacheOfflineBackup(context, jsonPayload)

        if (!isNetworkAvailable(context)) {
            return@withContext Result.failure(Exception("Ma jiro xiriir internet oo firfircoon. Xogta waxaa lagu keydiyay taleefanka si nabad ah."))
        }

        var lastException: Exception? = null
        for (attempt in 1..2) {
            try {
                val requestObject = JSONObject().apply {
                    put("action", "backup")
                    put("schoolId", "mahdi-cali")
                    put("schoolName", schoolName)
                    put("updatedBy", updatedBy)
                    put("timestamp", System.currentTimeMillis())
                    put("payload", jsonPayload)
                }

                val body = requestObject.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
                val request = Request.Builder()
                    .url(scriptUrl)
                    .post(body)
                    .build()

                val response = httpClient.newCall(request).execute()
                val finalUrl = response.request.url.toString()

                if (response.code == 401 || finalUrl.contains("accounts.google.com") || finalUrl.contains("ServiceLogin")) {
                    isDriveScriptConnected.value = false
                    return@withContext Result.failure(
                        Exception("Server HTTP Error: 401 (Fadlan script.google.com ka dooro 'Anyone' oo kaliya. Ha dooran 'Anyone with Google account' sababtoo ah taasi waxay keenaysaa ciladdan 401).")
                    )
                }

                if (!response.isSuccessful) {
                    throw Exception("Server HTTP Error: ${response.code}")
                }

                val responseBody = response.body?.string() ?: ""
                isDriveScriptConnected.value = true
                val nowTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                setLastSyncTime(context, nowTime)
                driveStatusMessage.value = "Xogta si guul leh ayaa loogu keydiyay Google Drive!"

                return@withContext Result.success("✅ Xogta dugsiga si buuxda ayaa loogu keydiyay Google Drive Cloud!\nTaariikhda: $nowTime")
            } catch (e: Exception) {
                lastException = e
                if (attempt < 2) {
                    kotlinx.coroutines.delay(1000)
                }
            }
        }

        isDriveScriptConnected.value = false
        val errMsg = lastException?.localizedMessage ?: "Cilad xiriir Google Drive"
        driveStatusMessage.value = "Cilad xiriir Google Drive: $errMsg"
        Result.failure(Exception(errMsg))
    }

    suspend fun downloadFromGoogleDriveScript(context: Context): Result<Pair<String?, String?>> = withContext(Dispatchers.IO) {
        val scriptUrl = getScriptUrl(context)
        if (scriptUrl.isBlank()) {
            return@withContext Result.failure(Exception("Fadlan marka hore geli Google Apps Script Web App URL-ka."))
        }
        if (!isNetworkAvailable(context)) {
            return@withContext Result.failure(Exception("Ma jiro xiriir internet oo firfircoon."))
        }

        try {
            val requestObject = JSONObject().apply {
                put("action", "get_latest")
                put("schoolId", "mahdi-cali")
            }

            val body = requestObject.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(scriptUrl)
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val finalUrl = response.request.url.toString()
            if (response.code == 401 || finalUrl.contains("accounts.google.com") || finalUrl.contains("ServiceLogin")) {
                isDriveScriptConnected.value = false
                return@withContext Result.failure(
                    Exception("Server HTTP Error: 401 (Script-ka Google wuxuu xiran yahay 'Only myself'. Fadlan script.google.com ka dhig 'Who has access: Anyone').")
                )
            }

            if (!response.isSuccessful) {
                // Also attempt simple GET if POST failed
                val getRequest = Request.Builder().url(scriptUrl).get().build()
                val getResponse = httpClient.newCall(getRequest).execute()
                val getFinalUrl = getResponse.request.url.toString()
                if (getResponse.code == 401 || getFinalUrl.contains("accounts.google.com") || getFinalUrl.contains("ServiceLogin")) {
                    return@withContext Result.failure(
                        Exception("Server HTTP Error: 401 (Fadlan script.google.com ka dooro 'Anyone' oo kaliya. Ha dooran 'Anyone with Google account').")
                    )
                }
                if (!getResponse.isSuccessful) {
                    return@withContext Result.failure(Exception("Server HTTP Error: ${response.code}"))
                }
                return@withContext parseResponsePayload(getResponse.body?.string() ?: "")
            }

            val responseBody = response.body?.string() ?: ""
            parseResponsePayload(responseBody)
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Cilad soo dejin Google Drive"))
        }
    }

    private fun parseResponsePayload(responseBody: String): Result<Pair<String?, String?>> {
        return try {
            if (responseBody.isBlank()) {
                return Result.success(Pair(null, null))
            }
            val json = JSONObject(responseBody)
            if (json.optString("status") == "empty") {
                return Result.success(Pair(null, null))
            }

            var payload: String? = null
            var schoolName: String? = json.optString("schoolName", "Mahdi Cali School")

            if (json.has("payload")) {
                val p = json.get("payload")
                payload = if (p is JSONObject || p is org.json.JSONArray) p.toString() else p.toString()
            } else if (json.has("classes") || json.has("students")) {
                payload = responseBody
            }

            Result.success(Pair(payload, schoolName))
        } catch (e: Exception) {
            // Check if response is raw backup JSON
            if (responseBody.contains("\"classes\"") || responseBody.contains("\"students\"")) {
                Result.success(Pair(responseBody, "Mahdi Cali School"))
            } else {
                Result.failure(Exception("Format khalad ah: ${e.localizedMessage}"))
            }
        }
    }

    suspend fun testScriptConnection(context: Context): Result<String> = withContext(Dispatchers.IO) {
        val scriptUrl = getScriptUrl(context)
        if (scriptUrl.isBlank()) {
            return@withContext Result.failure(Exception("URL-ka Google Apps Script waa madhan yahay. Fadlan geli URL-ka Web App-ka."))
        }
        if (!isNetworkAvailable(context)) {
            return@withContext Result.failure(Exception("Ma jiro xiriir internet."))
        }

        try {
            val request = Request.Builder().url(scriptUrl).get().build()
            val response = httpClient.newCall(request).execute()
            val finalUrl = response.request.url.toString()
            if (response.code == 401 || finalUrl.contains("accounts.google.com") || finalUrl.contains("ServiceLogin")) {
                isDriveScriptConnected.value = false
                return@withContext Result.failure(
                    Exception("Server HTTP Error: 401 (Fadlan script.google.com ka dooro 'Anyone' oo kaliya. Ha dooran 'Anyone with Google account').")
                )
            }
            if (response.isSuccessful) {
                isDriveScriptConnected.value = true
                Result.success("Google Drive Script Connected 🟢 (Code: ${response.code})")
            } else {
                Result.failure(Exception("Script HTTP Jawaab: ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Lama xidhiidhi karo script-ka"))
        }
    }

    // Direct File Stream Write for Storage Access Framework (SAF)
    suspend fun writePayloadToStream(outputStream: OutputStream, jsonPayload: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            outputStream.use { os ->
                os.write(jsonPayload.toByteArray(Charsets.UTF_8))
                os.flush()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Direct File Stream Read for Storage Access Framework (SAF)
    suspend fun readPayloadFromStream(inputStream: InputStream): Result<String> = withContext(Dispatchers.IO) {
        try {
            val jsonString = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (jsonString.isBlank()) {
                Result.failure(Exception("Faylku waa madhan yahay."))
            } else {
                Result.success(jsonString)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
