import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { AddClientDialogComponent } from './add-client-dialog.component';

describe('AddClientDialogComponent', () => {
  let httpMock: HttpTestingController;
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    dialogRef = { close: vi.fn() };
    TestBed.configureTestingModule({
      imports: [AddClientDialogComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { caseId: 'k1' } },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function flushClients() {
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/clients`)
      .flush([{ id: 'c1', companyId: 'co1', firstName: 'Ada', lastName: 'Lovelace', email: 'a@b.test', phone: '1', status: 'ACTIVE' }]);
  }

  it('loads real tenant clients from ClientsService (no duplicate HTTP call)', () => {
    const fixture = TestBed.createComponent(AddClientDialogComponent);
    fixture.detectChanges();
    flushClients();

    expect(fixture.componentInstance.clients()?.length).toBe(1);
  });

  it('submits clientId/participationType/isPrimary and closes the dialog with true', () => {
    const fixture = TestBed.createComponent(AddClientDialogComponent);
    fixture.detectChanges();
    flushClients();

    fixture.componentInstance.form.setValue({ clientId: 'c1', participationType: 'HOLDER', isPrimary: true });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/clients`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ clientId: 'c1', participationType: 'HOLDER', isPrimary: true });
    req.flush(null);

    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });

  it('does not submit without a selected client and participation type', () => {
    const fixture = TestBed.createComponent(AddClientDialogComponent);
    fixture.detectChanges();
    flushClients();

    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/cases/k1/clients`);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('shows the backend error on failure', () => {
    const fixture = TestBed.createComponent(AddClientDialogComponent);
    fixture.detectChanges();
    flushClients();

    fixture.componentInstance.form.setValue({ clientId: 'c1', participationType: 'HOLDER', isPrimary: false });
    fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/clients`)
      .flush({ code: 'DUPLICATE', message: 'Client already linked to this case.', requestId: 'r1' }, { status: 409, statusText: 'Conflict' });

    expect(fixture.componentInstance.error()).toBe('Client already linked to this case.');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
