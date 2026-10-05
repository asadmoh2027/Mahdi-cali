# 🏫 Supabase Backend Architecture & Operations Guide (School Management System)

This directory contains the production-grade PostgreSQL schema, Row Level Security (RLS) policies, Storage Buckets, Database Functions, Automated Triggers, and Migration scripts for the **School Management System (Dugsiyeynta)**.

---

## 📁 Directory Structure

```
supabase/
├── full_schema.sql                  # All-in-one Master SQL setup script for Supabase SQL Editor
├── README.md                        # Complete Architecture, Backup, & Deployment guide
└── migrations/
    ├── 001_create_schools.sql       # Multi-tenant schools and branding settings
    ├── 002_create_users_and_roles.sql # Auth profiles, roles & permissions
    ├── 003_create_academic_years.sql  # Academic years, terms & dates
    ├── 004_create_classes_and_sections.sql # Classes, sections & room assignments
    ├── 005_create_teachers_and_staff.sql   # Teachers, credentials & assignments
    ├── 006_create_subjects.sql      # Curriculum subjects & pass thresholds
    ├── 007_create_students_and_parents.sql # Students, parents & class promotion history
    ├── 008_create_attendance.sql    # Attendance records & constraints
    ├── 009_create_exams_and_results.sql    # Exams, student marks & change history
    ├── 010_create_fees_and_payments.sql    # Fees, payments & expense tracking
    ├── 011_create_announcements_and_notices.sql # Bulletins & emergency alerts
    ├── 012_create_audit_logs_and_sync.sql  # Audit logs, soft-delete & school_sync
    ├── 013_create_storage_buckets.sql      # Storage buckets & access policies
    ├── 014_create_rls_policies.sql         # Granular Row Level Security policies
    ├── 015_create_functions_and_triggers.sql # Automatic grading & balance calculation
    └── 016_seed_data.sql                   # Default school initialization data
```

---

## ⚡ Quick Start: 3-Minute Setup on Supabase

1. Go to your [Supabase Dashboard](https://supabase.com/dashboard) and create or open your project.
2. Open the **SQL Editor** tab from the left sidebar.
3. Open `supabase/full_schema.sql` (or copy its contents).
4. Paste it into the Supabase SQL Editor and click **Run** (Ctrl + Enter).
5. All 14 core tables, 4 Storage buckets (`school_backups`, `student_photos`, `teacher_photos`, `school_documents`), RLS policies, and automated triggers will be created instantly.
6. Copy your **Project URL** and **Anon Publishable Key** from **Project Settings -> API**.
7. In the Android App, go to **Settings / Backup & Cloud Sync** and paste your URL and Key (or set them in `.env`).

---

## 🛡️ Row Level Security (RLS) & Multi-Tenancy

Every sensitive table is governed by PostgreSQL Row Level Security:
- **Admin**: Full administrative privileges across students, teachers, finances, exams, and settings.
- **Teacher**: Restricted to viewing and entering scores/attendance for their assigned classes and subjects.
- **Accountant / Cashier**: Permissions to collect payments, generate receipts, and manage fee registers.
- **Parent**: Restricted to viewing only their own registered children's report cards, attendance rates, and fee balances.
- **Tenant Isolation**: All records reference `school_id`, preventing data leakage across different school instances.

---

## 💾 Backup, Recovery & Disaster Protection

1. **Daily Automated Cloud Snapshots**:
   The app automatically streams SHA-256 verified JSON snapshots directly to Supabase PostgREST table `school_sync` and Storage bucket `school_backups` every time changes are made.
2. **Point-in-Time Recovery (PITR)**:
   Supabase PostgreSQL includes Write-Ahead Logging (WAL) and PITR for physical database recovery to any second within retention.
3. **Emergency Off-site Export**:
   From the App UI, go to **Maamulka Xogta & Keydka -> Export JSON** to download a standalone portable database file anytime.
4. **Disaster Recovery Steps**:
   - If a device is lost or replaced: Log in on a new device, connect to Supabase, and click **Soo Celi Xogta (Restore)**. The complete school database will be restored in seconds.

---

## 🔑 Environment Variables Configuration

Create a `.env` file based on `.env.example`:

```env
# Supabase Project Configuration
SUPABASE_URL=https://your-project-id.supabase.co
SUPABASE_ANON_KEY=sb_publishable_your_anon_key_here
DEFAULT_SCHOOL_ID=school_main_001
```

*Note: Never commit `.env` or the Supabase Service Role Key to GitHub.*
