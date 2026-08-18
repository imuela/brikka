/** Mirrors backend BankResponse. Global catalog (06_BANK_ENGINE_SPECIFICATION.md §2) — not
 * scoped to a tenant. */
export interface Bank {
  id: string;
  code: string;
  name: string;
  status: string;
  metadata: Record<string, unknown>;
}

/** Mirrors backend CreateBankApiRequest. */
export interface CreateBankRequest {
  code: string;
  name: string;
  metadata: Record<string, unknown>;
}

/** Mirrors backend UpdateBankApiRequest. */
export interface UpdateBankRequest {
  name: string;
  status: string;
  metadata: Record<string, unknown>;
}

/** Mirrors backend BankProductResponse. */
export interface BankProduct {
  id: string;
  bankId: string;
  code: string;
  name: string;
  status: string;
  metadata: Record<string, unknown>;
}

/** Mirrors backend CreateBankProductApiRequest. */
export interface CreateBankProductRequest {
  code: string;
  name: string;
  metadata: Record<string, unknown>;
}

/** Mirrors backend BankCriteriaVersionResponse. `rules` is a schemaless jsonb bag validated
 * server-side against the closed matching-engine schema (field/operator/severity/reason per
 * rule) — never assumed on the frontend. */
export interface BankCriteriaVersion {
  id: string;
  bankId: string;
  version: string;
  status: string;
  effectiveFrom: string;
  effectiveTo: string | null;
  rules: Record<string, unknown>;
}

/** Mirrors backend CreateBankCriteriaVersionApiRequest. */
export interface CreateBankCriteriaVersionRequest {
  version: string;
  effectiveFrom: string;
  effectiveTo: string | null;
  rules: Record<string, unknown>;
}
