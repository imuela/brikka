package com.brika.platform.document;

/**
 * No enum/CHECK constraint is documented for document_versions.review_status or documents.status —
 * this is the conservative technical default agreed for Sprint 4 (provisional, not a business
 * catalog), grounded in FUNCTIONAL_SPECIFICATION.md §11 ("aprobar", "rechazar"). "Solicitar nueva
 * versión" is represented as REJECTED with an explanatory review_comment, not a fourth status.
 */
public enum ReviewStatus {
  PENDING,
  APPROVED,
  REJECTED
}
