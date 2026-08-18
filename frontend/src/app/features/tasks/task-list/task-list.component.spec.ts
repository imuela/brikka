import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { SessionStore } from '../../../core/session/session.store';
import { Task } from '../task.model';
import { TaskListComponent } from './task-list.component';
import { CreateTaskDialogComponent } from '../task-dialogs/create-task-dialog.component';
import { EditTaskDialogComponent } from '../task-dialogs/edit-task-dialog.component';
import { ConfirmDialogComponent } from '../../../shared/dialogs/confirm-dialog.component';

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

describe('TaskListComponent', () => {
  let httpMock: HttpTestingController;
  let sessionStore: SessionStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [TaskListComponent],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    sessionStore = TestBed.inject(SessionStore);
  });

  afterEach(() => httpMock.verify());

  function flushInitialLoad(fixture: ReturnType<typeof TestBed.createComponent<TaskListComponent>>) {
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/tasks`).flush([task]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`).flush([user]);
    fixture.detectChanges();
  }

  it('loads and renders the task list', () => {
    const fixture = TestBed.createComponent(TaskListComponent);
    fixture.detectChanges();
    flushInitialLoad(fixture);

    expect(fixture.nativeElement.textContent).toContain('Llamar al cliente');
    expect(fixture.nativeElement.textContent).toContain('Grace Hopper');
  });

  it('gates "Nueva tarea" by TASK_CREATE', () => {
    const fixture = TestBed.createComponent(TaskListComponent);
    fixture.detectChanges();
    flushInitialLoad(fixture);

    expect(fixture.nativeElement.textContent).not.toContain('Nueva tarea');

    sessionStore.setPermissions(['TASK_CREATE']);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nueva tarea');
  });

  it('openCreate opens the dialog with caseId null and reloads on close with a result', () => {
    const fixture = TestBed.createComponent(TaskListComponent);
    fixture.detectChanges();
    flushInitialLoad(fixture);

    const dialog = TestBed.inject(MatDialog);
    const openSpy = vi
      .spyOn(dialog, 'open')
      .mockReturnValue({ afterClosed: () => of(task) } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.openCreate();

    expect(openSpy).toHaveBeenCalledWith(
      CreateTaskDialogComponent,
      expect.objectContaining({ data: { caseId: null } }),
    );
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/tasks`).flush([task]);
  });

  it('openEdit opens the dialog with the task and reloads on close with a result', () => {
    const fixture = TestBed.createComponent(TaskListComponent);
    fixture.detectChanges();
    flushInitialLoad(fixture);

    const dialog = TestBed.inject(MatDialog);
    const openSpy = vi
      .spyOn(dialog, 'open')
      .mockReturnValue({ afterClosed: () => of(task) } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.openEdit(task);

    expect(openSpy).toHaveBeenCalledWith(
      EditTaskDialogComponent,
      expect.objectContaining({ data: { task } }),
    );
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/tasks`).flush([task]);
  });

  it('complete() posts to the complete endpoint and reloads', () => {
    const fixture = TestBed.createComponent(TaskListComponent);
    fixture.detectChanges();
    flushInitialLoad(fixture);

    fixture.componentInstance.complete(task);

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/tasks/t1/complete`).flush(task);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/tasks`).flush([task]);
  });

  it('remove() asks for confirmation before deleting', () => {
    const fixture = TestBed.createComponent(TaskListComponent);
    fixture.detectChanges();
    flushInitialLoad(fixture);

    const dialog = TestBed.inject(MatDialog);
    const openSpy = vi
      .spyOn(dialog, 'open')
      .mockReturnValue({ afterClosed: () => of(true) } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.remove(task);

    expect(openSpy).toHaveBeenCalledWith(
      ConfirmDialogComponent,
      expect.objectContaining({ data: expect.objectContaining({ title: 'Eliminar tarea' }) }),
    );
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/tasks/t1`).flush(null);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/tasks`).flush([]);
  });

  it('remove() does not delete when the confirmation is declined', () => {
    const fixture = TestBed.createComponent(TaskListComponent);
    fixture.detectChanges();
    flushInitialLoad(fixture);

    const dialog = TestBed.inject(MatDialog);
    vi.spyOn(dialog, 'open').mockReturnValue({
      afterClosed: () => of(false),
    } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.remove(task);

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/tasks/t1`);
  });

  it('gates "Eliminar" by TASK_DELETE', () => {
    const fixture = TestBed.createComponent(TaskListComponent);
    fixture.detectChanges();
    flushInitialLoad(fixture);

    expect(fixture.nativeElement.textContent).not.toContain('Eliminar');

    sessionStore.setPermissions(['TASK_DELETE']);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Eliminar');
  });
});
