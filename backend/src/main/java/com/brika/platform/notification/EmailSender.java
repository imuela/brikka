package com.brika.platform.notification;

/**
 * Seam between "an EMAIL delivery is due" and "how it actually leaves Brika". Sprint 8 (D8-2): no
 * EMAIL provider is approved (ADR-INTEGRATIONS-001 forbids introducing one without approval), so
 * only NoOpEmailSender exists today. Swapping in a real provider later is a configuration change
 * behind this interface — no caller changes.
 */
public interface EmailSender {

  EmailSendResult send(String toEmail, String subject, String body);
}
