import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { CasesService } from './cases.service';
import { AssignableUser, Case, CaseAssignment, CaseClient } from './case.model';

describe('CasesService', () => {
  let service: CasesService;
  let httpMock: HttpTestingController;

  const theCase: Case = {
    id: 'k1',
    companyId: 'co1',
    reference: 'REF-1',
    status: 'PRESTUDY',
    operationType: 'MORTGAGE',
    createdBy: 'u1',
    createdAt: '2026-08-17T10:00:00Z',
    cancelledAt: null,
  };

  const assignment: CaseAssignment = {
    id: 'a1',
    caseId: 'k1',
    userId: 'u1',
    assignmentType: 'BROKER',
    active: true,
  };

  const caseClient: CaseClient = {
    clientId: 'c1',
    firstName: 'Ada',
    lastName: 'Lovelace',
    participationType: 'HOLDER',
    isPrimary: true,
  };

  const assignableUser: AssignableUser = {
    id: 'u1',
    email: 'u1@brika.test',
    firstName: 'Grace',
    lastName: 'Hopper',
    role: 'BROKER',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CasesService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('list() calls GET /api/v1/cases', () => {
    service.list().subscribe((cases) => expect(cases).toEqual([theCase]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases`);
    expect(req.request.method).toBe('GET');
    req.flush([theCase]);
  });

  it('get(id) calls GET /api/v1/cases/{id}', () => {
    service.get('k1').subscribe((result) => expect(result).toEqual(theCase));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1`);
    expect(req.request.method).toBe('GET');
    req.flush(theCase);
  });

  it('create() posts the exact CreateCaseApiRequest shape', () => {
    const request = { operationType: 'MORTGAGE' };
    service.create(request).subscribe((result) => expect(result).toEqual(theCase));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(theCase);
  });

  it('update(id) patches the exact UpdateCaseApiRequest shape', () => {
    const request = { operationType: 'REMORTGAGE' };
    service.update('k1', request).subscribe((result) => expect(result).toEqual(theCase));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual(request);
    req.flush(theCase);
  });

  it('changeStatus() posts to /status with the exact request shape', () => {
    const request = { newStatus: 'ANALYSIS', reason: 'moving forward' };
    service.changeStatus('k1', request).subscribe((result) => expect(result).toEqual(theCase));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/status`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(theCase);
  });

  it('cancel() posts to /cancel with the exact request shape', () => {
    const request = { reason: 'CLIENT_REQUEST', comment: 'client withdrew' };
    service.cancel('k1', request).subscribe((result) => expect(result).toEqual(theCase));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/cancel`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(theCase);
  });

  it('reopen() posts to /reopen with the exact request shape', () => {
    const request = { reason: 'new documents', targetStatus: 'DOCUMENTATION' };
    service.reopen('k1', request).subscribe((result) => expect(result).toEqual(theCase));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/reopen`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(theCase);
  });

  it('listAssignments(id) calls GET /api/v1/cases/{id}/assignments', () => {
    service.listAssignments('k1').subscribe((result) => expect(result).toEqual([assignment]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/assignments`);
    expect(req.request.method).toBe('GET');
    req.flush([assignment]);
  });

  it('assign() posts the exact CreateCaseAssignmentApiRequest shape', () => {
    const request = { userId: 'u1', assignmentType: 'BROKER' };
    service.assign('k1', request).subscribe((result) => expect(result).toEqual(assignment));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/assignments`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(assignment);
  });

  it('listClients(id) calls GET /api/v1/cases/{id}/clients', () => {
    service.listClients('k1').subscribe((result) => expect(result).toEqual([caseClient]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/clients`);
    expect(req.request.method).toBe('GET');
    req.flush([caseClient]);
  });

  it('addClient() posts the exact CaseClientApiRequest shape', () => {
    const request = { clientId: 'c1', participationType: 'HOLDER', isPrimary: true };
    service.addClient('k1', request).subscribe();
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/clients`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(null);
  });

  it('removeClient() calls DELETE /api/v1/cases/{id}/clients/{clientId}', () => {
    service.removeClient('k1', 'c1').subscribe();
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/clients/c1`);
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('listAssignableUsers() calls GET /api/v1/users', () => {
    service.listAssignableUsers().subscribe((result) => expect(result).toEqual([assignableUser]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`);
    expect(req.request.method).toBe('GET');
    req.flush([assignableUser]);
  });
});
