/** Mirrors backend BankRequestResponse. `contactSnapshot` is captured once at creation from the
 * BankContact used, so it never changes even if the contact is later edited
 * (06_BANK_ENGINE_SPECIFICATION.md §7) — schemaless, rendered defensively. status is
 * server-controlled, always 'SENT' this sprint (see BankRequestRepository javadoc). */
export interface BankRequest {
  id: string;
  caseId: string;
  bankId: string;
  bankContactId: string | null;
  status: string;
  submittedAt: string;
  contactSnapshot: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors backend CreateBankRequestApiRequest. bankContactId is optional. */
export interface CreateBankRequestRequest {
  bankId: string;
  bankContactId: string | null;
}

/** Mirrors backend BankResponseResponse. status is server-controlled, always 'RECEIVED'. */
export interface BankResponseRecord {
  id: string;
  bankRequestId: string;
  status: string;
  receivedAt: string;
  summary: string;
  payload: Record<string, unknown>;
  createdAt: string;
}

/** Mirrors backend CreateBankResponseApiRequest. */
export interface CreateBankResponseRequest {
  summary: string;
  payload: Record<string, unknown>;
}

/** Mirrors backend BankOfferResponse. status is server-controlled, always 'RECEIVED'. */
export interface BankOffer {
  id: string;
  bankRequestId: string;
  bankId: string;
  status: string;
  amount: number;
  interestRate: number;
  termMonths: number;
  payment: number;
  conditions: Record<string, unknown>;
  receivedAt: string;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors backend CreateBankOfferApiRequest. */
export interface CreateBankOfferRequest {
  amount: number;
  interestRate: number;
  termMonths: number;
  payment: number;
  conditions: Record<string, unknown>;
}

/** Mirrors backend FinalFinancingResponse. case_id is UNIQUE — at most one per case. status is
 * server-controlled, always 'ACTIVE'. */
export interface FinalFinancing {
  id: string;
  caseId: string;
  bankOfferId: string;
  status: string;
  finalizedAt: string;
  createdAt: string;
  updatedAt: string;
}
