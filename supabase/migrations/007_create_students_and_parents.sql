-- ==========================================================
-- 007_create_students_and_parents.sql
-- Parents, Guardians, Students & Enrollment History
-- ==========================================================

-- Parents / Guardians Table
CREATE TABLE IF NOT EXISTS public.parents (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    parent_code VARCHAR(50) NOT NULL, -- e.g. "PAR-001"
    father_name VARCHAR(255) NOT NULL,
    mother_name VARCHAR(255),
    phone VARCHAR(50) NOT NULL,
    phone_alt VARCHAR(50),
    email VARCHAR(255),
    occupation VARCHAR(255),
    address TEXT,
    city VARCHAR(100),
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    CONSTRAINT uq_school_parent_phone UNIQUE (school_id, phone)
);

CREATE INDEX IF NOT EXISTS idx_parents_school ON public.parents(school_id);
CREATE INDEX IF NOT EXISTS idx_parents_phone ON public.parents(phone);

-- Students Table
CREATE TABLE IF NOT EXISTS public.students (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    parent_id BIGINT REFERENCES public.parents(id) ON DELETE SET NULL,
    student_id VARCHAR(50) NOT NULL, -- e.g. "AUTO-001", "STD-2026-001"
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
    is_free BOOLEAN DEFAULT FALSE, -- Fee-exempt / Scholarship
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'TRANSFERRED', 'GRADUATED', 'DELETED')),
    version BIGINT DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    CONSTRAINT uq_school_student_id UNIQUE (school_id, student_id)
);

CREATE INDEX IF NOT EXISTS idx_students_school ON public.students(school_id);
CREATE INDEX IF NOT EXISTS idx_students_class ON public.students(class_id);
CREATE INDEX IF NOT EXISTS idx_students_id_code ON public.students(student_id);
CREATE INDEX IF NOT EXISTS idx_students_parent ON public.students(parent_id);
CREATE INDEX IF NOT EXISTS idx_students_status ON public.students(status);
CREATE INDEX IF NOT EXISTS idx_students_phone ON public.students(phone);

-- Student Class Promotion / Transfer History
CREATE TABLE IF NOT EXISTS public.student_class_history (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    student_id BIGINT REFERENCES public.students(id) ON DELETE CASCADE,
    student_code VARCHAR(50) NOT NULL,
    student_name VARCHAR(255) NOT NULL,
    previous_class_id BIGINT,
    previous_class_name VARCHAR(100) NOT NULL,
    new_class_id BIGINT,
    new_class_name VARCHAR(100) NOT NULL,
    academic_year_id VARCHAR(50) NOT NULL,
    transfer_date TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    reason VARCHAR(255) DEFAULT 'Promotion',
    changed_by VARCHAR(255) DEFAULT 'Admin',
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_student_history_student ON public.student_class_history(student_id);
CREATE INDEX IF NOT EXISTS idx_student_history_school ON public.student_class_history(school_id);
