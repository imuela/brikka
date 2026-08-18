import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { Task } from './task.model';
import { TaskService } from './task.service';

describe('TaskService', () => {
  let service: TaskService;
  let httpMock: HttpTestingController;

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

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(TaskService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('list() calls GET /api/v1/tasks', () => {
    service.list().subscribe((result) => expect(result).toEqual([task]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/tasks`);
    expect(req.request.method).toBe('GET');
    req.flush([task]);
  });

  it('create() POSTs the exact CreateTaskApiRequest shape', () => {
    const request = {
      caseId: 'k1',
      assignedTo: 'u1',
      type: 'CALL',
      title: 'Llamar al cliente',
      description: null,
      dueAt: null,
    };
    service.create(request).subscribe((result) => expect(result).toEqual(task));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/tasks`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(task);
  });

  it('update() PATCHes the exact UpdateTaskApiRequest shape', () => {
    const request = {
      title: 'Llamar al cliente',
      description: null,
      status: 'IN_PROGRESS',
      dueAt: null,
      assignedTo: 'u1',
    };
    service.update('t1', request).subscribe((result) => expect(result).toEqual(task));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/tasks/t1`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(request);
    req.flush(task);
  });

  it('complete() POSTs to /api/v1/tasks/{id}/complete', () => {
    service.complete('t1').subscribe((result) => expect(result).toEqual(task));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/tasks/t1/complete`);
    expect(req.request.method).toBe('POST');
    req.flush(task);
  });

  it('delete() DELETEs /api/v1/tasks/{id}', () => {
    service.delete('t1').subscribe();
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/tasks/t1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });
});
