import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { BankOffer } from '../bank-request.model';
import { CreateBankOfferDialogComponent } from './create-bank-offer-dialog.component';

const createdOffer: BankOffer = {
  id: 'off1',
  bankRequestId: 'br1',
  bankId: 'b1',
  status: 'RECEIVED',
  amount: 180000,
  interestRate: 3.2,
  termMonths: 300,
  payment: 870.5,
  conditions: {},
  receivedAt: '2026-08-18T10:02:00Z',
  createdAt: '2026-08-18T10:02:00Z',
  updatedAt: '2026-08-18T10:02:00Z',
};

function configure() {
  const dialogRef = { close: vi.fn() };
  TestBed.configureTestingModule({
    imports: [CreateBankOfferDialogComponent, NoopAnimationsModule],
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      { provide: MatDialogRef, useValue: dialogRef },
      { provide: MAT_DIALOG_DATA, useValue: { bankRequestId: 'br1' } },
    ],
  });
  return dialogRef;
}

describe('CreateBankOfferDialogComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => httpMock.verify());

  it('submits and closes with the returned offer', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateBankOfferDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({
      amount: '180000',
      interestRate: '3.2',
      termMonths: '300',
      payment: '870.5',
    });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/bank-requests/br1/offers`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      amount: 180000,
      interestRate: 3.2,
      termMonths: 300,
      payment: 870.5,
      conditions: {},
    });
    req.flush(createdOffer);

    expect(dialogRef.close).toHaveBeenCalledWith(createdOffer);
  });

  it('does not submit with missing required fields', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateBankOfferDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/bank-requests/br1/offers`);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('cancel() closes the dialog without a result', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateBankOfferDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();

    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
