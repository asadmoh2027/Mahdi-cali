package com.example.data

import android.content.Context
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class SchoolRepository(private val db: SchoolDatabase) {

    val classDao = db.schoolClassDao()
    val studentDao = db.studentDao()
    val userDao = db.userDao()
    val attendanceDao = db.attendanceDao()
    val examDao = db.examDao()
    val feeDao = db.feeDao()
    val auditLogDao = db.auditLogDao()
    val academicYearDao = db.academicYearDao()
    val subjectDao = db.subjectDao()
    val historyDao = db.studentClassHistoryDao()
    val markHistoryDao = db.markChangeHistoryDao()
    val recycleBinDao = db.recycleBinDao()
    val backupRecordDao = db.backupRecordDao()
    val announcementDao = db.announcementDao()
    val syncQueueDao = db.syncQueueDao()

    // --- Academic Years ---

    fun getAllAcademicYears(): Flow<List<AcademicYear>> = academicYearDao.getAllAcademicYears()
    suspend fun getCurrentAcademicYear(): AcademicYear? = academicYearDao.getCurrentAcademicYear()
    suspend fun insertAcademicYear(year: AcademicYear): Long = academicYearDao.insertAcademicYear(year)

    // --- Subjects ---
    fun getAllSubjects(): Flow<List<Subject>> = subjectDao.getAllSubjects()
    suspend fun getActiveSubjects(): List<Subject> = subjectDao.getActiveSubjects()
    suspend fun insertSubject(subject: Subject): Long = subjectDao.insertSubject(subject)
    suspend fun insertSubjects(subjects: List<Subject>) = subjectDao.insertSubjects(subjects)

    // --- Classes ---
    fun getAllClasses(): Flow<List<SchoolClass>> = classDao.getAllClasses()
    suspend fun getAllClassesList(): List<SchoolClass> = classDao.getAllClassesList()
    suspend fun getClassById(id: Long): SchoolClass? = classDao.getClassById(id)
    suspend fun insertClass(schoolClass: SchoolClass): Long = classDao.insertClass(schoolClass)
    suspend fun updateClass(schoolClass: SchoolClass) = classDao.updateClass(schoolClass)
    suspend fun deleteClass(id: Long) = classDao.deleteClassById(id)

    // --- Students ---
    fun getAllStudents(): Flow<List<Student>> = studentDao.getAllStudents()
    fun getStudentsByClass(classId: Long): Flow<List<Student>> = studentDao.getStudentsByClass(classId)
    fun getStudentsByClasses(classIds: List<Long>): Flow<List<Student>> = studentDao.getStudentsByClasses(classIds)
    suspend fun getStudentByStudentId(studentId: String): Student? = studentDao.getStudentByStudentId(studentId)
    suspend fun getStudentById(id: Long): Student? = studentDao.getStudentById(id)
    suspend fun insertStudent(student: Student): Long = studentDao.insertStudent(student)
    suspend fun updateStudent(student: Student) = studentDao.updateStudent(student)
    suspend fun deleteStudent(id: Long) = studentDao.deleteStudentById(id)
    suspend fun getNextStudentId(): String {
        val count = studentDao.getStudentCount() + 1
        return "AUTO-" + String.format("%03d", count)
    }

    // --- Student Class History ---
    fun getAllClassHistory(): Flow<List<StudentClassHistory>> = historyDao.getAllHistory()
    suspend fun insertClassHistory(history: StudentClassHistory): Long = historyDao.insertHistory(history)

    // --- Recycle Bin & Soft Delete System ---
    fun getAllRecycleBinItems(): Flow<List<RecycleBinItem>> = recycleBinDao.getAllRecycleBinItems()
    suspend fun insertRecycleBinItem(item: RecycleBinItem): Long = recycleBinDao.insertRecycleBinItem(item)
    suspend fun deleteRecycleBinItem(id: Long) = recycleBinDao.deleteRecycleBinItemById(id)
    suspend fun clearRecycleBin() = recycleBinDao.clearRecycleBin()

    suspend fun softDeleteStudent(student: Student, deletedBy: String): Boolean {
        val json = JSONObject().apply {
            put("studentId", student.studentId)
            put("name", student.name)
            put("gender", student.gender)
            put("motherName", student.motherName)
            put("phone", student.phone)
            put("classId", student.classId)
            put("isFree", student.isFree)
            put("academicYearId", student.academicYearId)
        }.toString()

        recycleBinDao.insertRecycleBinItem(
            RecycleBinItem(
                entityType = "STUDENT",
                originalId = student.id,
                itemIdentifier = student.studentId,
                itemName = student.name,
                detailsJson = json,
                deletedBy = deletedBy,
                deletedAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            )
        )
        studentDao.updateStudent(student.copy(status = "DELETED"))
        return true
    }

    suspend fun softDeleteClass(schoolClass: SchoolClass, deletedBy: String): Boolean {
        val json = JSONObject().apply {
            put("id", schoolClass.id)
            put("name", schoolClass.name)
            put("grade", schoolClass.grade)
            put("section", schoolClass.section)
            put("inchargeTeacher", schoolClass.inchargeTeacher)
        }.toString()

        recycleBinDao.insertRecycleBinItem(
            RecycleBinItem(
                entityType = "CLASS",
                originalId = schoolClass.id,
                itemIdentifier = schoolClass.name,
                itemName = schoolClass.name,
                detailsJson = json,
                deletedBy = deletedBy,
                deletedAt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
            )
        )
        classDao.updateClass(schoolClass.copy(status = "DELETED"))
        return true
    }

    suspend fun restoreRecycleBinItem(item: RecycleBinItem): Boolean {
        return try {
            when (item.entityType) {
                "STUDENT" -> {
                    val s = studentDao.getStudentById(item.originalId)
                    if (s != null) {
                        studentDao.updateStudent(s.copy(status = "ACTIVE"))
                    } else {
                        val obj = JSONObject(item.detailsJson)
                        studentDao.insertStudent(
                            Student(
                                id = item.originalId,
                                studentId = obj.optString("studentId", item.itemIdentifier),
                                name = obj.optString("name", item.itemName),
                                gender = obj.optString("gender", "Male"),
                                motherName = obj.optString("motherName", ""),
                                phone = obj.optString("phone", ""),
                                classId = obj.optLong("classId", 1L),
                                isFree = obj.optBoolean("isFree", false),
                                status = "ACTIVE"
                            )
                        )
                    }
                }
                "CLASS" -> {
                    val c = classDao.getClassById(item.originalId)
                    if (c != null) {
                        classDao.updateClass(c.copy(status = "ACTIVE"))
                    }
                }
                "EXAM" -> {
                    val e = examDao.getExamById(item.originalId)
                    if (e != null) {
                        examDao.updateExam(e.copy(status = "PUBLISHED"))
                    }
                }
                "FEE" -> {
                    val f = feeDao.getFeeRecordById(item.originalId)
                    if (f != null) {
                        feeDao.updateFeeRecord(f.copy(status = "ACTIVE"))
                    }
                }
            }
            recycleBinDao.deleteRecycleBinItemById(item.id)
            true
        } catch (e: Exception) {
            false
        }
    }

    // --- Backups & Metadata ---
    fun getAllBackupRecords(): Flow<List<BackupRecord>> = backupRecordDao.getAllBackups()
    suspend fun getBackupRecordById(backupId: String): BackupRecord? = backupRecordDao.getBackupById(backupId)
    suspend fun insertBackupRecord(record: BackupRecord): Long = backupRecordDao.insertBackupRecord(record)
    suspend fun deleteBackupRecord(id: Long) = backupRecordDao.deleteBackupById(id)

    // --- Mark Change History ---
    fun getAllMarkHistory(): Flow<List<MarkChangeHistory>> = markHistoryDao.getAllMarkHistory()
    suspend fun logMarkChange(history: MarkChangeHistory) = markHistoryDao.insertMarkHistory(history)

    suspend fun ensureInitialData() {
        // No automatic demo-data seeding in production as per cloud-first architecture
    }

    suspend fun restoreDefaultOfflineData() {
        SchoolDatabase.populateCompleteOfflineData(db)
    }

    // --- Users ---
    fun getAllUsers(): Flow<List<User>> = userDao.getAllUsers()
    suspend fun getAllUsersList(): List<User> = userDao.getAllUsersList()
    suspend fun getUserByUsername(username: String): User? = userDao.getUserByUsername(username)
    suspend fun getUserById(id: Long): User? = userDao.getUserById(id)
    suspend fun insertUser(user: User): Long = userDao.insertUser(user)
    suspend fun updateUser(user: User) = userDao.updateUser(user)
    suspend fun deleteUser(id: Long) = userDao.deleteUserById(id)

    // --- Attendance ---
    fun getAttendanceForClassAndDate(classId: Long, date: String): Flow<List<AttendanceRecord>> =
        attendanceDao.getAttendanceForClassAndDate(classId, date)
    fun getAttendanceForStudent(studentId: Long): Flow<List<AttendanceRecord>> =
        attendanceDao.getAttendanceForStudent(studentId)
    fun getAllAttendance(): Flow<List<AttendanceRecord>> = attendanceDao.getAllAttendance()
    suspend fun saveAttendanceList(classId: Long, date: String, records: List<AttendanceRecord>) =
        attendanceDao.saveClassAttendanceForDate(classId, date, records)
    suspend fun saveAttendanceList(records: List<AttendanceRecord>) =
        attendanceDao.insertAttendanceList(records)
    suspend fun deleteAttendanceLog(classId: Long, date: String) =
        attendanceDao.deleteAttendanceForClassAndDate(classId, date)

    // --- Exams ---
    fun getAllExams(): Flow<List<Exam>> = examDao.getAllExams()
    fun getExamsByClass(classId: Long): Flow<List<Exam>> = examDao.getExamsByClass(classId)
    fun getExamsByClasses(classIds: List<Long>): Flow<List<Exam>> = examDao.getExamsByClasses(classIds)
    suspend fun getExamById(id: Long): Exam? = examDao.getExamById(id)
    suspend fun insertExam(exam: Exam): Long = examDao.insertExam(exam)
    suspend fun updateExam(exam: Exam) = examDao.updateExam(exam)
    suspend fun deleteExam(id: Long) {
        examDao.deleteMarksByExamId(id)
        examDao.deleteExamById(id)
    }
    fun getMarksForExam(examId: Long): Flow<List<ExamMark>> = examDao.getMarksForExam(examId)
    suspend fun getMarksListForExam(examId: Long): List<ExamMark> = examDao.getMarksListForExam(examId)
    fun getAllExamMarks(): Flow<List<ExamMark>> = examDao.getAllExamMarks()
    fun getMarksForStudent(studentId: Long): Flow<List<ExamMark>> = examDao.getMarksForStudent(studentId)
    suspend fun getMark(examId: Long, studentId: Long): ExamMark? = examDao.getMark(examId, studentId)
    suspend fun saveExamMarks(marks: List<ExamMark>) = examDao.insertMarks(marks)

    // --- Fees ---
    fun getAllFees(): Flow<List<FeeRecord>> = feeDao.getAllFees()
    fun getFeesByClass(classId: Long): Flow<List<FeeRecord>> = feeDao.getFeesByClass(classId)
    fun getFeesByClasses(classIds: List<Long>): Flow<List<FeeRecord>> = feeDao.getFeesByClasses(classIds)
    fun getFeesForStudent(studentId: Long): Flow<List<FeeRecord>> = feeDao.getFeesForStudent(studentId)
    suspend fun insertFeeRecord(fee: FeeRecord): Long = feeDao.insertFeeRecord(fee)
    suspend fun updateFeeRecord(fee: FeeRecord) = feeDao.updateFeeRecord(fee)
    suspend fun deleteFee(id: Long) = feeDao.deleteFeeById(id)

    // --- Audit & Transparency Logs ---
    fun getAllAuditLogs(): Flow<List<AuditLog>> = auditLogDao.getAllAuditLogs()
    fun getAuditLogsByCategory(category: String): Flow<List<AuditLog>> = auditLogDao.getAuditLogsByCategory(category)
    suspend fun insertAuditLog(log: AuditLog): Long = auditLogDao.insertAuditLog(log)
    suspend fun insertAuditLogs(logs: List<AuditLog>) = auditLogDao.insertAuditLogs(logs)
    suspend fun deleteAuditLog(id: Long) = auditLogDao.deleteAuditLog(id)
    suspend fun clearAllAuditLogs() = auditLogDao.clearAllAuditLogs()

    // --- Announcements ---
    fun getAllAnnouncements(): Flow<List<Announcement>> = announcementDao.getAllAnnouncements()
    fun getAnnouncementsForAudience(audience: String): Flow<List<Announcement>> = announcementDao.getAnnouncementsForAudience(audience)
    suspend fun insertAnnouncement(announcement: Announcement): Long = announcementDao.insertAnnouncement(announcement)
    suspend fun updateAnnouncement(announcement: Announcement) = announcementDao.updateAnnouncement(announcement)
    suspend fun deleteAnnouncement(id: Long) = announcementDao.deleteAnnouncementById(id)
    suspend fun clearAllAnnouncements() = announcementDao.clearAllAnnouncements()


    // --- Backup JSON Export (Full) ---
    suspend fun exportBackupJson(
        classList: List<SchoolClass>,
        studentList: List<Student>,
        userList: List<User>,
        examList: List<Exam>,
        feeList: List<FeeRecord>,
        attendanceList: List<AttendanceRecord>,
        markList: List<ExamMark> = emptyList(),
        academicYear: String = "2025-2026"
    ): String {
        val root = JSONObject()
        root.put("version", "4.0")
        root.put("type", "FULL_BACKUP")
        root.put("academicYear", academicYear)
        root.put("timestamp", System.currentTimeMillis())

        val totalRecords = classList.size + studentList.size + userList.size + examList.size + feeList.size + attendanceList.size + markList.size
        root.put("recordCount", totalRecords)

        // Classes
        val classesArr = JSONArray()
        classList.forEach { c ->
            val obj = JSONObject()
            obj.put("id", c.id)
            obj.put("name", c.name)
            obj.put("grade", c.grade)
            obj.put("section", c.section)
            obj.put("academicYearId", c.academicYearId)
            obj.put("startDate", c.startDate)
            obj.put("endDate", c.endDate)
            obj.put("inchargeTeacher", c.inchargeTeacher)
            obj.put("updatedAt", c.updatedAt)
            classesArr.put(obj)
        }
        root.put("classes", classesArr)

        // Students
        val studentsArr = JSONArray()
        studentList.forEach { s ->
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("studentId", s.studentId)
            obj.put("admissionNumber", s.admissionNumber)
            obj.put("name", s.name)
            obj.put("gender", s.gender)
            obj.put("motherName", s.motherName)
            obj.put("phone", s.phone)
            obj.put("classId", s.classId)
            obj.put("academicYearId", s.academicYearId)
            obj.put("isFree", s.isFree)
            obj.put("updatedAt", s.updatedAt)
            studentsArr.put(obj)
        }
        root.put("students", studentsArr)

        // Users
        val usersArr = JSONArray()
        userList.forEach { u ->
            val obj = JSONObject()
            obj.put("id", u.id)
            obj.put("username", u.username)
            obj.put("passwordHash", u.passwordHash)
            obj.put("fullName", u.fullName)
            obj.put("role", u.role)
            obj.put("assignedClassIds", u.assignedClassIds)
            obj.put("isLocked", u.isLocked)
            obj.put("updatedAt", u.updatedAt)
            usersArr.put(obj)
        }
        root.put("users", usersArr)

        // Exams
        val examsArr = JSONArray()
        examList.forEach { e ->
            val obj = JSONObject()
            obj.put("id", e.id)
            obj.put("classId", e.classId)
            obj.put("name", e.name)
            obj.put("subject", e.subject)
            obj.put("date", e.date)
            obj.put("totalMarks", e.totalMarks)
            obj.put("passMarks", e.passMarks)
            obj.put("academicYearId", e.academicYearId)
            obj.put("status", e.status)
            obj.put("isFinalized", e.isFinalized)
            obj.put("updatedAt", e.updatedAt)
            examsArr.put(obj)
        }
        root.put("exams", examsArr)

        // Marks
        val marksArr = JSONArray()
        markList.forEach { m ->
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("examId", m.examId)
            obj.put("studentId", m.studentId)
            obj.put("score", m.score)
            obj.put("isAbsent", m.isAbsent)
            obj.put("updatedAt", m.updatedAt)
            marksArr.put(obj)
        }
        root.put("marks", marksArr)

        // Attendance
        val attArr = JSONArray()
        attendanceList.forEach { a ->
            val obj = JSONObject()
            obj.put("id", a.id)
            obj.put("classId", a.classId)
            obj.put("studentId", a.studentId)
            obj.put("date", a.date)
            obj.put("status", a.status)
            obj.put("updatedAt", a.recordedAt)
            attArr.put(obj)
        }
        root.put("attendance", attArr)

        // Fees
        val feesArr = JSONArray()
        feeList.forEach { f ->
            val obj = JSONObject()
            obj.put("id", f.id)
            obj.put("classId", f.classId)
            obj.put("studentId", f.studentId)
            obj.put("feeType", f.feeType)
            obj.put("amount", f.amount)
            obj.put("currency", f.currency)
            obj.put("dueDate", f.dueDate)
            obj.put("month", f.month)
            obj.put("paidStatus", f.paidStatus)
            obj.put("paidDate", f.paidDate)
            obj.put("version", f.version)
            obj.put("notes", f.notes)
            feesArr.put(obj)
        }
        root.put("fees", feesArr)

        return root.toString(2)
    }

    // --- Class-Specific Backup JSON Export ---
    suspend fun exportClassBackupJson(
        targetClass: SchoolClass,
        studentList: List<Student>,
        examList: List<Exam>,
        feeList: List<FeeRecord>,
        attendanceList: List<AttendanceRecord>,
        markList: List<ExamMark>
    ): String {
        val root = JSONObject()
        root.put("version", "4.0")
        root.put("type", "CLASS_BACKUP")
        root.put("classId", targetClass.id)
        root.put("className", targetClass.name)
        root.put("timestamp", System.currentTimeMillis())

        val classObj = JSONObject()
        classObj.put("id", targetClass.id)
        classObj.put("name", targetClass.name)
        classObj.put("grade", targetClass.grade)
        classObj.put("section", targetClass.section)
        classObj.put("startDate", targetClass.startDate)
        classObj.put("endDate", targetClass.endDate)
        classObj.put("inchargeTeacher", targetClass.inchargeTeacher)
        root.put("class", classObj)

        val classStudents = studentList.filter { it.classId == targetClass.id }
        val studArr = JSONArray()
        classStudents.forEach { s ->
            val obj = JSONObject()
            obj.put("id", s.id)
            obj.put("studentId", s.studentId)
            obj.put("name", s.name)
            obj.put("gender", s.gender)
            obj.put("motherName", s.motherName)
            obj.put("phone", s.phone)
            obj.put("classId", s.classId)
            obj.put("isFree", s.isFree)
            obj.put("updatedAt", s.updatedAt)
            studArr.put(obj)
        }
        root.put("students", studArr)

        val classExams = examList.filter { it.classId == targetClass.id }
        val examIds = classExams.map { it.id }.toSet()
        val examArr = JSONArray()
        classExams.forEach { e ->
            val obj = JSONObject()
            obj.put("id", e.id)
            obj.put("classId", e.classId)
            obj.put("name", e.name)
            obj.put("subject", e.subject)
            obj.put("date", e.date)
            obj.put("totalMarks", e.totalMarks)
            obj.put("passMarks", e.passMarks)
            examArr.put(obj)
        }
        root.put("exams", examArr)

        val classMarks = markList.filter { examIds.contains(it.examId) }
        val markArr = JSONArray()
        classMarks.forEach { m ->
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("examId", m.examId)
            obj.put("studentId", m.studentId)
            obj.put("score", m.score)
            obj.put("isAbsent", m.isAbsent)
            obj.put("updatedAt", m.updatedAt)
            markArr.put(obj)
        }
        root.put("marks", markArr)

        val classAtt = attendanceList.filter { it.classId == targetClass.id }
        val attArr = JSONArray()
        classAtt.forEach { a ->
            val obj = JSONObject()
            obj.put("id", a.id)
            obj.put("classId", a.classId)
            obj.put("studentId", a.studentId)
            obj.put("date", a.date)
            obj.put("status", a.status)
            obj.put("updatedAt", a.recordedAt)
            attArr.put(obj)
        }
        root.put("attendance", attArr)

        val classFees = feeList.filter { it.classId == targetClass.id }
        val feeArr = JSONArray()
        classFees.forEach { f ->
            val obj = JSONObject()
            obj.put("id", f.id)
            obj.put("classId", f.classId)
            obj.put("studentId", f.studentId)
            obj.put("feeType", f.feeType)
            obj.put("amount", f.amount)
            obj.put("currency", f.currency)
            obj.put("dueDate", f.dueDate)
            obj.put("month", f.month)
            obj.put("paidStatus", f.paidStatus)
            obj.put("paidDate", f.paidDate)
            obj.put("version", f.version)
            obj.put("notes", f.notes)
            feeArr.put(obj)
        }
        root.put("fees", feeArr)

        return root.toString(2)
    }

    // --- Direct Export from DAOs to ensure fresh payload ---
    suspend fun exportFullDatabaseDirectFromDb(): String {
        val classes = classDao.getAllClassesList()
        val students = studentDao.getAllStudentsList()
        val users = userDao.getAllUsersList()
        val exams = examDao.getAllExamsList()
        val fees = feeDao.getAllFeesList()
        val attendance = attendanceDao.getAllAttendanceList()
        val marks = examDao.getAllExamMarksList()
        return exportBackupJson(classes, students, users, exams, fees, attendance, marks)
    }

    // --- Safe Full & Selective Backup Import with Validation ---
    suspend fun importBackupJson(
        jsonString: String,
        selectedCategories: Set<String> = setOf("CLASSES", "STUDENTS", "USERS", "EXAMS", "MARKS", "ATTENDANCE", "FEES"),
        clearFirst: Boolean = false // Parameter kept for backward compatibility but ignored
    ): Boolean {
        return try {
            db.withTransaction {
                val root = JSONObject(jsonString)

                // The destructive 'clearFirst' logic has been removed to ensure ZERO DATA LOSS.
                // We now strictly use the safe merge/upsert logic below.

                if (selectedCategories.contains("CLASSES") && root.has("classes")) {
                    val arr = root.getJSONArray("classes")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optLong("id", 0)
                        val cName = obj.optString("name", "Class").trim()
                        
                        val existing = classDao.getClassById(id)
                        
                        // Smart merge: Only update if not exists or if JSON data is newer
                        if (existing == null) {
                            classDao.insertClass(
                                SchoolClass(
                                    id = id,
                                    name = cName,
                                    grade = obj.optString("grade", ""),
                                    section = obj.optString("section", ""),
                                    academicYearId = obj.optString("academicYearId", "2025-2026"),
                                    startDate = obj.optString("startDate", ""),
                                    endDate = obj.optString("endDate", ""),
                                    inchargeTeacher = obj.optString("inchargeTeacher", ""),
                                    updatedAt = obj.optString("updatedAt", "")
                                )
                            )
                        } else {
                            val jsonUpdatedAt = obj.optString("updatedAt", "")
                            if (jsonUpdatedAt > existing.updatedAt) {
                                classDao.updateClass(
                                    existing.copy(
                                        name = cName,
                                        grade = obj.optString("grade", existing.grade),
                                        section = obj.optString("section", existing.section),
                                        inchargeTeacher = obj.optString("inchargeTeacher", existing.inchargeTeacher),
                                        updatedAt = jsonUpdatedAt
                                    )
                                )
                            }
                        }
                    }
                }

                if (selectedCategories.contains("CLASSES") && root.has("class")) {
                    val obj = root.getJSONObject("class")
                    val cName = obj.optString("name", "Class").trim()
                    val existingClasses = classDao.getAllClassesList()
                    val existing = existingClasses.find { it.id == obj.optLong("id", 0) || it.name.trim().equals(cName, ignoreCase = true) }
                    val targetId = existing?.id ?: obj.optLong("id", 0)
                    classDao.insertClass(
                        SchoolClass(
                            id = targetId,
                            name = cName,
                            grade = obj.optString("grade", ""),
                            section = obj.optString("section", ""),
                            startDate = obj.optString("startDate", ""),
                            endDate = obj.optString("endDate", ""),
                            inchargeTeacher = obj.optString("inchargeTeacher", "")
                        )
                    )
                }

                if (selectedCategories.contains("STUDENTS") && root.has("students")) {
                    val arr = root.getJSONArray("students")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optLong("id", 0L)
                        val sId = obj.optString("studentId", "STU_$i").trim()
                        val name = obj.optString("name", "Student").trim()
                        
                        val existing = studentDao.getStudentById(id)
                        
                        // Smart merge: Only update if not exists or if JSON data is newer
                        if (existing == null) {
                            studentDao.insertStudent(
                                Student(
                                    id = id,
                                    studentId = sId,
                                    admissionNumber = obj.optString("admissionNumber", ""),
                                    name = name,
                                    gender = obj.optString("gender", "Male"),
                                    motherName = obj.optString("motherName", ""),
                                    phone = obj.optString("phone", ""),
                                    classId = obj.optLong("classId", 0L),
                                    academicYearId = obj.optString("academicYearId", "2025-2026"),
                                    isFree = obj.optBoolean("isFree", false),
                                    updatedAt = obj.optString("updatedAt", "")
                                )
                            )
                        } else {
                            val jsonUpdatedAt = obj.optString("updatedAt", "")
                            if (jsonUpdatedAt > existing.updatedAt) {
                                studentDao.updateStudent(
                                    existing.copy(
                                        studentId = sId,
                                        admissionNumber = obj.optString("admissionNumber", existing.admissionNumber),
                                        name = name,
                                        gender = obj.optString("gender", existing.gender),
                                        motherName = obj.optString("motherName", existing.motherName),
                                        phone = obj.optString("phone", existing.phone),
                                        classId = obj.optLong("classId", existing.classId),
                                        isFree = obj.optBoolean("isFree", existing.isFree),
                                        updatedAt = jsonUpdatedAt
                                    )
                                )
                            }
                        }
                    }
                }

                if (selectedCategories.contains("USERS") && root.has("users")) {
                    val arr = root.getJSONArray("users")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val uName = obj.optString("username", "").trim()
                        if (uName.isNotEmpty()) {
                            val existing = userDao.getUserByUsername(uName)
                            val targetId = existing?.id ?: obj.optLong("id", 0L)
                        // Smart merge: Only update if not exists or if JSON data is newer
                        if (existing == null) {
                            userDao.insertUser(
                                User(
                                    id = obj.optLong("id", 0L),
                                    username = uName,
                                    passwordHash = obj.optString("passwordHash", "").trim(),
                                    fullName = obj.optString("fullName", "").trim(),
                                    role = obj.optString("role", "TEACHER"),
                                    assignedClassIds = obj.optString("assignedClassIds", ""),
                                    isLocked = obj.optBoolean("isLocked", false),
                                    createdAt = obj.optString("createdAt", ""),
                                    updatedAt = obj.optString("updatedAt", "")
                                )
                            )
                        } else {
                            val jsonUpdatedAt = obj.optString("updatedAt", "")
                            if (jsonUpdatedAt > existing.updatedAt) {
                                userDao.updateUser(
                                    existing.copy(
                                        passwordHash = obj.optString("passwordHash", existing.passwordHash).trim(),
                                        fullName = obj.optString("fullName", existing.fullName).trim(),
                                        role = obj.optString("role", existing.role),
                                        assignedClassIds = obj.optString("assignedClassIds", existing.assignedClassIds),
                                        isLocked = obj.optBoolean("isLocked", existing.isLocked),
                                        updatedAt = jsonUpdatedAt
                                    )
                                )
                            }
                        }
                        }
                    }
                }

                if (selectedCategories.contains("EXAMS") && root.has("exams")) {
                    val arr = root.getJSONArray("exams")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optLong("id", 0)
                        val existing = examDao.getExamById(id)
                        
                        // Smart merge
                        if (existing == null) {
                            examDao.insertExam(
                                Exam(
                                    id = id,
                                    classId = obj.optLong("classId", 0L),
                                    name = obj.optString("name", ""),
                                    subject = obj.optString("subject", ""),
                                    date = obj.optString("date", ""),
                                    totalMarks = obj.optDouble("totalMarks", 100.0),
                                    passMarks = obj.optDouble("passMarks", 40.0),
                                    academicYearId = obj.optString("academicYearId", "2025-2026"),
                                    status = obj.optString("status", "PUBLISHED"),
                                    isFinalized = obj.optBoolean("isFinalized", false),
                                    createdAt = obj.optString("createdAt", ""),
                                    updatedAt = obj.optString("updatedAt", "")
                                )
                            )
                        } else {
                            val jsonUpdatedAt = obj.optString("updatedAt", "")
                            if (jsonUpdatedAt > existing.updatedAt) {
                                examDao.updateExam(
                                    existing.copy(
                                        name = obj.optString("name", existing.name),
                                        subject = obj.optString("subject", existing.subject),
                                        date = obj.optString("date", existing.date),
                                        totalMarks = obj.optDouble("totalMarks", existing.totalMarks),
                                        passMarks = obj.optDouble("passMarks", existing.passMarks),
                                        status = obj.optString("status", existing.status),
                                        isFinalized = obj.optBoolean("isFinalized", existing.isFinalized),
                                        updatedAt = jsonUpdatedAt
                                    )
                                )
                            }
                        }
                    }
                }

                if (selectedCategories.contains("MARKS") && root.has("marks")) {
                    val arr = root.getJSONArray("marks")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optLong("id", 0)
                        val existing = examDao.getExamMarkById(id)
                        
                        if (existing == null) {
                            examDao.insertExamMark(
                                ExamMark(
                                    id = id,
                                    examId = obj.optLong("examId", 0L),
                                    studentId = obj.optLong("studentId", 0L),
                                    score = obj.optDouble("score", 0.0),
                                    isAbsent = obj.optBoolean("isAbsent", false),
                                    updatedAt = obj.optString("updatedAt", "")
                                )
                            )
                        } else {
                            val jsonUpdatedAt = obj.optString("updatedAt", "")
                            if (jsonUpdatedAt > existing.updatedAt) {
                                examDao.updateExamMark(
                                    existing.copy(
                                        score = obj.optDouble("score", existing.score),
                                        isAbsent = obj.optBoolean("isAbsent", existing.isAbsent),
                                        updatedAt = jsonUpdatedAt
                                    )
                                )
                            }
                        }
                    }
                }

                if (selectedCategories.contains("ATTENDANCE") && root.has("attendance")) {
                    val arr = root.getJSONArray("attendance")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val classId = obj.optLong("classId", 0L)
                        val studentId = obj.optLong("studentId", 0L)
                        val date = obj.optString("date", "")
                        
                        val existing = attendanceDao.getAttendanceByDetails(classId, studentId, date)
                        
                        if (existing == null) {
                            attendanceDao.insertAttendance(
                                AttendanceRecord(
                                    id = obj.optLong("id", 0),
                                    classId = classId,
                                    studentId = studentId,
                                    date = date,
                                    status = obj.optString("status", "Present"),
                                    academicYearId = obj.optString("academicYearId", "2025-2026"),
                                    recordedAt = obj.optString("recordedAt", "")
                                )
                            )
                        } else {
                            // If exists, update status if different
                            if (existing.status != obj.optString("status", existing.status)) {
                                attendanceDao.updateAttendance(
                                    existing.copy(status = obj.optString("status", existing.status))
                                )
                            }
                        }
                    }
                }

                if (selectedCategories.contains("FEES") && root.has("fees")) {
                    val arr = root.getJSONArray("fees")
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val id = obj.optLong("id", 0)
                        val existing = feeDao.getFeeRecordById(id)
                        
                        if (existing == null) {
                            feeDao.insertFeeRecord(
                                FeeRecord(
                                    id = id,
                                    classId = obj.optLong("classId", 0L),
                                    studentId = obj.optLong("studentId", 0L),
                                    feeType = obj.optString("feeType", ""),
                                    amount = obj.optDouble("amount", 0.0),
                                    paidStatus = obj.optString("paidStatus", "Pending")
                                )
                            )
                        } else {
                            if (existing.paidStatus != obj.optString("paidStatus", existing.paidStatus)) {
                                feeDao.updateFeeRecord(
                                    existing.copy(paidStatus = obj.optString("paidStatus", existing.paidStatus))
                                )
                            }
                        }
                    }
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // --- HTML Print Support ---
    companion object {
        private val activeWebViews = java.util.Collections.synchronizedList(mutableListOf<WebView>())
    }

    fun printHtml(context: Context, htmlContent: String, jobName: String) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        mainHandler.post {
            try {
                val webView = WebView(context)
                activeWebViews.add(webView)
                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        try {
                            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                            val printAdapter = webView.createPrintDocumentAdapter(jobName)
                            if (printManager != null) {
                                printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            // Delay removing to allow the print dialog to open and bind to it safely
                            mainHandler.postDelayed({
                                try {
                                    activeWebViews.remove(webView)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }, 60000) // Keep strong reference for 60 seconds
                        }
                    }
                }
                webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun printHtmlReport(context: Context, htmlContent: String, jobName: String) = printHtml(context, htmlContent, jobName)
}
