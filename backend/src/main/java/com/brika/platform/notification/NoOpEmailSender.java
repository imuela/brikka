package com.brika.platform.notification;

import org.springframework.stereotype.Component;

/**
 * D8-2: no EMAIL provider is approved. Every send is structurally recorded as not-sent, with an
 * explicit reason, rather than silently pretending to succeed.
 */
@Component
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
