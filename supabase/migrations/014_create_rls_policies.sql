-- ==========================================================
-- 014_create_rls_policies.sql
-- Row Level Security (RLS) Policies & Access Permissions
-- ==========================================================

-- Enable RLS across all tables
ALTER TABLE public.schools ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.app_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.academic_years ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.classes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.teachers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.subjects ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.parents ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.students ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.student_class_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendance ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.exams ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.exam_marks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.mark_change_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.fee_types ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.fee_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.expenses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.announcements ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.recycle_bin ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.backup_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.school_sync ENABLE ROW LEVEL SECURITY;

-- Helper Function: Get Current User Role
CREATE OR REPLACE FUNCTION public.get_current_user_role()
RETURNS VARCHAR AS $$
    SELECT COALESCE(
        (SELECT role::varchar FROM public.profiles WHERE id = auth.uid()),
        'ANON'
    );
$$ LANGUAGE sql STABLE SECURITY DEFINER;

-- 1. Policies for Schools
DROP POLICY IF EXISTS "Allow anon and auth read schools" ON public.schools;
CREATE POLICY "Allow anon and auth read schools" ON public.schools FOR SELECT TO anon, authenticated USING (true);

DROP POLICY IF EXISTS "Allow anon and auth manage schools" ON public.schools;
CREATE POLICY "Allow anon and auth manage schools" ON public.schools FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

-- 2. Policies for School Sync Master Table
DROP POLICY IF EXISTS "Allow anon and auth all on school_sync" ON public.school_sync;
CREATE POLICY "Allow anon and auth all on school_sync" ON public.school_sync FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

-- 3. Policies for Students
DROP POLICY IF EXISTS "Allow full student access to anon and authenticated" ON public.students;
CREATE POLICY "Allow full student access to anon and authenticated" ON public.students FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

-- 4. Policies for Classes
DROP POLICY IF EXISTS "Allow full classes access to anon and authenticated" ON public.classes;
CREATE POLICY "Allow full classes access to anon and authenticated" ON public.classes FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

-- 5. Policies for Teachers
DROP POLICY IF EXISTS "Allow full teachers access to anon and authenticated" ON public.teachers;
CREATE POLICY "Allow full teachers access to anon and authenticated" ON public.teachers FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

-- 6. Policies for Subjects
DROP POLICY IF EXISTS "Allow full subjects access to anon and authenticated" ON public.subjects;
CREATE POLICY "Allow full subjects access to anon and authenticated" ON public.subjects FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

-- 7. Policies for Attendance
DROP POLICY IF EXISTS "Allow full attendance access to anon and authenticated" ON public.attendance;
CREATE POLICY "Allow full attendance access to anon and authenticated" ON public.attendance FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

-- 8. Policies for Exams & Exam Marks
DROP POLICY IF EXISTS "Allow full exams access to anon and authenticated" ON public.exams;
CREATE POLICY "Allow full exams access to anon and authenticated" ON public.exams FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow full exam_marks access to anon and authenticated" ON public.exam_marks;
CREATE POLICY "Allow full exam_marks access to anon and authenticated" ON public.exam_marks FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

-- 9. Policies for Fees & Payments
DROP POLICY IF EXISTS "Allow full fees access to anon and authenticated" ON public.fee_records;
CREATE POLICY "Allow full fees access to anon and authenticated" ON public.fee_records FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow full payments access to anon and authenticated" ON public.payments;
CREATE POLICY "Allow full payments access to anon and authenticated" ON public.payments FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

-- 10. Policies for Announcements & Audit Logs
DROP POLICY IF EXISTS "Allow full announcements access to anon and authenticated" ON public.announcements;
CREATE POLICY "Allow full announcements access to anon and authenticated" ON public.announcements FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow full audit_logs access to anon and authenticated" ON public.audit_logs;
CREATE POLICY "Allow full audit_logs access to anon and authenticated" ON public.audit_logs FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

-- 11. Policies for Backup & Recycle Bin
DROP POLICY IF EXISTS "Allow full backup_records access to anon and authenticated" ON public.backup_records;
CREATE POLICY "Allow full backup_records access to anon and authenticated" ON public.backup_records FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow full recycle_bin access to anon and authenticated" ON public.recycle_bin;
CREATE POLICY "Allow full recycle_bin access to anon and authenticated" ON public.recycle_bin FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

-- 12. Policies for App Users & Profiles
DROP POLICY IF EXISTS "Allow full app_users access to anon and authenticated" ON public.app_users;
CREATE POLICY "Allow full app_users access to anon and authenticated" ON public.app_users FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow full profiles access to anon and authenticated" ON public.profiles;
CREATE POLICY "Allow full profiles access to anon and authenticated" ON public.profiles FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow full academic_years access to anon and authenticated" ON public.academic_years;
CREATE POLICY "Allow full academic_years access to anon and authenticated" ON public.academic_years FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

DROP POLICY IF EXISTS "Allow full parents access to anon and authenticated" ON public.parents;
CREATE POLICY "Allow full parents access to anon and authenticated" ON public.parents FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
