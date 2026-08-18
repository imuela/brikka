/** Sprint 20 (ADR-PROCESS-008): propertyType is free text in the backend contract (no CHECK
 * constraint, no enum). Frontend-only closed catalog approved explicitly by the project owner;
 * the backend still accepts any string. FLAT is the only value in real use before this sprint and
 * remains valid unchanged. */
export const PROPERTY_TYPES = [
  'FLAT',
  'HOUSE',
  'CHALET',
  'STUDIO',
  'COMMERCIAL_PREMISES',
  'LAND',
  'GARAGE',
] as const;
export type PropertyType = (typeof PROPERTY_TYPES)[number];

/** Mirrors backend PropertyResponse. address is a schemaless jsonb Map<String,Object> on the
 * backend (no documented field shape — 16_POSTGRESQL_SCHEMA_SPECIFICATION.md §properties: "Los
 * campos de dirección se detallarán en migraciones/DTO según el modelo final"). The frontend
 * treats it as a small set of common free-text address fields (street/city/postalCode/province)
 * rather than inventing a business schema — address itself is deliberately NOT given a closed
 * catalog like propertyType above, since there is no finite set of possible address values. */
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
