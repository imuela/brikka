package com.brika.platform.notification.web;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID id, String type, Object payload, Instant readAt, Instant createdAt) {}
