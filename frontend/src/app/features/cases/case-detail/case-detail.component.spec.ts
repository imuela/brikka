import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { SessionStore } from '../../../core/session/session.store';
import { CaseDetailComponent } from './case-detail.component';

const theCase = {
  id: 'k1',
  companyId: 'co1',
  reference: 'REF-1',
  status: 'PRESTUDY',
  operationType: 'MORTGAGE',
  createdBy: 'u1',
  createdAt: '2026-08-17T10:00:00Z',
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
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/clients`).flush([]);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/property`)
      .flush({ code: 'PROPERTY_NOT_FOUND', message: 'Property not found.', requestId: 'r1' }, { status: 404, statusText: 'Not Found' });
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/document-types`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/documents`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/document-requests`).flush([]);
  }

  it('loads and renders the case, its assignments and its clients', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1`).flush(theCase);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/assignments`)
      .flush([{ id: 'a1', caseId: 'k1', userId: 'u1', assignmentType: 'BROKER', active: true }]);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/clients`)
      .flush([{ clientId: 'c1', firstName: 'Ada', lastName: 'Lovelace', participationType: 'HOLDER', isPrimary: true }]);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/property`)
      .flush({ code: 'PROPERTY_NOT_FOUND', message: 'Property not found.', requestId: 'r1' }, { status: 404, statusText: 'Not Found' });
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/document-types`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/documents`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/document-requests`).flush([]);
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
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/clients`).flush([]);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/property`)
      .flush({ code: 'PROPERTY_NOT_FOUND', message: 'Property not found.', requestId: 'r1' }, { status: 404, statusText: 'Not Found' });
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/document-types`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/documents`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/document-requests`).flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Case not found.');
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

  it('removeClient calls DELETE then reloads the client list', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

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
});
