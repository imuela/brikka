-- Sprint 31 (scoring/viabilidad financiera).
--
-- Ubicación de la decisión Cliente vs Caso: este análisis depende de datos de financiación
-- (capital/tipo/plazo, procedentes de la oferta bancaria final o de la simulación más reciente del
-- caso), no solo del perfil financiero del cliente — 02_PRODUCT_SPECIFICATION.md y el propio
-- Sprint 30 tratan la operación hipotecaria como el eje central. Por tanto vive en el dominio de
-- Case, con FK a case_id, nunca dentro de client_financial_profiles (que sigue siendo
-- exclusivamente estructural, sin resultados calculados — regla explícita del Sprint 30).
--
-- Multi-cliente: un caso puede tener varios clientes (case_clients, cualquier participation_type).
-- No existe ninguna regla documentada ni en Legacy ni en la documentación de Brikka para sumar
-- ingresos entre titulares — Legacy solo agregaba a nivel de score ya calculado, nunca a nivel de
-- ingreso bruto. Por tanto cada ejecución de este análisis produce una fila POR CLIENTE del caso
-- (misma cuota/capital/tipo/plazo para todos, ya que es una única hipoteca; income/deudas/DTI/
-- categoría propios de cada cliente) — nunca una cifra combinada de hogar.
--
-- Trazabilidad (07_DATA_GOVERNANCE_SPECIFICATION.md §2/§4/§5/§8): append-only, nunca se actualiza
-- una fila ya escrita (mismo patrón que scoring_results/bank_match_results/case_status_history) —
-- si el perfil financiero o la financiación cambian después, el resultado histórico no cambia en
-- silencio; hay que volver a ejecutar el análisis para obtener uno nuevo. rules_version identifica
-- qué versión de los umbrales de viabilidad se usó (ver ViabilityClassifier).
CREATE TABLE case_financial_analysis_results (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    case_id uuid NOT NULL REFERENCES cases (id),
    client_id uuid NOT NULL REFERENCES clients (id),

    principal numeric(14,2) NOT NULL,
    interest_rate numeric(7,4) NOT NULL,
    term_months integer NOT NULL,
    monthly_payment numeric(14,2) NOT NULL,

    monthly_income numeric(14,2) NOT NULL,
    existing_monthly_debts numeric(14,2) NOT NULL,
    dti_percent numeric(7,2) NOT NULL,
    viability_category varchar(30) NOT NULL,

    -- 'BANK_OFFER' (financiación final seleccionada) o 'SIMULATION' (simulación más reciente),
    -- nunca 'FINANCING_REQUEST' — financing_requests no tiene tipo de interés, no puede ser fuente
    -- de una cuota calculada por sí solo (ver FinancialAnalysisService).
    quota_source varchar(30) NOT NULL,
    quota_source_id uuid NOT NULL,
    rules_version varchar(50) NOT NULL,
    explanation jsonb NOT NULL DEFAULT '{}'::jsonb,

    calculated_by uuid NOT NULL REFERENCES users (id),
    calculated_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT chk_case_financial_analysis_results_viability_category
        CHECK (viability_category IN ('FAVORABLE', 'REVISAR', 'NO_VIABLE')),
    CONSTRAINT chk_case_financial_analysis_results_quota_source
        CHECK (quota_source IN ('BANK_OFFER', 'SIMULATION'))
);
CREATE INDEX idx_case_financial_analysis_results_company_id
    ON case_financial_analysis_results (company_id);
CREATE INDEX idx_case_financial_analysis_results_case_id
    ON case_financial_analysis_results (case_id);
CREATE INDEX idx_case_financial_analysis_results_client_id
    ON case_financial_analysis_results (client_id);
