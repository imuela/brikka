package com.brika.platform.auth;

import com.brika.platform.notification.EmailSendResult;
import com.brika.platform.notification.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Sprint 22 cierre, punto 5: builds the internal-realm password reset link and sends it via the
 * existing {@link EmailSender} abstraction (D8-2) — real delivery only when {@code
 * brika.notifications.email-transport=smtp} (local Mailpit); otherwise this composes the same email
 * and hands it to {@code NoOpEmailSender}, which structurally records "not sent" exactly as
 * ADR-NOTIF-001 already does for every other EMAIL notification in the system.
 */
@Component
@Qualifier("userPasswordResetNotifier")
public class UserPasswordResetEmailNotifier implements PasswordResetNotifier {

  private static final Logger log = LoggerFactory.getLogger(UserPasswordResetEmailNotifier.class);

  private final EmailSender emailSender;
  private final String frontendBaseUrl;

  public UserPasswordResetEmailNotifier(
      EmailSender emailSender,
      @Value("${brika.frontend-base-url:http://localhost:4200}") String frontendBaseUrl) {
    this.emailSender = emailSender;
    this.frontendBaseUrl = frontendBaseUrl;
  }

  @Override
  public void send(String toEmail, String rawResetToken) {
    String link = frontendBaseUrl + "/password-reset?token=" + rawResetToken;
    String body =
        "Hemos recibido una solicitud para restablecer tu contraseña de Brika.\n\n"
            + "Si has sido tú, sigue este enlace (caduca en un tiempo limitado, uso único):\n"
            + link
            + "\n\nSi no has solicitado esto, puedes ignorar este mensaje.";
    EmailSendResult result = emailSender.send(toEmail, "Restablecer tu contraseña de Brika", body);
    if (!result.sent()) {
      log.info(
          "Password reset email not actually delivered (transport=noop unless configured"
              + " otherwise): {}",
          result.failureReason());
    }
  }
}
