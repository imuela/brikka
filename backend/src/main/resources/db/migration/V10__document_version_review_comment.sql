-- Sprint 4: FUNCTIONAL_SPECIFICATION.md §11 requires that a rejection can carry a
-- reason ("Cuando rechace un documento deberá indicar el motivo cuando la
-- configuración lo requiera"), but no column existed to persist one. Nullable,
-- additive only — does not modify V1-V9.
ALTER TABLE document_versions ADD COLUMN review_comment text;
