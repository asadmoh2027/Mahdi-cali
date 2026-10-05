-- ==========================================================
-- 003_create_academic_years.sql
-- Academic Years, Terms, and Semesters
-- ==========================================================

CREATE TABLE IF NOT EXISTS public.academic_years (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    year_name VARCHAR(50) NOT NULL, -- e.g. "2025-2026", "2026-2027"
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    is_current BOOLEAN DEFAULT TRUE,
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CLOSED', 'ARCHIVED')),
    terms JSONB DEFAULT '[
        {"term_number": 1, "name": "Term 1 (Xilli 1)", "start_date": "2025-09-01", "end_date": "2026-01-15"},
        {"term_number": 2, "name": "Term 2 (Xilli 2)", "start_date": "2026-01-20", "end_date": "2026-06-30"}
    ]'::jsonb,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    CONSTRAINT uq_school_year UNIQUE (school_id, year_name)
);

CREATE INDEX IF NOT EXISTS idx_academic_years_school ON public.academic_years(school_id);
CREATE INDEX IF NOT EXISTS idx_academic_years_current ON public.academic_years(is_current);
