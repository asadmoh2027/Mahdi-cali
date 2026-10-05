-- ==========================================================
-- 001_create_schools.sql
-- School Management System - Supabase Schema: Schools & Tenants
-- ==========================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- Schools / Organizations Table (Multi-tenant Foundation)
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
        "A": {"min": 90, "max": 100, "gpa": 4.0, "remarks": "Excellent (Heer Sare)"},
        "B": {"min": 80, "max": 89, "gpa": 3.0, "remarks": "Very Good (Aad u Wanaagsan)"},
        "C": {"min": 70, "max": 79, "gpa": 2.0, "remarks": "Good (Wanaagsan)"},
        "D": {"min": 50, "max": 69, "gpa": 1.0, "remarks": "Pass (Gudbay)"},
        "F": {"min": 0, "max": 49, "gpa": 0.0, "remarks": "Fail (Dhacay)"}
    }'::jsonb,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_schools_code ON public.schools(school_code);
CREATE INDEX IF NOT EXISTS idx_schools_active ON public.schools(is_active);
