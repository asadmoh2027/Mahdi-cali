-- ==========================================================
-- 005_create_teachers_and_staff.sql
-- Teachers, Academic Staff, and Qualifications
-- ==========================================================

CREATE TABLE IF NOT EXISTS public.teachers (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    teacher_id_code VARCHAR(50) NOT NULL, -- e.g. "TCH-001"
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

CREATE INDEX IF NOT EXISTS idx_teachers_school ON public.teachers(school_id);
CREATE INDEX IF NOT EXISTS idx_teachers_code ON public.teachers(teacher_id_code);
CREATE INDEX IF NOT EXISTS idx_teachers_phone ON public.teachers(phone);
CREATE INDEX IF NOT EXISTS idx_teachers_status ON public.teachers(status);
