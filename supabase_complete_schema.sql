-- ═══════════════════════════════════════════════════════════════════
-- AGA (Aqua Green Agencies) — Complete Supabase Schema
-- Run this entire script in Supabase Dashboard → SQL Editor
-- ═══════════════════════════════════════════════════════════════════

-- Enable extensions
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Drop existing tables (safe re-run)
DROP TABLE IF EXISTS operation_history     CASCADE;
DROP TABLE IF EXISTS communication_logs    CASCADE;
DROP TABLE IF EXISTS automation_rules      CASCADE;
DROP TABLE IF EXISTS filter_presets        CASCADE;
DROP TABLE IF EXISTS quotations            CASCADE;
DROP TABLE IF EXISTS payments              CASCADE;
DROP TABLE IF EXISTS sales                 CASCADE;
DROP TABLE IF EXISTS service_requests      CASCADE;
DROP TABLE IF EXISTS enquiries             CASCADE;
DROP TABLE IF EXISTS leads                 CASCADE;
DROP TABLE IF EXISTS app_users             CASCADE;
DROP TABLE IF EXISTS document_templates    CASCADE;
DROP TABLE IF EXISTS stock_items           CASCADE;
DROP TABLE IF EXISTS service_items         CASCADE;
DROP TABLE IF EXISTS gallery_items         CASCADE;
DROP TABLE IF EXISTS blogs                 CASCADE;
DROP TABLE IF EXISTS brand_partners        CASCADE;
DROP TABLE IF EXISTS awards                CASCADE;
DROP TABLE IF EXISTS products              CASCADE;
DROP TABLE IF EXISTS employees             CASCADE;
DROP TABLE IF EXISTS customers             CASCADE;

-- ─── 1. customers ────────────────────────────────────────────────
CREATE TABLE customers (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(150) NOT NULL,
    mobile          VARCHAR(15)  NOT NULL UNIQUE,
    email           VARCHAR(150),
    address         TEXT,
    city            VARCHAR(100),
    gst_number      VARCHAR(30),
    customer_type   VARCHAR(50)  DEFAULT 'RESIDENTIAL',
    active          BOOLEAN      DEFAULT TRUE,
    created_at      TIMESTAMP    DEFAULT NOW(),
    updated_at      TIMESTAMP    DEFAULT NOW()
);
CREATE INDEX idx_customers_mobile ON customers(mobile);
CREATE INDEX idx_customers_name   ON customers(LOWER(name));

-- ─── 2. employees ────────────────────────────────────────────────
CREATE TABLE employees (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(150) NOT NULL,
    mobile           VARCHAR(15)  NOT NULL,
    email            VARCHAR(150),
    role             VARCHAR(100),
    joining_date     DATE,
    salary           NUMERIC(10,2),
    avatar_initials  VARCHAR(5),
    active           BOOLEAN      DEFAULT TRUE,
    created_at       TIMESTAMP    DEFAULT NOW(),
    updated_at       TIMESTAMP    DEFAULT NOW()
);

-- ─── 3. products ─────────────────────────────────────────────────
CREATE TABLE products (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(200) NOT NULL,
    description       TEXT,
    price             NUMERIC(10,2),
    original_price    NUMERIC(10,2),
    pricing_mode      VARCHAR(30)  DEFAULT 'CONTACT_FOR_PRICE',
    show_price        BOOLEAN      DEFAULT FALSE,
    is_free           BOOLEAN      DEFAULT FALSE,
    category          VARCHAR(100),
    image_url         TEXT,
    image_alt         VARCHAR(300),
    capacity_litres   INTEGER,
    features          TEXT,
    specifications    TEXT,
    seo_title         VARCHAR(200),
    seo_description   TEXT,
    active            BOOLEAN      DEFAULT TRUE,
    display_order     INTEGER      DEFAULT 0,
    stock             INTEGER      DEFAULT 0,
    min_stock         INTEGER      DEFAULT 3,
    created_at        TIMESTAMP    DEFAULT NOW(),
    updated_at        TIMESTAMP    DEFAULT NOW()
);

