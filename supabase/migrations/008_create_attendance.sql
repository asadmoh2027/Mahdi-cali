-- ==========================================================
-- 008_create_attendance.sql
-- Daily Attendance Tracking & Reports
-- ==========================================================

CREATE TABLE IF NOT EXISTS public.attendance (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    class_id BIGINT REFERENCES public.classes(id) ON DELETE CASCADE,
    student_id BIGINT REFERENCES public.students(id) ON DELETE CASCADE,
    date DATE NOT NULL, -- YYYY-MM-DD
    status VARCHAR(20) NOT NULL CHECK (status IN ('Present', 'Absent', 'Late', 'Free', 'Excused')),
    academic_year_id VARCHAR(50) DEFAULT '2025-2026',
    remarks TEXT DEFAULT '',
    recorded_by VARCHAR(255) DEFAULT '',
    recorded_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    CONSTRAINT uq_student_class_date UNIQUE (school_id, student_id, class_id, date)
);

CREATE INDEX IF NOT EXISTS idx_attendance_school ON public.attendance(school_id);
CREATE INDEX IF NOT EXISTS idx_attendance_class_date ON public.attendance(class_id, date);
CREATE INDEX IF NOT EXISTS idx_attendance_student ON public.attendance(student_id);
CREATE INDEX IF NOT EXISTS idx_attendance_date ON public.attendance(date);
CREATE INDEX IF NOT EXISTS idx_attendance_status ON public.attendance(status);
