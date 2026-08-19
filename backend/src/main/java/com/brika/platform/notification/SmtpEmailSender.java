package com.brika.platform.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Sprint 22 cierre, punto 5: real SMTP delivery, activated only when {@code
 * brika.notifications.email-transport=smtp} — local development points this at Mailpit (a local
 * SMTP catcher, never a production provider). Selecting a real transactional provider remains a
 * separate, explicitly pending decision (ADR-NOTIF-001 D8-2); this class only changes where local
 * SMTP traffic lands, not whether a production provider has been approved. On any send failure
 * (e.g. Mailpit not running), this fails soft — same "structural EmailSendResult, never throw"
 * contract as {@link NoOpEmailSender} — so a caller (e.g. password reset) is never broken by an
 * unreachable local mail catcher.
 */
@Component
@ConditionalOnProperty(name = "brika.notifications.email-transport", havingValue = "smtp")
public class SmtpEmailSender implements EmailSender {

  private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

  private final JavaMailSender mailSender;
  private final String fromAddress;

  public SmtpEmailSender(
      JavaMailSender mailSender,
      @Value("${brika.notifications.email-from:no-reply@brika.local}") String fromAddress) {
    this.mailSender = mailSender;
    this.fromAddress = fromAddress;
  }

  @Override
  public EmailSendResult send(String toEmail, String subject, String body) {
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setFrom(fromAddress);
      message.setTo(toEmail);
      message.setSubject(subject);
      message.setText(body);
      mailSender.send(message);
      return new EmailSendResult(true, null, null);
    } catch (MailException e) {
      log.warn("SMTP send failed (toEmail redacted, transport=smtp): {}", e.getMessage());
      return new EmailSendResult(false, null, "SMTP send failed: " + e.getClass().getSimpleName());
    }
  }
}
