-- ==========================================================
-- 006_create_subjects.sql
-- Curriculum Subjects, Codes, and Grade Levels
-- ==========================================================

CREATE TABLE IF NOT EXISTS public.subjects (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    subject_code VARCHAR(50) NOT NULL, -- e.g. "DIIN", "SOM", "XIS", "ENG", "SCI"
    subject_name VARCHAR(255) NOT NULL, -- e.g. "Diinta Islaamka", "Af Soomaali", "Xisaab"
    grade VARCHAR(50) DEFAULT '',
    pass_marks NUMERIC(5, 2) DEFAULT 40.0,
    total_marks NUMERIC(5, 2) DEFAULT 100.0,
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED')),
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    CONSTRAINT uq_school_subject_code UNIQUE (school_id, subject_code, grade)
);

CREATE INDEX IF NOT EXISTS idx_subjects_school ON public.subjects(school_id);
CREATE INDEX IF NOT EXISTS idx_subjects_code ON public.subjects(subject_code);
CREATE INDEX IF NOT EXISTS idx_subjects_grade ON public.subjects(grade);
