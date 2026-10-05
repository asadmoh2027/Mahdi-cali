-- ==========================================================
-- 012_create_audit_logs_and_sync.sql
-- Audit Trails, Recycle Bin & School Cloud Synchronization
-- ==========================================================

-- Audit Logs Table
CREATE TABLE IF NOT EXISTS public.audit_logs (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    user_name VARCHAR(255) NOT NULL,
    user_role VARCHAR(50) DEFAULT 'TEACHER',
    action_category VARCHAR(100) NOT NULL, -- "CREATE", "UPDATE", "DELETE", "RESTORE", "BACKUP", "IMPORT", "EXPORT", "PROMOTE_STUDENT", "CHANGE_MARK", "UNLOCK_EXAM"
    title VARCHAR(255) NOT NULL,
    class_name VARCHAR(100) DEFAULT '',
    details TEXT DEFAULT '',
    status VARCHAR(50) DEFAULT 'SUCCESS',
    raw_data_summary TEXT DEFAULT '',
    ip_address VARCHAR(100),
    user_agent TEXT,
    timestamp TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_school ON public.audit_logs(school_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_timestamp ON public.audit_logs(timestamp);
CREATE INDEX IF NOT EXISTS idx_audit_logs_action ON public.audit_logs(action_category);

-- Recycle Bin / Soft-Delete Items
CREATE TABLE IF NOT EXISTS public.recycle_bin (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    entity_type VARCHAR(50) NOT NULL, -- "STUDENT", "CLASS", "EXAM", "FEE", "TEACHER"
    original_id BIGINT NOT NULL,
    item_identifier VARCHAR(100) NOT NULL,
    item_name VARCHAR(255) NOT NULL,
    details_json JSONB NOT NULL,
    deleted_by VARCHAR(255) NOT NULL,
    academic_year_id VARCHAR(50) DEFAULT '2025-2026',
    deleted_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_recycle_bin_school ON public.recycle_bin(school_id);
CREATE INDEX IF NOT EXISTS idx_recycle_bin_entity ON public.recycle_bin(entity_type);

-- Backup Records
CREATE TABLE IF NOT EXISTS public.backup_records (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    backup_id VARCHAR(100) NOT NULL,
    academic_year_id VARCHAR(50) DEFAULT '2025-2026',
    created_by VARCHAR(255) NOT NULL,
    app_version VARCHAR(50) DEFAULT '3.0',
    record_count INTEGER DEFAULT 0,
    checksum VARCHAR(128) NOT NULL,
    backup_status VARCHAR(50) DEFAULT 'VERIFIED',
    notes TEXT DEFAULT '',
    json_payload TEXT,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    CONSTRAINT uq_school_backup_id UNIQUE (school_id, backup_id)
);

CREATE INDEX IF NOT EXISTS idx_backup_records_school ON public.backup_records(school_id);

-- School Sync & Snapshot Master Table (For Instant Multi-device Live Sync)
CREATE TABLE IF NOT EXISTS public.school_sync (
    school_id TEXT PRIMARY KEY,
    school_name TEXT NOT NULL,
    backup_json TEXT NOT NULL,
    updated_by TEXT DEFAULT 'Administrator',
    checksum TEXT NOT NULL,
    record_count INTEGER DEFAULT 0,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_school_sync_updated ON public.school_sync(updated_at);
