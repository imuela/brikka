package com.brika.platform.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * D8-2: no EMAIL provider is approved (ADR-NOTIF-001). Every send is structurally recorded as
 * not-sent, with an explicit reason, rather than silently pretending to succeed. It remains the
 * active implementation wherever {@code brika.notifications.email-transport} is unset/noop. Sprint
 * 24 (ADR-ENV-001): LOCAL and PROD now opt into {@code smtp} (Mailpit / real SMTP), so {@code noop}
 * is never used in production — enforced fail-closed by {@code ProdEnvironmentValidator}.
 */
@Component
@ConditionalOnProperty(
    name = "brika.notifications.email-transport",
    havingValue = "noop",
    matchIfMissing = true)
public class NoOpEmailSender implements EmailSender {

  @Override
  public EmailSendResult send(String toEmail, String subject, String body) {
    return new EmailSendResult(
        false,
        null,
        "No EMAIL provider configured (ADR-NOTIF-001 D8-2 — pending business decision to select a"
            + " provider).");
  }
}
