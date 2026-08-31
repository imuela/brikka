-- BRIKKA V2 · Bloque I2 (scoring de fábrica + indicador RAG del expediente).
--
-- Fuente funcional: BRIKKA_V2_MIGRATION_SCOPE.md §1 I2 + BRIKKA_V2_BUSINESS_RULES_GAP.md R09/R10 +
-- ADR-SCORING-001. El motor de scoring (ScoringEngine + scoring_rulesets/scoring_rules, D9-*) YA
-- existe; lo único que faltaba era un ruleset ACTIVE "de fábrica" para que la acción "Calcular
-- scoring" produzca una categoría sin obligar a un administrador a crearlo a mano vía
-- POST /api/v1/scoring/rulesets en cada entorno.
--
-- NO se recupera la fórmula Legacy de scoring de cliente ni la ponderación 65/35: las reglas
-- puntúan EXCLUSIVAMENTE señales de la operación/inmueble (los 5 campos cerrados de ScoreField),
-- la configuración (umbrales de categoría) vive en el propio ruleset (jsonb `rules.categories`),
-- nunca en código Java. Este ruleset alimenta además el eje "scoring de operación" del indicador
-- RAG del expediente (CaseRagService).
--
-- Categorías — ScoringRulesValidator D9-3 exige orden ascendente por maxScore y una última entrada
-- con maxScore=null (catch-all). El total acumula "puntos a favor", así que a MENOS señales
-- favorables corresponde la categoría más baja:
--   RED    total <= 40   (pocas señales favorables)
--   AMBER  total <= 69
--   GREEN  resto          (catch-all, maxScore=null)
--
-- Reglas (peso = puntos a favor si la condición se cumple; máximo teórico 100):
--   ltv-strong     computed.ltv                     <= 0.70  -> 50
--   ltv-moderate   computed.ltv                     <= 0.80  -> 25
--   term-standard  financingRequest.termMonths      <= 360   -> 20
--   amount-known   financingRequest.requestedAmount >  0     -> 5
--
-- Un expediente sin inmueble ni solicitud de financiación puntúa 0 (todas las reglas quedan
-- NOT_EVALUATED por campo nulo) y resuelve a RED, comportamiento coherente con "sin señal
-- suficiente todavía".
--
-- Idempotencia / reproducibilidad: es una migración Flyway versionada — se aplica exactamente una
-- vez por base de datos (flyway_schema_history + checksum), igual que V27/V28. El código/versión
-- del ruleset está fijado, de modo que toda base construida desde cero obtiene el mismo seed.

INSERT INTO scoring_rulesets (id, code, version, status, rules) VALUES
    (gen_random_uuid(), 'default-operation-v1', 'v1', 'ACTIVE',
     '{"categories":[{"name":"RED","maxScore":40},{"name":"AMBER","maxScore":69},{"name":"GREEN","maxScore":null}]}'::jsonb);

INSERT INTO scoring_rules (id, ruleset_id, code, weight, configuration)
SELECT gen_random_uuid(), rs.id, seed.code, seed.weight, seed.configuration::jsonb
FROM (
    VALUES
        ('ltv-strong',    50.0000, '{"field":"computed.ltv","operator":"LESS_THAN_OR_EQUAL","value":0.70}'),
        ('ltv-moderate',  25.0000, '{"field":"computed.ltv","operator":"LESS_THAN_OR_EQUAL","value":0.80}'),
        ('term-standard', 20.0000, '{"field":"financingRequest.termMonths","operator":"LESS_THAN_OR_EQUAL","value":360}'),
        ('amount-known',   5.0000, '{"field":"financingRequest.requestedAmount","operator":"GREATER_THAN","value":0}')
) AS seed (code, weight, configuration)
JOIN scoring_rulesets rs ON rs.code = 'default-operation-v1' AND rs.version = 'v1';
