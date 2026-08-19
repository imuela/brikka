package com.brika.platform.auth;

import com.brika.platform.notification.EmailSendResult;
import com.brika.platform.notification.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Portal Cliente counterpart of {@link UserPasswordResetEmailNotifier} — separate class per
 * ADR-PORTAL-AUTH-001, builds the {@code /portal/password-reset} link instead.
 */
@Component
@Qualifier("portalPasswordResetNotifier")
public class PortalPasswordResetEmailNotifier implements PasswordResetNotifier {

  private static final Logger log = LoggerFactory.getLogger(PortalPasswordResetEmailNotifier.class);

  private final EmailSender emailSender;
  private final String frontendBaseUrl;

  public PortalPasswordResetEmailNotifier(
      EmailSender emailSender,
      @Value("${brika.frontend-base-url:http://localhost:4200}") String frontendBaseUrl) {
    this.emailSender = emailSender;
    this.frontendBaseUrl = frontendBaseUrl;
  }

  @Override
  public void send(String toEmail, String rawResetToken) {
    String link = frontendBaseUrl + "/portal/password-reset?token=" + rawResetToken;
    String body =
        "Hemos recibido una solicitud para restablecer tu contraseña del Portal Cliente de"
            + " Brika.\n\nSi has sido tú, sigue este enlace (caduca en un tiempo limitado, uso"
            + " único):\n"
            + link
            + "\n\nSi no has solicitado esto, puedes ignorar este mensaje.";
    EmailSendResult result =
        emailSender.send(toEmail, "Restablecer tu contraseña del Portal Brika", body);
    if (!result.sent()) {
      log.info(
          "Portal password reset email not actually delivered (transport=noop unless configured"
              + " otherwise): {}",
          result.failureReason());
    }
  }
}
