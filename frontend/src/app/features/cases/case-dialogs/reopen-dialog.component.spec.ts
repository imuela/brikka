import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { ReopenDialogComponent } from './reopen-dialog.component';

describe('ReopenDialogComponent', () => {
  let httpMock: HttpTestingController;
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    dialogRef = { close: vi.fn() };
    TestBed.configureTestingModule({
      imports: [ReopenDialogComponent, NoopAnimationsModule],
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

  it('submits targetStatus/reason and closes the dialog with the reopened case', () => {
    const fixture = TestBed.createComponent(ReopenDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ targetStatus: 'DOCUMENTATION', reason: 'new documents' });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/reopen`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ targetStatus: 'DOCUMENTATION', reason: 'new documents' });
    req.flush({ id: 'k1', status: 'DOCUMENTATION' });

    expect(dialogRef.close).toHaveBeenCalledWith({ id: 'k1', status: 'DOCUMENTATION' });
  });

  it('does not submit without a selected target status', () => {
    const fixture = TestBed.createComponent(ReopenDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/cases/k1/reopen`);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('shows the backend error on failure (e.g. terminal target rejected)', () => {
    const fixture = TestBed.createComponent(ReopenDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ targetStatus: 'CANCELLED', reason: '' });
    fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/reopen`)
      .flush({ code: 'INVALID_TRANSITION', message: 'Cannot reopen into a terminal status.', requestId: 'r1' }, { status: 409, statusText: 'Conflict' });

    expect(fixture.componentInstance.error()).toBe('Cannot reopen into a terminal status.');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
