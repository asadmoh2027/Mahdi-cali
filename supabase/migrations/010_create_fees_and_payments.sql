-- ==========================================================
-- 010_create_fees_and_payments.sql
-- Financial Management, Fee Billing, Receipts & Payments
-- ==========================================================

-- Fee Types (Tuition, Exam, Admission, Bus, Library, Uniform, Other)
CREATE TABLE IF NOT EXISTS public.fee_types (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL, -- e.g. "Tuition (Fiiga Bisha)", "Exam Fee"
    default_amount NUMERIC(10, 2) DEFAULT 0.00,
    currency VARCHAR(10) DEFAULT 'USD',
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    CONSTRAINT uq_school_fee_type UNIQUE (school_id, name)
);

-- Fee Records (Student Invoices)
CREATE TABLE IF NOT EXISTS public.fee_records (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    class_id BIGINT REFERENCES public.classes(id) ON DELETE SET NULL,
    student_id BIGINT REFERENCES public.students(id) ON DELETE CASCADE,
    fee_type VARCHAR(100) NOT NULL, -- "Tuition", "Exam", "Registration", "Transport", "Other"
    amount NUMERIC(10, 2) NOT NULL,
    paid_amount NUMERIC(10, 2) DEFAULT 0.00,
    currency VARCHAR(10) DEFAULT 'USD' CHECK (currency IN ('USD', 'SLS', 'ETB', 'SOS')),
    due_date DATE,
    month VARCHAR(50) DEFAULT '',
    paid_status VARCHAR(20) DEFAULT 'Pending' CHECK (paid_status IN ('Pending', 'Partial', 'Paid')),
    paid_date TIMESTAMPTZ,
    receipt_number VARCHAR(100),
    payment_method VARCHAR(50) DEFAULT 'CASH' CHECK (payment_method IN ('CASH', 'EVC_PLUS', 'ZAAD', 'SAHAL', 'EDAHAB', 'BANK', 'OTHER')),
    notes TEXT DEFAULT '',
    academic_year_id VARCHAR(50) DEFAULT '2025-2026',
    status VARCHAR(20) DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'VOIDED', 'DELETED')),
    recorded_by VARCHAR(255) DEFAULT '',
    version BIGINT DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    updated_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_fee_records_school ON public.fee_records(school_id);
CREATE INDEX IF NOT EXISTS idx_fee_records_student ON public.fee_records(student_id);
CREATE INDEX IF NOT EXISTS idx_fee_records_class ON public.fee_records(class_id);
CREATE INDEX IF NOT EXISTS idx_fee_records_status ON public.fee_records(paid_status);
CREATE INDEX IF NOT EXISTS idx_fee_records_receipt ON public.fee_records(receipt_number);

-- Payment Transactions & Receipts
CREATE TABLE IF NOT EXISTS public.payments (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    fee_record_id BIGINT REFERENCES public.fee_records(id) ON DELETE SET NULL,
    student_id BIGINT REFERENCES public.students(id) ON DELETE CASCADE,
    receipt_number VARCHAR(100) NOT NULL,
    amount_paid NUMERIC(10, 2) NOT NULL,
    currency VARCHAR(10) DEFAULT 'USD',
    payment_method VARCHAR(50) DEFAULT 'CASH',
    transaction_ref VARCHAR(100),
    payment_date TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    collected_by VARCHAR(255) NOT NULL,
    notes TEXT DEFAULT '',
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL,
    CONSTRAINT uq_school_receipt_number UNIQUE (school_id, receipt_number)
);

CREATE INDEX IF NOT EXISTS idx_payments_school ON public.payments(school_id);
CREATE INDEX IF NOT EXISTS idx_payments_student ON public.payments(student_id);
CREATE INDEX IF NOT EXISTS idx_payments_receipt ON public.payments(receipt_number);

-- School Expenses (Bills, Salaries, Maintenance)
CREATE TABLE IF NOT EXISTS public.expenses (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT gen_random_uuid() UNIQUE,
    school_id UUID REFERENCES public.schools(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL, -- e.g. "Salary", "Rent", "Utilities", "Supplies", "Maintenance"
    amount NUMERIC(12, 2) NOT NULL,
    currency VARCHAR(10) DEFAULT 'USD',
    payment_method VARCHAR(50) DEFAULT 'CASH',
    paid_to VARCHAR(255),
    expense_date DATE DEFAULT CURRENT_DATE,
    approved_by VARCHAR(255),
    notes TEXT,
    created_at TIMESTAMPTZ DEFAULT timezone('utc'::text, now()) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_expenses_school ON public.expenses(school_id);
CREATE INDEX IF NOT EXISTS idx_expenses_category ON public.expenses(category);
