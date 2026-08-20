-- Sprint 27, Bloque 3 (FUNCTIONAL_SPECIFICATION.md §6 "Crear cliente"): a client is richer than
-- name/email/phone. All new columns are nullable and optional — the legacy creation flow keeps
-- working with just first/last name, email and phone.
ALTER TABLE clients
    ADD COLUMN document_type varchar(30),
    ADD COLUMN document_number varchar(50),
    ADD COLUMN date_of_birth date,
    ADD COLUMN nationality varchar(100),
    ADD COLUMN address varchar(255),
    ADD COLUMN employment_status varchar(50);