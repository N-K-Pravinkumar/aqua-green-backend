# Aqua Green Agencies — Backend API

Spring Boot 3.2 (Java 17) REST API used by both `customer-frontend`
and `admin-frontend`. Uses PostgreSQL (Supabase) in production.

## Run locally
```bash
mvn clean install
mvn spring-boot:run
```
Runs on http://localhost:8080. Copy `.env.example` to `.env` and fill
in your Supabase database credentials before running against Supabase
(or leave as-is to use the local H2 in-memory database for quick demos).

## Deploy (Render)
1. Run `supabase-setup.sql` / `supabase_complete_schema.sql` in the
   Supabase SQL editor to create your tables.
2. Push this folder to its own GitHub repo.
3. Create a Web Service on Render pointing at that repo.
   - Build command: `mvn clean package -DskipTests`
   - Start command: `java -jar target/*.jar`
4. Set the environment variables from `.env.example` in Render's
   dashboard (never commit real credentials).

## Related repos
- `customer-frontend` — the public website
- `admin-frontend` — the staff/admin dashboard
Both frontends call this API via `REACT_APP_API_URL`.
