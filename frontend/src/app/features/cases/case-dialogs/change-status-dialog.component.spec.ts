import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { ChangeStatusDialogComponent } from './change-status-dialog.component';

describe('ChangeStatusDialogComponent', () => {
  let httpMock: HttpTestingController;
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    dialogRef = { close: vi.fn() };
    TestBed.configureTestingModule({
      imports: [ChangeStatusDialogComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: MatDialogRef, useValue: dialogRef },
        { provide: MAT_DIALOG_DATA, useValue: { caseId: 'k1' } },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('exposes the closed set of case statuses for the picker', () => {
    const fixture = TestBed.createComponent(ChangeStatusDialogComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.statuses).toContain('ANALYSIS');
  });

  it('submits newStatus/reason and closes the dialog with the updated case', () => {
    const fixture = TestBed.createComponent(ChangeStatusDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ newStatus: 'ANALYSIS', reason: 'progressing' });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/status`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ newStatus: 'ANALYSIS', reason: 'progressing' });
    req.flush({ id: 'k1', status: 'ANALYSIS' });

    expect(dialogRef.close).toHaveBeenCalledWith({ id: 'k1', status: 'ANALYSIS' });
  });

  it('does not submit without a selected status', () => {
    const fixture = TestBed.createComponent(ChangeStatusDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/cases/k1/status`);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('shows the backend error and does not close on failure', () => {
    const fixture = TestBed.createComponent(ChangeStatusDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ newStatus: 'ANALYSIS', reason: '' });
    fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/status`)
      .flush({ code: 'INVALID_TRANSITION', message: 'Invalid status transition.', requestId: 'r1' }, { status: 409, statusText: 'Conflict' });

    expect(fixture.componentInstance.error()).toBe('No es posible realizar ese cambio de estado en este momento.');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('cancel() closes the dialog without a result', () => {
    const fixture = TestBed.createComponent(ChangeStatusDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();
    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
