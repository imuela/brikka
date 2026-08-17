package com.brika.platform.audit;

import java.util.UUID;

/**
 * Seam between "a security/compliance-relevant action happened" and "how it reaches audit_events" —
 * same conceptual pattern as {@code ActivityPublisher} (Sprint 3, ADR-AUDIT-001): callers depend
 * only on this interface, so swapping the mechanism later (e.g. async via RabbitMQ) does not change
 * any caller.
 *
 * <p>Sprint 11: no domain service calls this yet. The V1 catalog of auditable actions is not
 * defined in any approved document ("acciones sensibles", 06_SECURITY_SPECIFICATION.md §7, and
 * "cuando corresponda", 17_API_SPECIFICATION_DETAILED.md line 251, are both deliberately
 * unenumerated) — wiring arbitrary actions now would mean inventing an audit policy that hasn't
 * been approved. This interface and {@link SynchronousAuditEventWriter} exist as prepared
 * infrastructure only, per Sprint 11 D11-5.
 */
public interface AuditEventWriter {

  void write(
      UUID companyId,
      UUID actorUserId,
      UUID actorClientId,
      String action,
      String resourceType,
      UUID resourceId,
      String requestId,
      String metadataJson);
}
