package com.brika.platform.notification;

/**
 * Centralized catalog of the notification {@code type} strings produced by Sprint 25. The column is
 * a free varchar (ADR-NOTIF-001), so keeping them in one place prevents drift between the producers
 * (CaseService, DocumentService, ConversationMessageService) and any consumers. Only the types
 * actually wired to a real domain event this sprint live here — no speculative future types.
 */
public final class NotificationType {

  public static final String CASE_STATUS_CHANGED = "CASE_STATUS_CHANGED";
  public static final String CASE_CANCELLED = "CASE_CANCELLED";
  public static final String CASE_REOPENED = "CASE_REOPENED";

  public static final String DOCUMENT_UPLOADED = "DOCUMENT_UPLOADED";
  public static final String DOCUMENT_REVIEWED = "DOCUMENT_REVIEWED";
  public static final String DOCUMENT_PUBLISHED = "DOCUMENT_PUBLISHED";

  public static final String NEW_MESSAGE = "NEW_MESSAGE";

  private NotificationType() {}
}
