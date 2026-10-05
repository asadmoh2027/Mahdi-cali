-- ==========================================================
-- 015_create_functions_and_triggers.sql
-- Database Functions, Calculations & Automated Triggers
-- ==========================================================

-- 1. Helper Function: Update timestamp
CREATE OR REPLACE FUNCTION public.handle_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = timezone('utc'::text, now());
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply updated_at trigger to relevant tables
DROP TRIGGER IF EXISTS tr_schools_updated_at ON public.schools;
CREATE TRIGGER tr_schools_updated_at BEFORE UPDATE ON public.schools FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

DROP TRIGGER IF EXISTS tr_classes_updated_at ON public.classes;
CREATE TRIGGER tr_classes_updated_at BEFORE UPDATE ON public.classes FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

DROP TRIGGER IF EXISTS tr_students_updated_at ON public.students;
CREATE TRIGGER tr_students_updated_at BEFORE UPDATE ON public.students FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

DROP TRIGGER IF EXISTS tr_exams_updated_at ON public.exams;
CREATE TRIGGER tr_exams_updated_at BEFORE UPDATE ON public.exams FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

DROP TRIGGER IF EXISTS tr_exam_marks_updated_at ON public.exam_marks;
CREATE TRIGGER tr_exam_marks_updated_at BEFORE UPDATE ON public.exam_marks FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

DROP TRIGGER IF EXISTS tr_fee_records_updated_at ON public.fee_records;
CREATE TRIGGER tr_fee_records_updated_at BEFORE UPDATE ON public.fee_records FOR EACH ROW EXECUTE FUNCTION public.handle_updated_at();

-- 2. Automated Grade & Pass/Fail Calculation Trigger
CREATE OR REPLACE FUNCTION public.calculate_exam_grade()
RETURNS TRIGGER AS $$
DECLARE
    v_total NUMERIC(5, 2);
    v_pass NUMERIC(5, 2);
    v_pct NUMERIC(5, 2);
BEGIN
    -- Fetch exam total and pass marks
    SELECT total_marks, pass_marks INTO v_total, v_pass FROM public.exams WHERE id = NEW.exam_id;
    IF v_total IS NULL OR v_total <= 0 THEN
        v_total := 100.0;
    END IF;
    IF v_pass IS NULL THEN
        v_pass := 40.0;
    END IF;

    IF NEW.is_absent THEN
        NEW.score := 0.0;
        NEW.grade := 'F';
        NEW.is_passed := FALSE;
        NEW.teacher_remarks := COALESCE(NEW.teacher_remarks, '') || ' [Maqnaa / Absent]';
    ELSE
        v_pct := (NEW.score / v_total) * 100.0;
        IF v_pct >= 90 THEN
            NEW.grade := 'A';
        ELSIF v_pct >= 80 THEN
            NEW.grade := 'B';
        ELSIF v_pct >= 70 THEN
            NEW.grade := 'C';
        ELSIF v_pct >= 50 THEN
            NEW.grade := 'D';
        ELSE
            NEW.grade := 'F';
        END IF;

        NEW.is_passed := (NEW.score >= v_pass);
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_calculate_exam_grade ON public.exam_marks;
CREATE TRIGGER tr_calculate_exam_grade BEFORE INSERT OR UPDATE OF score, is_absent ON public.exam_marks FOR EACH ROW EXECUTE FUNCTION public.calculate_exam_grade();

-- 3. Automatic Payment to Fee Balance Sync Trigger
CREATE OR REPLACE FUNCTION public.sync_payment_to_fee()
RETURNS TRIGGER AS $$
DECLARE
    v_total_paid NUMERIC(10, 2);
    v_fee_amount NUMERIC(10, 2);
BEGIN
    IF NEW.fee_record_id IS NOT NULL THEN
        SELECT COALESCE(SUM(amount_paid), 0) INTO v_total_paid FROM public.payments WHERE fee_record_id = NEW.fee_record_id;
        SELECT amount INTO v_fee_amount FROM public.fee_records WHERE id = NEW.fee_record_id;

        UPDATE public.fee_records
        SET 
            paid_amount = v_total_paid,
            paid_status = CASE 
                WHEN v_total_paid >= v_fee_amount THEN 'Paid'
                WHEN v_total_paid > 0 THEN 'Partial'
                ELSE 'Pending'
            END,
            paid_date = timezone('utc'::text, now()),
            updated_at = timezone('utc'::text, now())
        WHERE id = NEW.fee_record_id;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS tr_sync_payment_to_fee ON public.payments;
CREATE TRIGGER tr_sync_payment_to_fee AFTER INSERT OR UPDATE OR DELETE ON public.payments FOR EACH ROW EXECUTE FUNCTION public.sync_payment_to_fee();
