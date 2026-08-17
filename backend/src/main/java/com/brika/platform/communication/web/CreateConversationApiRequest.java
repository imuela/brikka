package com.brika.platform.communication.web;

import java.util.List;
import java.util.UUID;

/**
 * type is CLIENT or INTERNAL (SYSTEM is not created by any endpoint — no sprint documents it).
 * clientIds is required and must be non-empty for CLIENT (ADR-COMMS-002: never created without
 * participants); ignored for INTERNAL, which has no conversation_participants row (ADR-COMMS-002:
 * authorization stays implicit via CASE ASSIGNMENT).
 */
public record CreateConversationApiRequest(String type, List<UUID> clientIds) {}
