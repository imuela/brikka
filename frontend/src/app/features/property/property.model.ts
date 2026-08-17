/** Mirrors backend PropertyResponse. address is a schemaless jsonb Map<String,Object> on the
 * backend (no documented field shape — 16_POSTGRESQL_SCHEMA_SPECIFICATION.md §properties: "Los
 * campos de dirección se detallarán en migraciones/DTO según el modelo final"). The frontend
 * treats it as a small set of common free-text address fields (street/city/postalCode/province)
 * rather than inventing a business schema — same reasoning already applied to Case.operationType
 * in Sprint 14. */
export interface Property {
  id: string;
  companyId: string;
  caseId: string;
  address: Record<string, string>;
  propertyType: string;
  valuation: number | null;
  purchasePrice: number | null;
}

/** Mirrors backend UpsertPropertyApiRequest. PUT is idempotent create-or-replace (1 Case ⇄ 0..1
 * Property) — there is no separate create endpoint. */
export interface UpsertPropertyRequest {
  address: Record<string, string>;
  propertyType: string;
  valuation: number | null;
  purchasePrice: number | null;
}
