import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { AssignDialogComponent } from './assign-dialog.component';

describe('AssignDialogComponent', () => {
  let httpMock: HttpTestingController;
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    dialogRef = { close: vi.fn() };
    TestBed.configureTestingModule({
      imports: [AssignDialogComponent, NoopAnimationsModule],
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

  function flushUsers() {
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/users`)
      .flush([{ id: 'u1', email: 'u1@brika.test', firstName: 'Grace', lastName: 'Hopper', role: 'BROKER' }]);
  }

  it('loads assignable users from GET /api/v1/users on init', () => {
    const fixture = TestBed.createComponent(AssignDialogComponent);
    fixture.detectChanges();
    flushUsers();

    expect(fixture.componentInstance.users()).toEqual([
      { id: 'u1', email: 'u1@brika.test', firstName: 'Grace', lastName: 'Hopper', role: 'BROKER' },
    ]);
  });

  it('submits userId/assignmentType and closes the dialog with the new assignment', () => {
    const fixture = TestBed.createComponent(AssignDialogComponent);
    fixture.detectChanges();
    flushUsers();

    fixture.componentInstance.form.setValue({ userId: 'u1', assignmentType: 'BROKER' });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/assignments`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ userId: 'u1', assignmentType: 'BROKER' });
    req.flush({ id: 'a1', caseId: 'k1', userId: 'u1', assignmentType: 'BROKER', active: true });

    expect(dialogRef.close).toHaveBeenCalledWith({
      id: 'a1',
      caseId: 'k1',
      userId: 'u1',
      assignmentType: 'BROKER',
      active: true,
    });
  });

  it('does not submit without a selected user', () => {
    const fixture = TestBed.createComponent(AssignDialogComponent);
    fixture.detectChanges();
    flushUsers();

    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/cases/k1/assignments`);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('shows the backend error on failure (e.g. BROKER lacks CASE_ASSIGN)', () => {
    const fixture = TestBed.createComponent(AssignDialogComponent);
    fixture.detectChanges();
    flushUsers();

    fixture.componentInstance.form.setValue({ userId: 'u1', assignmentType: 'BROKER' });
    fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/assignments`)
      .flush({ code: 'FORBIDDEN', message: 'Access denied.', requestId: 'r1' }, { status: 403, statusText: 'Forbidden' });

    expect(fixture.componentInstance.error()).toBe('Access denied.');
    expect(dialogRef.close).not.toHaveBeenCalled();
  });
});
