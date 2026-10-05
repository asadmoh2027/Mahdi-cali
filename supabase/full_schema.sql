-- =========================================================================
-- COMPLETE SUPABASE POSTGRESQL SCHEMA FOR SCHOOL MANAGEMENT SYSTEM (DUGSI)
-- Author: Full-Stack & Supabase Architect
-- Compatible with Supabase PostgreSQL, PostgREST, Storage & Auth
-- =========================================================================

-- Enable Required Extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. SCHOOLS (Multi-Tenant Organization Table)
CREATE TABLE IF NOT EXISTS public.schools (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    tagline VARCHAR(255) DEFAULT 'A School of Excellence & Integrity',
    principal_name VARCHAR(255) DEFAULT 'Principal',
    phone VARCHAR(50),
    email VARCHAR(255),
    website VARCHAR(255),
    address TEXT,
    city VARCHAR(100),
    country VARCHAR(100) DEFAULT 'Somalia',
    logo_url TEXT,
    stamp_url TEXT,
    currency VARCHAR(10) DEFAULT 'USD',
    academic_year_current VARCHAR(50) DEFAULT '2025-2026',
    grading_system JSONB DEFAULT '{
        "A": {"min": 90, "max": 100, "gpa": 4.0, "remarks": "Excellent"},
        "B": {"min": 80, "max": 89, "gpa": 3.0, "remarks": "Very Good"},
        "C": {"min": 70, "max": 79, "gpa": 2.0, "remarks": "Good"},
        "D": {"min": 50, "max": 69, "gpa": 1.0, "remarks": "Pass"},
        "F": {"min": 0, "max": 49, "gpa": 0.0, "remarks": "Fail"}
    }'::jsonb,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 2. ACADEMIC YEARS
CREATE TABLE IF NOT EXISTS public.academic_years (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    year_name VARCHAR(50) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    is_current BOOLEAN DEFAULT TRUE,
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CLOSED', 'ARCHIVED')),
    terms JSONB DEFAULT '[
        {"term_number": 1, "name": "Term 1", "start_date": "2025-09-01", "end_date": "2026-01-15"},
        {"term_number": 2, "name": "Term 2", "start_date": "2026-01-20", "end_date": "2026-06-30"}
    ]'::jsonb,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    CONSTRAINT uq_school_year UNIQUE (school_id, year_name)
);

-- 3. CLASSES
CREATE TABLE IF NOT EXISTS public.classes (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    grade VARCHAR(50) DEFAULT '',
    section VARCHAR(50) DEFAULT '',
    academic_year_id VARCHAR(50) DEFAULT '2025-2026',
    incharge_teacher VARCHAR(255) DEFAULT '',
    room_number VARCHAR(50),
    capacity INTEGER DEFAULT 45,
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DELETED', 'ARCHIVED')),
    version BIGINT DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    CONSTRAINT uq_school_class_name UNIQUE (school_id, name, academic_year_id)
);

-- 4. SUBJECTS
CREATE TABLE IF NOT EXISTS public.subjects (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    subject_code VARCHAR(50) NOT NULL,
    subject_name VARCHAR(255) NOT NULL,
    grade VARCHAR(50) DEFAULT '',
    pass_marks NUMERIC(5, 2) DEFAULT 40.0,
    total_marks NUMERIC(5, 2) DEFAULT 100.0,
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    CONSTRAINT uq_school_subject_code UNIQUE (school_id, subject_code, grade)
);

-- 5. PARENTS
CREATE TABLE IF NOT EXISTS public.parents (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    parent_code VARCHAR(50) NOT NULL,
    father_name VARCHAR(255) NOT NULL,
    mother_name VARCHAR(255),
    phone VARCHAR(50) NOT NULL,
    phone_alt VARCHAR(50),
    email VARCHAR(255),
    occupation VARCHAR(255),
    address TEXT,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    CONSTRAINT uq_school_parent_phone UNIQUE (school_id, phone)
);

-- 6. TEACHERS
CREATE TABLE IF NOT EXISTS public.teachers (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    teacher_id_code VARCHAR(50) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    gender VARCHAR(20) DEFAULT 'Male',
    phone VARCHAR(50) NOT NULL,
    email VARCHAR(255),
    address TEXT,
    qualification VARCHAR(255),
    specialization VARCHAR(255),
    date_of_joining DATE DEFAULT CURRENT_DATE,
    photo_url TEXT,
    assigned_class_ids TEXT DEFAULT '',
    assigned_subject_ids TEXT DEFAULT '',
    salary NUMERIC(12, 2) DEFAULT 0.00,
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'ON_LEAVE', 'TERMINATED', 'DELETED')),
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    CONSTRAINT uq_school_teacher_code UNIQUE (school_id, teacher_id_code)
);

