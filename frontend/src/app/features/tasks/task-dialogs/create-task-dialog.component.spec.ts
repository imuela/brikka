import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { Task } from '../task.model';
import { CreateTaskDialogComponent } from './create-task-dialog.component';

const user = { id: 'u1', email: 'u1@brika.test', firstName: 'Grace', lastName: 'Hopper', role: 'BROKER' };
const task: Task = {
  id: 't1',
  caseId: 'k1',
  assignedTo: 'u1',
  type: 'CALL',
  title: 'Llamar al cliente',
  description: null,
  status: 'TODO',
  dueAt: null,
  createdBy: 'u1',
  completedAt: null,
  createdAt: '2026-08-18T10:00:00Z',
  updatedAt: '2026-08-18T10:00:00Z',
};

function configure(caseId: string | null) {
  const dialogRef = { close: vi.fn() };
  TestBed.configureTestingModule({
    imports: [CreateTaskDialogComponent, NoopAnimationsModule],
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      { provide: MatDialogRef, useValue: dialogRef },
      { provide: MAT_DIALOG_DATA, useValue: { caseId } },
    ],
  });
  return dialogRef;
}

describe('CreateTaskDialogComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => httpMock.verify());

  it('loads assignable users on init', () => {
    configure('k1');
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateTaskDialogComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`).flush([user]);

    expect(fixture.componentInstance.users()).toEqual([user]);
  });

  it('submits with the case id from dialog data and closes with the created task', () => {
    const dialogRef = configure('k1');
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateTaskDialogComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`).flush([user]);

    fixture.componentInstance.form.setValue({
      type: 'CALL',
      title: 'Llamar al cliente',
      description: '',
      assignedTo: 'u1',
      dueAt: '',
    });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/tasks`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      caseId: 'k1',
      assignedTo: 'u1',
      type: 'CALL',
      title: 'Llamar al cliente',
      description: null,
      dueAt: null,
    });
    req.flush(task);

    expect(dialogRef.close).toHaveBeenCalledWith(task);
  });

  it('submits with a null case id when opened from the tenant-wide task list', () => {
    configure(null);
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateTaskDialogComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`).flush([user]);

    fixture.componentInstance.form.setValue({
      type: 'CALL',
      title: 'Llamar al cliente',
      description: '',
      assignedTo: '',
      dueAt: '',
    });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/tasks`);
    expect(req.request.body).toEqual({
      caseId: null,
      assignedTo: null,
      type: 'CALL',
      title: 'Llamar al cliente',
      description: null,
      dueAt: null,
    });
    req.flush(task);
  });

  it('does not submit without a required field', () => {
    configure('k1');
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateTaskDialogComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`).flush([user]);
    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/tasks`);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('shows a mat-error explaining why an invalid field is invalid (Sprint 36: D36-1b, a silently-blocked submit gave no visible reason)', () => {
    configure('k1');
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateTaskDialogComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`).flush([user]);

    const type = fixture.componentInstance.form.controls.type;
    type.markAsTouched();
    const title = fixture.componentInstance.form.controls.title;
    title.markAsTouched();
    fixture.detectChanges();

    const errorText = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(errorText).toContain('Selecciona un tipo.');
    expect(errorText).toContain('El título es obligatorio.');
  });

  it('cancel() closes the dialog without a result', () => {
    const dialogRef = configure('k1');
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateTaskDialogComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`).flush([user]);
    fixture.componentInstance.cancel();

    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
