-- ADR-PLATFORM-001. Plans/entitlements/company subscriptions.
-- Global catalog (plans, entitlements, plan_entitlements) is not tenant-owned.
-- No billing/payment tables: explicitly out of V1 scope.

CREATE TABLE plans (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(100) NOT NULL UNIQUE,
    name varchar(255) NOT NULL,
    status varchar(30) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE entitlements (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(100) NOT NULL UNIQUE,
    name varchar(255) NOT NULL,
    description text,
    value_type varchar(20) NOT NULL,
    CONSTRAINT chk_entitlements_value_type CHECK (value_type IN ('BOOLEAN', 'NUMERIC', 'JSON'))
);

CREATE TABLE plan_entitlements (
    plan_id uuid NOT NULL REFERENCES plans (id),
    entitlement_id uuid NOT NULL REFERENCES entitlements (id),
    value jsonb NOT NULL DEFAULT '{}'::jsonb,
    PRIMARY KEY (plan_id, entitlement_id)
);

CREATE TABLE company_subscriptions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL UNIQUE REFERENCES companies (id),
    plan_id uuid NOT NULL REFERENCES plans (id),
    status varchar(30) NOT NULL,
    started_at timestamptz NOT NULL DEFAULT now(),
    current_period_end timestamptz,
    cancelled_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_company_subscriptions_status
        CHECK (status IN ('ACTIVE', 'TRIAL', 'SUSPENDED', 'CANCELLED'))
);
