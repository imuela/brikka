package com.brika.platform.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * Exactly one of recipientUserId / recipientClientId is set (chk_notifications_single_recipient).
 * Written by NotificationService, driven by the Sprint 25 event producers (CaseService,
 * DocumentService, ConversationMessageService) through NotificationPublisher — see ADR-NOTIF-002.
 */
public record Notification(
    UUID id,
    UUID companyId,
    UUID recipientUserId,
    UUID recipientClientId,
    String type,
    String payload,
    Instant readAt,
    Instant createdAt) {}
