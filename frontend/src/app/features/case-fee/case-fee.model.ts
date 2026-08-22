/** Sprint 32. Mirrors backend CaseFeeResponse/CaseFeeHistoryResponse exactly
 * (com.brika.platform.casefee.web). */
export interface CaseFee {
  id: string;
  caseId: string;
  feeType: 'FIXED' | 'PERCENTAGE';
  fixedAmount: number | null;
  percentage: number | null;
  calculationBase: number | null;
  calculatedAmount: number;
  status: 'PROPOSED' | 'AGREED' | 'CANCELLED';
  agreedAt: string | null;
  updatedBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface CaseFeeHistoryEntry {
  id: string;
  caseId: string;
  feeId: string;
  feeType: 'FIXED' | 'PERCENTAGE';
  fixedAmount: number | null;
  percentage: number | null;
  calculationBase: number | null;
  calculatedAmount: number;
  status: 'PROPOSED' | 'AGREED' | 'CANCELLED';
  agreedAt: string | null;
  changedBy: string;
  changedAt: string;
}

export interface UpsertCaseFeeRequest {
  feeType: 'FIXED' | 'PERCENTAGE';
  fixedAmount: number | null;
  percentage: number | null;
  calculationBase: number | null;
  status: 'PROPOSED' | 'AGREED' | 'CANCELLED';
  agreedAt: string | null;
}
