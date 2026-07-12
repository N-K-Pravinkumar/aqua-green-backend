-- ════════════════════════════════════════════════════════════════
-- AGA Supabase PostgreSQL Setup
-- Run this in Supabase Dashboard > SQL Editor BEFORE first start
-- ════════════════════════════════════════════════════════════════

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create storage bucket for images (run in Supabase Dashboard > Storage)
-- Bucket name: aga-images
-- Public: true (so images are accessible without auth)

-- The Spring Boot app will auto-create tables via JPA (ddl-auto=update)
-- Just make sure to set SPRING_JPA_DDL=update in your env

-- ── Row Level Security (optional) ──────────────────────────────
-- After tables are created by Spring Boot, you can enable RLS:
-- ALTER TABLE products ENABLE ROW LEVEL SECURITY;
-- CREATE POLICY "Public read" ON products FOR SELECT USING (true);

-- ── Storage bucket policy ───────────────────────────────────────
-- In Supabase Dashboard > Storage > aga-images > Policies:
-- Add policy: Allow public read (SELECT) for everyone
-- Add policy: Allow authenticated users to upload (INSERT)

-- ── How to connect ─────────────────────────────────────────────
-- 1. Go to https://supabase.com/dashboard
-- 2. Create new project (or use existing)
-- 3. Go to Settings > Database
-- 4. Copy "Connection string" (URI format)
-- 5. Replace [YOUR-PASSWORD] with your database password
-- 6. Set as SPRING_DATASOURCE_URL in your .env file
-- 7. Restart backend — JPA will auto-create all tables
