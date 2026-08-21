-- Sprint 31. New permission pair for the financial viability analysis (POST/GET
-- /cases/{caseId}/financial-analysis) — same precedent as BANK_MATCHING_RUN/READ (V13) and
-- SCORING_RUN/SCORING_READ (seeded in V3/V9): a genuinely new case-scoped computed action gets its
-- own dedicated permission pair rather than overloading an existing one from a different domain
-- (e.g. FINANCING_REQUEST_READ, which is about a different resource). Granted to
-- SUPERADMIN/MANAGER/BROKER, matching the exact same grant shape as both precedents above — never
-- CLIENT/Portal (see FinancialAnalysisController javadoc for why).
INSERT INTO permissions (id, code, name) VALUES
    (gen_random_uuid(), 'FINANCIAL_ANALYSIS_RUN', 'Ejecutar el análisis de viabilidad financiera de un caso'),
    (gen_random_uuid(), 'FINANCIAL_ANALYSIS_READ', 'Consultar resultados de viabilidad financiera');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM (
  VALUES
    ('SUPERADMIN', 'FINANCIAL_ANALYSIS_RUN'),
    ('MANAGER', 'FINANCIAL_ANALYSIS_RUN'),
    ('BROKER', 'FINANCIAL_ANALYSIS_RUN'),
    ('SUPERADMIN', 'FINANCIAL_ANALYSIS_READ'),
    ('MANAGER', 'FINANCIAL_ANALYSIS_READ'),
    ('BROKER', 'FINANCIAL_ANALYSIS_READ')
) AS approved (role_code, permission_code)
JOIN roles r ON r.code = approved.role_code
JOIN permissions p ON p.code = approved.permission_code;
