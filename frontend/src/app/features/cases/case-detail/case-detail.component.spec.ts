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
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Case not found.');
  });

  it('gates the action buttons by their exact backend permission', () => {
    const fixture = TestBed.createComponent(CaseDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    for (const label of ['Cambiar estado', 'Cancelar', 'Reabrir', 'Asignar']) {
      expect(fixture.nativeElement.textContent).not.toContain(label);
    }

    sessionStore.setPermissions(['CASE_CHANGE_STATUS', 'CASE_CANCEL', 'CASE_REOPEN', 'CASE_ASSIGN']);
    fixture.detectChanges();

    for (const label of ['Cambiar estado', 'Cancelar', 'Reabrir', 'Asignar']) {
      expect(fixture.nativeElement.textContent).toContain(label);
    }
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
});
