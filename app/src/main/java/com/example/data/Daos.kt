package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AcademicYearDao {
    @Query("SELECT * FROM academic_years ORDER BY id DESC")
    fun getAllAcademicYears(): Flow<List<AcademicYear>>

    @Query("SELECT * FROM academic_years WHERE isCurrent = 1 LIMIT 1")
    suspend fun getCurrentAcademicYear(): AcademicYear?

    @Query("SELECT * FROM academic_years WHERE yearName = :yearName LIMIT 1")
    suspend fun getAcademicYearByName(yearName: String): AcademicYear?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAcademicYear(year: AcademicYear): Long

    @Update
    suspend fun updateAcademicYear(year: AcademicYear)

    @Query("DELETE FROM academic_years WHERE id = :id")
    suspend fun deleteAcademicYearById(id: Long)
}

@Dao
interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY subjectName ASC")
    fun getAllSubjects(): Flow<List<Subject>>

    @Query("SELECT * FROM subjects WHERE status = 'ACTIVE' ORDER BY subjectName ASC")
    suspend fun getActiveSubjects(): List<Subject>

    @Query("SELECT * FROM subjects WHERE subjectCode = :code LIMIT 1")
    suspend fun getSubjectByCode(code: String): Subject?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: Subject): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubjects(subjects: List<Subject>)

    @Update
    suspend fun updateSubject(subject: Subject)

    @Query("DELETE FROM subjects WHERE id = :id")
    suspend fun deleteSubjectById(id: Long)
}

@Dao
interface StudentClassHistoryDao {
    @Query("SELECT * FROM student_class_history ORDER BY id DESC")
    fun getAllHistory(): Flow<List<StudentClassHistory>>

    @Query("SELECT * FROM student_class_history WHERE studentId = :studentId ORDER BY id DESC")
    fun getHistoryForStudent(studentId: Long): Flow<List<StudentClassHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: StudentClassHistory): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryList(list: List<StudentClassHistory>)

    @Query("DELETE FROM student_class_history WHERE studentId = :studentId")
    suspend fun deleteHistoryForStudent(studentId: Long)
}

@Dao
interface MarkChangeHistoryDao {
    @Query("SELECT * FROM mark_change_history ORDER BY id DESC")
    fun getAllMarkHistory(): Flow<List<MarkChangeHistory>>

    @Query("SELECT * FROM mark_change_history WHERE studentId = :studentId ORDER BY id DESC")
    fun getMarkHistoryForStudent(studentId: Long): Flow<List<MarkChangeHistory>>

    @Query("SELECT * FROM mark_change_history WHERE examId = :examId ORDER BY id DESC")
    fun getMarkHistoryForExam(examId: Long): Flow<List<MarkChangeHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarkHistory(history: MarkChangeHistory): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarkHistories(list: List<MarkChangeHistory>)
}

@Dao
interface RecycleBinDao {
    @Query("SELECT * FROM recycle_bin ORDER BY id DESC")
    fun getAllRecycleBinItems(): Flow<List<RecycleBinItem>>

    @Query("SELECT * FROM recycle_bin WHERE entityType = :type ORDER BY id DESC")
    fun getRecycleBinItemsByType(type: String): Flow<List<RecycleBinItem>>

    @Query("SELECT * FROM recycle_bin WHERE id = :id LIMIT 1")
    suspend fun getRecycleBinItemById(id: Long): RecycleBinItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecycleBinItem(item: RecycleBinItem): Long

    @Query("DELETE FROM recycle_bin WHERE id = :id")
    suspend fun deleteRecycleBinItemById(id: Long)

    @Query("DELETE FROM recycle_bin")
    suspend fun clearRecycleBin()
}

@Dao
interface BackupRecordDao {
    @Query("SELECT * FROM backup_records ORDER BY id DESC")
    fun getAllBackups(): Flow<List<BackupRecord>>

    @Query("SELECT * FROM backup_records WHERE backupId = :backupId LIMIT 1")
    suspend fun getBackupById(backupId: String): BackupRecord?

    @Query("SELECT * FROM backup_records ORDER BY id DESC LIMIT 1")
    suspend fun getLatestBackup(): BackupRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBackupRecord(record: BackupRecord): Long

    @Update
    suspend fun updateBackupRecord(record: BackupRecord)

    @Query("DELETE FROM backup_records WHERE id = :id")
    suspend fun deleteBackupById(id: Long)

    @Query("SELECT COUNT(*) FROM backup_records")
    suspend fun getBackupCount(): Int
}

@Dao
interface SchoolClassDao {
    @Query("SELECT * FROM school_classes WHERE status != 'DELETED' ORDER BY name ASC")
    fun getAllClasses(): Flow<List<SchoolClass>>

    @Query("SELECT * FROM school_classes WHERE status != 'DELETED' ORDER BY name ASC")
    suspend fun getAllClassesList(): List<SchoolClass>

