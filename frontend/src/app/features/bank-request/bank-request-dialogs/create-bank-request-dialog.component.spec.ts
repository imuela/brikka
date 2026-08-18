import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { BankRequest } from '../bank-request.model';
import { CreateBankRequestDialogComponent } from './create-bank-request-dialog.component';

const bank = { id: 'b1', code: 'DEVBANK', name: 'Banco Demo Desarrollo', status: 'ACTIVE', metadata: {} };
const createdRequest: BankRequest = {
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

function configure() {
  const dialogRef = { close: vi.fn() };
  TestBed.configureTestingModule({
    imports: [CreateBankRequestDialogComponent, NoopAnimationsModule],
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      { provide: MatDialogRef, useValue: dialogRef },
      { provide: MAT_DIALOG_DATA, useValue: { caseId: 'k1' } },
    ],
  });
  return dialogRef;
}

describe('CreateBankRequestDialogComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => httpMock.verify());

  it('submits with bankContactId null and closes with the returned request', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateBankRequestDialogComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks`).flush([bank]);
    fixture.componentInstance.form.setValue({ bankId: 'b1' });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/bank-requests`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ bankId: 'b1', bankContactId: null });
    req.flush(createdRequest);

    expect(dialogRef.close).toHaveBeenCalledWith(createdRequest);
  });

  it('does not submit without selecting a bank', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateBankRequestDialogComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks`).flush([bank]);
    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/cases/k1/bank-requests`);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('cancel() closes the dialog without a result', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateBankRequestDialogComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks`).flush([bank]);
    fixture.componentInstance.cancel();

    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