-- ─── 4. service_items (website service cards) ────────────────────
CREATE TABLE service_items (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(200) NOT NULL,
    description      TEXT,
    price            NUMERIC(10,2),
    pricing_mode     VARCHAR(30)  DEFAULT 'CONTACT_FOR_PRICE',
    image_url        TEXT,
    seo_title        VARCHAR(200),
    seo_description  TEXT,
    active           BOOLEAN      DEFAULT TRUE,
    display_order    INTEGER      DEFAULT 0,
    created_at       TIMESTAMP    DEFAULT NOW(),
    updated_at       TIMESTAMP    DEFAULT NOW()
);

-- ─── 5. stock_items (spare parts) ────────────────────────────────
CREATE TABLE stock_items (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(200) NOT NULL,
    category       VARCHAR(100),
    description    TEXT,
    brand          VARCHAR(100),
    image_url      TEXT,
    opening_stock  INTEGER      DEFAULT 0,
    current_stock  INTEGER      DEFAULT 0,
    min_stock      INTEGER      DEFAULT 5,
    unit           VARCHAR(20)  DEFAULT 'PCS',
    active         BOOLEAN      DEFAULT TRUE,
    created_at     TIMESTAMP    DEFAULT NOW(),
    updated_at     TIMESTAMP    DEFAULT NOW()
);
CREATE INDEX idx_stock_category ON stock_items(category);

-- ─── 6. gallery_items ────────────────────────────────────────────
CREATE TABLE gallery_items (
    id             BIGSERIAL PRIMARY KEY,
    title          VARCHAR(200) NOT NULL,
    description    TEXT,
    image_url      TEXT NOT NULL,
    image_alt      VARCHAR(300),
    category       VARCHAR(100),
    active         BOOLEAN      DEFAULT TRUE,
    display_order  INTEGER      DEFAULT 0,
    created_at     TIMESTAMP    DEFAULT NOW(),
    updated_at     TIMESTAMP    DEFAULT NOW()
);

-- ─── 7. blogs ────────────────────────────────────────────────────
CREATE TABLE blogs (
    id                  BIGSERIAL PRIMARY KEY,
    title               VARCHAR(300) NOT NULL,
    slug                VARCHAR(300) UNIQUE,
    content             TEXT,
    excerpt             TEXT,
    featured_image_url  TEXT,
    seo_title           VARCHAR(300),
    seo_description     TEXT,
    status              VARCHAR(20)  DEFAULT 'DRAFT',
    author              VARCHAR(150),
    published_at        TIMESTAMP,
    created_at          TIMESTAMP    DEFAULT NOW(),
    updated_at          TIMESTAMP    DEFAULT NOW()
);

-- ─── 8. brand_partners ───────────────────────────────────────────
CREATE TABLE brand_partners (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(150) NOT NULL,
    logo_url       TEXT,
    website_url    VARCHAR(300),
    active         BOOLEAN      DEFAULT TRUE,
    display_order  INTEGER      DEFAULT 0,
    created_at     TIMESTAMP    DEFAULT NOW()
);

-- ─── 9. awards ───────────────────────────────────────────────────
CREATE TABLE awards (
    id             BIGSERIAL PRIMARY KEY,
    title          VARCHAR(200),
    description    TEXT,
    image_url      TEXT,
    year           INTEGER,
    active         BOOLEAN      DEFAULT TRUE,
    display_order  INTEGER      DEFAULT 0,
    created_at     TIMESTAMP    DEFAULT NOW()
);

-- ─── 10. document_templates ──────────────────────────────────────
CREATE TABLE document_templates (
    id               BIGSERIAL PRIMARY KEY,
    name             VARCHAR(200) NOT NULL,
    template_type    VARCHAR(50)  NOT NULL,
    message_content  TEXT,
    html_content     TEXT,
    document_json    TEXT,
    header_config    TEXT,
    footer_config    TEXT,
    watermark_config TEXT,
    page_config      TEXT,
    branding_config  TEXT,
    placeholders     TEXT,
    msg91_template_id VARCHAR(100),
    sms_event        VARCHAR(100),
    is_default       BOOLEAN      DEFAULT FALSE,
    active           BOOLEAN      DEFAULT TRUE,
    created_at       TIMESTAMP    DEFAULT NOW(),
    updated_at       TIMESTAMP    DEFAULT NOW()
);

