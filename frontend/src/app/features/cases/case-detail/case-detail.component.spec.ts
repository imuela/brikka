import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { SessionStore } from '../../../core/session/session.store';
import { ConfirmDialogComponent } from '../../../shared/dialogs/confirm-dialog.component';
import { CaseDetailComponent } from './case-detail.component';

const theCase = {
  id: 'k1',
  companyId: 'co1',
  reference: 'REF-1',
  status: 'PRESTUDY',
  operationType: 'MORTGAGE',
  createdBy: 'u1',
  createdAt: '2026-08-17T10:00:00Z',
  requestedAmount: null,
    description: null,
    cancelledAt: null,
};

describe('CaseDetailComponent', () => {
  let httpMock: HttpTestingController;
  let sessionStore: SessionStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CaseDetailComponent],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: 'k1' }) } },
        },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    sessionStore = TestBed.inject(SessionStore);
  });

  afterEach(() => httpMock.verify());

  function flushInitialLoad() {
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1`).flush(theCase);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/assignments`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/clients`).flush([]);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/property`)
      .flush({ code: 'PROPERTY_NOT_FOUND', message: 'Property not found.', requestId: 'r1' }, { status: 404, statusText: 'Not Found' });
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/document-types`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/documents`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/document-requests`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/simulations`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/financing-requests`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/matching`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/bank-requests`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/offers`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/tasks`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/conversations`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/financial-analysis`).flush([]);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/fee`)
      .flush({ code: 'CASE_FEE_NOT_FOUND', message: 'x', requestId: 'r1' }, { status: 404, statusText: 'Not Found' });
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/contract`)
      .flush({ documentId: null, versions: [] });
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/dossier`)
      .flush({ documentId: null, versions: [] });
  }

  it('loads and renders the case, its assignments and its clients', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1`).flush(theCase);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/assignments`)
      .flush([{ id: 'a1', caseId: 'k1', userId: 'u1', assignmentType: 'BROKER', active: true }]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`).flush([]);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/clients`)
      .flush([{ clientId: 'c1', firstName: 'Ada', lastName: 'Lovelace', participationType: 'HOLDER', isPrimary: true }]);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/property`)
      .flush({ code: 'PROPERTY_NOT_FOUND', message: 'Property not found.', requestId: 'r1' }, { status: 404, statusText: 'Not Found' });
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/document-types`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/documents`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/document-requests`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/simulations`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/financing-requests`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/matching`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/bank-requests`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/offers`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/tasks`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/conversations`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/financial-analysis`).flush([]);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/fee`)
      .flush({ code: 'CASE_FEE_NOT_FOUND', message: 'x', requestId: 'r1' }, { status: 404, statusText: 'Not Found' });
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/contract`)
      .flush({ documentId: null, versions: [] });
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/dossier`)
      .flush({ documentId: null, versions: [] });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('REF-1');
    expect(fixture.nativeElement.textContent).toContain('BROKER');
    expect(fixture.nativeElement.textContent).toContain('Ada Lovelace');
  });

  it('shows the backend error when loading the case fails', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1`)
      .flush({ code: 'CASE_NOT_FOUND', message: 'Case not found.', requestId: 'r1' }, { status: 404, statusText: 'Not Found' });
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/assignments`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/clients`).flush([]);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/property`)
      .flush({ code: 'PROPERTY_NOT_FOUND', message: 'Property not found.', requestId: 'r1' }, { status: 404, statusText: 'Not Found' });
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/document-types`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/documents`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/document-requests`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/simulations`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/financing-requests`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/matching`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/bank-requests`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/offers`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/tasks`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/conversations`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/financial-analysis`).flush([]);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/fee`)
      .flush({ code: 'CASE_FEE_NOT_FOUND', message: 'x', requestId: 'r1' }, { status: 404, statusText: 'Not Found' });
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/contract`)
      .flush({ documentId: null, versions: [] });
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/dossier`)
      .flush({ documentId: null, versions: [] });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No se ha encontrado la operación solicitada.');
  });

  it('gates the action buttons by their exact backend permission', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const gatedLabels = [
      'Cambiar estado',
      'Cancelar',
      'Reabrir',
      'Asignar',
      'Registrar inmueble',
      'Nuevo documento',
      'Nueva simulación',
      'Nueva solicitud de financiación',
      'Ejecutar matching',
      'Nueva solicitud a banco',
      'Nueva tarea',
      'Nueva conversación',
      'Configurar honorarios',
      'Generar contrato',
      'Generar dossier',
    ];
    for (const label of gatedLabels) {
      expect(fixture.nativeElement.textContent).not.toContain(label);
    }

    sessionStore.setPermissions([
      'CASE_CHANGE_STATUS',
      'CASE_CANCEL',
      'CASE_REOPEN',
      'CASE_ASSIGN',
      'PROPERTY_READ',
      'PROPERTY_UPDATE',
      'DOCUMENT_READ',
      'DOCUMENT_CREATE',
      'DOCUMENT_UPLOAD',
      'CASE_READ',
      'CASE_UPDATE',
      'SIMULATION_READ',
      'SIMULATION_CREATE',
      'FINANCING_REQUEST_READ',
      'FINANCING_REQUEST_CREATE',
      'BANK_MATCHING_READ',
      'BANK_MATCHING_RUN',
      'BANK_REQUEST_READ',
      'BANK_REQUEST_CREATE',
      'TASK_READ',
      'TASK_CREATE',
      'CONVERSATION_READ',
      'CONVERSATION_CREATE',
    ]);
    fixture.detectChanges();

    for (const label of gatedLabels) {
      expect(fixture.nativeElement.textContent).toContain(label);
    }
  });

  it('the Documentos and Solicitudes sections are gated by DOCUMENT_READ / DOCUMENT_REQUEST', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Documentos');
    expect(fixture.nativeElement.textContent).not.toContain('Solicitudes de documentos');

    sessionStore.setPermissions(['DOCUMENT_READ', 'DOCUMENT_REQUEST']);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Documentos');
    expect(fixture.nativeElement.textContent).toContain('Solicitudes de documentos');
  });

  it('the Simulaciones and Financiación sections are gated by SIMULATION_READ / FINANCING_REQUEST_READ', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Simulaciones');
    expect(fixture.nativeElement.textContent).not.toContain('Financiación');

    sessionStore.setPermissions(['SIMULATION_READ', 'FINANCING_REQUEST_READ']);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Simulaciones');
    expect(fixture.nativeElement.textContent).toContain('Financiación');
  });

  it('the Matching, Solicitudes a bancos and Ofertas sections are gated by their READ permission', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Matching bancario');
    expect(fixture.nativeElement.textContent).not.toContain('Solicitudes a bancos');
    expect(fixture.nativeElement.textContent).not.toContain('Ofertas');

    sessionStore.setPermissions(['BANK_MATCHING_READ', 'BANK_REQUEST_READ', 'BANK_OFFER_READ']);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Matching bancario');
    expect(fixture.nativeElement.textContent).toContain('Solicitudes a bancos');
    expect(fixture.nativeElement.textContent).toContain('Ofertas');
  });

  it('the Tareas and Conversaciones sections are gated by TASK_READ / CONVERSATION_READ', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Tareas');
    expect(fixture.nativeElement.textContent).not.toContain('Conversaciones');

    sessionStore.setPermissions(['TASK_READ', 'CONVERSATION_READ']);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Tareas');
    expect(fixture.nativeElement.textContent).toContain('Conversaciones');
  });

  it('bankName() resolves a known bank and falls back to the raw id otherwise', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1`).flush(theCase);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/assignments`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/clients`).flush([]);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/property`)
      .flush({ code: 'PROPERTY_NOT_FOUND', message: 'Property not found.', requestId: 'r1' }, { status: 404, statusText: 'Not Found' });
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/document-types`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/documents`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/document-requests`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/simulations`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/financing-requests`).flush([]);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/banks`)
      .flush([{ id: 'b1', code: 'DEVBANK', name: 'Banco Demo Desarrollo', status: 'ACTIVE', metadata: {} }]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/matching`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/bank-requests`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/offers`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/tasks`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/conversations`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/financial-analysis`).flush([]);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/fee`)
      .flush({ code: 'CASE_FEE_NOT_FOUND', message: 'x', requestId: 'r1' }, { status: 404, statusText: 'Not Found' });
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/contract`)
      .flush({ documentId: null, versions: [] });
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/dossier`)
      .flush({ documentId: null, versions: [] });

    expect(fixture.componentInstance.bankName('b1')).toBe('Banco Demo Desarrollo');
    expect(fixture.componentInstance.bankName('unknown')).toBe('unknown');
  });

  it('openRunMatching opens the dialog and reloads match results after close with a result', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const dialog = TestBed.inject(MatDialog);
    const openSpy = vi.spyOn(dialog, 'open').mockReturnValue({
      afterClosed: () =>
        of({
          id: 'm1',
          caseId: 'k1',
          bankId: 'b1',
          bankCriteriaVersionId: 'c1',
          globalResult: 'PASS',
          effectiveGlobalResult: 'PASS',
          evaluatedAt: '2026-08-18T10:00:00Z',
          inputSnapshot: {},
          ruleResults: [],
        }),
    } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.openRunMatching();

    expect(openSpy).toHaveBeenCalled();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/matching`).flush([]);
  });

  it('openMatchingResultDetail opens the detail dialog with the given result', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const result = {
      id: 'm1',
      caseId: 'k1',
      bankId: 'b1',
      bankCriteriaVersionId: 'c1',
      globalResult: 'PASS',
      effectiveGlobalResult: 'PASS',
      evaluatedAt: '2026-08-18T10:00:00Z',
      inputSnapshot: {},
      ruleResults: [],
    };
    const dialog = TestBed.inject(MatDialog);
    const openSpy = vi
      .spyOn(dialog, 'open')
      .mockReturnValue({ afterClosed: () => of(undefined) } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.openMatchingResultDetail(result);

    expect(openSpy).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ data: { caseId: 'k1', result } }),
    );
  });

  it('openCreateBankRequest opens the dialog and reloads bank requests after close with a result', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const bankRequest = {
      id: 'br1',
      caseId: 'k1',
      bankId: 'b1',
      bankContactId: null,
      status: 'SENT',
      submittedAt: '2026-08-18T10:00:00Z',
      contactSnapshot: {},
      createdAt: '2026-08-18T10:00:00Z',
      updatedAt: '2026-08-18T10:00:00Z',
    };
    const dialog = TestBed.inject(MatDialog);
    const openSpy = vi
      .spyOn(dialog, 'open')
      .mockReturnValue({ afterClosed: () => of(bankRequest) } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.openCreateBankRequest();

    expect(openSpy).toHaveBeenCalled();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/bank-requests`).flush([]);
  });

  it('openCreateBankResponse opens the response dialog for the given bank request', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const dialog = TestBed.inject(MatDialog);
    const openSpy = vi
      .spyOn(dialog, 'open')
      .mockReturnValue({ afterClosed: () => of(undefined) } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.openCreateBankResponse('br1');

    expect(openSpy).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ data: { bankRequestId: 'br1' } }),
    );
  });

  it('openCreateBankOffer opens the dialog and reloads offers after close with a result', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const offer = {
      id: 'off1',
      bankRequestId: 'br1',
      bankId: 'b1',
      status: 'RECEIVED',
      amount: 180000,
      interestRate: 3.2,
      termMonths: 300,
      payment: 870.5,
      conditions: {},
      receivedAt: '2026-08-18T10:00:00Z',
      createdAt: '2026-08-18T10:00:00Z',
      updatedAt: '2026-08-18T10:00:00Z',
    };
    const dialog = TestBed.inject(MatDialog);
    const openSpy = vi
      .spyOn(dialog, 'open')
      .mockReturnValue({ afterClosed: () => of(offer) } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.openCreateBankOffer('br1');

    expect(openSpy).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ data: { bankRequestId: 'br1' } }),
    );
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/offers`).flush([]);
  });

  it('selectOffer opens a confirmation dialog and does not call select when cancelled', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const offer = {
      id: 'off1',
      bankRequestId: 'br1',
      bankId: 'b1',
      status: 'RECEIVED',
      amount: 180000,
      interestRate: 3.2,
      termMonths: 300,
      payment: 870.5,
      conditions: {},
      receivedAt: '2026-08-18T10:00:00Z',
      createdAt: '2026-08-18T10:00:00Z',
      updatedAt: '2026-08-18T10:00:00Z',
    };
    const dialog = TestBed.inject(MatDialog);
    const openSpy = vi
      .spyOn(dialog, 'open')
      .mockReturnValue({ afterClosed: () => of(false) } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.selectOffer(offer);

    expect(openSpy).toHaveBeenCalledWith(
      ConfirmDialogComponent,
      expect.objectContaining({ data: expect.objectContaining({ title: 'Seleccionar oferta final' }) }),
    );
    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/bank-offers/off1/select`);
  });

  it('selectOffer calls POST /select then reloads offers once confirmed', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const offer = {
      id: 'off1',
      bankRequestId: 'br1',
      bankId: 'b1',
      status: 'RECEIVED',
      amount: 180000,
      interestRate: 3.2,
      termMonths: 300,
      payment: 870.5,
      conditions: {},
      receivedAt: '2026-08-18T10:00:00Z',
      createdAt: '2026-08-18T10:00:00Z',
      updatedAt: '2026-08-18T10:00:00Z',
    };
    const dialog = TestBed.inject(MatDialog);
    vi.spyOn(dialog, 'open').mockReturnValue({
      afterClosed: () => of(true),
    } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.selectOffer(offer);

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/bank-offers/off1/select`)
      .flush({
        id: 'ff1',
        caseId: 'k1',
        bankOfferId: 'off1',
        status: 'ACTIVE',
        finalizedAt: '2026-08-18T10:05:00Z',
        createdAt: '2026-08-18T10:05:00Z',
        updatedAt: '2026-08-18T10:05:00Z',
      });

    expect(fixture.componentInstance.finalFinancing()?.bankOfferId).toBe('off1');
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/offers`).flush([]);
  });

  it('openCreateSimulation opens the dialog and reloads simulations after close with a result', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const dialog = TestBed.inject(MatDialog);
    const openSpy = vi.spyOn(dialog, 'open').mockReturnValue({
      afterClosed: () =>
        of({
          id: 's1',
          caseId: 'k1',
          principal: 200000,
          interestRate: 3.5,
          termMonths: 300,
          estimatedPayment: 950.25,
          metadata: {},
          createdBy: 'u1',
          createdAt: '2026-08-17T10:00:00Z',
        }),
    } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.openCreateSimulation();

    expect(openSpy).toHaveBeenCalled();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/simulations`).flush([]);
  });

  it('openCreateFinancingRequest opens the dialog and reloads financing requests after close with a result', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const financingRequest = {
      id: 'fr1',
      caseId: 'k1',
      status: 'PENDING',
      requestedAmount: 180000,
      termMonths: 300,
      createdAt: '2026-08-17T10:00:00Z',
      updatedAt: '2026-08-17T10:00:00Z',
    };
    const dialog = TestBed.inject(MatDialog);
    const openSpy = vi
      .spyOn(dialog, 'open')
      .mockReturnValue({ afterClosed: () => of(financingRequest) } as MatDialogRef<
        unknown,
        unknown
      >);

    fixture.componentInstance.openCreateFinancingRequest();

    expect(openSpy).toHaveBeenCalled();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/financing-requests`).flush([]);
  });

  it('openUpdateFinancingRequest opens the dialog with the given request and reloads on close with a result', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const financingRequest = {
      id: 'fr1',
      caseId: 'k1',
      status: 'PENDING',
      requestedAmount: 180000,
      termMonths: 300,
      createdAt: '2026-08-17T10:00:00Z',
      updatedAt: '2026-08-17T10:00:00Z',
    };
    const updated = { ...financingRequest, status: 'IN_PROGRESS' };
    const dialog = TestBed.inject(MatDialog);
    const openSpy = vi
      .spyOn(dialog, 'open')
      .mockReturnValue({ afterClosed: () => of(updated) } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.openUpdateFinancingRequest(financingRequest);

    expect(openSpy).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ data: { financingRequest } }),
    );
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/financing-requests`).flush([]);
  });

  it('openChangeStatus opens the dialog and applies the returned case on close', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const updatedCase = { ...theCase, status: 'ANALYSIS' };
    const dialog = TestBed.inject(MatDialog);
    const openSpy = vi
      .spyOn(dialog, 'open')
      .mockReturnValue({ afterClosed: () => of(updatedCase) } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.openChangeStatus();

    expect(openSpy).toHaveBeenCalled();
    expect(fixture.componentInstance.theCase()?.status).toBe('ANALYSIS');
  });

  it('openAssign reloads assignments after the dialog closes with a result', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const dialog = TestBed.inject(MatDialog);
    vi.spyOn(dialog, 'open').mockReturnValue({
      afterClosed: () => of({ id: 'a2', caseId: 'k1', userId: 'u2', assignmentType: 'MANAGER', active: true }),
    } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.openAssign();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/assignments`)
      .flush([{ id: 'a2', caseId: 'k1', userId: 'u2', assignmentType: 'MANAGER', active: true }]);
  });

  it('removeClient opens a confirmation dialog before doing anything else', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const dialog = TestBed.inject(MatDialog);
    const openSpy = vi
      .spyOn(dialog, 'open')
      .mockReturnValue({ afterClosed: () => of(undefined) } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.removeClient('c1');

    expect(openSpy).toHaveBeenCalledWith(
      ConfirmDialogComponent,
      expect.objectContaining({ data: expect.objectContaining({ title: 'Quitar cliente' }) }),
    );
    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/cases/k1/clients/c1`);
  });

  it('removeClient does not call DELETE when the confirmation is cancelled', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const dialog = TestBed.inject(MatDialog);
    vi.spyOn(dialog, 'open').mockReturnValue({
      afterClosed: () => of(false),
    } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.removeClient('c1');

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/cases/k1/clients/c1`);
  });

  it('removeClient calls DELETE then reloads the client list once confirmed', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const dialog = TestBed.inject(MatDialog);
    vi.spyOn(dialog, 'open').mockReturnValue({
      afterClosed: () => of(true),
    } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.removeClient('c1');

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/clients/c1`).flush(null);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/clients`).flush([]);
  });

  it('openProperty opens the dialog and applies the returned property on close', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const property = {
      id: 'p1',
      companyId: 'co1',
      caseId: 'k1',
      address: { street: 'Gran Via' },
      propertyType: 'FLAT',
      valuation: 250000,
      purchasePrice: null,
    };
    const dialog = TestBed.inject(MatDialog);
    const openSpy = vi
      .spyOn(dialog, 'open')
      .mockReturnValue({ afterClosed: () => of(property) } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.openProperty();

    expect(openSpy).toHaveBeenCalled();
    expect(fixture.componentInstance.property()).toEqual(property);
  });

  it('publish and unpublish call the correct document endpoints', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    fixture.componentInstance.publish('d1');
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/documents/d1/publish`)
      .flush({ id: 'pub1', documentId: 'd1', documentVersionId: 'v1', publishedToPortal: true, publishedAt: '2026-08-17T10:00:00Z' });

    fixture.componentInstance.unpublish('d1');
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/documents/d1/unpublish`).flush(null);
  });

  it('updateDocumentRequestStatus patches the request then reloads the list', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    fixture.componentInstance.updateDocumentRequestStatus('dr1', 'FULFILLED');

    const patchReq = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/document-requests/dr1`);
    expect(patchReq.request.method).toBe('PATCH');
    expect(patchReq.request.body).toEqual({ status: 'FULFILLED' });
    patchReq.flush({
      id: 'dr1',
      companyId: 'co1',
      caseId: 'k1',
      documentTypeId: 't1',
      requestedFromClientId: null,
      status: 'FULFILLED',
      dueAt: null,
      requestedBy: 'u1',
      requirementId: null,
    });

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/document-requests`).flush([]);
  });

  const task = {
    id: 't1',
    caseId: 'k1',
    assignedTo: null,
    type: 'CALL',
    title: 'Llamar al cliente',
    description: null,
    status: 'TODO',
    dueAt: null,
    createdBy: 'u1',
    completedAt: null,
    createdAt: '2026-08-18T10:00:00Z',
    updatedAt: '2026-08-18T10:00:00Z',
  };

  it('openCreateTask opens the dialog with this case id and reloads tasks on close with a result', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const dialog = TestBed.inject(MatDialog);
    const openSpy = vi
      .spyOn(dialog, 'open')
      .mockReturnValue({ afterClosed: () => of(task) } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.openCreateTask();

    expect(openSpy).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ data: { caseId: 'k1' } }),
    );
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/tasks`).flush([task]);
  });

  it('openEditTask opens the dialog with the task and reloads on close with a result', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const dialog = TestBed.inject(MatDialog);
    const openSpy = vi
      .spyOn(dialog, 'open')
      .mockReturnValue({ afterClosed: () => of(task) } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.openEditTask(task);

    expect(openSpy).toHaveBeenCalledWith(expect.anything(), expect.objectContaining({ data: { task } }));
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/tasks`).flush([task]);
  });

  it('completeTask posts to the complete endpoint and reloads tasks', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    fixture.componentInstance.completeTask(task);

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/tasks/t1/complete`).flush(task);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/tasks`).flush([task]);
  });

  it('deleteTask asks for confirmation before deleting', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const dialog = TestBed.inject(MatDialog);
    vi.spyOn(dialog, 'open').mockReturnValue({
      afterClosed: () => of(true),
    } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.deleteTask(task);

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/tasks/t1`).flush(null);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/tasks`).flush([]);
  });

  it('deleteTask does not delete when the confirmation is declined', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const dialog = TestBed.inject(MatDialog);
    vi.spyOn(dialog, 'open').mockReturnValue({
      afterClosed: () => of(false),
    } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.deleteTask(task);

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/tasks/t1`);
  });

  const conversation = {
    id: 'conv1',
    caseId: 'k1',
    type: 'INTERNAL',
    status: 'ACTIVE',
    createdAt: '2026-08-18T10:00:00Z',
    updatedAt: '2026-08-18T10:00:00Z',
  };

  it('openCreateConversation opens the dialog with this case id and its clients and reloads on a result', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const dialog = TestBed.inject(MatDialog);
    const openSpy = vi
      .spyOn(dialog, 'open')
      .mockReturnValue({ afterClosed: () => of(conversation) } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.openCreateConversation();

    expect(openSpy).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({ data: { caseId: 'k1', clients: [] } }),
    );
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/conversations`).flush([conversation]);
  });

  it('openConversationDetail opens the dialog with the conversation, clients and assignable users', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const dialog = TestBed.inject(MatDialog);
    const openSpy = vi.spyOn(dialog, 'open').mockReturnValue({
      afterClosed: () => of(undefined),
    } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.openConversationDetail(conversation);

    expect(openSpy).toHaveBeenCalledWith(
      expect.anything(),
      expect.objectContaining({
        data: { conversation, clients: [], assignableUsers: [] },
      }),
    );
  });

  it('the Análisis financiero section is gated by FINANCIAL_ANALYSIS_READ', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Análisis financiero');

    sessionStore.setPermissions(['FINANCIAL_ANALYSIS_READ']);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Análisis financiero');
    expect(fixture.nativeElement.textContent).toContain('Sin análisis financiero todavía');
  });

  it('shows the "Ejecutar análisis" button only with FINANCIAL_ANALYSIS_RUN', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    sessionStore.setPermissions(['FINANCIAL_ANALYSIS_READ']);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Ejecutar análisis');

    sessionStore.setPermissions(['FINANCIAL_ANALYSIS_READ', 'FINANCIAL_ANALYSIS_RUN']);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Ejecutar análisis');
  });

  it('running the analysis renders the DTI, payment, viability and disclaimer for each client', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    sessionStore.setPermissions(['FINANCIAL_ANALYSIS_READ', 'FINANCIAL_ANALYSIS_RUN']);
    fixture.detectChanges();

    fixture.componentInstance.runFinancialAnalysis();
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/financial-analysis`);
    expect(req.request.method).toBe('POST');
    req.flush([
      {
        id: 'far1',
        caseId: 'k1',
        clientId: 'c1',
        principal: 200000,
        interestRate: 3.5,
        termMonths: 360,
        monthlyPayment: 898.09,
        monthlyIncome: 3000,
        existingMonthlyDebts: 300,
        dtiPercent: 39.94,
        viabilityCategory: 'REVISAR',
        quotaSource: 'SIMULATION',
        quotaSourceId: 's1',
        rulesVersion: 'brikka-dti-v1',
        explanation: {
          disclaimer:
            'Regla orientativa interna de Brikka V1. No representa un criterio oficial ni garantiza la aprobación por una entidad financiera.',
        },
        calculatedBy: 'u1',
        calculatedAt: '2026-08-21T10:00:00Z',
      },
    ]);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('A revisar');
    expect(text).toContain('39.94');
    expect(text).toContain('orientativa interna');
  });

  it('shows the structured backend error when the analysis cannot be run', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    sessionStore.setPermissions(['FINANCIAL_ANALYSIS_READ', 'FINANCIAL_ANALYSIS_RUN']);
    fixture.detectChanges();

    fixture.componentInstance.runFinancialAnalysis();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/financial-analysis`)
      .flush(
        { code: 'FINANCING_DATA_REQUIRED', message: 'no financing', requestId: 'r1' },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();

    expect(fixture.componentInstance.financialAnalysisError()).toContain(
      'oferta bancaria seleccionada o una simulación',
    );
  });

  it('the Honorarios section is gated by CASE_READ and shows the empty state', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Honorarios');

    sessionStore.setPermissions(['CASE_READ']);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Honorarios');
    expect(fixture.nativeElement.textContent).toContain('Sin honorarios configurados todavía');
    expect(fixture.nativeElement.textContent).not.toContain('Configurar honorarios');
  });

  it('configuring a percentage fee renders the computed amount', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    sessionStore.setPermissions(['CASE_READ', 'CASE_UPDATE']);
    fixture.detectChanges();

    const dialog = TestBed.inject(MatDialog);
    vi.spyOn(dialog, 'open').mockReturnValue({
      afterClosed: () =>
        of({
          id: 'f1',
          caseId: 'k1',
          feeType: 'PERCENTAGE',
          fixedAmount: null,
          percentage: 2.5,
          calculationBase: 200000,
          calculatedAmount: 5000,
          status: 'PROPOSED',
          agreedAt: null,
          updatedBy: 'u1',
          createdAt: '2026-08-22T10:00:00Z',
          updatedAt: '2026-08-22T10:00:00Z',
        }),
    } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.openEditCaseFee();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Porcentaje');
    expect(text).toContain('Propuesto');
  });

  it('the Contrato de encargo and Dossier de viabilidad sections are gated by DOCUMENT_READ', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Contrato de encargo');
    expect(fixture.nativeElement.textContent).not.toContain('Dossier de viabilidad');

    sessionStore.setPermissions(['DOCUMENT_READ']);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Contrato de encargo');
    expect(fixture.nativeElement.textContent).toContain('Sin contrato generado todavía');
    expect(fixture.nativeElement.textContent).toContain('Dossier de viabilidad');
    expect(fixture.nativeElement.textContent).toContain('Sin dossier generado todavía');
    expect(fixture.nativeElement.textContent).not.toContain('Generar contrato');
    expect(fixture.nativeElement.textContent).not.toContain('Generar dossier');
  });

  it('generating the dossier lists the new version and allows regenerating', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    sessionStore.setPermissions(['DOCUMENT_READ', 'DOCUMENT_UPLOAD']);
    fixture.detectChanges();

    fixture.componentInstance.generateDossier();
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/dossier`);
    expect(req.request.method).toBe('POST');
    req.flush({
      id: 'v1',
      documentId: 'd1',
      versionNumber: 1,
      originalFilename: 'dossier-viabilidad.html',
      mimeType: 'text/html',
      sizeBytes: 100,
      checksum: 'abc',
      uploadedBy: 'u1',
      uploadedAt: '2026-08-22T10:00:00Z',
      reviewStatus: 'PENDING',
      reviewedBy: null,
      reviewedAt: null,
      reviewComment: null,
    });
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/dossier`)
      .flush({
        documentId: 'd1',
        versions: [
          {
            id: 'v1',
            documentId: 'd1',
            versionNumber: 1,
            originalFilename: 'dossier-viabilidad.html',
            mimeType: 'text/html',
            sizeBytes: 100,
            checksum: 'abc',
            uploadedBy: 'u1',
            uploadedAt: '2026-08-22T10:00:00Z',
            reviewStatus: 'PENDING',
            reviewedBy: null,
            reviewedAt: null,
            reviewComment: null,
          },
        ],
      });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Versión 1');
    expect(fixture.nativeElement.textContent).toContain('Regenerar dossier');
  });

  it('shows the structured backend error when the contract cannot be generated', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    sessionStore.setPermissions(['DOCUMENT_READ', 'DOCUMENT_UPLOAD']);
    fixture.detectChanges();

    fixture.componentInstance.generateContract();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/contract`)
      .flush(
        { code: 'NO_CLIENTS_ON_CASE', message: 'no clients', requestId: 'r1' },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();

    expect(fixture.componentInstance.contractError()).toContain(
      'no tiene ningún cliente asociado',
    );
  });
});
