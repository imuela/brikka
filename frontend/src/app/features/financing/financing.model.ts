/** Mirrors backend FinancingRequestStatus (no formal business catalog — disclosed as technical
 * debt in the backend enum itself; a server-controlled lifecycle marker, not a workflow). */
export const FINANCING_REQUEST_STATUSES = ['PENDING', 'IN_PROGRESS', 'CLOSED'] as const;
export type FinancingRequestStatus = (typeof FINANCING_REQUEST_STATUSES)[number];

/** Mirrors backend SimulationResponse. Sprint 16.1: list + create only — no single-get, update or
 * delete endpoint exists (17_API_SPECIFICATION_DETAILED.md §11). */
export interface Simulation {
  id: string;
  caseId: string;
  principal: number;
  interestRate: number;
  termMonths: number;
  estimatedPayment: number;
  metadata: Record<string, unknown>;
  createdBy: string;
  createdAt: string;
}

/** Mirrors backend CreateSimulationApiRequest. */
export interface CreateSimulationRequest {
  principal: number;
  interestRate: number;
  termMonths: number;
  estimatedPayment: number;
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
