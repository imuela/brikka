package com.brika.platform.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * D8-2: no EMAIL provider is approved. Every send is structurally recorded as not-sent, with an
 * explicit reason, rather than silently pretending to succeed. Remains the default active
 * implementation in every environment (matchIfMissing = true) — ADR-NOTIF-001's "no provider"
 * decision is not reversed by Sprint 22 cierre §5, which only activates {@link SmtpEmailSender}
 * against a local Mailpit instance when explicitly opted into via {@code
 * brika.notifications.email-transport=smtp}.
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
