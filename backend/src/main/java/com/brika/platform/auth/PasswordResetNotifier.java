package com.brika.platform.auth;

/**
 * Abstraction for delivering a password reset token to the user. Implementations build the reset
 * link and deliver it via the {@code EmailSender} abstraction: {@link
 * UserPasswordResetEmailNotifier} (internal) and {@link PortalPasswordResetEmailNotifier} (Portal).
 * Delivery is real when the active transport is {@code smtp} (local Mailpit / production); with the
 * {@code noop} fallback the email is composed but structurally not sent (ADR-NOTIF-001).
 */
public interface PasswordResetNotifier {

  void send(String toEmail, String rawResetToken);
}
