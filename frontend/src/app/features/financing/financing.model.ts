/** Mirrors backend FinancingRequestStatus (no formal business catalog — disclosed as technical
 * debt in the backend enum itself; a server-controlled lifecycle marker, not a workflow). */
export const FINANCING_REQUEST_STATUSES = ['PENDING', 'IN_PROGRESS', 'CLOSED'] as const;
export type FinancingRequestStatus = (typeof FINANCING_REQUEST_STATUSES)[number];

/** BRIKKA V2 I4. Mirrors backend SimulationInterestType (R18). */
export const INTEREST_TYPES = ['FIXED', 'VARIABLE', 'MIXED'] as const;
export type InterestType = (typeof INTEREST_TYPES)[number];

/** BRIKKA V2 I4. Mirrors backend SimulationBonification / SimulationResponse.Bonification (R19).
 * `rate` is a percentage-point reduction applied to the base rate when `active`. */
export interface SimulationBonification {
  code: string;
  label: string;
  rate: number;
  active: boolean;
}

/** BRIKKA V2 I4. Mirrors backend SimulationResponse.VariablePhase — only present for MIXED. */
export interface SimulationVariablePhase {
  baseInterestRate: number;
  finalInterestRate: number;
  outstandingBalanceAtSwitch: number;
  monthlyPayment: number;
}

/** Mirrors backend SimulationResponse. Sprint 16.1: list + create only — no single-get, update or
 * delete endpoint exists (17_API_SPECIFICATION_DETAILED.md §11). BRIKKA V2 I4 added the interest
 * breakdown: `interestRate` is the effective (final) annual rate, `estimatedPayment` is
 * server-computed (French amortization; for MIXED, the fixed tranche). */
export interface Simulation {
  id: string;
  caseId: string;
  principal: number;
  interestRate: number;
  termMonths: number;
  estimatedPayment: number;
  interestType: InterestType;
  baseInterestRate: number;
  finalInterestRate: number;
  euriborRate: number | null;
  spreadRate: number | null;
  fixedPeriodMonths: number | null;
  fixedPeriodRate: number | null;
  icoGuarantee: boolean;
  bonifications: SimulationBonification[];
  variablePhase: SimulationVariablePhase | null;
  metadata: Record<string, unknown>;
  createdBy: string;
  createdAt: string;
}

/** Mirrors backend CreateSimulationApiRequest. Only the rate fields relevant to `interestType`
 * are sent; the effective rate and payment are computed by the backend. */
export interface CreateSimulationRequest {
  interestType: InterestType;
  principal: number;
  termMonths: number;
  fixedRate?: number | null;
  euriborRate?: number | null;
  spreadRate?: number | null;
  fixedPeriodMonths?: number | null;
  fixedPeriodRate?: number | null;
  bonifications: SimulationBonification[];
  icoGuarantee: boolean;
  metadata: Record<string, unknown>;
}

/** Mirrors backend FinancingRequestResponse. */
export interface FinancingRequest {
  id: string;
  caseId: string;
  status: string;
  requestedAmount: number;
  termMonths: number;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors backend CreateFinancingRequestApiRequest. */
export interface CreateFinancingRequestRequest {
  requestedAmount: number;
  termMonths: number;
}

/** Mirrors backend UpdateFinancingRequestApiRequest — PATCH replaces all three fields at once. */
export interface UpdateFinancingRequestRequest {
  status: string;
  requestedAmount: number;
  termMonths: number;
}
