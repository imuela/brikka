/** Mirrors backend CompanyResponse (17_API_SPECIFICATION_DETAILED.md §4B). status has no CHECK
 * constraint in the schema, but CompanyController only ever writes ACTIVE/SUSPENDED/DELETED. */
export interface Company {
  id: string;
  legalName: string;
  tradeName: string;
  taxId: string;
  status: string;
}

/** Mirrors backend CreateCompanyApiRequest. */
export interface CreateCompanyRequest {
  legalName: string;
  tradeName: string;
  taxId: string;
}

/** Mirrors backend UpdateCompanyApiRequest — never accepts status; lifecycle transitions go
 * through the dedicated suspend/delete endpoints. */
export interface UpdateCompanyRequest {
  legalName: string;
  tradeName: string;
  taxId: string;
}

/** Mirrors backend CompanySubscriptionResponse. Lives in this model file rather than its own
 * feature — the real API only exposes it nested under a company (/companies/{id}/subscription),
 * with no standalone screen or nav entry of its own. */
export interface CompanySubscription {
  id: string;
  companyId: string;
  planId: string;
  status: string;
}

/** Mirrors backend UpsertCompanySubscriptionApiRequest. status must be one of the values in
 * chk_company_subscriptions_status (ACTIVE/TRIAL/SUSPENDED/CANCELLED). */
export interface UpsertCompanySubscriptionRequest {
  planId: string;
  status: string;
}
