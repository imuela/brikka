package com.brika.platform.financing;

/**
 * No status catalog is documented anywhere for financing_requests (same category as
 * DocumentRequestStatus in Sprint 4). Conservative default, disclosed as technical debt: created
 * (PENDING), actively worked (IN_PROGRESS), no longer active (CLOSED). Not a business rule and not
 * to be used to infer states for other entities.
 */
public enum FinancingRequestStatus {
  PENDING,
  IN_PROGRESS,
  CLOSED
}
