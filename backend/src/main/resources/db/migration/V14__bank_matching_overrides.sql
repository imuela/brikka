-- ADR-BANKENGINE-002 (Sprint 6C): manual overrides of individual bank_match_rule_results.
-- bank_match_results / bank_match_rule_results (V13) remain fully immutable and untouched by this
-- migration — overrides live in their own append-only table, never UPDATEd, and the effective
-- result (per-rule and global) is always derived at read time, never persisted back onto the
-- original rows.

CREATE TABLE bank_match_rule_overrides (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    bank_match_rule_result_id uuid NOT NULL REFERENCES bank_match_rule_results (id),
    previous_result varchar(20) NOT NULL,
    new_result varchar(20) NOT NULL,
    reason text NOT NULL,
    overridden_by uuid NOT NULL REFERENCES users (id),
    overridden_at timestamptz NOT NULL DEFAULT now(),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_bank_match_rule_overrides_previous_result CHECK (
        previous_result IN ('PASS', 'FAIL', 'WARNING', 'NOT_EVALUATED')
    ),
    CONSTRAINT chk_bank_match_rule_overrides_new_result CHECK (
        new_result IN ('PASS', 'FAIL', 'WARNING', 'NOT_EVALUATED')
    ),
    CONSTRAINT chk_bank_match_rule_overrides_result_changed CHECK (previous_result <> new_result)
);
CREATE INDEX idx_bank_match_rule_overrides_rule_result_id ON bank_match_rule_overrides (bank_match_rule_result_id);
CREATE INDEX idx_bank_match_rule_overrides_company_id ON bank_match_rule_overrides (company_id);

INSERT INTO permissions (id, code, name) VALUES
    (gen_random_uuid(), 'BANK_MATCHING_OVERRIDE', 'Corregir manualmente el resultado de una regla de matching bancario');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (
  VALUES
    ('MANAGER', 'BANK_MATCHING_OVERRIDE'),
    ('SUPERADMIN', 'BANK_MATCHING_OVERRIDE')
) AS approved (role_code, permission_code)
JOIN roles r ON r.code = approved.role_code
JOIN permissions p ON p.code = approved.permission_code;
