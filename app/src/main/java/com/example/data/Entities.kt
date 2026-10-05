package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "academic_years")
data class AcademicYear(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val yearName: String, // e.g. "2025-2026", "2026-2027"
    val isCurrent: Boolean = true,
    val startDate: String = "2025-09-01",
    val endDate: String = "2026-06-30",
    val status: String = "ACTIVE", // "ACTIVE", "CLOSED", "ARCHIVED"
    val createdAt: String = "",
    val updatedAt: String = ""
)

@Entity(tableName = "subjects")
data class Subject(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectCode: String, // e.g. "DIIN", "SOM", "XIS"
    val subjectName: String, // e.g. "Diinta Islam", "Xisaab"
    val grade: String = "",
    val status: String = "ACTIVE",
    val createdAt: String = "",
    val updatedAt: String = ""
)

@Entity(tableName = "school_classes")
data class SchoolClass(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val grade: String = "",
    val section: String = "",
    val academicYearId: String = "2025-2026",
    val startDate: String = "",
    val endDate: String = "",
    val inchargeTeacher: String = "",
    val status: String = "ACTIVE", // "ACTIVE", "DELETED"
    val version: Long = 1L,
    val createdAt: String = "",
    val updatedAt: String = ""
)

@Entity(tableName = "students")
data class Student(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: String, // e.g. AUTO-001, AUTO-002
    val admissionNumber: String = "",
    val name: String,
    val gender: String = "Male",
    val motherName: String = "",
    val phone: String = "",
    val classId: Long,
    val academicYearId: String = "2025-2026",
    val isFree: Boolean = false, // Fee-exempt / Scholarship student
    val status: String = "ACTIVE", // "ACTIVE", "TRANSFERRED", "DELETED"
    val isDeleted: Boolean = false,
    val version: Long = 1L,
    val createdAt: String = "",
    val updatedAt: String = ""
)

@Entity(tableName = "student_class_history")
data class StudentClassHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val studentId: Long,
    val studentCode: String,
    val studentName: String,
    val previousClassId: Long,
    val previousClassName: String,
    val newClassId: Long,
    val newClassName: String,
    val academicYearId: String,
    val transferDate: String,
    val reason: String = "Promotion",
    val changedBy: String = "Admin"
)

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val username: String,
    val passwordHash: String, // Stored securely
    val fullName: String,
    val role: String = "TEACHER", // ADMIN, TEACHER, CASHIER, SUPER_ADMIN
    val assignedClassIds: String = "", // Comma separated class IDs e.g. "1,2"
    val isLocked: Boolean = false, // If true, user account is disabled by Admin
    val isHidden: Boolean = false, // If true, user account is hidden from UI lists
    val createdAt: String = "",
    val updatedAt: String = ""
) {
    fun getAssignedClassIdSet(): Set<Long> {
        if (assignedClassIds.isBlank()) return emptySet()
        return assignedClassIds.split(",").mapNotNull { it.trim().toLongOrNull() }.toSet()
    }
}

@Entity(tableName = "attendance")
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classId: Long,
    val studentId: Long,
    val date: String, // YYYY-MM-DD
    val status: String, // "Present", "Absent", "Free"
    val academicYearId: String = "2025-2026",
    val recordedBy: String = "",
    val recordedAt: String = ""
)

@Entity(tableName = "exams")
data class Exam(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classId: Long,
    val name: String,
    val subject: String,
    val date: String = "",
    val totalMarks: Double = 100.0,
    val passMarks: Double = 40.0,
    val academicYearId: String = "2025-2026",
    val status: String = "PUBLISHED", // "DRAFT", "PUBLISHED", "FINALIZED"
    val isFinalized: Boolean = false,
    val lockedBy: String = "",
    val lockedAt: String = "",
    val version: Long = 1L,
    val createdAt: String = "",
    val updatedAt: String = ""
)

@Entity(tableName = "exam_marks")
data class ExamMark(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val examId: Long,
    val studentId: Long,
    val score: Double = 0.0,
    val isAbsent: Boolean = false,
    val version: Long = 1L,
    val status: String = "ACTIVE",
    val updatedAt: String = "",
    val updatedBy: String = ""
)

@Entity(tableName = "mark_change_history")
data class MarkChangeHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val markId: Long,
    val examId: Long,
    val examName: String,
    val subject: String,
    val studentId: Long,
    val studentName: String,
    val oldScore: Double,
    val newScore: Double,
    val changedBy: String,
    val changedAt: String,
    val reason: String = "Manual Correction"
)

@Entity(tableName = "fee_records")
data class FeeRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val classId: Long,
    val studentId: Long,
    val feeType: String, // "Tuition", "Exam", "Registration", "Library", "Sports", "Transport", "Other"
    val amount: Double,
    val currency: String = "USD", // "USD", "SLS", "ETB"
    val dueDate: String = "",
    val month: String = "",
    val paidStatus: String = "Pending", // "Pending" or "Paid"
    val paidDate: String = "",
    val notes: String = "",
    val academicYearId: String = "2025-2026",
    val status: String = "ACTIVE",
    val version: Long = 1L
)

@Entity(tableName = "audit_logs")
data class AuditLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: String, // e.g. "2026-08-18 10:45:00"
    val userName: String, // "Mr. Ahmed Omar" or "Administrator"
    val userRole: String = "TEACHER", // "TEACHER", "ADMIN", "CASHIER", "SUPER_ADMIN"
    val actionCategory: String, // "CREATE", "UPDATE", "DELETE", "RESTORE", "BACKUP", "IMPORT", "EXPORT", "PROMOTE_STUDENT", "CHANGE_MARK", "UNLOCK_EXAM"
    val title: String, // e.g. "Diiwaangelinta Xaadirinta Class 10A"
    val className: String = "", // e.g. "Class 10A"
    val details: String = "", // e.g. "30 arday: 28 Jooga, 2 Maqan"
    val status: String = "SUCCESS", // "SUCCESS", "MODIFIED", "DELETED"
    val rawDataSummary: String = "" // Detailed breakdown for inspector dialog/expansion
)

@Entity(tableName = "recycle_bin")
data class RecycleBinItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String, // "STUDENT", "CLASS", "EXAM", "FEE"
    val originalId: Long,
    val itemIdentifier: String, // e.g. studentId or class name
    val itemName: String,
    val detailsJson: String,
    val deletedBy: String,
    val deletedAt: String,
    val academicYearId: String = "2025-2026"
)

@Entity(tableName = "backup_records")
data class BackupRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val backupId: String, // e.g. "backup_2026_08_24_001"
    val academicYearId: String = "2025-2026",
    val createdBy: String,
    val createdAt: String,
    val versionNumber: Long = 1L,
    val recordCount: Int,
    val checksum: String = "",
    val backupStatus: String = "VERIFIED", // "CREATED", "VERIFIED", "RESTORED", "FAILED"
    val notes: String = "",
    val jsonPayload: String = ""
)

@Entity(tableName = "announcements")
data class Announcement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val targetAudience: String = "ALL", // "ALL", "TEACHERS", "PARENTS", "STUDENTS"
    val category: String = "GENERAL", // "GENERAL", "EXAM", "FEE", "HOLIDAY", "EVENT"
    val isPinned: Boolean = false,
    val author: String = "Admin",
    val date: String = "",
    val createdAt: String = ""
)

@Entity(tableName = "sync_queue")
data class SyncQueue(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,
    val entityId: String,
    val operation: String, // INSERT, UPDATE, DELETE
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "PENDING"
)

