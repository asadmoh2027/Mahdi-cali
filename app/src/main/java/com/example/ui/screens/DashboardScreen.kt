package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.User
import com.example.ui.theme.*

data class MenuOption(
    val title: String,
    val subtitle: String,
    val icon: String,
    val route: String,
    val containerColor: Color = TealPrimary
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    user: User?,
    schoolName: String = "Mahdi Cali School",
    onNavigate: (String) -> Unit,
    onLogoutClick: () -> Unit
) {
    val role = user?.role ?: "ADMIN"
    val isAdmin = role == "ADMIN" || role == "SUPER_ADMIN"
    val isTeacher = role == "TEACHER"
    val isCashier = role == "CASHIER" || role == "ACCOUNTANT"
    val isParent = role == "PARENT"
    val isStudent = role == "STUDENT"

    val menuOptions = buildList {
        when {
            isParent -> {
                add(MenuOption("STUDENT PORTAL", "Raadi buundooyinka & warqadda dhibcaha", "🎓", "portal"))
                add(MenuOption("BEDEL PASSWORD", "Bedel furahaaga sirta ah", "🔐", "users"))
            }
            isStudent -> {
                add(MenuOption("NATIIJADEYDA", "Eeg buundooyinka & warbixinta imtixaanka", "🎓", "portal"))
                add(MenuOption("STUDENT MARKSHEET", "Warqadda natiijada sanadlaha ah", "📜", "marksheet"))
                add(MenuOption("BEDEL PASSWORD", "Bedel furahaaga sirta ah", "🔐", "users"))
            }
            isCashier -> {
                add(MenuOption("FEE REGISTER", "USD, SLS, ETB Fee tracking & qaadista", "💰", "fees"))
                add(MenuOption("STUDENTS", "Diiwaanka ardayda dugsiga", "👨‍🎓", "students"))
                add(MenuOption("REPORTS", "Warbixinta maaliyadda & fiiga", "📊", "reports"))
                add(MenuOption("PASSWORD & PROFILE", "Bedel password-kaaga", "🔐", "users"))
            }
            isTeacher -> {
                add(MenuOption("CLASSES", "Fasalladaada & macalimiinta", "🏫", "classes"))
                add(MenuOption("STUDENTS", "Diiwaanka ardayda", "👨‍🎓", "students"))
                add(MenuOption("ATTENDANCE", "Xaadirinta maalinlaha ah ee fasalka", "✅", "attendance"))
                add(MenuOption("EXAMS & MARKS", "Geli buundooyinka imtixaanka", "📝", "exams"))
                add(MenuOption("MAADOYINKA", "Liiska maadooyinka dugsiga", "📚", "subjects"))
                add(MenuOption("STUDENT MARKSHEET", "Warqadda dhibcaha ardayda", "📜", "marksheet"))
                add(MenuOption("REPORTS", "Warbixinnada fasallada", "📊", "reports"))
                add(MenuOption("PASSWORD & PROFILE", "Bedel password-kaaga", "🔐", "users"))
            }
            else -> {
                // Administrator / Super Admin
                add(MenuOption("CLASSES", "Manage school classes & teachers", "🏫", "classes"))
                add(MenuOption("STUDENTS", "Register & view students (AUTO IDs)", "👨‍🎓", "students"))
                add(MenuOption("MAADOYINKA", "Curriculum & school subjects", "📚", "subjects"))
                add(MenuOption("ATTENDANCE", "Daily class attendance tracking", "✅", "attendance"))
                add(MenuOption("EXAMS & MARKS", "Create exams & enter scores", "📝", "exams"))
                add(MenuOption("FEE REGISTER", "USD, SLS, ETB Fee tracking", "💰", "fees"))
                add(MenuOption("KORMEERKA & AUDIT", "Teacher activity, attendance & grading log", "👁️", "transparency"))
                add(MenuOption("REPORTS", "Class, Exam & Fee Reports + Excel", "📊", "reports"))
                add(MenuOption("STUDENT MARKSHEET", "Official Annual Report Card & Results", "📜", "marksheet"))
                add(MenuOption("WARQADDA ARDAYGA", "Student Clearance & Record Sheet", "📄", "clearance"))
                add(MenuOption("GUDBINTA ARDAYDA", "Annual Promotion (Pass 350+ / Fail <=349)", "📈", "promotion"))
                add(MenuOption("STUDENT PORTAL", "Search results by Student ID", "🎓", "portal"))
                add(MenuOption("PASSWORD & USERS", "Change password & manage multi-role accounts", "🔐", "users"))
                add(MenuOption("BACKUP & RESTORE", "Google Drive, Cloud Script & Firebase Sync", "📁", "backup"))
            }
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.school_logo),
                                contentDescription = "Logo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        }
                        Column {
                            Text(
                                text = schoolName.ifBlank { "School Management System" },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Logged in as: ${user?.fullName ?: "Admin"} (${user?.role ?: "ADMIN"})",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onLogoutClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Logout",
                            tint = Color.White
                        )
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
                .padding(16.dp)
        ) {
            // User Greeting Header Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = TealContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = TealPrimary,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = Color.White)
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Welcome, ${user?.fullName ?: "Admin"}!",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TealDark
                        )
                        val roleInfo = when (user?.role) {
                            "TEACHER" -> "Teacher Mode • Access Restricted to Permitted Classes"
                            "CASHIER" -> "Cashier Mode • Fee Register & Financial Management (View All Classes/Students)"
                            else -> "Administrator • Full System Control"
                        }
                        Text(
                            text = roleInfo,
                            fontSize = 11.sp,
                            color = DarkText
                        )
                    }
                }
            }

            // Grid Options
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(menuOptions) { option ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clickable { onNavigate(option.route) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = TealPrimary),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = option.icon,
                                fontSize = 28.sp
                            )

                            Column {
                                Text(
                                    text = option.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = option.subtitle,
                                    fontSize = 9.sp,
                                    color = Color.White.copy(alpha = 0.85f),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
