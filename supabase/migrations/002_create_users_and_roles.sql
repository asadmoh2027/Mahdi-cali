-- ==========================================================
-- 002_create_users_and_roles.sql
-- User Profiles, Authentication Mapping & Roles
-- ==========================================================

-- User Roles ENUM
DO $$ BEGIN
    CREATE TYPE user_role_type AS ENUM ('SUPER_ADMIN', 'ADMIN', 'PRINCIPAL', 'TEACHER', 'ACCOUNTANT', 'PARENT', 'STUDENT');
EXCEPTION
    WHEN duplicate_object THEN null;
END $$;

-- User Profiles Table (Linked to auth.users if Supabase Auth is enabled)
CREATE TABLE IF NOT EXISTS public.profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    username VARCHAR(100) UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    phone VARCHAR(50),
    avatar_url TEXT,
    role user_role_type DEFAULT 'TEACHER' NOT NULL,
    assigned_class_ids TEXT, -- Comma-separated or JSON list of class IDs
    assigned_subject_ids TEXT, -- Comma-separated or JSON list of subject IDs
    is_locked BOOLEAN DEFAULT FALSE NOT NULL,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- Indexes for lightning fast lookups
CREATE INDEX IF NOT EXISTS idx_profiles_school ON public.profiles(school_id);
CREATE INDEX IF NOT EXISTS idx_profiles_role ON public.profiles(role);
CREATE INDEX IF NOT EXISTS idx_profiles_username ON public.profiles(username);
CREATE INDEX IF NOT EXISTS idx_profiles_phone ON public.profiles(phone);

-- Application Native Users (For offline/desktop sync or direct app authentication)
CREATE TABLE IF NOT EXISTS public.app_users (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    role VARCHAR(50) DEFAULT 'TEACHER' NOT NULL,
    assigned_class_ids VARCHAR(255) DEFAULT '',
    is_locked BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    CONSTRAINT uq_school_username UNIQUE (school_id, username)
);

CREATE INDEX IF NOT EXISTS idx_app_users_school ON public.app_users(school_id);
CREATE INDEX IF NOT EXISTS idx_app_users_role ON public.app_users(role);
