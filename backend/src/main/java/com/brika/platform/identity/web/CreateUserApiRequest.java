package com.brika.platform.identity.web;

/**
 * externalIdentityId must be supplied by the caller — an opaque, unique login identifier for this
 * user (Sprint 22 cierre, ADR-AUTH-001: Brika's own auth, no external identity provider). No
 * automatic generation or validation happens here; the credential itself is established separately
 * (password-reset flow or {@code InternalCredentialBootstrapController}).
 *
 * <p>companyId (Sprint 27, ADR-RBAC-002) is optional and only honoured when the caller is a GLOBAL
 * SUPERADMIN (who has no company of their own). For a tenant user (MANAGER/BROKER) the target
 * company is always the caller's own tenant, so any supplied companyId is ignored.
 */
public record CreateUserApiRequest(
    String email,
    String firstName,
    String lastName,
    String role,
    String externalIdentityId,
    java.util.UUID companyId) {}