-- ─── 11. app_users ───────────────────────────────────────────────
CREATE TABLE app_users (
    id                    BIGSERIAL PRIMARY KEY,
    username              VARCHAR(100) UNIQUE NOT NULL,
    email                 VARCHAR(200) UNIQUE NOT NULL,
    password              VARCHAR(300) NOT NULL,  -- bcrypt hash
    full_name             VARCHAR(200),
    mobile                VARCHAR(15),
    role                  VARCHAR(50)  NOT NULL,
    permissions           TEXT,
    department            VARCHAR(100),
    avatar_url            TEXT,
    active                BOOLEAN      DEFAULT TRUE,
    last_login_at         TIMESTAMP,
    password_reset_token  VARCHAR(300),
    password_reset_expiry TIMESTAMP,
    refresh_token         TEXT,
    customer_id           BIGINT REFERENCES customers(id),
    employee_id           BIGINT REFERENCES employees(id),
    created_at            TIMESTAMP    DEFAULT NOW(),
    updated_at            TIMESTAMP    DEFAULT NOW()
);

-- ─── 12. leads ───────────────────────────────────────────────────
CREATE TABLE leads (
    id                BIGSERIAL PRIMARY KEY,
    name              VARCHAR(150) NOT NULL,
    mobile            VARCHAR(15)  NOT NULL,
    email             VARCHAR(150),
    city              VARCHAR(100),
    requirement       TEXT,
    source            VARCHAR(50),
    assigned_employee VARCHAR(150),
    status            VARCHAR(30)  DEFAULT 'NEW',
    notes             TEXT,
    follow_up_date    TIMESTAMP,
    created_at        TIMESTAMP    DEFAULT NOW(),
    updated_at        TIMESTAMP    DEFAULT NOW()
);
CREATE INDEX idx_leads_status ON leads(status);
CREATE INDEX idx_leads_mobile ON leads(mobile);

-- ─── 13. enquiries ───────────────────────────────────────────────
CREATE TABLE enquiries (
    id               BIGSERIAL PRIMARY KEY,
    customer_name    VARCHAR(150) NOT NULL,
    mobile           VARCHAR(15)  NOT NULL,
    email            VARCHAR(150),
    address          TEXT,
    product_id       BIGINT REFERENCES products(id),
    product_name     VARCHAR(200),
    service_required VARCHAR(200),
    message          TEXT,
    source           VARCHAR(50),
    status           VARCHAR(30)  DEFAULT 'NEW',
    created_at       TIMESTAMP    DEFAULT NOW(),
    updated_at       TIMESTAMP    DEFAULT NOW()
);
CREATE INDEX idx_enquiries_status ON enquiries(status);

-- ─── 14. quotations ──────────────────────────────────────────────
CREATE TABLE quotations (
    id                BIGSERIAL PRIMARY KEY,
    quotation_number  VARCHAR(50) UNIQUE,
    customer_id       BIGINT REFERENCES customers(id),
    customer_name     VARCHAR(150),
    customer_mobile   VARCHAR(15),
    customer_address  TEXT,
    items_json        TEXT,
    subtotal          NUMERIC(10,2) DEFAULT 0,
    gst_amount        NUMERIC(10,2) DEFAULT 0,
    total_amount      NUMERIC(10,2) DEFAULT 0,
    notes             TEXT,
    status            VARCHAR(30)   DEFAULT 'DRAFT',
    validity_days     INTEGER       DEFAULT 30,
    created_at        TIMESTAMP     DEFAULT NOW(),
    updated_at        TIMESTAMP     DEFAULT NOW()
);

