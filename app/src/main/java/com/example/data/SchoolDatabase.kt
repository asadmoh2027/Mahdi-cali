package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        AcademicYear::class,
        Subject::class,
        SchoolClass::class,
        Student::class,
        StudentClassHistory::class,
        User::class,
        AttendanceRecord::class,
        Exam::class,
        ExamMark::class,
        MarkChangeHistory::class,
        FeeRecord::class,
        AuditLog::class,
        RecycleBinItem::class,
        BackupRecord::class,
        Announcement::class,
        SyncQueue::class
    ],
    version = 7,
    exportSchema = false
)
abstract class SchoolDatabase : RoomDatabase() {

    abstract fun academicYearDao(): AcademicYearDao
    abstract fun subjectDao(): SubjectDao
    abstract fun schoolClassDao(): SchoolClassDao
    abstract fun studentDao(): StudentDao
    abstract fun studentClassHistoryDao(): StudentClassHistoryDao
    abstract fun userDao(): UserDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun examDao(): ExamDao
    abstract fun markChangeHistoryDao(): MarkChangeHistoryDao
    abstract fun feeDao(): FeeDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun recycleBinDao(): RecycleBinDao
    abstract fun backupRecordDao(): BackupRecordDao
    abstract fun announcementDao(): AnnouncementDao
    abstract fun syncQueueDao(): SyncQueueDao


