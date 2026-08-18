import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { FinancingRequest } from '../financing.model';
import { CreateFinancingRequestDialogComponent } from './create-financing-request-dialog.component';

const createdRequest: FinancingRequest = {
  id: 'fr1',
  caseId: 'k1',
  status: 'PENDING',
  requestedAmount: 180000,
  termMonths: 300,
  createdAt: '2026-08-17T10:00:00Z',
  updatedAt: '2026-08-17T10:00:00Z',
};

function configure() {
  const dialogRef = { close: vi.fn() };
  TestBed.configureTestingModule({
    imports: [CreateFinancingRequestDialogComponent, NoopAnimationsModule],
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      { provide: MatDialogRef, useValue: dialogRef },
      { provide: MAT_DIALOG_DATA, useValue: { caseId: 'k1' } },
    ],
  });
  return dialogRef;
}

describe('CreateFinancingRequestDialogComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => httpMock.verify());

  it('submits and closes with the returned financing request', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateFinancingRequestDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ requestedAmount: '180000', termMonths: '300' });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/financing-requests`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ requestedAmount: 180000, termMonths: 300 });
    req.flush(createdRequest);

    expect(dialogRef.close).toHaveBeenCalledWith(createdRequest);
  });

  it('does not submit with missing required fields', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateFinancingRequestDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/cases/k1/financing-requests`);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('shows the backend error when the request fails', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateFinancingRequestDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ requestedAmount: '180000', termMonths: '300' });
    fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/financing-requests`)
      .flush(
        { code: 'VALIDATION_ERROR', message: 'Invalid amount.', requestId: 'r1' },
        { status: 400, statusText: 'Bad Request' },
      );

    expect(fixture.componentInstance.error()).toBe(
      'No se han podido guardar los cambios. Revisa los datos introducidos.',
    );
  });

  it('cancel() closes the dialog without a result', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateFinancingRequestDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();

    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
