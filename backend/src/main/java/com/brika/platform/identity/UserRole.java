package com.brika.platform.identity;

/** Mirrors roles.code seeded in V3__seed_roles_permissions.sql (ADR-004). */
public enum UserRole {
  SUPERADMIN,
  MANAGER,
  BROKER,
  CLIENT
}
