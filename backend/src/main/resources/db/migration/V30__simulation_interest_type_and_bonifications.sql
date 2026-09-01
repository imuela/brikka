-- BRIKKA V2 · Bloque I4 (simulación hipotecaria enriquecida).
--
-- Fuente funcional: BRIKKA_V2_MIGRATION_SCOPE.md §1 I4 + BRIKKA_V2_BUSINESS_RULES_GAP.md R18/R19.
-- La BRIKKA moderna ya calcula la cuota por sistema francés (MortgagePaymentCalculator, Sprint 31);
-- lo que faltaba es el desglose del tipo de interés y las bonificaciones. Legacy guardaba ese
-- desglose y las columnas bonus_* pero NUNCA las aplicaba (bug F17c): aquí las bonificaciones
-- reducen de verdad el tipo que alimenta el cálculo. NO se recuperan de Legacy
-- monthly_payment_phase2, total_interest ni recommended (valores incorrectos o columnas muertas).
--
-- Aditivo y no destructivo: ninguna columna existente se elimina ni se altera. Las simulaciones
-- históricas eran un único tipo plano -> se clasifican como FIXED con base = final = interest_rate
-- (backfill abajo); su estimated_payment se conserva tal cual.

ALTER TABLE simulations
    ADD COLUMN interest_type varchar(20) NOT NULL DEFAULT 'FIXED'
        CHECK (interest_type IN ('FIXED', 'VARIABLE', 'MIXED')),
    ADD COLUMN base_interest_rate numeric(7, 4),
    ADD COLUMN final_interest_rate numeric(7, 4),
    ADD COLUMN euribor_rate numeric(7, 4),
    ADD COLUMN spread_rate numeric(7, 4),
    ADD COLUMN fixed_period_months integer
        CHECK (fixed_period_months IS NULL OR fixed_period_months > 0),
    ADD COLUMN fixed_period_rate numeric(7, 4),
    ADD COLUMN ico_guarantee boolean NOT NULL DEFAULT false,
    ADD COLUMN bonifications jsonb NOT NULL DEFAULT '[]'::jsonb;

-- Backfill no destructivo de las filas existentes (equivalen a FIXED sin bonificaciones).
UPDATE simulations
SET base_interest_rate = interest_rate,
    final_interest_rate = interest_rate
WHERE base_interest_rate IS NULL;

-- A partir de aquí el desglose base/final siempre está presente (el servicio lo calcula en cada
-- alta); euribor/spread/fixed_period_* siguen siendo NULL cuando el tipo no los usa.
ALTER TABLE simulations
    ALTER COLUMN base_interest_rate SET NOT NULL,
    ALTER COLUMN final_interest_rate SET NOT NULL;
