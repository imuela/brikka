package com.brika.platform.audit;

import java.util.UUID;

/**
 * Seam between "a security/compliance-relevant action happened" and "how it reaches audit_events" —
 * same conceptual pattern as {@code ActivityPublisher} (Sprint 3, ADR-AUDIT-001): callers depend
 * only on this interface, so swapping the mechanism later (e.g. async via RabbitMQ) does not change
 * any caller.
 *
 * <p>Sprint 12 (D12-2, {@code ADR-AUDIT-002}): connected to the 9 categories of {@code
 * FUNCTIONAL_SPECIFICATION.md} §24 "Auditoría funcional" that have a concrete, already-implemented
 * endpoint — see {@code ADR-AUDIT-002} for the full catalog and for the 3 categories deliberately
 * left unconnected (login, exportaciones, integraciones) because no real hook exists for them
 * without inventing new functionality. {@code requestId} is captured internally by the
 * implementation from the request's correlation id (MDC) — callers never need to touch MDC
 * themselves, keeping every call site a single, uniform statement.
 */
public interface AuditEventWriter {

  void write(
      UUID companyId,
      UUID actorUserId,
      UUID actorClientId,
      String action,
      String resourceType,
      UUID resourceId,
      String metadataJson);
}