-- ─── 15. sales ───────────────────────────────────────────────────
CREATE TABLE sales (
    id                BIGSERIAL PRIMARY KEY,
    customer_id       BIGINT REFERENCES customers(id),
    customer_name     VARCHAR(150),
    customer_mobile   VARCHAR(15),
    customer_address  TEXT,
    product_id        BIGINT REFERENCES products(id),
    product_name      VARCHAR(200),
    product_model     VARCHAR(100),
    quantity          INTEGER       DEFAULT 1,
    unit_price        NUMERIC(10,2),
    discount_amount   NUMERIC(10,2) DEFAULT 0,
    gst_amount        NUMERIC(10,2) DEFAULT 0,
    total_amount      NUMERIC(10,2),
    sales_person      VARCHAR(150),
    invoice_number    VARCHAR(50) UNIQUE,
    payment_status    VARCHAR(30)   DEFAULT 'PENDING',
    payment_method    VARCHAR(30),
    notes             TEXT,
    items_json        TEXT,
    stock_deducted    BOOLEAN       DEFAULT FALSE,
    created_at        TIMESTAMP     DEFAULT NOW(),
    updated_at        TIMESTAMP     DEFAULT NOW()
);
CREATE INDEX idx_sales_customer     ON sales(customer_id);
CREATE INDEX idx_sales_invoice      ON sales(invoice_number);
CREATE INDEX idx_sales_created      ON sales(created_at DESC);

-- ─── 16. service_requests ────────────────────────────────────────
CREATE TABLE service_requests (
    id                   BIGSERIAL PRIMARY KEY,
    ticket_number        VARCHAR(50) UNIQUE,
    customer_id          BIGINT REFERENCES customers(id),
    customer_name        VARCHAR(150),
    customer_mobile      VARCHAR(15),
    customer_address     TEXT,
    product_name         VARCHAR(200),
    product_model        VARCHAR(100),
    issue_description    TEXT,
    assigned_technician  VARCHAR(150),
    service_charge       NUMERIC(10,2) DEFAULT 0,
    status               VARCHAR(30)   DEFAULT 'PENDING',
    priority             VARCHAR(20)   DEFAULT 'MEDIUM',
    technician_notes     TEXT,
    spare_parts_json     TEXT,
    spare_parts_total    NUMERIC(10,2) DEFAULT 0,
    products_sold_json   TEXT,
    total_bill_amount    NUMERIC(10,2) DEFAULT 0,
    payment_status       VARCHAR(20)   DEFAULT 'PENDING',
    payment_method       VARCHAR(30),
    invoice_number       VARCHAR(50),
    stock_deducted       BOOLEAN       DEFAULT FALSE,
    next_filter_due_date TIMESTAMP,
    next_service_due_date TIMESTAMP,
    completed_at         TIMESTAMP,
    created_at           TIMESTAMP     DEFAULT NOW(),
    updated_at           TIMESTAMP     DEFAULT NOW()
);
CREATE INDEX idx_sr_customer ON service_requests(customer_id);
CREATE INDEX idx_sr_status   ON service_requests(status);
CREATE INDEX idx_sr_ticket   ON service_requests(ticket_number);
CREATE INDEX idx_sr_filter_due ON service_requests(next_filter_due_date);

-- ─── 17. payments ────────────────────────────────────────────────
CREATE TABLE payments (
    id               BIGSERIAL PRIMARY KEY,
    payment_number   VARCHAR(50),
    customer_id      BIGINT REFERENCES customers(id),
    customer_name    VARCHAR(150),
    sale_id          BIGINT REFERENCES sales(id),
    invoice_number   VARCHAR(50),
    amount           NUMERIC(10,2) NOT NULL,
    payment_method   VARCHAR(30),
    payment_status   VARCHAR(30)   DEFAULT 'PENDING',
    transaction_id   VARCHAR(150),
    remarks          TEXT,
    received_by      VARCHAR(150),
    created_at       TIMESTAMP     DEFAULT NOW(),
    updated_at       TIMESTAMP     DEFAULT NOW()
);
CREATE INDEX idx_payments_customer ON payments(customer_id);

