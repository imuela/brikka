import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { FinancingRequest } from '../financing.model';
import { UpdateFinancingRequestDialogComponent } from './update-financing-request-dialog.component';

const existingRequest: FinancingRequest = {
  id: 'fr1',
  caseId: 'k1',
  status: 'PENDING',
  requestedAmount: 180000,
  termMonths: 300,
  createdAt: '2026-08-17T10:00:00Z',
  updatedAt: '2026-08-17T10:00:00Z',
};

const updatedRequest: FinancingRequest = { ...existingRequest, status: 'IN_PROGRESS' };

function configure() {
  const dialogRef = { close: vi.fn() };
  TestBed.configureTestingModule({
    imports: [UpdateFinancingRequestDialogComponent, NoopAnimationsModule],
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      { provide: MatDialogRef, useValue: dialogRef },
      { provide: MAT_DIALOG_DATA, useValue: { financingRequest: existingRequest } },
    ],
  });
  return dialogRef;
}

describe('UpdateFinancingRequestDialogComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => httpMock.verify());

  it('pre-fills the form from the existing financing request', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(UpdateFinancingRequestDialogComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.form.value.status).toBe('PENDING');
    expect(fixture.componentInstance.form.value.requestedAmount).toBe('180000');
    expect(fixture.componentInstance.form.value.termMonths).toBe('300');
  });

  it('submits PATCH with the full replacement payload and closes with the result', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(UpdateFinancingRequestDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.patchValue({ status: 'IN_PROGRESS' });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/financing-requests/fr1`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({
      status: 'IN_PROGRESS',
      requestedAmount: 180000,
      termMonths: 300,
    });
    req.flush(updatedRequest);

    expect(dialogRef.close).toHaveBeenCalledWith(updatedRequest);
  });

  it('shows the backend error when the request fails', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(UpdateFinancingRequestDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/financing-requests/fr1`)
      .flush(
        { code: 'FINANCING_REQUEST_NOT_FOUND', message: 'Not found.', requestId: 'r1' },
        { status: 404, statusText: 'Not Found' },
      );

    expect(fixture.componentInstance.error()).toBe('No se ha encontrado el recurso solicitado.');
  });

  it('cancel() closes the dialog without a result', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(UpdateFinancingRequestDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();

    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
