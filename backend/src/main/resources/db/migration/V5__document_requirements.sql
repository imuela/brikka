-- ADR-DOC-001. Document requirement catalog, independent of document_requests.

CREATE TABLE document_requirements (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    operation_type varchar(100) NOT NULL,
    document_type_id uuid NOT NULL REFERENCES document_types (id),
    mandatory boolean NOT NULL DEFAULT true,
    conditions jsonb NOT NULL DEFAULT '{}'::jsonb,
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_document_requirements_operation_type ON document_requirements (operation_type);

ALTER TABLE document_requests
    ADD COLUMN requirement_id uuid REFERENCES document_requirements (id);