-- ─── 18. communication_logs ──────────────────────────────────────
CREATE TABLE communication_logs (
    id               BIGSERIAL PRIMARY KEY,
    channel          VARCHAR(30) NOT NULL,
    status           VARCHAR(30),
    customer_id      BIGINT REFERENCES customers(id),
    customer_name    VARCHAR(150),
    customer_mobile  VARCHAR(15),
    customer_email   VARCHAR(150),
    template_id      BIGINT REFERENCES document_templates(id),
    template_name    VARCHAR(200),
    message_content  TEXT,
    subject          TEXT,
    attachment_url   TEXT,
    sent_by          VARCHAR(150),
    created_at       TIMESTAMP DEFAULT NOW()
);

-- ─── 19. operation_history ───────────────────────────────────────
CREATE TABLE operation_history (
    id           BIGSERIAL PRIMARY KEY,
    action       VARCHAR(100) NOT NULL,
    entity_type  VARCHAR(50),
    entity_id    BIGINT,
    entity_name  VARCHAR(200),
    customer_id  BIGINT REFERENCES customers(id),
    performed_by VARCHAR(150),
    remarks      TEXT,
    meta_json    TEXT,
    created_at   TIMESTAMP DEFAULT NOW()
);
CREATE INDEX idx_history_customer ON operation_history(customer_id);
CREATE INDEX idx_history_entity   ON operation_history(entity_type, entity_id);

-- ─── 20. filter_presets & automation_rules ───────────────────────
CREATE TABLE filter_presets (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(150),
    entity_type  VARCHAR(50),
    filter_json  TEXT,
    created_by   VARCHAR(150),
    created_at   TIMESTAMP DEFAULT NOW()
);

CREATE TABLE automation_rules (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(200),
    trigger     VARCHAR(100),
    conditions  TEXT,
    actions     TEXT,
    active      BOOLEAN   DEFAULT TRUE,
    created_at  TIMESTAMP DEFAULT NOW(),
    updated_at  TIMESTAMP DEFAULT NOW()
);

-- ═══════════════════════════════════════════════════════════════════
-- Row Level Security (RLS) — public read for website content
-- ═══════════════════════════════════════════════════════════════════
ALTER TABLE products      ENABLE ROW LEVEL SECURITY;
ALTER TABLE service_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE gallery_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE blogs         ENABLE ROW LEVEL SECURITY;
ALTER TABLE brand_partners ENABLE ROW LEVEL SECURITY;
ALTER TABLE stock_items   ENABLE ROW LEVEL SECURITY;

-- Public can read active products, services, gallery, blogs, brands, stock
CREATE POLICY "public_read_products"       ON products       FOR SELECT USING (active = TRUE);
CREATE POLICY "public_read_service_items"  ON service_items  FOR SELECT USING (active = TRUE);
CREATE POLICY "public_read_gallery"        ON gallery_items  FOR SELECT USING (active = TRUE);
CREATE POLICY "public_read_blogs"          ON blogs          FOR SELECT USING (status = 'PUBLISHED');
CREATE POLICY "public_read_brands"         ON brand_partners FOR SELECT USING (active = TRUE);
CREATE POLICY "public_read_stock"          ON stock_items    FOR SELECT USING (active = TRUE);

-- Public can submit enquiries (INSERT only)
ALTER TABLE enquiries ENABLE ROW LEVEL SECURITY;
ALTER TABLE leads     ENABLE ROW LEVEL SECURITY;
CREATE POLICY "public_submit_enquiry" ON enquiries FOR INSERT WITH CHECK (TRUE);
CREATE POLICY "public_submit_lead"    ON leads     FOR INSERT WITH CHECK (TRUE);

-- ═══════════════════════════════════════════════════════════════════
-- Seed: Admin user (password = AGA@Admin2026! bcrypt hashed)
-- ═══════════════════════════════════════════════════════════════════
-- NOTE: Insert this AFTER the Spring Boot app starts once
-- The app will auto-seed users via DataSeeder on first run.
-- Run the app first, THEN set SPRING_JPA_DDL=validate.

-- ═══════════════════════════════════════════════════════════════════
-- Verify: you should see 20 tables
-- ═══════════════════════════════════════════════════════════════════
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
ORDER BY table_name;
