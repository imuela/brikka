package com.brika.platform.notification;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Sprint 24 (perfil TEST): sender en memoria que captura cada envío para poder asertar el contenido
 * real de un email (p. ej. el enlace de password-reset) en pruebas que activan el perfil {@code
 * test} con {@code brika.notifications.email-transport=test}. Solo vive en src/test y solo cuando
 * el perfil test está activo — nunca en local/prod.
 */
@Component
@Profile("test")
@ConditionalOnProperty(name = "brika.notifications.email-transport", havingValue = "test")
public class RecordingEmailSender implements EmailSender {

  private final List<RecordedEmail> sent = new CopyOnWriteArrayList<>();

  public record RecordedEmail(String toEmail, String subject, String body) {}

  @Override
  public EmailSendResult send(String toEmail, String subject, String body) {
    sent.add(new RecordedEmail(toEmail, subject, body));
    return new EmailSendResult(true, null, null);
  }

  public List<RecordedEmail> sent() {
    return sent;
  }

  public void reset() {
    sent.clear();
  }
}
