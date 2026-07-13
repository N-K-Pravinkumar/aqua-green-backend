# ════════════════════════════════════════════════════════════
# AGA Backend — Environment Variables
# Copy this file to .env and fill in your values
# ════════════════════════════════════════════════════════════

# ── Supabase PostgreSQL (get from Supabase Dashboard > Settings > Database) ──
# Go to: https://supabase.com/dashboard > Your Project > Settings > Database
# Connection string format:
SPRING_DATASOURCE_URL=jdbc:postgresql://db.tqwmchynitpwcjhpyuau.supabase.co:5432/postgres
SPRING_DATASOURCE_DRIVER=org.postgresql.Driver
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=Mohanbabu@1986
SPRING_JPA_PLATFORM=org.hibernate.dialect.PostgreSQLDialect
SPRING_JPA_DDL=update


# ── Supabase API (get from Supabase Dashboard > Settings > API) ──
SUPABASE_URL=https://tqwmchynitpwcjhpyuau.supabase.co
SUPABASE_KEY=sb_publishable_L2G7dT4Zlj0iEPnA5fCKog_a95iet1C
SUPABASE_BUCKET=aga-images

# ── CORS — which frontend URLs are allowed to call this API ──
# Comma-separated, no spaces. Include local dev ports AND your real
# deployed frontend domains once you have them.
CORS_ALLOWED_ORIGINS=https://www.aquagreenagencies.com,https://admin.aquagreenagencies.com,https://aqua-green-admin-frontend.vercel.app

# ── JWT ──────────────────────────────────────────────────────
JWT_SECRET=AquaGreenAgencies2026SecretKeyForJWTSigning@Coimbatore

# ── MSG91 SMS ────────────────────────────────────────────────
MSG91_ENABLED=false
MSG91_AUTH_KEY=your-msg91-key
MSG91_SENDER_ID=AGAPUR


