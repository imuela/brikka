-- Seed document_types with exactly the examples listed in
-- FUNCTIONAL_SPECIFICATION.md §11 ("Ejemplos"). Nothing invented beyond
-- what the specification names explicitly.

INSERT INTO document_types (id, code, name, active) VALUES
    (gen_random_uuid(), 'DNI', 'DNI', true),
    (gen_random_uuid(), 'NIE', 'NIE', true),
    (gen_random_uuid(), 'PAYSLIP', 'Nómina', true),
    (gen_random_uuid(), 'INCOME_TAX_RETURN', 'IRPF', true),
    (gen_random_uuid(), 'EMPLOYMENT_HISTORY', 'Vida laboral', true),
    (gen_random_uuid(), 'BANK_STATEMENT', 'Extracto bancario', true),
    (gen_random_uuid(), 'EMPLOYMENT_CONTRACT', 'Contrato laboral', true),
    (gen_random_uuid(), 'DEPOSIT_CONTRACT', 'Contrato de arras', true),
    (gen_random_uuid(), 'LAND_REGISTRY_EXTRACT', 'Nota simple', true),
    (gen_random_uuid(), 'PROPERTY_APPRAISAL', 'Tasación', true);
