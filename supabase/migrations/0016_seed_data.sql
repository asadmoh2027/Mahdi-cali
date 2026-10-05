-- ==========================================================
-- 016_seed_data.sql
-- Initial Seed Data: School Profile, Academic Years, Subjects & Classes
-- ==========================================================

-- 1. Insert Default School
INSERT INTO public.schools (id, school_code, name, tagline, principal_name, phone, email, address, currency, academic_year_current)
VALUES (
    '00000000-0000-0000-0000-000000000001'::uuid,
    'SCH-DUGSI-001',
    'Dugsiga Sare ee Waxbarashada',
    'Xarunta Barashada Aqoonta & Anshaxa',
    'Maamulaha Guud',
    '+252 61 5000000',
    'info@dugsi.edu.so',
    'Mogadishu, Somalia',
    'USD',
    '2025-2026'
) ON CONFLICT (school_code) DO NOTHING;

-- 2. Insert Academic Year
INSERT INTO public.academic_years (school_id, year_name, start_date, end_date, is_current, status)
VALUES (
    '00000000-0000-0000-0000-000000000001'::uuid,
    '2025-2026',
    '2025-09-01',
    '2026-06-30',
    TRUE,
    'ACTIVE'
) ON CONFLICT (school_id, year_name) DO NOTHING;

-- 3. Insert Curriculum Subjects
INSERT INTO public.subjects (school_id, subject_code, subject_name, grade, pass_marks, total_marks)
VALUES
    ('00000000-0000-0000-0000-000000000001'::uuid, 'DIIN', 'Diinta Islaamka', 'General', 40.0, 100.0),
    ('00000000-0000-0000-0000-000000000001'::uuid, 'SOM', 'Af Soomaali', 'General', 40.0, 100.0),
    ('00000000-0000-0000-0000-000000000001'::uuid, 'XIS', 'Xisaab (Mathematics)', 'General', 40.0, 100.0),
    ('00000000-0000-0000-0000-000000000001'::uuid, 'ENG', 'English Language', 'General', 40.0, 100.0),
    ('00000000-0000-0000-0000-000000000001'::uuid, 'SCI', 'Saynis Guud (General Science)', 'General', 40.0, 100.0),
    ('00000000-0000-0000-0000-000000000001'::uuid, 'TAAR', 'Taariikh & Juqraafi (Social Studies)', 'General', 40.0, 100.0),
    ('00000000-0000-0000-0000-000000000001'::uuid, 'CAR', 'Af Carabi (Arabic Language)', 'General', 40.0, 100.0)
ON CONFLICT (school_id, subject_code, grade) DO NOTHING;

-- 4. Insert Default Classes
INSERT INTO public.classes (school_id, name, grade, section, academic_year_id, incharge_teacher)
VALUES
    ('00000000-0000-0000-0000-000000000001'::uuid, 'Class 10A', 'Grade 10', 'A', '2025-2026', 'Ustaad Axmed'),
    ('00000000-0000-0000-0000-000000000001'::uuid, 'Class 10B', 'Grade 10', 'B', '2025-2026', 'Ustaad Faarax'),
    ('00000000-0000-0000-0000-000000000001'::uuid, 'Class 9A', 'Grade 9', 'A', '2025-2026', 'Macalin Cali'),
    ('00000000-0000-0000-0000-000000000001'::uuid, 'Grade 8', 'Grade 8', 'Main', '2025-2026', 'Macalimad Maryan')
ON CONFLICT (school_id, name, academic_year_id) DO NOTHING;

-- 5. Insert Default Fee Types
INSERT INTO public.fee_types (school_id, name, default_amount, currency, description)
VALUES
    ('00000000-0000-0000-0000-000000000001'::uuid, 'Tuition (Fiiga Bisha)', 15.00, 'USD', 'Monthly tuition fee per student'),
    ('00000000-0000-0000-0000-000000000001'::uuid, 'Exam Fee (Imtixaan)', 5.00, 'USD', 'Term examination fee'),
    ('00000000-0000-0000-0000-000000000001'::uuid, 'Registration (Diiwaangelin)', 10.00, 'USD', 'Annual admission and registration fee')
ON CONFLICT (school_id, name) DO NOTHING;

-- 6. Insert Default Announcements
INSERT INTO public.announcements (school_id, title, content, target_audience, category, is_pinned, author)
VALUES (
    '00000000-0000-0000-0000-000000000001'::uuid,
    'Kusoo Dhawoow Nidaamka Cusub ee Dugsiga',
    'Waxaan ku faraxsanahay inaan daahfurno nidaamka casriga ah ee maaraynta xogta dugsiga, buundooyinka, xaadirinta iyo fiiga oo toos ugu xidhan Supabase Cloud Server.',
    'ALL',
    'GENERAL',
    TRUE,
    'Maamulka Dugsiga'
);
