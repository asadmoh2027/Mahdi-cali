package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.data.Exam
import com.example.ui.SchoolViewModel
import com.example.ui.components.SyncLoadingScreen
import com.example.ui.screens.*
import com.example.ui.theme.SchoolSystemTheme
import kotlinx.coroutines.flow.collectLatest

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SchoolSystemTheme {
                val viewModel: SchoolViewModel = viewModel()
                val context = LocalContext.current
                val navController = rememberNavController()

                // State Flow Collectors
                val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
                val classes by viewModel.classes.collectAsStateWithLifecycle()
                val students by viewModel.students.collectAsStateWithLifecycle()
                val exams by viewModel.exams.collectAsStateWithLifecycle()
                val fees by viewModel.fees.collectAsStateWithLifecycle()
                val attendance by viewModel.allAttendance.collectAsStateWithLifecycle()
                val allMarks by viewModel.allExamMarks.collectAsStateWithLifecycle()
                val users by viewModel.users.collectAsStateWithLifecycle()
                val schoolName by viewModel.schoolName.collectAsStateWithLifecycle()
                val subjects by viewModel.subjects.collectAsStateWithLifecycle()
                val selectedShift by viewModel.selectedShift.collectAsStateWithLifecycle()

                val searchedReportCard by viewModel.searchedReportCard.collectAsStateWithLifecycle()

                val classroomsCount by viewModel.classroomsCount.collectAsStateWithLifecycle()
                val chairsCount by viewModel.chairsCount.collectAsStateWithLifecycle()
                val toiletsCount by viewModel.toiletsCount.collectAsStateWithLifecycle()
                val officesCount by viewModel.officesCount.collectAsStateWithLifecycle()
                val kitchenFeedingCount by viewModel.kitchenFeedingCount.collectAsStateWithLifecycle()

                val lastCloudSyncTime by viewModel.lastCloudSyncTime.collectAsStateWithLifecycle()
                val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
                val autoCloudSyncEnabled by viewModel.autoCloudSyncEnabled.collectAsStateWithLifecycle()
                // val statusMessage by viewModel.statusMessage.collectAsStateWithLifecycle()
                // val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()

                // Toast Messages
                LaunchedEffect(Unit) {
                    viewModel.uiMessage.collectLatest { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }

                // Temporary active exam selected for mark editing
                var activeExam by remember { mutableStateOf<Exam?>(null) }
                var loginErrorMessage by remember { mutableStateOf<String?>(null) }
                val isStartupLoading by viewModel.isStartupLoading.collectAsStateWithLifecycle()

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isStartupLoading) {
                        SyncLoadingScreen(status = syncStatus)
                    } else {
                        NavHost(
                            navController = navController,
                            startDestination = if (currentUser != null) "dashboard" else "login",
                            modifier = Modifier.fillMaxSize()
                        ) {
                    // Welcome Landing Screen (still accessible if needed)
                    composable("welcome") {
                        WelcomeScreen(
                            schoolName = schoolName,
                            onProceedToLogin = { navController.navigate("login") },
                            onOpenStudentPortal = { navController.navigate("portal") }
                        )
                    }

                    // Login Screen (Primary Start Screen)
                    composable("login") {
                        LoginScreen(
                            onLoginClick = { user, pass ->
                                viewModel.login(user, pass) { success, msg ->
                                    if (success) {
                                        loginErrorMessage = null
                                        navController.navigate("dashboard") {
                                            popUpTo("login") { inclusive = true }
                                        }
                                    } else {
                                        loginErrorMessage = msg
                                    }
                                }
                            },
                            onOpenPortalClick = {
                                navController.navigate("portal")
                            },
                            errorMessage = loginErrorMessage,
                            onBackClick = null
                        )
                    }

                    // Dashboard Screen
                    composable("dashboard") {
                        DashboardScreen(
                            user = currentUser,
                            schoolName = schoolName,
                            selectedShift = selectedShift,
                            onShiftSelected = { shift -> viewModel.setSelectedShift(shift) },
                            classList = classes,
                            studentList = students,
                            userList = users,
                            attendanceList = attendance,
                            classroomsCount = classroomsCount,
                            chairsCount = chairsCount,
                            toiletsCount = toiletsCount,
                            officesCount = officesCount,
                            kitchenFeedingCount = kitchenFeedingCount,
                            onUpdateFacilities = { cls, chr, tlt, off, ktc ->
                                viewModel.updateSchoolFacilities(cls, chr, tlt, off, ktc)
                            },
                            onNavigate = { route ->
                                navController.navigate(route)
                            },
                            onLogoutClick = {
                                viewModel.logout()
                                navController.navigate("login") {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        )
                    }

                    // Student Exam Result Portal Screen
                    composable("portal") {
                        StudentPortalScreen(
                            reportCard = searchedReportCard,
                            onSearchClick = { query ->
                                viewModel.searchStudentPortal(query)
                            },
                            onPrintClick = { card ->
                                viewModel.printReportCardHtml(context, card)
                            },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    // Classes Screen
                    composable("classes") {
                        ClassesScreen(
                            classes = classes,
                            currentUser = currentUser,
                            selectedShift = selectedShift,
                            onShiftSelected = { shift -> viewModel.setSelectedShift(shift) },
                            onAddClassClick = { name, teacher, start, end, shift ->
                                viewModel.addClass(name, teacher, start, end, shift)
                            },
                            onDeleteClassClick = { id ->
                                viewModel.deleteClass(id)
                            },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    // Students Directory Screen
                    composable("students") {
                        StudentsScreen(
                            students = students,
                            classes = classes,
                            currentUser = currentUser,
                            fees = fees,
                            exams = exams,
                            marks = allMarks,
                            attendance = attendance,
                            onAddStudentClick = { navController.navigate("add_student") },
                            onBulkUploadClick = { classId, csvData, callback ->
                                viewModel.bulkAddStudents(classId, csvData, callback)
                            },
                            onDownloadSampleSheet = { viewModel.downloadSampleStudentCsv(context) },
                            onExportStudentsCsv = { classId -> viewModel.exportStudentsExcel(this@MainActivity, classId) },
                            onPrintStudentsListMarkHtml = { classId, subject, examTitle, maxMarks, yr, teacher ->
                                viewModel.printStudentsListMarkHtml(this@MainActivity, classId, subject, examTitle, maxMarks, yr, teacher)
                            },
                            onExportStudentsListMarkCsv = { classId, subject, examTitle, maxMarks ->
                                viewModel.exportStudentsListMarkCsv(this@MainActivity, classId, subject, examTitle, maxMarks)
                            },
                            onDeleteStudentClick = { id -> viewModel.deleteStudent(id) },
                            onDeleteAllClassStudentsClick = { classId -> viewModel.deleteClassStudents(classId) },
                            onToggleFreeClick = { id, isFree -> viewModel.toggleStudentFree(id, isFree) },
                            schoolName = schoolName,
                            onPrintStudentReport = { student -> viewModel.printSingleStudentReport(this@MainActivity, student) },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    // Add Student Screen
                    composable("add_student") {
                        var nextId by remember { mutableStateOf("AUTO-001") }
                        LaunchedEffect(Unit) {
                            nextId = viewModel.repository.getNextStudentId()
                        }
                        AddStudentScreen(
                            nextStudentId = nextId,
                            classes = classes,
                            currentUser = currentUser,
                            onSaveStudentClick = { name, gender, mother, phone, classId ->
                                viewModel.addStudent(name, gender, mother, phone, classId)
                                navController.popBackStack()
                            },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    // Attendance Screen
                    composable("attendance") {
                        AttendanceScreen(
                            classes = classes,
                            students = students,
                            existingAttendance = attendance,
                            currentUser = currentUser,
                            onSaveAttendanceClick = { classId, date, records ->
                                viewModel.saveAttendance(classId, date, records)
                            },
                            onPrintMonthlySheet = { classId, yearMonth ->
                                viewModel.printMonthlyAttendanceSheetHtml(this@MainActivity, classId, yearMonth)
                            },
                            onExportMonthlyCsv = { classId, yearMonth ->
                                viewModel.exportMonthlyAttendanceCsv(this@MainActivity, classId, yearMonth)
                            },
                            onPrintStudentLateWarning = { student, sRecords, clsName ->
                                viewModel.printStudentLateWarningHtml(this@MainActivity, student, sRecords, clsName)
                            },
                            onPrintClassLateReport = { classId, yearMonth ->
                                viewModel.printClassLateReportHtml(this@MainActivity, classId, yearMonth)
                            },
                            onExportClassLateReportCsv = { classId, yearMonth ->
                                viewModel.exportClassLateReportCsv(this@MainActivity, classId, yearMonth)
                            },
                            onDeleteAttendanceLog = { classId, date ->
                                viewModel.deleteAttendanceLog(classId, date)
                            },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    // Exams List Screen
                    composable("exams") {
                        ExamsScreen(
                            exams = exams,
                            classes = classes,
                            currentUser = currentUser,
                            onAddExamClick = { classId, name, subject, total, pass, date ->
                                viewModel.addExam(classId, name, subject, total, pass, date)
                            },
                            onUpdateExamClick = { updatedExam ->
                                viewModel.updateExam(updatedExam)
                            },
                            onEditMarksClick = { exam ->
                                activeExam = exam
                                navController.navigate("edit_marks")
                            },
                            onDeleteExamClick = { id -> viewModel.deleteExam(id) },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    // Edit Marks Screen
                    composable("edit_marks") {
                        val currentExam = activeExam
                        if (currentExam == null) {
                            LaunchedEffect(Unit) { navController.popBackStack() }
                        } else {
                            val examStudents = students.filter { it.classId == currentExam.classId }
                            val existingMarks by viewModel.repository.getMarksForExam(currentExam.id).collectAsStateWithLifecycle(initialValue = emptyList())

                            EditMarksScreen(
                                exam = currentExam,
                                classes = classes,
                                students = examStudents,
                                existingMarks = existingMarks,
                                onUpdateExamClick = { updatedExam ->
                                    activeExam = updatedExam
                                    viewModel.updateExam(updatedExam)
                                },
                                onSaveMarksClick = { list ->
                                    viewModel.saveExamMarks(currentExam.id, list)
                                    navController.popBackStack()
                                },
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }

                    // Fees Register Screen
                    composable("fees") {
                        FeesScreen(
                            fees = fees,
                            students = students,
                            classes = classes,
                            currentUser = currentUser,
                            onRecordFeePayment = { studentId, classId, amt, curr, type, notes, cb ->
                                viewModel.recordStudentPayment(studentId, classId, amt, curr, type, notes, cb)
                            },
                            onDeleteFeeClick = { id -> viewModel.deleteFee(id) },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    // Administrator Transparency & Teacher Audit Dashboard
                    composable("transparency") {
                        TransparencyDashboardScreen(
                            viewModel = viewModel,
                            currentUser = currentUser,
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    // Add Fee Screen
                    composable("add_fee") {
                        AddFeeScreen(
                            students = students,
                            classes = classes,
                            onSaveFeeClick = { classId, studentId, type, amount, currency, dueDate, month, notes ->
                                viewModel.addFee(classId, studentId, type, amount, currency, dueDate, month, notes)
                                navController.popBackStack()
                            },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    // Reports Screen
                    composable("reports") {
                        ReportsScreen(
                            schoolName = schoolName,
                            onSaveSchoolName = { name -> viewModel.saveSchoolName(name) },
                            classes = classes,
                            students = students,
                            users = users,
                            exams = exams,
                            fees = fees,
                            attendance = attendance,
                            classroomsCount = classroomsCount,
                            chairsCount = chairsCount,
                            toiletsCount = toiletsCount,
                            officesCount = officesCount,
                            kitchenFeedingCount = kitchenFeedingCount,
                            onUpdateFacilities = { classrooms, chairs, toilets, offices, kitchenFeeding ->
                                viewModel.updateSchoolFacilities(classrooms, chairs, toilets, offices, kitchenFeeding)
                            },
                            onPrintSchoolOverviewReport = { viewModel.printSchoolOverviewReportHtml(context) },
                            onExportExamCsv = { viewModel.exportExamReportCSV(context) },
                            onExportFeeCsv = { viewModel.exportFeeReportCSV(context) },
                            onPrintSingleStudentReport = { student -> viewModel.printSingleStudentReport(context, student) },
                            onPrintAllStudentsReportCards = { classId -> viewModel.printAllStudentsComprehensiveReportsHtml(context, classId) },
                            onPrintFeeReport = { viewModel.printFeeReportHtml(context) },
                            onPrintClassReport = { classId -> viewModel.printClassReportHtml(context, classId) },
                            onPrintAttendanceReport = { classId, yearMonth -> viewModel.printAttendanceReportHtml(context, classId, yearMonth) },
                            onPrintExamReport = { classId -> viewModel.printExamReportHtml(context, classId) },
                            onOpenClearanceClick = { navController.navigate("clearance") },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    // Promotion Screen (Gudbinta Ardayda)
                    composable("promotion") {
                        PromotionScreen(
                            classes = classes,
                            students = students,
                            exams = exams,
                            allMarks = allMarks,
                            onPromoteStudents = { studentIds, targetClassId ->
                                viewModel.promoteStudents(studentIds, targetClassId)
                            },
                            onUpdateSingleStudentClass = { studentId, targetClassId ->
                                viewModel.updateStudentClass(studentId, targetClassId)
                            },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    // Clearance & Student Record Sheet (Warqadda Ardayga)
                    composable("clearance") {
                        StudentClearanceScreen(
                            schoolName = schoolName,
                            classes = classes,
                            students = students,
                            exams = exams,
                            allMarks = allMarks,
                            onPrintClearanceHtml = { ctx, student, prevSch, destSch, destCls, acYr ->
                                viewModel.printStudentClearanceHtml(ctx, student, prevSch, destSch, destCls, acYr)
                            },
                            onPrintAllClassClearancesHtml = { ctx, classId, prevSch, destSch, destCls, acYr ->
                                viewModel.printAllClassClearancesHtml(ctx, classId, prevSch, destSch, destCls, acYr)
                            },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    // Student Marksheet (Official Results Card)
                    composable("marksheet") {
                        StudentMarksheetScreen(
                            schoolName = schoolName,
                            classes = classes,
                            students = students,
                            exams = exams,
                            allMarks = allMarks,
                            onPrintMarksheetHtml = { ctx, student, acYr, destCls ->
                                viewModel.printStudentMarksheetHtml(ctx, student, acYr, destCls)
                            },
                            onPrintAllClassMarksheetsHtml = { ctx, classId, acYr, destCls ->
                                viewModel.printAllClassMarksheetsHtml(ctx, classId, acYr, destCls)
                            },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    // Password & User Management Screen
                    composable("users") {
                        UserManagementScreen(
                            currentUser = currentUser,
                            users = users,
                            classes = classes,
                            onChangePasswordClick = { oldPass, newPass, callback ->
                                viewModel.changePassword(oldPass, newPass, callback)
                            },
                            onCreateTeacherClick = { u, p, name, assigned, callback ->
                                viewModel.createTeacher(u, p, name, assigned, callback)
                            },
                            onCreateCashierClick = { u, p, name, callback ->
                                viewModel.createCashier(u, p, name, callback)
                            },
                            onResetPasswordClick = { id, newPass, callback ->
                                viewModel.adminResetUserPassword(id, newPass, callback)
                            },
                            onToggleUserLock = { id, isLocked ->
                                viewModel.toggleUserLock(id, isLocked)
                            },
                            onDeleteUserClick = { id -> viewModel.deleteUser(id) },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    // Backup & Restore Screen
                    composable("backup") {
                        BackupScreen(
                            viewModel = viewModel,
                            classes = classes,
                            lastCloudSyncTime = lastCloudSyncTime,
                            autoCloudSyncEnabled = autoCloudSyncEnabled,
                            onToggleAutoCloudSync = { enabled -> viewModel.setAutoCloudSync(enabled) },
                            onSyncToCloudClick = { callback -> viewModel.syncToCloud(context, callback) },
                            onRestoreFromCloudClick = { pass, callback -> viewModel.restoreFromCloud(context, pass, callback) },

                            onLoadStarterDataClick = { callback -> viewModel.loadStarterOfflineData(callback) },
                            onSyncClassToCloudClick = { classId, callback -> viewModel.syncClassToCloud(context, classId, callback) },
                            onRestoreClassFromCloudClick = { classId, pass, callback -> viewModel.restoreClassFromCloud(context, classId, pass, callback) },
                            onExportBackupClick = { viewModel.exportBackup(context) },
                            onExportClassBackupClick = { classId -> viewModel.exportClassBackup(context, classId) },
                            onRestoreBackupClick = { jsonStr, callback ->
                                viewModel.restoreBackupJson(jsonStr, callback)
                            },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                    // Subjects Screen
                    composable("subjects") {
                        SubjectsScreen(
                            currentUser = currentUser,
                            subjects = subjects,
                            onAddSubject = { code, name, grade, callback ->
                                viewModel.addSubject(code, name, grade, callback)
                            },
                            onDeleteSubject = { id, callback ->
                                viewModel.deleteSubject(id, callback)
                            },
                            onBackClick = { navController.popBackStack() }
                        )
                    }

                }
            }
        }
    }
}
}
}
