import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { BankMatchResult } from '../bank-matching.model';
import { RunMatchingDialogComponent } from './run-matching-dialog.component';

const bank = { id: 'b1', code: 'DEVBANK', name: 'Banco Demo Desarrollo', status: 'ACTIVE', metadata: {} };
const matchResult: BankMatchResult = {
  id: 'm1',
  caseId: 'k1',
  bankId: 'b1',
  bankCriteriaVersionId: 'c1',
  globalResult: 'PASS',
  effectiveGlobalResult: 'PASS',
  evaluatedAt: '2026-08-18T10:00:00Z',
  inputSnapshot: {},
  ruleResults: [],
};

function configure() {
  const dialogRef = { close: vi.fn() };
  TestBed.configureTestingModule({
    imports: [RunMatchingDialogComponent, NoopAnimationsModule],
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      { provide: MatDialogRef, useValue: dialogRef },
      { provide: MAT_DIALOG_DATA, useValue: { caseId: 'k1' } },
    ],
  });
  return dialogRef;
}

describe('RunMatchingDialogComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => httpMock.verify());

  it('loads the bank catalog on init', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(RunMatchingDialogComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks`).flush([bank]);
    fixture.detectChanges();

    // mat-select only renders its mat-option content into an overlay once opened, not into
    // nativeElement.textContent — asserting on the component's own signal is the reliable check.
    expect(fixture.componentInstance.banks()).toEqual([bank]);
  });

  it('submits and closes with the returned match result', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(RunMatchingDialogComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks`).flush([bank]);
    fixture.componentInstance.form.setValue({ bankId: 'b1' });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(
      `${environment.apiBaseUrl}/api/v1/cases/k1/banks/b1/matching`,
    );
    expect(req.request.method).toBe('POST');
    req.flush(matchResult);

    expect(dialogRef.close).toHaveBeenCalledWith(matchResult);
  });

  it('does not submit without selecting a bank', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(RunMatchingDialogComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks`).flush([bank]);
    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/cases/k1/banks/b1/matching`);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('cancel() closes the dialog without a result', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(RunMatchingDialogComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks`).flush([bank]);
    fixture.componentInstance.cancel();

    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
