package com.brika.platform.communication.web;

import java.util.List;
import java.util.UUID;

/**
 * Sprint 7 (D2): only CLIENT-type conversations can be created — INTERNAL/SYSTEM are Sprint 8
 * scope. clientIds must be non-empty (ADR-COMMS-002: never created without participants).
 */
public record CreateConversationApiRequest(List<UUID> clientIds) {}
