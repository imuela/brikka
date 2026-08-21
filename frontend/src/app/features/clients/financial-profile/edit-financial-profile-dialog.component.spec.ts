import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { EditFinancialProfileDialogComponent } from './edit-financial-profile-dialog.component';

describe('EditFinancialProfileDialogComponent', () => {
  let httpMock: HttpTestingController;
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  function configure(profile: unknown = null) {
    dialogRef = { close: vi.fn() };
    TestBed.configureTestingModule({
      imports: [EditFinancialProfileDialogComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { clientId: 'c1', profile } },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  }

  afterEach(() => httpMock.verify());

  it('creates a profile with defaults (BROKER/PENDING) when none existed before', () => {
    configure(null);
    const fixture = TestBed.createComponent(EditFinancialProfileDialogComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.form.controls.source.value).toBe('BROKER');
    expect(fixture.componentInstance.form.controls.status.value).toBe('PENDING');

    fixture.componentInstance.form.patchValue({ monthlyIncome: 2000 });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/clients/c1/financial-profile`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body.monthlyIncome).toBe(2000);
    expect(req.request.body.source).toBe('BROKER');
    req.flush({ id: 'fp1', monthlyIncome: 2000 });

    expect(dialogRef.close).toHaveBeenCalled();
  });

  it('pre-fills the form from an existing profile', () => {
    configure({
      maritalStatus: 'MARRIED',
      dependents: 1,
      employmentType: null,
      contractType: null,
      employerName: 'Acme S.L.',
      yearsEmployed: 3,
      monthlyIncome: 3000,
      savings: null,
      otherDebtsMonthlyPayment: null,
      creditCardDebt: null,
      source: 'CLIENT',
      status: 'CONFIRMED',
      evidenceDocumentVersionId: null,
    });
    const fixture = TestBed.createComponent(EditFinancialProfileDialogComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.form.controls.employerName.value).toBe('Acme S.L.');
    expect(fixture.componentInstance.form.controls.source.value).toBe('CLIENT');
    expect(fixture.componentInstance.form.controls.status.value).toBe('CONFIRMED');
  });

  it('rejects a negative monthly income before submitting', () => {
    configure(null);
    const fixture = TestBed.createComponent(EditFinancialProfileDialogComponent);
    fixture.detectChanges();

    fixture.componentInstance.form.patchValue({ monthlyIncome: -100 });
    expect(fixture.componentInstance.form.controls.monthlyIncome.hasError('min')).toBe(true);

    fixture.componentInstance.submit();
    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/clients/c1/financial-profile`);
  });

  it('shows the backend error on failure', () => {
    configure(null);
    const fixture = TestBed.createComponent(EditFinancialProfileDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/clients/c1/financial-profile`)
      .flush(
        { code: 'NEGATIVE_FINANCIAL_VALUE', message: 'bad value', requestId: 'r1' },
        { status: 400, statusText: 'Bad Request' },
      );

    expect(fixture.componentInstance.error()).toBe(
      'Los importes y cantidades no pueden ser negativos.',
    );
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('cancel() closes the dialog without a result', () => {
    configure(null);
    const fixture = TestBed.createComponent(EditFinancialProfileDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();
    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
