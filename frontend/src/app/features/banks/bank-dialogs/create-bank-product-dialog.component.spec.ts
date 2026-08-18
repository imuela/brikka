import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { BankProduct } from '../bank.model';
import { CreateBankProductDialogComponent } from './create-bank-product-dialog.component';

const createdProduct: BankProduct = {
  id: 'p1',
  bankId: 'b1',
  code: 'HIP-30',
  name: 'Hipoteca 30 años',
  status: 'ACTIVE',
  metadata: {},
};

function configure() {
  const dialogRef = { close: vi.fn() };
  TestBed.configureTestingModule({
    imports: [CreateBankProductDialogComponent, NoopAnimationsModule],
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      { provide: MatDialogRef, useValue: dialogRef },
      { provide: MAT_DIALOG_DATA, useValue: { bankId: 'b1' } },
    ],
  });
  return dialogRef;
}

describe('CreateBankProductDialogComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => httpMock.verify());

  it('submits and closes with the returned product', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateBankProductDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ code: 'HIP-30', name: 'Hipoteca 30 años' });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks/b1/products`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ code: 'HIP-30', name: 'Hipoteca 30 años', metadata: {} });
    req.flush(createdProduct);

    expect(dialogRef.close).toHaveBeenCalledWith(createdProduct);
  });

  it('does not submit with missing required fields', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateBankProductDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/banks/b1/products`);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('cancel() closes the dialog without a result', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateBankProductDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();

    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
