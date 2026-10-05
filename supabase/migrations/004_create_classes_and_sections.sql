-- ==========================================================
-- 004_create_classes_and_sections.sql
-- Classes, Streams, and Sections
-- ==========================================================

CREATE TABLE IF NOT EXISTS public.classes (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL, -- e.g. "Class 10A", "Grade 1"
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

CREATE INDEX IF NOT EXISTS idx_classes_school ON public.classes(school_id);
CREATE INDEX IF NOT EXISTS idx_classes_status ON public.classes(status);
CREATE INDEX IF NOT EXISTS idx_classes_academic_year ON public.classes(academic_year_id);
