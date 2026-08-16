package com.brika.platform.activity;

/**
 * Seam between "a CASE domain event happened" and "how it reaches activities". Sprint 3 pre-flight
 * Decision A: implemented synchronously (SynchronousActivityPublisher) in the same transaction as
 * the triggering operation — no RabbitMQ/outbox yet (20_RABBITMQ_SPECIFICATION.md describes the
 * target async architecture for a later hardening sprint). Callers depend only on this interface,
 * so swapping the mechanism later does not change CaseService or any other caller.
 */
public interface ActivityPublisher {

  void publish(CaseActivityEvent event);
}
