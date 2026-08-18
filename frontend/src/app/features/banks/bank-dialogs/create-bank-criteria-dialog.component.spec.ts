import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { BankCriteriaVersion } from '../bank.model';
import { CreateBankCriteriaDialogComponent } from './create-bank-criteria-dialog.component';

const createdCriteria: BankCriteriaVersion = {
  id: 'c1',
  bankId: 'b1',
  version: 'v1',
  status: 'ACTIVE',
  effectiveFrom: '2026-08-18T00:00:00Z',
  effectiveTo: null,
  rules: { rules: [] },
};

function configure() {
  const dialogRef = { close: vi.fn() };
  TestBed.configureTestingModule({
    imports: [CreateBankCriteriaDialogComponent, NoopAnimationsModule],
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      { provide: MatDialogRef, useValue: dialogRef },
      { provide: MAT_DIALOG_DATA, useValue: { bankId: 'b1' } },
    ],
  });
  return dialogRef;
}

describe('CreateBankCriteriaDialogComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => httpMock.verify());

  it('pre-fills a valid default rules JSON and today as effectiveFrom', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateBankCriteriaDialogComponent);
    fixture.detectChanges();

    expect(() => JSON.parse(fixture.componentInstance.form.value.rules!)).not.toThrow();
    expect(fixture.componentInstance.form.value.effectiveFrom).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it('submits and closes with the returned criteria version', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateBankCriteriaDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.patchValue({
      version: 'v1',
      effectiveFrom: '2026-08-18',
      rules: '{"rules":[]}',
    });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks/b1/criteria`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      version: 'v1',
      effectiveFrom: '2026-08-18T00:00:00Z',
      effectiveTo: null,
      rules: { rules: [] },
    });
    req.flush(createdCriteria);

    expect(dialogRef.close).toHaveBeenCalledWith(createdCriteria);
  });

  it('shows a local error and does not submit when the rules JSON is invalid', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateBankCriteriaDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.patchValue({ rules: 'not json' });
    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/banks/b1/criteria`);
    expect(fixture.componentInstance.error()).toBe('El JSON de reglas no es válido.');
  });

  it('cancel() closes the dialog without a result', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateBankCriteriaDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();

    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
