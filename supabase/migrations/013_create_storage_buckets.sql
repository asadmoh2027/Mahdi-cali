-- ==========================================================
-- 013_create_storage_buckets.sql
-- Storage Buckets & Storage Security Policies
-- ==========================================================

-- 1. Create Storage Buckets
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES 
    ('school_backups', 'school_backups', true, 52428800, ARRAY['application/json', 'application/zip', 'text/plain']),
    ('student_photos', 'student_photos', true, 10485760, ARRAY['image/jpeg', 'image/png', 'image/webp', 'image/gif']),
    ('teacher_photos', 'teacher_photos', true, 10485760, ARRAY['image/jpeg', 'image/png', 'image/webp', 'image/gif']),
    ('school_documents', 'school_documents', true, 20971520, ARRAY['application/pdf', 'image/jpeg', 'image/png', 'application/msword'])
ON CONFLICT (id) DO UPDATE SET
    public = EXCLUDED.public,
    file_size_limit = EXCLUDED.file_size_limit;

-- 2. Storage Policies for School Backups
DROP POLICY IF EXISTS "Allow Public/Anon Access to school_backups" ON storage.objects;
CREATE POLICY "Allow Public/Anon Access to school_backups"
ON storage.objects FOR ALL
TO anon, authenticated
USING (bucket_id = 'school_backups')
WITH CHECK (bucket_id = 'school_backups');

-- 3. Storage Policies for Student Photos
DROP POLICY IF EXISTS "Allow Public/Anon Access to student_photos" ON storage.objects;
CREATE POLICY "Allow Public/Anon Access to student_photos"
ON storage.objects FOR ALL
TO anon, authenticated
USING (bucket_id = 'student_photos')
WITH CHECK (bucket_id = 'student_photos');

-- 4. Storage Policies for Teacher Photos
DROP POLICY IF EXISTS "Allow Public/Anon Access to teacher_photos" ON storage.objects;
CREATE POLICY "Allow Public/Anon Access to teacher_photos"
ON storage.objects FOR ALL
TO anon, authenticated
USING (bucket_id = 'teacher_photos')
WITH CHECK (bucket_id = 'teacher_photos');

-- 5. Storage Policies for School Documents
DROP POLICY IF EXISTS "Allow Public/Anon Access to school_documents" ON storage.objects;
CREATE POLICY "Allow Public/Anon Access to school_documents"
ON storage.objects FOR ALL
TO anon, authenticated
USING (bucket_id = 'school_documents')
WITH CHECK (bucket_id = 'school_documents');
