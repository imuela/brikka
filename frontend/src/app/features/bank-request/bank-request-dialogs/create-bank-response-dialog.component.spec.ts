import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { BankResponseRecord } from '../bank-request.model';
import { CreateBankResponseDialogComponent } from './create-bank-response-dialog.component';

const createdResponse: BankResponseRecord = {
  id: 'bres1',
  bankRequestId: 'br1',
  status: 'RECEIVED',
  receivedAt: '2026-08-18T10:01:00Z',
  summary: 'Aprobado.',
  payload: {},
  createdAt: '2026-08-18T10:01:00Z',
};

function configure() {
  const dialogRef = { close: vi.fn() };
  TestBed.configureTestingModule({
    imports: [CreateBankResponseDialogComponent, NoopAnimationsModule],
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      { provide: MatDialogRef, useValue: dialogRef },
      { provide: MAT_DIALOG_DATA, useValue: { bankRequestId: 'br1' } },
    ],
  });
  return dialogRef;
}

describe('CreateBankResponseDialogComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => httpMock.verify());

  it('submits and closes with the returned response', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateBankResponseDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ summary: 'Aprobado.' });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/bank-requests/br1/responses`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ summary: 'Aprobado.', payload: {} });
    req.flush(createdResponse);

    expect(dialogRef.close).toHaveBeenCalledWith(createdResponse);
  });

  it('does not submit with a missing summary', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateBankResponseDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/bank-requests/br1/responses`);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('cancel() closes the dialog without a result', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateBankResponseDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();

    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
