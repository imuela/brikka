-- ADR-IDENTITY-001: SUPERADMIN may exist without a company (BRIKA_MASTER_SPEC.md
-- §4.1). V1-V7 remain immutable; this is a new incremental migration.
ALTER TABLE users ALTER COLUMN company_id DROP NOT NULL;

-- uq_users_company_email (company_id, email) does not catch duplicate emails
-- among company_id IS NULL rows, because SQL treats every NULL as distinct in
-- a unique index. This partial index closes that gap for SUPERADMIN users.
CREATE UNIQUE INDEX uq_users_email_no_company ON users (email) WHERE company_id IS NULL;
