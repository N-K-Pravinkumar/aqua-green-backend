# AGA Backend — Supabase PostgreSQL Setup Guide

## Step 1 — Create Supabase Project

1. Go to [https://supabase.com](https://supabase.com) and sign in
2. Click **New Project**
3. Enter project name: `aga-aquagreen`
4. Set a strong database password (save this!)
5. Choose region: **Southeast Asia (Singapore)**
6. Click **Create New Project** — wait 2 minutes

---

## Step 2 — Get Connection Details

1. In your Supabase project, go to **Settings → Database**
2. Scroll to **Connection String**
3. Select **URI** tab
4. Copy the string — it looks like:
   ```
   postgresql://postgres:[YOUR-PASSWORD]@db.abcdefghij.supabase.co:5432/postgres
   ```

---

## Step 3 — Create Storage Bucket for Images

1. In Supabase, go to **Storage**
2. Click **New Bucket**
3. Name: `aga-images`
4. Toggle **Public bucket** to ON
5. Click **Create bucket**

Then add a policy:
1. Click `aga-images` bucket → **Policies**
2. Click **New Policy → For full customization**
3. Policy name: `Allow public read`
4. Operation: SELECT
5. Using expression: `true`
6. Click **Review** → **Save policy**

---

## Step 4 — Get API Keys

1. Go to **Settings → API**
2. Copy **Project URL** and **anon/public key**

---

## Step 5 — Set Environment Variables

Create a `.env` file in the `backend/` folder:

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://db.YOURPROJECTID.supabase.co:5432/postgres
SPRING_DATASOURCE_DRIVER=org.postgresql.Driver
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=YOUR_DATABASE_PASSWORD_HERE
SPRING_JPA_PLATFORM=org.hibernate.dialect.PostgreSQLDialect
SPRING_JPA_DDL=update

SUPABASE_URL=https://YOURPROJECTID.supabase.co
SUPABASE_KEY=your-anon-public-key-here
SUPABASE_BUCKET=aga-images
JWT_SECRET=AquaGreenAgencies2026SecretKeyForJWTSigning@Coimbatore
```

---

## Step 6 — Run Backend

```powershell
cd backend
mvn clean spring-boot:run
```

Spring Boot will automatically create all database tables in Supabase on first start.
Products, spare parts, customers and all seed data will be loaded automatically.

---

## Step 7 — Verify

1. Go to your Supabase project → **Table Editor**
2. You should see tables: `products`, `customers`, `service_requests`, `sales`, `stock_items` etc.
3. Click `products` — you should see all 17 products loaded

---

## For Production (when you go live)

Change `SPRING_JPA_DDL=update` to `SPRING_JPA_DDL=validate` after first start.
This prevents accidental data loss if the schema changes.