    companion object {
        @Volatile
        private var INSTANCE: SchoolDatabase? = null

        private val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE school_classes ADD COLUMN shift TEXT NOT NULL DEFAULT 'Gelin Hore'")
                } catch (e: Exception) {}
                try {
                    db.execSQL("ALTER TABLE students ADD COLUMN shift TEXT NOT NULL DEFAULT 'Gelin Hore'")
                } catch (e: Exception) {}
                try {
                    db.execSQL("ALTER TABLE users ADD COLUMN shift TEXT NOT NULL DEFAULT 'Dhammaan'")
                } catch (e: Exception) {}
            }
        }

        fun getDatabase(context: Context, scope: CoroutineScope): SchoolDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SchoolDatabase::class.java,
                    "school_system_db"
                )
                .addMigrations(MIGRATION_6_7)
                .fallbackToDestructiveMigrationOnDowngrade()
                .addCallback(SchoolDatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun populateCompleteOfflineData(db: SchoolDatabase) {
            val userDao = db.userDao()
            val classDao = db.schoolClassDao()
            val studentDao = db.studentDao()
            val examDao = db.examDao()
            val feeDao = db.feeDao()
            val attendanceDao = db.attendanceDao()
            val auditDao = db.auditLogDao()
            val academicYearDao = db.academicYearDao()
            val subjectDao = db.subjectDao()

            // 0. Academic Years
            val existingYears = academicYearDao.getAcademicYearByName("2025-2026")
            if (existingYears == null) {
                academicYearDao.insertAcademicYear(
                    AcademicYear(
                        yearName = "2025-2026",
                        isCurrent = true,
                        startDate = "2025-09-01",
                        endDate = "2026-06-30",
                        status = "ACTIVE",
                        createdAt = "2025-09-01 08:00:00"
                    )
                )
                academicYearDao.insertAcademicYear(
                    AcademicYear(
                        yearName = "2026-2027",
                        isCurrent = false,
                        startDate = "2026-09-01",
                        endDate = "2027-06-30",
                        status = "ACTIVE",
                        createdAt = "2026-08-01 08:00:00"
                    )
                )
            }

            // Standard 7 Subjects
            val standardSubjects = listOf(
                Subject(subjectCode = "Diin", subjectName = "Diinta Islam", grade = "All"),
                Subject(subjectCode = "Som", subjectName = "Af Soomaali", grade = "All"),
                Subject(subjectCode = "Car", subjectName = "Carabi", grade = "All"),
                Subject(subjectCode = "Eng", subjectName = "English", grade = "All"),
                Subject(subjectCode = "Xis", subjectName = "Xisaab", grade = "All"),
                Subject(subjectCode = "Say", subjectName = "Saynis", grade = "All"),
                Subject(subjectCode = "C/B", subjectName = "Cilmiga Bulshada", grade = "All")
            )
            subjectDao.insertSubjects(standardSubjects)

            // 1. Prepopulate Admin, Staff & Teacher Users
            val initialUsers = listOf(
                User(username = "admin", passwordHash = "2536", fullName = "Administrator", role = "ADMIN", assignedClassIds = "", isLocked = false, isHidden = false),
                User(username = "sma", passwordHash = "2536", fullName = "SMA Admin", role = "SUPER_ADMIN", assignedClassIds = "", isLocked = false, isHidden = false),
                User(username = "sacad", passwordHash = "3746", fullName = "Sacad Admin 1", role = "ADMIN", assignedClassIds = "", isLocked = false, isHidden = false),
                User(username = "sacad", passwordHash = "3746", fullName = "Sacad Admin 2", role = "ADMIN", assignedClassIds = "", isLocked = false, isHidden = true),
                User(username = "cashier", passwordHash = "1234", fullName = "School Cashier", role = "CASHIER", assignedClassIds = "", isLocked = false, isHidden = false),
                User(username = "khasnaji", passwordHash = "1234", fullName = "Khasnaji Guud", role = "CASHIER", assignedClassIds = "", isLocked = false, isHidden = false),
                User(username = "teacher", passwordHash = "1234", fullName = "Macalin Guud", role = "TEACHER", assignedClassIds = "1,2", isLocked = false, isHidden = false),
                User(username = "teacher1", passwordHash = "1234", fullName = "Mr. Ahmed Omar", role = "TEACHER", assignedClassIds = "1", isLocked = false, isHidden = false),
                User(username = "macalin", passwordHash = "1234", fullName = "Macalin Hassan", role = "TEACHER", assignedClassIds = "1,2,3", isLocked = false, isHidden = false),
                User(username = "macalin1", passwordHash = "1234", fullName = "Macalin Cabdi", role = "TEACHER", assignedClassIds = "2", isLocked = false, isHidden = false)
            )
            for (u in initialUsers) {
                if (userDao.getUserByUsername(u.username) == null) {
                    userDao.insertUser(u)
                }
            }

            // 2. Prepopulate Classes
            val class1Id = classDao.insertClass(
                SchoolClass(
                    name = "Form 1A (Grade 9)",
                    grade = "9",
                    section = "A",
                    academicYearId = "2025-2026",
                    startDate = "01-09-2025",
                    endDate = "30-06-2026",
                    inchargeTeacher = "Mr. Ahmed Omar"
                )
            )

            val class2Id = classDao.insertClass(
                SchoolClass(
                    name = "Form 1B (Grade 9)",
                    grade = "9",
                    section = "B",
                    academicYearId = "2025-2026",
                    startDate = "01-09-2025",
                    endDate = "30-06-2026",
                    inchargeTeacher = "Mrs. Fatima Hassan"
                )
            )

            val class3Id = classDao.insertClass(
                SchoolClass(
                    name = "Form 2A (Grade 10)",
                    grade = "10",
                    section = "A",
                    academicYearId = "2025-2026",
                    startDate = "01-09-2025",
                    endDate = "30-06-2026",
                    inchargeTeacher = "Macalin Hassan"
                )
            )

            val class4Id = classDao.insertClass(
                SchoolClass(
                    name = "Form 3A (Grade 11)",
                    grade = "11",
                    section = "A",
                    academicYearId = "2025-2026",
                    startDate = "01-09-2025",
                    endDate = "30-06-2026",
                    inchargeTeacher = "Macalin Guud"
                )
            )

            // Link classes to teacher
            userDao.insertUser(
                User(
                    username = "macalin_ahmed",
                    passwordHash = "1234",
                    fullName = "Mr. Ahmed Omar",
                    role = "TEACHER",
                    assignedClassIds = "$class1Id",
                    isLocked = false
                )
            )

            // 3. Prepopulate Students
            val s1Id = studentDao.insertStudent(
                Student(
                    studentId = "AUTO-001",
                    admissionNumber = "ADM-2025-001",
                    name = "Maxamed Cali Xasan",
                    gender = "Male",
                    motherName = "Aamina Jaamac",
                    phone = "+252615123456",
                    classId = class1Id,
                    academicYearId = "2025-2026",
                    isFree = false
                )
            )

            val s2Id = studentDao.insertStudent(
                Student(
                    studentId = "AUTO-002",
                    admissionNumber = "ADM-2025-002",
                    name = "Caasha Cabdi Faarax",
                    gender = "Female",
                    motherName = "Maryam Siciid",
                    phone = "+252634889900",
                    classId = class1Id,
                    academicYearId = "2025-2026",
                    isFree = true // Deeq Waxbarasho (Free)
                )
            )

            val s3Id = studentDao.insertStudent(
                Student(
                    studentId = "AUTO-003",
                    admissionNumber = "ADM-2025-003",
                    name = "Cabdiraxmaan Cumar Warsame",
                    gender = "Male",
                    motherName = "Faadumo Cilmi",
                    phone = "+252612334455",
                    classId = class1Id,
                    academicYearId = "2025-2026",
                    isFree = false
                )
            )

            val s4Id = studentDao.insertStudent(
                Student(
                    studentId = "AUTO-004",
                    admissionNumber = "ADM-2025-004",
                    name = "Nimco Xuseen Guuleed",
                    gender = "Female",
                    motherName = "Sahra Xaashi",
                    phone = "+252618991122",
                    classId = class2Id,
                    academicYearId = "2025-2026",
                    isFree = false
                )
            )

            val s5Id = studentDao.insertStudent(
                Student(
                    studentId = "AUTO-005",
                    admissionNumber = "ADM-2025-005",
                    name = "Khaalid Jaamac Axmed",
                    gender = "Male",
                    motherName = "Khadra Shire",
                    phone = "+252637112233",
                    classId = class3Id,
                    academicYearId = "2025-2026",
                    isFree = false
                )
            )

            // 4. Prepopulate Standard Subject Exams
            val subjectsList = listOf(
                "Diin" to "Term 1 - Diin",
                "Som" to "Term 1 - Soomaali",
                "Car" to "Term 1 - Carabi",
                "Eng" to "Term 1 - English",
                "Xis" to "Term 1 - Xisaab",
                "Say" to "Term 1 - Saynis",
                "C/B" to "Term 1 - Cilmiga Bulshada"
            )

            val examIds = mutableListOf<Long>()
            for ((subKey, exName) in subjectsList) {
                val eid = examDao.insertExam(
                    Exam(
                        classId = class1Id,
                        name = exName,
                        subject = subKey,
                        date = "2026-02-15",
                        totalMarks = 50.0,
                        passMarks = 25.0,
                        academicYearId = "2025-2026",
                        status = "PUBLISHED"
                    )
                )
                examIds.add(eid)
            }

            // 5. Prepopulate Realistic Marks for Students
            if (examIds.isNotEmpty()) {
                val marks = listOf(
                    // Maxamed Cali Xasan (s1)
                    ExamMark(examId = examIds[0], studentId = s1Id, score = 48.0, isAbsent = false),
                    ExamMark(examId = examIds[1], studentId = s1Id, score = 45.0, isAbsent = false),
                    ExamMark(examId = examIds[2], studentId = s1Id, score = 46.0, isAbsent = false),
                    ExamMark(examId = examIds[3], studentId = s1Id, score = 44.0, isAbsent = false),
                    ExamMark(examId = examIds[4], studentId = s1Id, score = 50.0, isAbsent = false),
                    ExamMark(examId = examIds[5], studentId = s1Id, score = 47.0, isAbsent = false),
                    ExamMark(examId = examIds[6], studentId = s1Id, score = 49.0, isAbsent = false),

                    // Caasha Cabdi Faarax (s2)
                    ExamMark(examId = examIds[0], studentId = s2Id, score = 46.0, isAbsent = false),
                    ExamMark(examId = examIds[1], studentId = s2Id, score = 48.0, isAbsent = false),
                    ExamMark(examId = examIds[2], studentId = s2Id, score = 43.0, isAbsent = false),
                    ExamMark(examId = examIds[3], studentId = s2Id, score = 45.0, isAbsent = false),
                    ExamMark(examId = examIds[4], studentId = s2Id, score = 47.0, isAbsent = false),
                    ExamMark(examId = examIds[5], studentId = s2Id, score = 44.0, isAbsent = false),
                    ExamMark(examId = examIds[6], studentId = s2Id, score = 48.0, isAbsent = false),

                    // Cabdiraxmaan Cumar Warsame (s3)
                    ExamMark(examId = examIds[0], studentId = s3Id, score = 38.0, isAbsent = false),
                    ExamMark(examId = examIds[1], studentId = s3Id, score = 35.0, isAbsent = false),
                    ExamMark(examId = examIds[2], studentId = s3Id, score = 40.0, isAbsent = false),
                    ExamMark(examId = examIds[3], studentId = s3Id, score = 32.0, isAbsent = false),
                    ExamMark(examId = examIds[4], studentId = s3Id, score = 36.0, isAbsent = false),
                    ExamMark(examId = examIds[5], studentId = s3Id, score = 39.0, isAbsent = false),
                    ExamMark(examId = examIds[6], studentId = s3Id, score = 37.0, isAbsent = false)
                )
                examDao.insertMarks(marks)
            }

            // 6. Prepopulate Fees Records
            feeDao.insertFeeRecord(
                FeeRecord(
                    classId = class1Id,
                    studentId = s1Id,
                    feeType = "Tuition",
                    amount = 25.0,
                    currency = "USD",
                    dueDate = "2026-02-05",
                    month = "Febraayo 2026",
                    paidStatus = "Paid",
                    paidDate = "2026-02-03",
                    notes = "Bixiyey Kaash",
                    academicYearId = "2025-2026"
                )
            )
            feeDao.insertFeeRecord(
                FeeRecord(
                    classId = class1Id,
                    studentId = s3Id,
                    feeType = "Tuition",
                    amount = 25.0,
                    currency = "USD",
                    dueDate = "2026-02-05",
                    month = "Febraayo 2026",
                    paidStatus = "Pending",
                    paidDate = "",
                    notes = "Waa la sugayaa",
                    academicYearId = "2025-2026"
                )
            )

            // 7. Initial Audit Trail
            auditDao.insertAuditLog(
                AuditLog(
                    timestamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()),
                    userName = "System Administrator",
                    userRole = "SUPER_ADMIN",
                    actionCategory = "SYSTEM_INITIALIZE",
                    title = "Nidaamka Dugsiga iyo Keydka Firebase waa la Diyaariyay",
                    className = "All Classes",
                    details = "Fasallada, Ardayda, Maadooyinka iyo Nidaamka Amniga & Kaabayaasha waa la bilaabay.",
                    status = "SUCCESS"
                )
            )
        }
    }

    private class SchoolDatabaseCallback(
        private val scope: CoroutineScope
    ) : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            try {
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_students_classId ON students(classId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_students_status ON students(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_attendance_studentId ON attendance(studentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_attendance_classId ON attendance(classId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_attendance_date ON attendance(date)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_exam_marks_examId ON exam_marks(examId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_exam_marks_studentId ON exam_marks(studentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_exams_classId ON exams(classId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_fee_records_studentId ON fee_records(studentId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_fee_records_classId ON fee_records(classId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON audit_logs(timestamp)")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                scope.launch(Dispatchers.IO) {
                    val userDao = database.userDao()
                    val academicYearDao = database.academicYearDao()
                    val subjectDao = database.subjectDao()

                    // Initialize Academic Years
                    if (academicYearDao.getAcademicYearByName("2025-2026") == null) {
                        academicYearDao.insertAcademicYear(
                            AcademicYear(
                                yearName = "2025-2026",
                                isCurrent = true,
                                startDate = "2025-09-01",
                                endDate = "2026-06-30",
                                status = "ACTIVE",
                                createdAt = "2025-09-01 08:00:00"
                            )
                        )
                    }

                    // Standard 7 Subjects
                    val standardSubjects = listOf(
                        Subject(subjectCode = "Diin", subjectName = "Diinta Islam", grade = "All"),
                        Subject(subjectCode = "Som", subjectName = "Af Soomaali", grade = "All"),
                        Subject(subjectCode = "Car", subjectName = "Carabi", grade = "All"),
                        Subject(subjectCode = "Eng", subjectName = "English", grade = "All"),
                        Subject(subjectCode = "Xis", subjectName = "Xisaab", grade = "All"),
                        Subject(subjectCode = "Say", subjectName = "Saynis", grade = "All"),
                        Subject(subjectCode = "C/B", subjectName = "Cilmiga Bulshada", grade = "All")
                    )
                    subjectDao.insertSubjects(standardSubjects)

                    // System Admin Users (NO demo students or demo marks automatically inserted)
                    val initialUsers = listOf(
                        User(username = "admin", passwordHash = "2536", fullName = "Administrator", role = "ADMIN", assignedClassIds = "", isLocked = false, isHidden = false),
                        User(username = "sma", passwordHash = "2536", fullName = "SMA Admin", role = "SUPER_ADMIN", assignedClassIds = "", isLocked = false, isHidden = false),
                        User(username = "sacad", passwordHash = "3746", fullName = "Sacad Admin 1", role = "ADMIN", assignedClassIds = "", isLocked = false, isHidden = false),
                        User(username = "sacad", passwordHash = "3746", fullName = "Sacad Admin 2", role = "ADMIN", assignedClassIds = "", isLocked = false, isHidden = true)
                    )
                    for (u in initialUsers) {
                        if (userDao.getUserByUsername(u.username) == null) {
                            userDao.insertUser(u)
                        }
                    }
                }
            }
        }
    }
}