    @Query("SELECT * FROM school_classes WHERE id = :id")
    suspend fun getClassById(id: Long): SchoolClass?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClass(schoolClass: SchoolClass): Long

    @Update
    suspend fun updateClass(schoolClass: SchoolClass)

    @Query("DELETE FROM school_classes WHERE id = :id")
    suspend fun deleteClassById(id: Long)

    @Query("DELETE FROM school_classes")
    suspend fun clearAllClasses()

    @Query("SELECT * FROM school_classes WHERE id IN (:ids)")
    suspend fun getClassesByIds(ids: List<Long>): List<SchoolClass>
}

@Dao
interface StudentDao {
    @Query("SELECT * FROM students WHERE status != 'DELETED' ORDER BY name COLLATE NOCASE ASC")
    fun getAllStudents(): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE status != 'DELETED' ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllStudentsList(): List<Student>

    @Query("SELECT * FROM students WHERE classId = :classId AND status != 'DELETED' ORDER BY name COLLATE NOCASE ASC")
    fun getStudentsByClass(classId: Long): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE classId IN (:classIds) AND status != 'DELETED' ORDER BY name COLLATE NOCASE ASC")
    fun getStudentsByClasses(classIds: List<Long>): Flow<List<Student>>

    @Query("SELECT * FROM students WHERE studentId = :studentId LIMIT 1")
    suspend fun getStudentByStudentId(studentId: String): Student?

    @Query("SELECT * FROM students WHERE id = :id LIMIT 1")
    suspend fun getStudentById(id: Long): Student?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: Student): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudents(students: List<Student>)

    @Update
    suspend fun updateStudent(student: Student)

    @Query("DELETE FROM students WHERE id = :id")
    suspend fun deleteStudentById(id: Long)

    @Query("DELETE FROM students")
    suspend fun clearAllStudents()

    @Query("SELECT COUNT(*) FROM students WHERE status != 'DELETED'")
    suspend fun getStudentCount(): Int
}

@Dao
interface UserDao {
    @Query("SELECT * FROM users ORDER BY username ASC")
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT * FROM users ORDER BY username ASC")
    suspend fun getAllUsersList(): List<User>

