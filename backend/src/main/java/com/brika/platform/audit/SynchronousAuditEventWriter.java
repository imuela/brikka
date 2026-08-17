package com.brika.platform.audit;

import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Sprint 11 (ADR-AUDIT-001 adenda): synchronous, in-transaction implementation — same mechanism as
 * {@code SynchronousActivityPublisher} (Sprint 3), no RabbitMQ/outbox yet
 * (20_RABBITMQ_SPECIFICATION.md describes the target async architecture for a later hardening
 * sprint). No caller wires this yet — see {@link AuditEventWriter}.
 */
@Component
public class SynchronousAuditEventWriter implements AuditEventWriter {

  private final AuditEventRepository auditEventRepository;

  public SynchronousAuditEventWriter(AuditEventRepository auditEventRepository) {
    this.auditEventRepository = auditEventRepository;
  }

  @Override
  public void write(
      UUID companyId,
      UUID actorUserId,
      UUID actorClientId,
      String action,
      String resourceType,
      UUID resourceId,
      String requestId,
      String metadataJson) {
    auditEventRepository.insert(
        companyId,
        actorUserId,
        actorClientId,
        action,
        resourceType,
        resourceId,
        requestId,
        metadataJson);
  }
}
