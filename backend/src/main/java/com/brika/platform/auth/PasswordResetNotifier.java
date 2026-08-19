package com.brika.platform.auth;

/**
 * Abstraction for delivering a password reset token to the user (autorización Sprint 22 §8:
 * "prepara la abstracción de envío de email" — selecting/contracting a real provider is explicitly
 * NOT authorized in this sprint). The only implementation wired today, {@link
 * LoggingPasswordResetNotifier}, is a development-only stand-in; a real implementation is a
 * decision pending user authorization (see the Sprint 22 final report, §"decisiones pendientes").
 */
public interface PasswordResetNotifier {

  void send(String toEmail, String rawResetToken);
}
