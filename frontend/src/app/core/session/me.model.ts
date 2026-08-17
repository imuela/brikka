/** Mirrors backend MeResponse exactly (17_API_SPECIFICATION_DETAILED.md §4) — no firstName/
 * lastName field exists there (Sprint 13 Fase 0 finding H4), so the shell shows email only. */
export interface MeResponse {
  id: string;
  email: string;
  role: string;
  companyId: string | null;
  entitlements: Record<string, string>;
}

export interface PermissionsResponse {
  permissions: string[];
}
