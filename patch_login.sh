#!/bin/bash
cat << 'INNER_EOF' > /tmp/login_new.kt
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
                u.equals("cashier", ignoreCase = true) && p == "1234" -> Pair("CASHIER", "School Cashier")
                u.equals("teacher", ignoreCase = true) && p == "1234" -> Pair("TEACHER", "Macalin Guud")
                else -> null
            }

            // Let's check internet connectivity
            val isOnline = isInternetAvailable(getApplication())
            
            if (isOnline) {
                try {
                    FirebaseCloudSync.ensureInitialized(getApplication())
                    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                    val email = "${u.lowercase().replace(" ", "")}@schoolapp.local"
                    
                    var authSuccess = false
                    var authUid = ""
                    var authErrorMsg = ""

                    // 1. Try to sign in
                    val signInResult = suspendCancellableCoroutine<com.google.firebase.auth.AuthResult?> { cont ->
                        auth.signInWithEmailAndPassword(email, p)
                            .addOnSuccessListener { res -> cont.resume(res) }
                            .addOnFailureListener { err -> 
                                authErrorMsg = err.localizedMessage ?: "Unknown auth error"
                                cont.resume(null) 
                            }
                    }

                    if (signInResult != null) {
                        authSuccess = true
                        authUid = signInResult.user?.uid ?: ""
                    }

                    // 2. If sign-in failed, check if we need to auto-create predefined account
                    if (!authSuccess && predefinedUser != null) {
                        val createResult = FirebaseCloudSync.createFirebaseAuthUser(
                            context = getApplication(),
                            username = u,
                            pass = p,
                            fullName = predefinedUser.second,
                            role = predefinedUser.first,
                            assignedClassIds = if (predefinedUser.first == "TEACHER") "1,2" else "",
                            schoolId = FirebaseCloudSync.DEFAULT_SCHOOL_ID
                        )
                        
                        if (createResult.isSuccess) {
                            val retrySignIn = suspendCancellableCoroutine<com.google.firebase.auth.AuthResult?> { cont ->
                                auth.signInWithEmailAndPassword(email, p)
                                    .addOnSuccessListener { res -> cont.resume(res) }
                                    .addOnFailureListener { cont.resume(null) }
                            }
                            if (retrySignIn != null) {
                                authSuccess = true
                                authUid = retrySignIn.user?.uid ?: ""
                            }
                        } else {
                            authErrorMsg = createResult.exceptionOrNull()?.localizedMessage ?: "Failed to create cloud user"
                        }
                    }

                    // 3. If online auth succeeded, fetch profile
                    if (authSuccess) {
                        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        val profileDoc = suspendCancellableCoroutine<com.google.firebase.firestore.DocumentSnapshot?> { cont ->
                            db.collection("users").document(authUid).get()
                                .addOnSuccessListener { doc -> cont.resume(doc) }
                                .addOnFailureListener { cont.resume(null) }
                        }
                        
                        // If profile exists or it's a predefined user (we can auto-create the local profile)
                        if ((profileDoc != null && profileDoc.exists()) || predefinedUser != null) {
                            val role = profileDoc?.getString("role") ?: predefinedUser?.first ?: "TEACHER"
                            val schoolId = profileDoc?.getString("schoolId") ?: FirebaseCloudSync.DEFAULT_SCHOOL_ID
                            val fullName = profileDoc?.getString("fullName") ?: predefinedUser?.second ?: u
                            val assignedClassIds = profileDoc?.getString("assignedClassIds") ?: ""
                            val isLocked = profileDoc?.getBoolean("isLocked") ?: false

                            if (isLocked) {
                                auth.signOut()
                                onResult(false, "❌ Akoonkan waxaa xannibay Maamulaha guud.")
                                return@launch
                            }

                            FirebaseCloudSync.setActiveSchoolId(getApplication(), schoolId)

                            val uObj = User(
                                id = profileDoc?.getLong("id") ?: System.currentTimeMillis(),
                                username = u,
                                passwordHash = p,
                                fullName = fullName,
                                role = role,
                                assignedClassIds = assignedClassIds,
                                isLocked = false
                            )

                            val localUser = repository.getUserByUsername(u)
                            if (localUser != null) {
                                repository.updateUser(uObj.copy(id = localUser.id))
                            } else {
                                repository.insertUser(uObj)
                            }

                            val fbDownload = FirebaseCloudSync.downloadFullDatabaseFromFirestore(getApplication(), schoolId)
                            var restoredMsg = ""
                            if (fbDownload.isSuccess) {
                                val (payload, schoolName) = fbDownload.getOrNull() ?: Pair(null, null)
                                if (!payload.isNullOrBlank()) {
                                    val success = repository.importBackupJson(payload, clearFirst = true)
                                    if (success) {
                                        if (!schoolName.isNullOrBlank()) {
                                            prefs.edit().putString("school_name", schoolName).apply()
                                            _schoolName.value = schoolName
                                        }
                                        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault())
                                        val timestamp = sdf.format(java.util.Date())
                                        prefs.edit().putString("last_cloud_sync_time", timestamp).putString("cloud_latest_json", payload).apply()
                                        _lastCloudSyncTime.value = timestamp
                                        restoredMsg = " (Xogta Server-ka waa la soo dejiyay ☁️)"
                                    }
                                }
                            }

                            setupFirebaseAutoSync(getApplication())
                            _currentUser.value = uObj
                            prefs.edit().putString("last_logged_in_username", u).apply()
                            onResult(true, "Kusoo dhawoow ${fullName} (${role})!$restoredMsg")
                            return@launch
                        } else {
                            onResult(false, "❌ Profile-kaaga laguma helin Firestore. La xidhiidh Maamulaha.")
                            return@launch
                        }
                    } else {
                        // Firebase Auth completely failed. Fallback to predefined local if matched
                        if (predefinedUser != null) {
                            val localU = repository.getUserByUsername(u)
                            val targetU = if (localU != null) {
                                localU.copy(passwordHash = p) // update pass
                            } else {
                                User(id = System.currentTimeMillis(), username = u, passwordHash = p, fullName = predefinedUser.second, role = predefinedUser.first)
                            }
                            if (localU != null) repository.updateUser(targetU) else repository.insertUser(targetU)
                            
                            _currentUser.value = targetU
                            prefs.edit().putString("last_logged_in_username", u).apply()
                            FirebaseCloudSync.setActiveSchoolId(getApplication(), FirebaseCloudSync.DEFAULT_SCHOOL_ID)
                            onResult(true, "⚠️ Cloud Login Failed ($authErrorMsg). Kusoo dhawoow ${targetU.fullName} (Offline Mode)")
                            return@launch
                        }

                        onResult(false, "❌ Magaca isticmaalaha ama password-ku ma saxna! ($authErrorMsg)")
                        return@launch
                    }
                } catch (e: Exception) {
                    if (predefinedUser != null) {
                        val localU = repository.getUserByUsername(u) ?: User(id = System.currentTimeMillis(), username = u, passwordHash = p, fullName = predefinedUser.second, role = predefinedUser.first)
                        if (repository.getUserByUsername(u) == null) repository.insertUser(localU)
                        _currentUser.value = localU
                        prefs.edit().putString("last_logged_in_username", u).apply()
                        onResult(true, "⚠️ Firebase Error. Kusoo dhawoow ${localU.fullName} (Offline Mode)")
                        return@launch
                    }
                    onResult(false, "❌ Cilad dhacday: ${e.localizedMessage}")
                    return@launch
                }
            } else {
                // Offline login fallback
                val dbUser = repository.getUserByUsername(u)
                if (dbUser != null) {
                    if (dbUser.isLocked) {
                        onResult(false, "❌ Akoonkan waxaa xannibay Maamulaha guud.")
                        return@launch
                    }
                    if (dbUser.passwordHash.trim() == p) {
                        _currentUser.value = dbUser
                        prefs.edit().putString("last_logged_in_username", u).apply()
                        onResult(true, "📱 Offline Mode: Kusoo dhawoow ${dbUser.fullName} (${dbUser.role})!")
                        return@launch
                    } else {
                        onResult(false, "❌ Password-ka aad galisay ma saxna!")
                        return@launch
                    }
                } else if (predefinedUser != null) {
                    // Auto-provision offline predefined
                    val uObj = User(id = System.currentTimeMillis(), username = u, passwordHash = p, fullName = predefinedUser.second, role = predefinedUser.first)
                    repository.insertUser(uObj)
                    _currentUser.value = uObj
                    prefs.edit().putString("last_logged_in_username", u).apply()
                    onResult(true, "📱 Offline Mode: Kusoo dhawoow ${uObj.fullName} (${uObj.role})!")
                    return@launch
                } else {
                    onResult(false, "❌ Internet-ka xidhan. Taleefanka xog hore uma hayo, fadlan daor internet-ka si aad markii ugu horreysay u gasho.")
                    return@launch
                }
            }
        }
    }
INNER_EOF
