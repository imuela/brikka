-- Sprint 32 (honorarios, contratos y dossier de viabilidad) — bloque de honorarios.
--
-- Análisis previo (obligatorio antes de diseñar, ver informe de cierre del sprint): ningún modelo de
-- honorarios/comisión/tarifa de broker existe en ningún punto de `docs/` ni del código actual (grep
-- exhaustivo sobre honorario|comision|comisión|fee|tarifa|retribución, cero resultados; Case.java,
-- Company.java, Plan.java, FinalFinancing.java, BankOffer.java sin ningún campo de este tipo). No hay
-- nada que duplicar: este es un modelo mínimo nuevo, explícito, sin precedente que reutilizar.
--
-- Dominio: pertenece al CASO, no al cliente ni a la empresa. Justificación: un mismo cliente puede
-- tener varios casos con honorarios distintos (cada operación es un encargo independiente), y no
-- existe ninguna base documental para un catálogo de tarifas por empresa (habría que inventarlo). Un
-- único registro "vigente" por caso (case_id UNIQUE), igual que client_financial_profiles (V22) es
-- único por cliente — mismo patrón mutable-con-historial ya validado en Sprint 30.
--
-- Base de cálculo: cuando fee_type = PERCENTAGE, calculation_base es un importe introducido/
-- confirmado explícitamente por el broker/manager al configurar el honorario, NUNCA derivado
-- automáticamente de otra tabla (Case.requested_amount, la oferta bancaria seleccionada o la
-- simulación más reciente podrían discrepar entre sí sin que exista ninguna regla documentada sobre
-- cuál debería prevalecer para honorarios — encadenar esa lógica sería inventar un cálculo financiero
-- no respaldado, exactamente lo que el sprint prohíbe). calculated_amount es siempre el resultado
-- determinista: fixed_amount tal cual, o calculation_base * percentage / 100 (BigDecimal, escala 2,
-- HALF_UP — ver CaseFeeService), calculado y persistido por el backend, nunca por el cliente.
--
-- Trazabilidad: misma disciplina que V22 — un registro "vigente" mutable (case_fees) más un historial
-- append-only (case_fee_history) con una fila por cada escritura.

CREATE TABLE case_fees (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    case_id uuid NOT NULL UNIQUE REFERENCES cases (id),

    fee_type varchar(20) NOT NULL,
    fixed_amount numeric(14,2),
    percentage numeric(7,4),
    calculation_base numeric(14,2),
    calculated_amount numeric(14,2) NOT NULL,

    status varchar(20) NOT NULL DEFAULT 'PROPOSED',
    agreed_at timestamptz,

    updated_by uuid NOT NULL REFERENCES users (id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT chk_case_fees_fee_type CHECK (fee_type IN ('FIXED', 'PERCENTAGE')),
    CONSTRAINT chk_case_fees_status CHECK (status IN ('PROPOSED', 'AGREED', 'CANCELLED'))
);
CREATE INDEX idx_case_fees_company_id ON case_fees (company_id);

-- Histórico append-only: mismo espíritu que client_financial_profile_history (V22).
CREATE TABLE case_fee_history (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id uuid NOT NULL REFERENCES companies (id),
    case_id uuid NOT NULL REFERENCES cases (id),
    fee_id uuid NOT NULL REFERENCES case_fees (id),

    fee_type varchar(20) NOT NULL,
    fixed_amount numeric(14,2),
    percentage numeric(7,4),
    calculation_base numeric(14,2),
    calculated_amount numeric(14,2) NOT NULL,
    status varchar(20) NOT NULL,
    agreed_at timestamptz,

    changed_by uuid NOT NULL REFERENCES users (id),
    changed_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_case_fee_history_case_id ON case_fee_history (case_id);
CREATE INDEX idx_case_fee_history_fee_id ON case_fee_history (fee_id);
