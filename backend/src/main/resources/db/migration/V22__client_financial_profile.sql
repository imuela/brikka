-- Sprint 30 (modelo financiero del cliente).
--
-- Fuente funcional: ADR-SCORING-001/D9-1 (12_DECISION_LOG.md) documenta explícitamente que ningún
-- modelo de datos financieros de cliente existe todavía en el esquema y lo deja fuera de alcance de
-- V1/Sprint 9 — esta migración es la continuación planificada de esa decisión, no una contradicción
-- con ella. Los campos de valor (situación familiar, empleo, ingresos, ahorro, deudas) están tomados
-- 1:1 del análisis funcional de Brikka Legacy (clients/create.php, ver informe de auditoría de esta
-- misma sesión) — ningún campo se ha inventado sin esa base. "company" de Legacy (empleador) se
-- renombra a employer_name para no colisionar con el "company" tenant de Brikka.
--
-- 03_DOMAIN_SPECIFICATION.md §5 ("perfil financiero... información de negocio controlada, no se
-- permitirá modificarlo sin las reglas de autorización, trazabilidad y procedencia") y
-- 07_DATA_GOVERNANCE_SPECIFICATION.md §2/§3/§6/§8 exigen explícitamente: procedencia (fuente,
-- actor, fecha, evidencia), historial de cambios, y distinguir dato confirmado/pendiente/estimado/
-- rechazado/desactualizado. De ahí source/status/evidence_document_version_id/updated_by y la
-- tabla de historial — no son adorno, son requisito documentado preexistente.
--
-- Los campos "heredados de Legacy" (marital_status, employment_type, contract_type) se dejan como
-- texto libre sin CHECK, igual que el resto de campos equivalentes ya existentes en clients
-- (document_type, employment_status, V18) — mismo patrón ya establecido en este proyecto. source y
-- status sí son un catálogo cerrado propio de esta migración (no proceden de Legacy), por lo que sí
-- llevan CHECK. Los rangos numéricos (no negativos) se validan en el servicio (ValidationException),
-- no aquí, siguiendo la disciplina establecida en Sprint 29 de fallar con un 400 estructurado antes
-- de tocar la base de datos, no depender de la excepción de la constraint.
--
-- Granularidad: un perfil por cliente (estructural, reutilizable entre casos — 03_DOMAIN §"6.
-- Separación de conceptos" y la propia auditoría separan explícitamente los datos del cliente de
-- los datos de la operación, que ya viven en cases/financing_requests/simulations). La evidencia
-- (07_DATA_GOVERNANCE §3, ejemplo "INGRESO DECLARADO → EVIDENCIA: NÓMINA") se modela a nivel de
-- perfil completo, no por campo — una tabla EAV por campo sería fragmentar innecesariamente el
-- modelo para lo que V1 necesita (instrucción explícita del sprint).

CREATE TABLE client_financial_profiles (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    client_id uuid NOT NULL UNIQUE REFERENCES clients (id),

    marital_status varchar(50),
    dependents integer,
    employment_type varchar(50),
    contract_type varchar(50),
    employer_name varchar(255),
    years_employed integer,
    monthly_income numeric(14,2),
    savings numeric(14,2),
    other_debts_monthly_payment numeric(14,2),
    credit_card_debt numeric(14,2),

    source varchar(30) NOT NULL DEFAULT 'BROKER',
    status varchar(30) NOT NULL DEFAULT 'PENDING',
    evidence_document_version_id uuid REFERENCES document_versions (id),

    updated_by uuid NOT NULL REFERENCES users (id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT chk_client_financial_profiles_source CHECK (source IN ('CLIENT', 'BROKER', 'AI')),
    CONSTRAINT chk_client_financial_profiles_status
        CHECK (status IN ('PENDING', 'CONFIRMED', 'ESTIMATED', 'REJECTED', 'OUTDATED'))
);
CREATE INDEX idx_client_financial_profiles_company_id ON client_financial_profiles (company_id);
CREATE INDEX idx_client_financial_profiles_client_id ON client_financial_profiles (client_id);

-- Histórico append-only: una fila por cada escritura, snapshot completo del estado resultante
-- (mismo espíritu que case_status_history: reconstruir cómo se llegó al valor actual).
CREATE TABLE client_financial_profile_history (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    client_id uuid NOT NULL REFERENCES clients (id),
    financial_profile_id uuid NOT NULL REFERENCES client_financial_profiles (id),

    marital_status varchar(50),
    dependents integer,
    employment_type varchar(50),
    contract_type varchar(50),
    employer_name varchar(255),
    years_employed integer,
    monthly_income numeric(14,2),
    savings numeric(14,2),
    other_debts_monthly_payment numeric(14,2),
    credit_card_debt numeric(14,2),
    source varchar(30) NOT NULL,
    status varchar(30) NOT NULL,
    evidence_document_version_id uuid REFERENCES document_versions (id),

    changed_by uuid NOT NULL REFERENCES users (id),
    changed_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_client_financial_profile_history_client_id
    ON client_financial_profile_history (client_id);
CREATE INDEX idx_client_financial_profile_history_profile_id
    ON client_financial_profile_history (financial_profile_id);
