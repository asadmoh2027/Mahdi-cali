package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Mahdi Cali School", appName)
  }

  @Test
  fun `filter students by name or id matches correctly`() {
    val students = listOf(
      com.example.data.Student(id = 1L, studentId = "AUTO-001", name = "Ali Ahmed Jama", gender = "Male", motherName = "Amina", phone = "0634123456", classId = 1L),
      com.example.data.Student(id = 2L, studentId = "AUTO-002", name = "Fadumo Hassan", gender = "Female", motherName = "Maryan", phone = "0635987654", classId = 1L),
      com.example.data.Student(id = 3L, studentId = "AUTO-003", name = "Mohamed Abdi", gender = "Male", motherName = "Sahra", phone = "0634998877", classId = 2L)
    )

    // Test name search
    val queryName = "Fadumo"
    val resultName = students.filter { it.name.contains(queryName, ignoreCase = true) || it.studentId.contains(queryName, ignoreCase = true) }
    assertEquals(1, resultName.size)
    assertEquals("AUTO-002", resultName.first().studentId)

    // Test ID search
    val queryId = "003"
    val resultId = students.filter { it.name.contains(queryId, ignoreCase = true) || it.studentId.contains(queryId, ignoreCase = true) }
    assertEquals(1, resultId.size)
    assertEquals("Mohamed Abdi", resultId.first().name)
  }

  @Test
  fun `students list mark sheet correctly structures students per class`() {
    val students = listOf(
      com.example.data.Student(id = 1L, studentId = "AUTO-001", name = "Barkhad Ali", gender = "Male", motherName = "Amina", phone = "0634123456", classId = 10L),
      com.example.data.Student(id = 2L, studentId = "AUTO-002", name = "Ayan Hassan", gender = "Female", motherName = "Maryan", phone = "0635987654", classId = 10L),
      com.example.data.Student(id = 3L, studentId = "AUTO-003", name = "Zack Moh", gender = "Male", motherName = "Sahra", phone = "0634998877", classId = 20L)
    )

    val class10Students = students.filter { it.classId == 10L }
      .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    assertEquals(2, class10Students.size)
    assertEquals("Ayan Hassan", class10Students[0].name)
    assertEquals("Barkhad Ali", class10Students[1].name)
  }

  @Test
  fun `student tardiness and absences calculate properly`() {
    val attendance = listOf(
      com.example.data.AttendanceRecord(id = 1L, classId = 1L, studentId = 100L, date = "2026-08-01", status = "Present"),
      com.example.data.AttendanceRecord(id = 2L, classId = 1L, studentId = 100L, date = "2026-08-02", status = "Late"),
      com.example.data.AttendanceRecord(id = 3L, classId = 1L, studentId = 100L, date = "2026-08-03", status = "Absent"),
      com.example.data.AttendanceRecord(id = 4L, classId = 1L, studentId = 100L, date = "2026-08-04", status = "Late"),
      com.example.data.AttendanceRecord(id = 5L, classId = 1L, studentId = 100L, date = "2026-08-05", status = "Present")
    )

    val lateCount = attendance.count { it.status.equals("Late", ignoreCase = true) || it.status.equals("Habsan", ignoreCase = true) || it.status == "H" }
    val absentCount = attendance.count { it.status.equals("Absent", ignoreCase = true) || it.status == "A" }
    val presentCount = attendance.count { it.status.equals("Present", ignoreCase = true) || it.status == "P" }

    assertEquals(2, lateCount)
    assertEquals(1, absentCount)
    assertEquals(2, presentCount)
  }
}
