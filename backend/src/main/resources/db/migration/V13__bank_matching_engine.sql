-- ADR-BANKENGINE-001 (Sprint 6B): deterministic bank matching engine.
-- Persists append-only, immutable execution history (§9/§10 of the ADR) —
-- neither table is ever UPDATEd by application code. Overrides (D-D) are
-- explicitly out of scope and NOT created here.

CREATE TABLE bank_match_results (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    case_id uuid NOT NULL REFERENCES cases (id),
    bank_id uuid NOT NULL REFERENCES banks (id),
    bank_criteria_version_id uuid NOT NULL REFERENCES bank_criteria_versions (id),
    global_result varchar(20) NOT NULL,
    input_snapshot jsonb NOT NULL,
    evaluated_by uuid NOT NULL REFERENCES users (id),
    evaluated_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_bank_match_results_global_result CHECK (
        global_result IN ('PASS', 'FAIL', 'WARNING', 'NOT_EVALUATED', 'ERROR')
    )
);
CREATE INDEX idx_bank_match_results_company_id ON bank_match_results (company_id);
CREATE INDEX idx_bank_match_results_case_id ON bank_match_results (case_id);

CREATE TABLE bank_match_rule_results (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    match_result_id uuid NOT NULL REFERENCES bank_match_results (id),
    rule_id varchar(64) NOT NULL,
    field varchar(100) NOT NULL,
    operator varchar(30) NOT NULL,
    expected_value jsonb NOT NULL,
    evaluated_value jsonb,
    result varchar(20) NOT NULL,
    reason text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_bank_match_rule_results_result CHECK (
        result IN ('PASS', 'FAIL', 'WARNING', 'NOT_EVALUATED')
    )
);
CREATE INDEX idx_bank_match_rule_results_match_result_id ON bank_match_rule_results (match_result_id);

INSERT INTO permissions (id, code, name) VALUES
    (gen_random_uuid(), 'BANK_MATCHING_RUN', 'Ejecutar el motor de matching bancario para un caso'),
    (gen_random_uuid(), 'BANK_MATCHING_READ', 'Consultar resultados de matching bancario');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (
  VALUES
    ('SUPERADMIN', 'BANK_MATCHING_RUN'),
    ('MANAGER', 'BANK_MATCHING_RUN'),
    ('BROKER', 'BANK_MATCHING_RUN'),
    ('SUPERADMIN', 'BANK_MATCHING_READ'),
    ('MANAGER', 'BANK_MATCHING_READ'),
    ('BROKER', 'BANK_MATCHING_READ')
) AS approved (role_code, permission_code)
JOIN roles r ON r.code = approved.role_code
JOIN permissions p ON p.code = approved.permission_code;
