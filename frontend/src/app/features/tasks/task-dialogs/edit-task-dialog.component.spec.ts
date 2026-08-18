import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { Task } from '../task.model';
import { EditTaskDialogComponent } from './edit-task-dialog.component';

const user = { id: 'u1', email: 'u1@brika.test', firstName: 'Grace', lastName: 'Hopper', role: 'BROKER' };
const task: Task = {
  id: 't1',
  caseId: 'k1',
  assignedTo: 'u1',
  type: 'CALL',
  title: 'Llamar al cliente',
  description: 'Confirmar documentación',
  status: 'TODO',
  dueAt: '2026-08-20T00:00:00Z',
  createdBy: 'u1',
  completedAt: null,
  createdAt: '2026-08-18T10:00:00Z',
  updatedAt: '2026-08-18T10:00:00Z',
};

function configure() {
  const dialogRef = { close: vi.fn() };
  TestBed.configureTestingModule({
    imports: [EditTaskDialogComponent, NoopAnimationsModule],
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      { provide: MatDialogRef, useValue: dialogRef },
      { provide: MAT_DIALOG_DATA, useValue: { task } },
    ],
  });
  return dialogRef;
}

describe('EditTaskDialogComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => httpMock.verify());

  it('pre-fills the form from the given task', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(EditTaskDialogComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`).flush([user]);

    expect(fixture.componentInstance.form.getRawValue()).toEqual({
      title: 'Llamar al cliente',
      description: 'Confirmar documentación',
      status: 'TODO',
      assignedTo: 'u1',
      dueAt: '2026-08-20',
    });
  });

  it('submits the full-replace PATCH and closes with the updated task', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(EditTaskDialogComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`).flush([user]);

    fixture.componentInstance.form.patchValue({ status: 'IN_PROGRESS' });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/tasks/t1`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({
      title: 'Llamar al cliente',
      description: 'Confirmar documentación',
      status: 'IN_PROGRESS',
      dueAt: new Date('2026-08-20').toISOString(),
      assignedTo: 'u1',
    });
    req.flush({ ...task, status: 'IN_PROGRESS' });

    expect(dialogRef.close).toHaveBeenCalledWith({ ...task, status: 'IN_PROGRESS' });
  });

  it('does not offer DONE as a selectable status', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(EditTaskDialogComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`).flush([user]);

    expect(fixture.componentInstance.updatableStatuses).not.toContain('DONE');
  });

  it('cancel() closes the dialog without a result', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(EditTaskDialogComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`).flush([user]);
    fixture.componentInstance.cancel();

    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
