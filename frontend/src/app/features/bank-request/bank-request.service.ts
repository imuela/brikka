import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/http/api-client';
import {
  BankOffer,
  BankRequest,
  BankResponseRecord,
  CreateBankOfferRequest,
  CreateBankRequestRequest,
  CreateBankResponseRequest,
  FinalFinancing,
} from './bank-request.model';

/** Sprint 16: thin wrapper over the real /api/v1/cases/{caseId}/bank-requests,
 * /api/v1/bank-requests/{id}, /api/v1/cases/{caseId}/offers, /api/v1/bank-offers/{id} contracts
 * (17_API_SPECIFICATION_DETAILED.md §14/§15) — no fields, endpoints or business rules beyond what
 * the backend exposes. No dedicated GET for final_financing: selecting an offer is the only way
 * to read it (Decision D4, Sprint 6A). */
@Injectable({ providedIn: 'root' })
export class BankRequestService {
  private readonly apiClient = inject(ApiClient);

  list(caseId: string): Observable<BankRequest[]> {
    return this.apiClient.get<BankRequest[]>(`/api/v1/cases/${caseId}/bank-requests`);
  }

  create(caseId: string, request: CreateBankRequestRequest): Observable<BankRequest> {
    return this.apiClient.post<BankRequest>(`/api/v1/cases/${caseId}/bank-requests`, request);
  }

  get(id: string): Observable<BankRequest> {
    return this.apiClient.get<BankRequest>(`/api/v1/bank-requests/${id}`);
  }

  createResponse(
    bankRequestId: string,
    request: CreateBankResponseRequest,
  ): Observable<BankResponseRecord> {
    return this.apiClient.post<BankResponseRecord>(
      `/api/v1/bank-requests/${bankRequestId}/responses`,
      request,
    );
  }

  createOffer(bankRequestId: string, request: CreateBankOfferRequest): Observable<BankOffer> {
    return this.apiClient.post<BankOffer>(
      `/api/v1/bank-requests/${bankRequestId}/offers`,
      request,
    );
  }

  listOffers(caseId: string): Observable<BankOffer[]> {
    return this.apiClient.get<BankOffer[]>(`/api/v1/cases/${caseId}/offers`);
  }

  getOffer(id: string): Observable<BankOffer> {
    return this.apiClient.get<BankOffer>(`/api/v1/bank-offers/${id}`);
  }

  selectOffer(id: string): Observable<FinalFinancing> {
    return this.apiClient.post<FinalFinancing>(`/api/v1/bank-offers/${id}/select`);
  }
}
