package com.brika.platform.identity.web;

/**
 * externalIdentityId must be supplied by the caller — an opaque, unique login identifier for this
 * user (Sprint 22 cierre, ADR-AUTH-001: Brika's own auth, no external identity provider). No
 * automatic generation or validation happens here; the credential itself is established separately
 * (password-reset flow or {@code InternalCredentialBootstrapController}).
 */
public record CreateUserApiRequest(
    String email, String firstName, String lastName, String role, String externalIdentityId) {}
