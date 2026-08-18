import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { BankMatchRuleOverride } from '../bank-matching.model';
import { OverrideRuleDialogComponent } from './override-rule-dialog.component';

const override: BankMatchRuleOverride = {
  id: 'o1',
  previousResult: 'FAIL',
  newResult: 'PASS',
  reason: 'Excepción autorizada.',
  overriddenBy: 'u1',
  overriddenAt: '2026-08-18T10:05:00Z',
};

function configure() {
  const dialogRef = { close: vi.fn() };
  TestBed.configureTestingModule({
    imports: [OverrideRuleDialogComponent, NoopAnimationsModule],
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      { provide: MatDialogRef, useValue: dialogRef },
      { provide: MAT_DIALOG_DATA, useValue: { ruleResultId: 'rr1', currentResult: 'FAIL' } },
    ],
  });
  return dialogRef;
}

describe('OverrideRuleDialogComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => httpMock.verify());

  it('submits with the current result as previousResult and closes with the result', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(OverrideRuleDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ newResult: 'PASS', reason: 'Excepción autorizada.' });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(
      `${environment.apiBaseUrl}/api/v1/bank-match-rule-results/rr1/overrides`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      previousResult: 'FAIL',
      newResult: 'PASS',
      reason: 'Excepción autorizada.',
    });
    req.flush(override);

    expect(dialogRef.close).toHaveBeenCalledWith(override);
  });

  it('does not submit with missing required fields', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(OverrideRuleDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/bank-match-rule-results/rr1/overrides`);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('cancel() closes the dialog without a result', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(OverrideRuleDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();

    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