-- 7. STUDENTS
CREATE TABLE IF NOT EXISTS public.students (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    parent_id BIGINT REFERENCES public.parents(id) ON DELETE SET NULL,
    student_id VARCHAR(50) NOT NULL,
    admission_number VARCHAR(100) DEFAULT '',
    name VARCHAR(255) NOT NULL,
    gender VARCHAR(20) DEFAULT 'Male' CHECK (gender IN ('Male', 'Female')),
    mother_name VARCHAR(255) DEFAULT '',
    date_of_birth DATE,
    phone VARCHAR(50) DEFAULT '',
    email VARCHAR(255),
    address TEXT,
    class_id BIGINT REFERENCES public.classes(id) ON DELETE SET NULL,
    academic_year_id VARCHAR(50) DEFAULT '2025-2026',
    admission_date DATE DEFAULT CURRENT_DATE,
    photo_url TEXT,
    is_free BOOLEAN DEFAULT FALSE,
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'TRANSFERRED', 'GRADUATED', 'DELETED')),
    version BIGINT DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    CONSTRAINT uq_school_student_id UNIQUE (school_id, student_id)
);

-- 8. ATTENDANCE
CREATE TABLE IF NOT EXISTS public.attendance (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    class_id BIGINT REFERENCES public.classes(id) ON DELETE CASCADE,
    student_id BIGINT REFERENCES public.students(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('Present', 'Absent', 'Late', 'Free', 'Excused')),
    academic_year_id VARCHAR(50) DEFAULT '2025-2026',
    remarks TEXT DEFAULT '',
    recorded_by VARCHAR(255) DEFAULT '',
    recorded_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    CONSTRAINT uq_student_class_date UNIQUE (school_id, student_id, class_id, date)
);

