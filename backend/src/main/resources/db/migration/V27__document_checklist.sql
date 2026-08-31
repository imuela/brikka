-- BRIKKA V2 · Bloque I1 (checklist documental del expediente).
--
-- Fuente funcional: BRIKKA_V2_MIGRATION_SCOPE.md §1 I1 + BRIKKA_V2_BUSINESS_RULES_GAP.md R13/R14.
-- El mecanismo (catálogo condicional `document_requirements` V5 + `document_requests` con
-- `requirement_id`) ya existe; esta migración aporta lo que faltaba: (a) los datos del catálogo para
-- PURCHASE, (b) la dimensión de titular en `documents`, (c) una garantía de unicidad del catálogo.
--
-- NO se inventa nada: el mapa de requisitos es el de Brikka Legacy (`config/document_checklist.php`
-- CLIENT_DOCUMENTS / CASE_DOCUMENTS) traducido a los códigos ya sembrados en V2. "oferta_bancaria"
-- de Legacy NO se incluye: en el modelo nuevo una oferta es un `bank_offers`, no un documento.

-- 1) Unicidad del catálogo: la auto-generación de `document_requests` (CaseChecklistService) asume
--    un requisito por (operation_type, document_type). No hay datos previos (V5 solo creó la tabla,
--    ninguna migración ni el seed insertan filas), por lo que la constraint no puede romper nada.
ALTER TABLE document_requirements
    ADD CONSTRAINT uq_document_requirements_op_type_doc_type
        UNIQUE (operation_type, document_type_id);

-- 2) Dimensión de titular en `documents`. Legacy separaba client_documents (por titular) de
--    case_documents (del expediente); el modelo V1 colapsó ambos en `documents(case_id,
--    document_type_id)`. Para cerrar un requisito PER_HOLDER por evidencia aprobada hace falta poder
--    atribuir un documento a un titular. Nullable: todo documento existente y todo flujo actual
--    (subida normal, generadores de dossier/contrato) sigue sin cliente asociado (client_id = NULL,
--    que es exactamente "documento del expediente").
ALTER TABLE documents
    ADD COLUMN client_id uuid REFERENCES clients (id);
CREATE INDEX idx_documents_client_id ON documents (client_id);

-- 3) Catálogo de requisitos documentales para operaciones de compra (PURCHASE).
--    conditions.appliesTo distingue documento por titular (uno por cada HOLDER/CO_HOLDER del caso)
--    de documento del expediente (uno por caso). El resto del jsonb queda libre para condiciones
--    futuras (ADR-DOC-001) sin cambiar el esquema.
INSERT INTO document_requirements (id, operation_type, document_type_id, mandatory, conditions, active)
SELECT
    gen_random_uuid(),
    'PURCHASE',
    dt.id,
    req.mandatory,
    req.conditions::jsonb,
    true
FROM (
    VALUES
        ('DNI',                   true,  '{"appliesTo":"PER_HOLDER"}'),
        ('PAYSLIP',               true,  '{"appliesTo":"PER_HOLDER"}'),
        ('EMPLOYMENT_HISTORY',    true,  '{"appliesTo":"PER_HOLDER"}'),
        ('INCOME_TAX_RETURN',     false, '{"appliesTo":"PER_HOLDER"}'),
        ('EMPLOYMENT_CONTRACT',   false, '{"appliesTo":"PER_HOLDER"}'),
        ('BANK_STATEMENT',        false, '{"appliesTo":"PER_HOLDER"}'),
        ('LAND_REGISTRY_EXTRACT', true,  '{"appliesTo":"PER_CASE"}'),
        ('DEPOSIT_CONTRACT',      true,  '{"appliesTo":"PER_CASE"}'),
        ('PROPERTY_APPRAISAL',    false, '{"appliesTo":"PER_CASE"}')
) AS req (code, mandatory, conditions)
JOIN document_types dt ON dt.code = req.code;
