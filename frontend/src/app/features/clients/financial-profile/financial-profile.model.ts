/** Sprint 30. Mirrors backend ClientFinancialProfileResponse exactly
 * (com.brika.platform.crm.web.ClientFinancialProfileResponse). */
export interface ClientFinancialProfile {
  id: string;
  companyId: string;
  clientId: string;
  maritalStatus: string | null;
  dependents: number | null;
  employmentType: string | null;
  contractType: string | null;
  employerName: string | null;
  yearsEmployed: number | null;
  monthlyIncome: number | null;
  savings: number | null;
  otherDebtsMonthlyPayment: number | null;
  creditCardDebt: number | null;
  source: string;
  status: string;
  evidenceDocumentVersionId: string | null;
  updatedBy: string;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors backend UpsertClientFinancialProfileApiRequest. */
export interface UpsertClientFinancialProfileRequest {
  maritalStatus: string | null;
  dependents: number | null;
  employmentType: string | null;
  contractType: string | null;
  employerName: string | null;
  yearsEmployed: number | null;
  monthlyIncome: number | null;
  savings: number | null;
  otherDebtsMonthlyPayment: number | null;
  creditCardDebt: number | null;
  source: string | null;
  status: string | null;
  evidenceDocumentVersionId: string | null;
}

/** Mirrors backend ClientFinancialProfileHistoryResponse. */
export interface ClientFinancialProfileHistoryEntry {
  id: string;
  financialProfileId: string;
  maritalStatus: string | null;
  dependents: number | null;
  employmentType: string | null;
  contractType: string | null;
  employerName: string | null;
  yearsEmployed: number | null;
  monthlyIncome: number | null;
  savings: number | null;
  otherDebtsMonthlyPayment: number | null;
  creditCardDebt: number | null;
  source: string;
  status: string;
  evidenceDocumentVersionId: string | null;
  changedBy: string;
  changedAt: string;
}

/** Closed catalogs owned by this feature (not inherited from Legacy — see V22 migration). */
export const FINANCIAL_PROFILE_SOURCES = ['CLIENT', 'BROKER', 'AI'] as const;
export const FINANCIAL_PROFILE_STATUSES = [
  'PENDING',
  'CONFIRMED',
  'ESTIMATED',
  'REJECTED',
  'OUTDATED',
] as const;
