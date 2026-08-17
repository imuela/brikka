package com.brika.platform.audit;

import com.brika.platform.common.observability.CorrelationIdFilter;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

/**
 * Sprint 11 (ADR-AUDIT-001 adenda): synchronous, in-transaction implementation — same mechanism as
 * {@code SynchronousActivityPublisher} (Sprint 3), no RabbitMQ/outbox yet
 * (20_RABBITMQ_SPECIFICATION.md describes the target async architecture for a later hardening
 * sprint). Connected to domain callers since Sprint 12 (D12-2, {@code ADR-AUDIT-002}) — see {@link
 * AuditEventWriter}. {@code requestId} is read from the same MDC key {@code GlobalExceptionHandler}
 * already uses, so audit events correlate with any error logged for the same request.
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
      String metadataJson) {
    String requestId = MDC.get(CorrelationIdFilter.MDC_KEY);
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
