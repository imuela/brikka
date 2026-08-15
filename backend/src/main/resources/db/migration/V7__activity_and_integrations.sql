-- ADR-AUDIT-001 (activities, distinct from audit_events).
-- ADR-INTEGRATIONS-001 (integrations, minimal extensibility scaffolding;
-- integration_events intentionally not created — no approved technical
-- dependency for it yet).

CREATE TABLE activities (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    case_id uuid REFERENCES cases (id),
    client_id uuid REFERENCES clients (id),
    actor_user_id uuid REFERENCES users (id),
    actor_client_id uuid REFERENCES clients (id),
    activity_type varchar(100) NOT NULL,
    summary text NOT NULL,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_activities_company_id ON activities (company_id);
CREATE INDEX idx_activities_case_id ON activities (case_id);

CREATE TABLE integrations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid REFERENCES companies (id),
    type varchar(100) NOT NULL,
    status varchar(30) NOT NULL,
    config jsonb NOT NULL DEFAULT '{}'::jsonb,
    credentials_ref varchar(255),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_integrations_company_id ON integrations (company_id);
