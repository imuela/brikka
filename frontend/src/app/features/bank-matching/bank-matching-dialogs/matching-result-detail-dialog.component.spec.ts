import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { SessionStore } from '../../../core/session/session.store';
import { BankMatchResult } from '../bank-matching.model';
import { MatchingResultDetailDialogComponent } from './matching-result-detail-dialog.component';
import { OverrideRuleDialogComponent } from './override-rule-dialog.component';

const result: BankMatchResult = {
  id: 'm1',
  caseId: 'k1',
  bankId: 'b1',
  bankCriteriaVersionId: 'c1',
  globalResult: 'FAIL',
  effectiveGlobalResult: 'FAIL',
  evaluatedAt: '2026-08-18T10:00:00Z',
  inputSnapshot: { ltv: 0.9 },
  ruleResults: [
    {
      id: 'rr1',
      ruleId: 'ltv-max',
      field: 'computed.ltv',
      operator: 'LESS_THAN_OR_EQUAL',
      expectedValue: 0.8,
      evaluatedValue: 0.9,
      result: 'FAIL',
      reason: 'El LTV no puede superar el 80%.',
      effectiveResult: 'FAIL',
      overrideCount: 0,
      overrides: [],
    },
  ],
};

function configure() {
  const dialogRef = { close: vi.fn() };
  TestBed.configureTestingModule({
    imports: [MatchingResultDetailDialogComponent, NoopAnimationsModule],
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      { provide: MatDialogRef, useValue: dialogRef },
      { provide: MAT_DIALOG_DATA, useValue: { caseId: 'k1', result } },
    ],
  });
  return dialogRef;
}

describe('MatchingResultDetailDialogComponent', () => {
  let httpMock: HttpTestingController;
  let sessionStore: SessionStore;
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);
    sessionStore = TestBed.inject(SessionStore);
  });

  afterEach(() => httpMock.verify());

  it('renders the rule breakdown with field/operator/expected/evaluated/result', () => {
    const fixture = TestBed.createComponent(MatchingResultDetailDialogComponent);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('computed.ltv');
    expect(text).toContain('LESS_THAN_OR_EQUAL');
    expect(text).toContain('0.8');
    expect(text).toContain('0.9');
    expect(text).toContain('No cumple');
  });

  it('gates "Corregir" by BANK_MATCHING_OVERRIDE', () => {
    const fixture = TestBed.createComponent(MatchingResultDetailDialogComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Corregir');

    sessionStore.setPermissions(['BANK_MATCHING_OVERRIDE']);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Corregir');
  });

  it('openOverride opens the override dialog and reloads the result on close with a result', () => {
    const fixture = TestBed.createComponent(MatchingResultDetailDialogComponent);
    fixture.detectChanges();

    // Spied on the prototype rather than the TestBed-resolved instance: this component both is a
    // dialog (imports MatDialogModule for its own template) and opens a nested dialog (injects
    // MatDialog) — that combination resolves a MatDialog instance via the component's own
    // MatDialogModule-derived injector, not the root instance TestBed.inject(MatDialog) returns.
    const openSpy = vi.spyOn(MatDialog.prototype, 'open').mockReturnValue({
      afterClosed: () =>
        of({
          id: 'o1',
          previousResult: 'FAIL',
          newResult: 'PASS',
          reason: 'Excepción',
          overriddenBy: 'u1',
          overriddenAt: '2026-08-18T10:05:00Z',
        }),
    } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.openOverride('rr1', 'FAIL');

    expect(openSpy).toHaveBeenCalledWith(
      OverrideRuleDialogComponent,
      expect.objectContaining({ data: { ruleResultId: 'rr1', currentResult: 'FAIL' } }),
    );
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/matching`).flush([result]);
  });

  it('formatValue renders null/undefined as an em dash and objects as JSON', () => {
    const fixture = TestBed.createComponent(MatchingResultDetailDialogComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.formatValue(null)).toBe('—');
    expect(fixture.componentInstance.formatValue(undefined)).toBe('—');
    expect(fixture.componentInstance.formatValue(0.8)).toBe('0.8');
    expect(fixture.componentInstance.formatValue({ a: 1 })).toBe('{"a":1}');
  });

  it('close() closes the dialog', () => {
    const fixture = TestBed.createComponent(MatchingResultDetailDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.close();

    expect(dialogRef.close).toHaveBeenCalled();
  });
});
