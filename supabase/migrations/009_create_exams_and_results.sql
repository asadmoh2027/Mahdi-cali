-- ==========================================================
-- 009_create_exams_and_results.sql
-- Examinations, Score Entry, Mark History & Grading
-- ==========================================================

-- Exams Table
CREATE TABLE IF NOT EXISTS public.exams (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    class_id BIGINT REFERENCES public.classes(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL, -- e.g. "Midterm Exam", "Final Exam", "Imtixaanka Bisha 1aad"
    subject VARCHAR(255) NOT NULL, -- e.g. "Xisaab (Math)", "Somali"
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

CREATE INDEX IF NOT EXISTS idx_exams_school ON public.exams(school_id);
CREATE INDEX IF NOT EXISTS idx_exams_class ON public.exams(class_id);
CREATE INDEX IF NOT EXISTS idx_exams_academic_year ON public.exams(academic_year_id);
CREATE INDEX IF NOT EXISTS idx_exams_status ON public.exams(status);

-- Student Exam Marks Table
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

CREATE INDEX IF NOT EXISTS idx_exam_marks_school ON public.exam_marks(school_id);
CREATE INDEX IF NOT EXISTS idx_exam_marks_exam ON public.exam_marks(exam_id);
CREATE INDEX IF NOT EXISTS idx_exam_marks_student ON public.exam_marks(student_id);

-- Mark Change History (Audit Trail for grade manipulations)
CREATE TABLE IF NOT EXISTS public.mark_change_history (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    mark_id BIGINT REFERENCES public.exam_marks(id) ON DELETE CASCADE,
    exam_id BIGINT REFERENCES public.exams(id) ON DELETE CASCADE,
    exam_name VARCHAR(255) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    student_id BIGINT REFERENCES public.students(id) ON DELETE CASCADE,
    student_name VARCHAR(255) NOT NULL,
    old_score NUMERIC(5, 2) NOT NULL,
    new_score NUMERIC(5, 2) NOT NULL,
    changed_by VARCHAR(255) NOT NULL,
    reason VARCHAR(255) DEFAULT 'Manual Correction',
    changed_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_mark_history_student ON public.mark_change_history(student_id);
CREATE INDEX IF NOT EXISTS idx_mark_history_exam ON public.mark_change_history(exam_id);
