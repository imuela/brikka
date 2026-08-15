-- BRIKA V1 — Initial physical schema.
-- Derived exclusively from 16_POSTGRESQL_SCHEMA_SPECIFICATION.md and 15_DEFINITIVE_ERD.md.
-- Tables added later by ADR (plans/entitlements, document_requirements,
-- communications extensions, activities/integrations) are NOT created here:
-- see V4-V7. gen_random_uuid() is native in PostgreSQL 13+, no extension needed.

-- =========================================================================
-- 3. Identity / Tenancy
-- =========================================================================

CREATE TABLE companies (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    legal_name varchar(255) NOT NULL,
    trade_name varchar(255) NOT NULL,
    tax_id varchar(50) NOT NULL,
    status varchar(30) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    external_identity_id varchar(255) NOT NULL,
    email varchar(255) NOT NULL,
    first_name varchar(255) NOT NULL,
    last_name varchar(255) NOT NULL,
    status varchar(30) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_company_id ON users (company_id);
CREATE UNIQUE INDEX uq_users_company_email ON users (company_id, email);

CREATE TABLE roles (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(100) NOT NULL UNIQUE,
    name varchar(255) NOT NULL
);

CREATE TABLE permissions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(100) NOT NULL UNIQUE,
    name varchar(255) NOT NULL
);