    @Query("SELECT * FROM users WHERE LOWER(TRIM(username)) = LOWER(TRIM(:username)) OR LOWER(TRIM(fullName)) = LOWER(TRIM(:username)) LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: Long): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Update
    suspend fun updateUser(user: User)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteUserById(id: Long)

    @Query("DELETE FROM users")
    suspend fun clearAllUsers()
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance WHERE classId = :classId AND date = :date")
    fun getAttendanceForClassAndDate(classId: Long, date: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance WHERE classId = :classId AND studentId = :studentId AND date = :date LIMIT 1")
    suspend fun getAttendanceByDetails(classId: Long, studentId: Long, date: String): AttendanceRecord?

    @Query("SELECT * FROM attendance WHERE studentId = :studentId")
    fun getAttendanceForStudent(studentId: Long): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance")
    fun getAllAttendance(): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance")
    suspend fun getAllAttendanceList(): List<AttendanceRecord>

    @Query("DELETE FROM attendance WHERE classId = :classId AND date = :date")
    suspend fun deleteAttendanceForClassAndDate(classId: Long, date: String)

    @Query("DELETE FROM attendance")
    suspend fun clearAllAttendance()

    @Query("DELETE FROM attendance WHERE studentId = :studentId AND date = :date")
    suspend fun deleteAttendanceForStudentAndDate(studentId: Long, date: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendance(record: AttendanceRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttendanceList(records: List<AttendanceRecord>)

    @Update
    suspend fun updateAttendance(record: AttendanceRecord)

    @Transaction
    suspend fun saveClassAttendanceForDate(classId: Long, date: String, records: List<AttendanceRecord>) {
        deleteAttendanceForClassAndDate(classId, date)
        insertAttendanceList(records)
    }
}

@Dao
interface ExamDao {
    @Query("SELECT * FROM exams WHERE status != 'DELETED' ORDER BY date DESC")
    fun getAllExams(): Flow<List<Exam>>

    @Query("SELECT * FROM exams WHERE status != 'DELETED' ORDER BY date DESC")
    suspend fun getAllExamsList(): List<Exam>

    @Query("SELECT * FROM exams WHERE classId = :classId AND status != 'DELETED' ORDER BY date DESC")
    fun getExamsByClass(classId: Long): Flow<List<Exam>>

    @Query("SELECT * FROM exams WHERE classId IN (:classIds) AND status != 'DELETED' ORDER BY date DESC")
    fun getExamsByClasses(classIds: List<Long>): Flow<List<Exam>>

    @Query("SELECT * FROM exams WHERE id = :id LIMIT 1")
    suspend fun getExamById(id: Long): Exam?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExam(exam: Exam): Long

    @Update
    suspend fun updateExam(exam: Exam)

    @Query("DELETE FROM exams WHERE id = :id")
    suspend fun deleteExamById(id: Long)

    @Query("DELETE FROM exams")
    suspend fun clearAllExams()

    @Query("DELETE FROM exam_marks")
    suspend fun clearAllExamMarks()

    @Query("SELECT * FROM exam_marks WHERE examId = :examId")
    fun getMarksForExam(examId: Long): Flow<List<ExamMark>>

    @Query("SELECT * FROM exam_marks WHERE examId = :examId")
    suspend fun getMarksListForExam(examId: Long): List<ExamMark>

    @Query("SELECT * FROM exam_marks")
    fun getAllExamMarks(): Flow<List<ExamMark>>

    @Query("SELECT * FROM exam_marks")
    suspend fun getAllExamMarksList(): List<ExamMark>

    @Query("SELECT * FROM exam_marks WHERE studentId = :studentId")
    fun getMarksForStudent(studentId: Long): Flow<List<ExamMark>>

    @Query("SELECT * FROM exam_marks WHERE examId = :examId AND studentId = :studentId LIMIT 1")
    suspend fun getMark(examId: Long, studentId: Long): ExamMark?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMarks(marks: List<ExamMark>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMark(mark: ExamMark): Long

    @Query("SELECT * FROM exam_marks WHERE id = :id LIMIT 1")
    suspend fun getExamMarkById(id: Long): ExamMark?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExamMark(mark: ExamMark): Long

    @Update
    suspend fun updateExamMark(mark: ExamMark)

    @Query("DELETE FROM exam_marks WHERE examId = :examId")
    suspend fun deleteMarksByExamId(examId: Long)
}

@Dao
interface FeeDao {
    @Query("SELECT * FROM fee_records WHERE status != 'DELETED' ORDER BY dueDate DESC")
    fun getAllFees(): Flow<List<FeeRecord>>

    @Query("SELECT * FROM fee_records WHERE status != 'DELETED' ORDER BY dueDate DESC")
    suspend fun getAllFeesList(): List<FeeRecord>

    @Query("SELECT * FROM fee_records WHERE classId = :classId AND status != 'DELETED' ORDER BY dueDate DESC")
    fun getFeesByClass(classId: Long): Flow<List<FeeRecord>>

    @Query("SELECT * FROM fee_records WHERE classId IN (:classIds) AND status != 'DELETED' ORDER BY dueDate DESC")
    fun getFeesByClasses(classIds: List<Long>): Flow<List<FeeRecord>>

    @Query("SELECT * FROM fee_records WHERE studentId = :studentId AND status != 'DELETED' ORDER BY dueDate DESC")
    fun getFeesForStudent(studentId: Long): Flow<List<FeeRecord>>

    @Query("SELECT * FROM fee_records WHERE id = :id LIMIT 1")
    suspend fun getFeeRecordById(id: Long): FeeRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeeRecord(fee: FeeRecord): Long

    @Update
    suspend fun updateFeeRecord(fee: FeeRecord)

    @Query("DELETE FROM fee_records WHERE id = :id")
    suspend fun deleteFeeById(id: Long)

    @Query("DELETE FROM fee_records")
    suspend fun clearAllFees()
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY id DESC")
    fun getAllAuditLogs(): Flow<List<AuditLog>>

    @Query("SELECT * FROM audit_logs WHERE actionCategory = :category ORDER BY id DESC")
    fun getAuditLogsByCategory(category: String): Flow<List<AuditLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLog(log: AuditLog): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAuditLogs(logs: List<AuditLog>)

    @Query("DELETE FROM audit_logs WHERE id = :id")
    suspend fun deleteAuditLog(id: Long)

    @Query("DELETE FROM audit_logs")
    suspend fun clearAllAuditLogs()
}

@Dao
interface AnnouncementDao {
    @Query("SELECT * FROM announcements ORDER BY isPinned DESC, id DESC")
    fun getAllAnnouncements(): Flow<List<Announcement>>

    @Query("SELECT * FROM announcements WHERE targetAudience = 'ALL' OR targetAudience = :audience ORDER BY isPinned DESC, id DESC")
    fun getAnnouncementsForAudience(audience: String): Flow<List<Announcement>>

    @Query("SELECT * FROM announcements WHERE id = :id LIMIT 1")
    suspend fun getAnnouncementById(id: Long): Announcement?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnouncement(announcement: Announcement): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnouncements(announcements: List<Announcement>)

    @Update
    suspend fun updateAnnouncement(announcement: Announcement)

    @Query("DELETE FROM announcements WHERE id = :id")
    suspend fun deleteAnnouncementById(id: Long)

    @Query("DELETE FROM announcements")
    suspend fun clearAllAnnouncements()
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingSyncItems(): List<SyncQueue>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncItem(item: SyncQueue): Long

    @Update
    suspend fun updateSyncItem(item: SyncQueue)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun deleteSyncItem(id: Long)
}

