-- Sprint 7 decision D3: a Portal Cliente principal (client_portal_accounts)
-- has no corresponding users row by construction (ADR-PORTAL-AUTH-001), so
-- document_versions.uploaded_by (NOT NULL REFERENCES users) cannot record a
-- client-initiated upload. uploaded_by becomes nullable; uploaded_by_client_id
-- is added for the Portal case. Internal users keep using uploaded_by
-- exactly as before — no existing row is touched, no fictitious/system user,
-- no reuse of users.id to represent a Portal client.

ALTER TABLE document_versions
    ALTER COLUMN uploaded_by DROP NOT NULL;

ALTER TABLE document_versions
    ADD COLUMN uploaded_by_client_id uuid REFERENCES clients (id);

ALTER TABLE document_versions
    ADD CONSTRAINT chk_document_versions_single_uploader CHECK (
        (uploaded_by IS NOT NULL AND uploaded_by_client_id IS NULL)
        OR (uploaded_by IS NULL AND uploaded_by_client_id IS NOT NULL)
    );