CREATE TABLE user_roles (
    user_id uuid NOT NULL REFERENCES users (id),
    role_id uuid NOT NULL REFERENCES roles (id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE role_permissions (
    role_id uuid NOT NULL REFERENCES roles (id),
    permission_id uuid NOT NULL REFERENCES permissions (id),
    PRIMARY KEY (role_id, permission_id)
);

-- =========================================================================
-- 4. CRM
-- =========================================================================

CREATE TABLE clients (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    first_name varchar(255) NOT NULL,
    last_name varchar(255) NOT NULL,
    email varchar(255) NOT NULL,
    phone varchar(50) NOT NULL,
    status varchar(30) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_clients_company_id ON clients (company_id);

CREATE TABLE client_portal_accounts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    client_id uuid NOT NULL REFERENCES clients (id),
    external_identity_id varchar(255) NOT NULL,
    status varchar(30) NOT NULL,
    last_login_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_client_portal_accounts_company_id ON client_portal_accounts (company_id);
CREATE INDEX idx_client_portal_accounts_client_id ON client_portal_accounts (client_id);

-- =========================================================================
-- 5. Cases
-- =========================================================================

CREATE TABLE cases (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    reference varchar(100) NOT NULL,
    status varchar(30) NOT NULL,
    operation_type varchar(100) NOT NULL,
    created_by uuid NOT NULL REFERENCES users (id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    cancelled_at timestamptz,
    CONSTRAINT uq_cases_company_reference UNIQUE (company_id, reference)
);
CREATE INDEX idx_cases_company_id ON cases (company_id);
CREATE INDEX idx_cases_status ON cases (status);

CREATE TABLE case_clients (
    case_id uuid NOT NULL REFERENCES cases (id),
    client_id uuid NOT NULL REFERENCES clients (id),
    participation_type varchar(30) NOT NULL,
    is_primary boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (case_id, client_id),
    CONSTRAINT chk_case_clients_participation_type
        CHECK (participation_type IN ('HOLDER', 'CO_HOLDER', 'GUARANTOR', 'OTHER'))
);
CREATE INDEX idx_case_clients_client_id ON case_clients (client_id);

CREATE TABLE case_assignments (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    case_id uuid NOT NULL REFERENCES cases (id),
    user_id uuid NOT NULL REFERENCES users (id),
    assignment_type varchar(50) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    ended_at timestamptz
);
CREATE INDEX idx_case_assignments_company_id ON case_assignments (company_id);
CREATE INDEX idx_case_assignments_case_id ON case_assignments (case_id);
CREATE INDEX idx_case_assignments_user_id ON case_assignments (user_id);

CREATE TABLE case_status_history (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    case_id uuid NOT NULL REFERENCES cases (id),
    previous_status varchar(30),
    new_status varchar(30) NOT NULL,
    changed_by uuid NOT NULL REFERENCES users (id),
    changed_at timestamptz NOT NULL DEFAULT now(),
    reason varchar(50),
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX idx_case_status_history_company_id ON case_status_history (company_id);
CREATE INDEX idx_case_status_history_case_id ON case_status_history (case_id);

-- =========================================================================
-- 6. Property
-- =========================================================================

CREATE TABLE properties (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    case_id uuid NOT NULL UNIQUE REFERENCES cases (id),
    address jsonb NOT NULL DEFAULT '{}'::jsonb,
    property_type varchar(100) NOT NULL,
    valuation numeric(14, 2),
    purchase_price numeric(14, 2),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_properties_company_id ON properties (company_id);

-- =========================================================================
-- 7. Documents (document_requirements deferred to V5)
-- =========================================================================

CREATE TABLE document_types (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(100) NOT NULL UNIQUE,
    name varchar(255) NOT NULL,
    active boolean NOT NULL DEFAULT true
);

CREATE TABLE document_requests (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    case_id uuid NOT NULL REFERENCES cases (id),
    document_type_id uuid NOT NULL REFERENCES document_types (id),
    requested_from_client_id uuid REFERENCES clients (id),
    status varchar(30) NOT NULL,
    due_at timestamptz,
    requested_by uuid NOT NULL REFERENCES users (id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_document_requests_company_id ON document_requests (company_id);
CREATE INDEX idx_document_requests_case_id ON document_requests (case_id);

CREATE TABLE documents (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    case_id uuid NOT NULL REFERENCES cases (id),
    document_type_id uuid NOT NULL REFERENCES document_types (id),
    current_version_id uuid,
    status varchar(30) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_documents_company_id ON documents (company_id);
CREATE INDEX idx_documents_case_id ON documents (case_id);

CREATE TABLE document_versions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id uuid NOT NULL REFERENCES documents (id),
    version_number integer NOT NULL,
    storage_key varchar(1024) NOT NULL,
    original_filename varchar(255) NOT NULL,
    mime_type varchar(255) NOT NULL,
    size_bytes bigint NOT NULL,
    checksum varchar(255) NOT NULL,
    uploaded_by uuid NOT NULL REFERENCES users (id),
    uploaded_at timestamptz NOT NULL DEFAULT now(),
    review_status varchar(30) NOT NULL,
    reviewed_by uuid REFERENCES users (id),
    reviewed_at timestamptz,
    CONSTRAINT uq_document_versions_document_version UNIQUE (document_id, version_number)
);
CREATE INDEX idx_document_versions_document_id ON document_versions (document_id);

-- documents.current_version_id and document_versions have a circular
-- dependency; the FK is added after both tables exist.
ALTER TABLE documents
    ADD CONSTRAINT fk_documents_current_version
    FOREIGN KEY (current_version_id) REFERENCES document_versions (id);

CREATE TABLE document_publications (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    document_id uuid NOT NULL REFERENCES documents (id),
    document_version_id uuid NOT NULL REFERENCES document_versions (id),
    published_to_portal boolean NOT NULL DEFAULT true,
    published_by uuid NOT NULL REFERENCES users (id),
    published_at timestamptz NOT NULL DEFAULT now(),
    revoked_at timestamptz
);
CREATE INDEX idx_document_publications_company_id ON document_publications (company_id);
CREATE INDEX idx_document_publications_document_id ON document_publications (document_id);

-- =========================================================================
-- 8. Financing
-- =========================================================================

CREATE TABLE simulations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    case_id uuid NOT NULL REFERENCES cases (id),
    principal numeric(14, 2) NOT NULL,
    interest_rate numeric(7, 4) NOT NULL,
    term_months integer NOT NULL,
    estimated_payment numeric(14, 2) NOT NULL,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_by uuid NOT NULL REFERENCES users (id),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_simulations_company_id ON simulations (company_id);
CREATE INDEX idx_simulations_case_id ON simulations (case_id);

CREATE TABLE financing_requests (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    case_id uuid NOT NULL REFERENCES cases (id),
    status varchar(30) NOT NULL,
    requested_amount numeric(14, 2) NOT NULL,
    term_months integer NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_financing_requests_company_id ON financing_requests (company_id);
CREATE INDEX idx_financing_requests_case_id ON financing_requests (case_id);

-- =========================================================================
-- 9. Banking
-- =========================================================================

CREATE TABLE banks (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(100) NOT NULL UNIQUE,
    name varchar(255) NOT NULL,
    status varchar(30) NOT NULL,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE bank_products (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_id uuid NOT NULL REFERENCES banks (id),
    code varchar(100) NOT NULL,
    name varchar(255) NOT NULL,
    status varchar(30) NOT NULL,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX idx_bank_products_bank_id ON bank_products (bank_id);

CREATE TABLE bank_criteria_versions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_id uuid NOT NULL REFERENCES banks (id),
    version varchar(50) NOT NULL,
    status varchar(30) NOT NULL,
    effective_from timestamptz NOT NULL,
    effective_to timestamptz,
    rules jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_bank_criteria_versions_bank_id ON bank_criteria_versions (bank_id);

CREATE TABLE bank_contacts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    bank_id uuid NOT NULL REFERENCES banks (id),
    owner_user_id uuid REFERENCES users (id),
    name varchar(255) NOT NULL,
    position varchar(255),
    department varchar(255),
    branch varchar(255),
    email varchar(255),
    phone varchar(50),
    secondary_phone varchar(50),
    notes text,
    visibility varchar(30) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_bank_contacts_visibility CHECK (visibility IN ('COMPANY', 'PRIVATE'))
);
CREATE INDEX idx_bank_contacts_company_bank_active ON bank_contacts (company_id, bank_id, active);

CREATE TABLE bank_requests (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    case_id uuid NOT NULL REFERENCES cases (id),
    bank_id uuid NOT NULL REFERENCES banks (id),
    bank_contact_id uuid REFERENCES bank_contacts (id),
    status varchar(30) NOT NULL,
    submitted_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    contact_snapshot jsonb NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX idx_bank_requests_company_id ON bank_requests (company_id);
CREATE INDEX idx_bank_requests_case_id ON bank_requests (case_id);

CREATE TABLE bank_responses (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_request_id uuid NOT NULL REFERENCES bank_requests (id),
    status varchar(30) NOT NULL,
    received_at timestamptz NOT NULL DEFAULT now(),
    summary text,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_bank_responses_bank_request_id ON bank_responses (bank_request_id);

CREATE TABLE bank_offers (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    bank_request_id uuid NOT NULL REFERENCES bank_requests (id),
    bank_id uuid NOT NULL REFERENCES banks (id),
    status varchar(30) NOT NULL,
    amount numeric(14, 2) NOT NULL,
    interest_rate numeric(7, 4) NOT NULL,
    term_months integer NOT NULL,
    payment numeric(14, 2) NOT NULL,
    conditions jsonb NOT NULL DEFAULT '{}'::jsonb,
    received_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_bank_offers_company_id ON bank_offers (company_id);
CREATE INDEX idx_bank_offers_bank_request_id ON bank_offers (bank_request_id);

CREATE TABLE final_financing (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    case_id uuid NOT NULL UNIQUE REFERENCES cases (id),
    bank_offer_id uuid NOT NULL REFERENCES bank_offers (id),
    status varchar(30) NOT NULL,
    finalized_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_final_financing_company_id ON final_financing (company_id);

-- =========================================================================
-- 10. Tasks
-- =========================================================================

CREATE TABLE tasks (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    case_id uuid REFERENCES cases (id),
    assigned_to uuid REFERENCES users (id),
    type varchar(100) NOT NULL,
    title varchar(255) NOT NULL,
    description text,
    status varchar(30) NOT NULL,
    due_at timestamptz,
    created_by uuid NOT NULL REFERENCES users (id),
    completed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_tasks_status
        CHECK (status IN ('TODO', 'IN_PROGRESS', 'BLOCKED', 'DONE', 'CANCELLED'))
);
CREATE INDEX idx_tasks_company_id ON tasks (company_id);
CREATE INDEX idx_tasks_case_id ON tasks (case_id);
CREATE INDEX idx_tasks_assigned_to ON tasks (assigned_to);

-- =========================================================================
-- 11. Communications (conversation_participants, message_attachments,
--     notification_deliveries deferred to V6)
-- =========================================================================

CREATE TABLE conversations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    case_id uuid NOT NULL REFERENCES cases (id),
    type varchar(30) NOT NULL,
    status varchar(30) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_conversations_type CHECK (type IN ('CLIENT', 'INTERNAL', 'SYSTEM'))
);
CREATE INDEX idx_conversations_company_id ON conversations (company_id);
CREATE INDEX idx_conversations_case_id ON conversations (case_id);

CREATE TABLE messages (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id uuid NOT NULL REFERENCES conversations (id),
    sender_user_id uuid REFERENCES users (id),
    sender_client_id uuid REFERENCES clients (id),
    body text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    edited_at timestamptz,
    CONSTRAINT chk_messages_single_sender CHECK (
        (sender_user_id IS NOT NULL AND sender_client_id IS NULL)
        OR (sender_user_id IS NULL AND sender_client_id IS NOT NULL)
    )
);
CREATE INDEX idx_messages_conversation_id ON messages (conversation_id);

CREATE TABLE notifications (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    recipient_user_id uuid REFERENCES users (id),
    recipient_client_id uuid REFERENCES clients (id),
    type varchar(100) NOT NULL,
    payload jsonb NOT NULL DEFAULT '{}'::jsonb,
    read_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_notifications_single_recipient CHECK (
        (recipient_user_id IS NOT NULL AND recipient_client_id IS NULL)
        OR (recipient_user_id IS NULL AND recipient_client_id IS NOT NULL)
    )
);
CREATE INDEX idx_notifications_company_id ON notifications (company_id);

-- =========================================================================
-- 12. Scoring
-- =========================================================================

CREATE TABLE scoring_rulesets (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code varchar(100) NOT NULL,
    version varchar(50) NOT NULL,
    status varchar(30) NOT NULL,
    rules jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_scoring_rulesets_code_version UNIQUE (code, version)
);

CREATE TABLE scoring_rules (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    ruleset_id uuid NOT NULL REFERENCES scoring_rulesets (id),
    code varchar(100) NOT NULL,
    weight numeric(7, 4) NOT NULL,
    configuration jsonb NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX idx_scoring_rules_ruleset_id ON scoring_rules (ruleset_id);

CREATE TABLE scoring_results (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    case_id uuid NOT NULL REFERENCES cases (id),
    ruleset_id uuid NOT NULL REFERENCES scoring_rulesets (id),
    total_score numeric(7, 2) NOT NULL,
    category varchar(50) NOT NULL,
    explanation jsonb NOT NULL DEFAULT '{}'::jsonb,
    calculated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_scoring_results_company_id ON scoring_results (company_id);
CREATE INDEX idx_scoring_results_case_id ON scoring_results (case_id);

-- =========================================================================
-- 13. AI
-- =========================================================================

CREATE TABLE document_extractions (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    document_version_id uuid NOT NULL REFERENCES document_versions (id),
    status varchar(30) NOT NULL,
    provider varchar(100) NOT NULL,
    model varchar(100) NOT NULL,
    extracted_data jsonb NOT NULL DEFAULT '{}'::jsonb,
    confidence jsonb NOT NULL DEFAULT '{}'::jsonb,
    validated_by uuid REFERENCES users (id),
    validated_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_document_extractions_company_id ON document_extractions (company_id);
CREATE INDEX idx_document_extractions_document_version_id ON document_extractions (document_version_id);

CREATE TABLE ai_usage (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    case_id uuid REFERENCES cases (id),
    user_id uuid REFERENCES users (id),
    provider varchar(100) NOT NULL,
    model varchar(100) NOT NULL,
    operation varchar(100) NOT NULL,
    input_tokens integer,
    output_tokens integer,
    estimated_cost numeric(10, 4),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_ai_usage_company_id ON ai_usage (company_id);

-- =========================================================================
-- 14. Audit
-- =========================================================================

CREATE TABLE audit_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid REFERENCES companies (id),
    actor_user_id uuid REFERENCES users (id),
    actor_client_id uuid REFERENCES clients (id),
    action varchar(100) NOT NULL,
    resource_type varchar(100) NOT NULL,
    resource_id uuid,
    request_id varchar(100),
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_events_company_id ON audit_events (company_id);
CREATE INDEX idx_audit_events_created_at ON audit_events (created_at);
