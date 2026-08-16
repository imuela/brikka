package com.brika.platform.identity.web;

/**
 * externalIdentityId must be supplied by the caller: Keycloak account provisioning/sync is not
 * implemented in Sprint 2 (no "brika" realm exists yet — ADR-IDENTITY-001 gate review), so the
 * MANAGER creating this record is responsible for having already created the matching identity
 * provider account out-of-band and knowing its subject id.
 */
public record CreateUserApiRequest(
    String email, String firstName, String lastName, String role, String externalIdentityId) {}
