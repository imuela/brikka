package com.brika.platform.notification;

import java.time.Instant;
import java.util.UUID;

/**
 * Exactly one of recipientUserId / recipientClientId is set (chk_notifications_single_recipient).
 * No producer exists yet in this codebase (nothing writes notifications — the writer belongs to
 * Sprint 8, ADR-NOTIF-001); Sprint 7 only needs the read surface for GET /portal/notifications,
 * which will simply be empty until then.
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
