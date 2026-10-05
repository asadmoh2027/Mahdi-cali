-- ==========================================================
-- 011_create_announcements_and_notices.sql
-- Announcements, Bulletins, Events & Push Notices
-- ==========================================================

CREATE TABLE IF NOT EXISTS public.announcements (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    target_audience VARCHAR(50) DEFAULT 'ALL' CHECK (target_audience IN ('ALL', 'TEACHERS', 'PARENTS', 'STUDENTS', 'STAFF')),
    category VARCHAR(50) DEFAULT 'GENERAL' CHECK (category IN ('GENERAL', 'EXAM', 'FEE', 'HOLIDAY', 'EVENT', 'EMERGENCY')),
    is_pinned BOOLEAN DEFAULT FALSE,
    author VARCHAR(255) DEFAULT 'Admin',
    event_date DATE,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_announcements_school ON public.announcements(school_id);
CREATE INDEX IF NOT EXISTS idx_announcements_audience ON public.announcements(target_audience);
CREATE INDEX IF NOT EXISTS idx_announcements_pinned ON public.announcements(is_pinned);
