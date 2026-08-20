-- Sprint 27, Bloque 4 (FUNCTIONAL_SPECIFICATION.md §7 "Crear operación ... introducir información
-- inicial"): the mortgage operation (case) is enriched with the initially-requested amount and a
-- free-text description. Both are nullable — the legacy create flow (operationType only) keeps
-- working. The amount is summary metadata on the expediente, distinct from financing/simulation
-- amounts (which live in their own tables).
ALTER TABLE cases
    ADD COLUMN requested_amount numeric(14, 2),
    ADD COLUMN description text;