-- 9. EXAMS & EXAM MARKS
CREATE TABLE IF NOT EXISTS public.exams (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    class_id BIGINT REFERENCES public.classes(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    exam_date DATE DEFAULT CURRENT_DATE,
    total_marks NUMERIC(5, 2) DEFAULT 100.0 NOT NULL,
    pass_marks NUMERIC(5, 2) DEFAULT 40.0 NOT NULL,
    academic_year_id VARCHAR(50) DEFAULT '2025-2026',
    status VARCHAR(20) DEFAULT 'PUBLISHED' CHECK (status IN ('DRAFT', 'PUBLISHED', 'FINALIZED')),
    is_finalized BOOLEAN DEFAULT FALSE NOT NULL,
    locked_by VARCHAR(255) DEFAULT '',
    locked_at TIMESTAMPTZ,
    version BIGINT DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE TABLE IF NOT EXISTS public.exam_marks (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    exam_id BIGINT REFERENCES public.exams(id) ON DELETE CASCADE,
    student_id BIGINT REFERENCES public.students(id) ON DELETE CASCADE,
    score NUMERIC(5, 2) DEFAULT 0.0 NOT NULL,
    is_absent BOOLEAN DEFAULT FALSE NOT NULL,
    grade VARCHAR(10) DEFAULT '',
    is_passed BOOLEAN DEFAULT FALSE,
    teacher_remarks TEXT DEFAULT '',
    version BIGINT DEFAULT 1,
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DELETED')),
    updated_by VARCHAR(255) DEFAULT '',
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    CONSTRAINT uq_exam_student UNIQUE (exam_id, student_id)
);

-- 10. FEE TYPES, FEE RECORDS & PAYMENTS
CREATE TABLE IF NOT EXISTS public.fee_types (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    default_amount NUMERIC(10, 2) DEFAULT 0.00,
    currency VARCHAR(10) DEFAULT 'USD',
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    CONSTRAINT uq_school_fee_type UNIQUE (school_id, name)
);

CREATE TABLE IF NOT EXISTS public.fee_records (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    class_id BIGINT REFERENCES public.classes(id) ON DELETE SET NULL,
    student_id BIGINT REFERENCES public.students(id) ON DELETE CASCADE,
    fee_type VARCHAR(100) NOT NULL,
    amount NUMERIC(10, 2) NOT NULL,
    paid_amount NUMERIC(10, 2) DEFAULT 0.00,
    currency VARCHAR(10) DEFAULT 'USD' CHECK (currency IN ('USD', 'SLS', 'ETB', 'SOS')),
    due_date DATE,
    month VARCHAR(50) DEFAULT '',
    paid_status VARCHAR(20) DEFAULT 'Pending' CHECK (paid_status IN ('Pending', 'Partial', 'Paid')),
    paid_date TIMESTAMPTZ,
    receipt_number VARCHAR(100),
    payment_method VARCHAR(50) DEFAULT 'CASH',
    notes TEXT DEFAULT '',
    academic_year_id VARCHAR(50) DEFAULT '2025-2026',
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'VOIDED', 'DELETED')),
    recorded_by VARCHAR(255) DEFAULT '',
    version BIGINT DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE TABLE IF NOT EXISTS public.payments (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    fee_record_id BIGINT REFERENCES public.fee_records(id) ON DELETE SET NULL,
    student_id BIGINT REFERENCES public.students(id) ON DELETE CASCADE,
    receipt_number VARCHAR(100) NOT NULL,
    amount_paid NUMERIC(10, 2) NOT NULL,
    currency VARCHAR(10) DEFAULT 'USD',
    payment_method VARCHAR(50) DEFAULT 'CASH',
    transaction_ref VARCHAR(100),
    payment_date TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    collected_by VARCHAR(255) NOT NULL,
    notes TEXT DEFAULT '',
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    CONSTRAINT uq_school_receipt_number UNIQUE (school_id, receipt_number)
);

-- 11. ANNOUNCEMENTS & AUDIT LOGS
CREATE TABLE IF NOT EXISTS public.announcements (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    target_audience VARCHAR(50) DEFAULT 'ALL',
    category VARCHAR(50) DEFAULT 'GENERAL',
    is_pinned BOOLEAN DEFAULT FALSE,
    author VARCHAR(255) DEFAULT 'Admin',
    event_date DATE,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE TABLE IF NOT EXISTS public.audit_logs (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    user_name VARCHAR(255) NOT NULL,
    user_role VARCHAR(50) DEFAULT 'TEACHER',
    action_category VARCHAR(100) NOT NULL,
    title VARCHAR(255) NOT NULL,
    class_name VARCHAR(100) DEFAULT '',
    details TEXT DEFAULT '',
    status VARCHAR(50) DEFAULT 'SUCCESS',
    raw_data_summary TEXT DEFAULT '',
    timestamp TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 12. SCHOOL SYNC MASTER TABLE
CREATE TABLE IF NOT EXISTS public.school_sync (
    school_id TEXT PRIMARY KEY,
    school_name TEXT NOT NULL,
    backup_json TEXT NOT NULL,
    updated_by TEXT DEFAULT 'Administrator',
    checksum TEXT NOT NULL,
    record_count INTEGER DEFAULT 0,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 13. ENABLE ROW LEVEL SECURITY & GLOBAL ACCESS POLICIES
ALTER TABLE public.schools ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.classes ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.subjects ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.students ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.parents ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.teachers ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendance ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.exams ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.exam_marks ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.fee_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.payments ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.announcements ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.school_sync ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Allow anon all on schools" ON public.schools FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon all on classes" ON public.classes FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon all on subjects" ON public.subjects FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon all on students" ON public.students FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon all on parents" ON public.parents FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon all on teachers" ON public.teachers FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon all on attendance" ON public.attendance FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon all on exams" ON public.exams FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon all on exam_marks" ON public.exam_marks FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon all on fee_records" ON public.fee_records FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon all on payments" ON public.payments FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon all on announcements" ON public.announcements FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon all on audit_logs" ON public.audit_logs FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);
CREATE POLICY "Allow anon all on school_sync" ON public.school_sync FOR ALL TO anon, authenticated USING (true) WITH CHECK (true);

-- 14. STORAGE BUCKETS
INSERT INTO storage.buckets (id, name, public)
VALUES 
    ('school_backups', 'school_backups', true),
    ('student_photos', 'student_photos', true),
    ('teacher_photos', 'teacher_photos', true),
    ('school_documents', 'school_documents', true)
ON CONFLICT (id) DO UPDATE SET public = true;

DROP POLICY IF EXISTS "Allow anon storage school_backups" ON storage.objects;
CREATE POLICY "Allow anon storage school_backups" ON storage.objects FOR ALL TO anon, authenticated USING (bucket_id = 'school_backups') WITH CHECK (bucket_id = 'school_backups');

DROP POLICY IF EXISTS "Allow anon storage student_photos" ON storage.objects;
CREATE POLICY "Allow anon storage student_photos" ON storage.objects FOR ALL TO anon, authenticated USING (bucket_id = 'student_photos') WITH CHECK (bucket_id = 'student_photos');

-- 15. AUTOMATIC TRIGGERS
CREATE OR REPLACE FUNCTION public.calculate_exam_grade()
RETURNS TRIGGER AS $$
DECLARE
    v_total NUMERIC(5, 2);
    v_pct NUMERIC(5, 2);
BEGIN
    SELECT COALESCE(total_marks, 100.0) INTO v_total FROM public.exams WHERE id = NEW.exam_id;
    IF NEW.is_absent THEN
        NEW.score := 0.0;
        NEW.grade := 'F';
        NEW.is_passed := FALSE;
    ELSE
        v_pct := (NEW.score / v_total) * 100.0;
        IF v_pct >= 90 THEN NEW.grade := 'A';
        ELSIF v_pct >= 80 THEN NEW.grade := 'B';
        ELSIF v_pct >= 70 THEN NEW.grade := 'C';
        ELSIF v_pct >= 50 THEN NEW.grade := 'D';
        ELSE NEW.grade := 'F';
        END IF;
        NEW.is_passed := (NEW.score >= 40.0);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_calculate_exam_grade ON public.exam_marks;
CREATE TRIGGER tr_calculate_exam_grade BEFORE INSERT OR UPDATE ON public.exam_marks FOR EACH ROW EXECUTE FUNCTION public.calculate_exam_grade();
