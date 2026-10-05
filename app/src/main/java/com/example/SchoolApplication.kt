package com.example

import android.app.Application
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth

class SchoolApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("SchoolApplication", "SchoolApplication initialized")
        
        try {
            if (FirebaseApp.getApps(this).isEmpty()) {
                val options = FirebaseOptions.Builder()
                    .setApplicationId("1:79780757286:web:8c0152b82799b2a5304be7")
                    .setApiKey("AIzaSyC0mpHzKEW7lcL1x4oH3OdDxgPDVIrLsi0")
                    .setProjectId("winter-territory-mq6d2")
                    .setDatabaseUrl("https://mahdi-cali-default-rtdb.us-central1.firebasedatabase.app")
                    .setStorageBucket("winter-territory-mq6d2.firebasestorage.app")
                    .build()
                FirebaseApp.initializeApp(this, options)
                Log.d("SchoolApplication", "Firebase manually initialized")
            }
            val auth = FirebaseAuth.getInstance()
            Log.d("SchoolApplication", "Firebase Auth initialized (currentUser: ${auth.currentUser?.uid ?: "none"})")
        } catch (e: Exception) {
            Log.e("SchoolApplication", "Firebase manual init failed", e)
        }

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val exceptionAsString = sw.toString()
            Log.e("FATAL_ERROR", exceptionAsString)
            try {
                val file = File(getExternalFilesDir(null), "crash_log.txt")
                file.writeText(exceptionAsString)
            } catch (e: Exception) {}
            // Kill process
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(1)
        }
    }
}
