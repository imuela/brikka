package com.brika.platform.notification;

/**
 * Seam between "an EMAIL delivery is due" and "how it actually leaves Brika". Implementations are
 * selected by {@code brika.notifications.email-transport} per environment (Sprint 24): {@link
 * SmtpEmailSender} for {@code smtp} (local Mailpit and production), a test recording sender for the
 * {@code test} profile, and {@link NoOpEmailSender} (the {@code noop} fallback). Callers never know
 * which transport is active.
 */
public interface EmailSender {

  EmailSendResult send(String toEmail, String subject, String body);
}
