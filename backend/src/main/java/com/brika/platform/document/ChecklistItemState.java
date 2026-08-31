package com.brika.platform.document;

/**
 * BRIKKA V2 I1. State of a single checklist requirement for a case, derived from the documents on
 * the case — never a stored flag. A requirement counts as satisfied only in {@link #APPROVED}
 * (product-owner decision §10.3: "no basta con que exista archivo").
 */
public enum ChecklistItemState {
  /** No document of this type (and holder, if per-holder) uploaded yet. */
  MISSING,
  /** A document exists with a current version pending review. */
  SUBMITTED,
  /** The current version of the matching document was rejected. */
  REJECTED,
  /** A matching document has an approved current version — the requirement is met. */
  APPROVED
}
