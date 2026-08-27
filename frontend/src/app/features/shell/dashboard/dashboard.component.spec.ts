import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { SessionStore } from '../../../core/session/session.store';
import { DashboardComponent } from './dashboard.component';
import { Dashboard } from './dashboard.model';

describe('DashboardComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [DashboardComponent],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  const payload: Dashboard = {
    activeCases: 3,
    casesByStatus: { PRESTUDY: 2, OFFER: 1 },
    pendingTasks: 4,
    overdueTasks: 1,
    pendingDocumentRequests: 2,
    recentActivity: [{ id: 'a1', caseId: 'c1', activityType: 'CASE_CREATED', summary: 'Caso creado', createdAt: '2026-01-01T10:00:00Z' }],
  };

  it('renders the dashboard metrics returned by the API', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/dashboard`).flush(payload);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('3');
    expect(fixture.nativeElement.textContent).toContain('Operaciones activas');
    expect(fixture.nativeElement.textContent).toContain('Tareas pendientes');
    expect(fixture.nativeElement.textContent).toContain('Documentación pendiente');
    expect(fixture.nativeElement.textContent).toContain('Caso creado');
  });

  it('shows an error message when the dashboard request fails', () => {
    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/dashboard`)
      .flush({ code: 'FORBIDDEN', message: 'Access denied.', requestId: 'r1' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No tienes permisos para realizar esta acción.');
  });

  it('renders the SUPERADMIN overview instead of the tenant-scoped dashboard, without calling /api/v1/dashboard', () => {
    const sessionStore = TestBed.inject(SessionStore);
    sessionStore.setUser({
      id: 'u1',
      email: 'super@brika.local',
      role: 'SUPERADMIN',
      companyId: null,
      entitlements: {},
    });

    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();

    // Only the SUPERADMIN overview's own list requests should fire — never /api/v1/dashboard.
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/companies`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/clients`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks`).flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Actividad reciente');
    expect(fixture.nativeElement.textContent).not.toContain('Operaciones por estado');
  });

  it('renders the MANAGER overview instead of the tenant-scoped dashboard, without calling /api/v1/dashboard', () => {
    const sessionStore = TestBed.inject(SessionStore);
    sessionStore.setUser({
      id: 'u1',
      email: 'manager@brika.local',
      role: 'MANAGER',
      companyId: 'c1',
      entitlements: {},
    });

    const fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();

    // Only the MANAGER overview's own list requests should fire — never /api/v1/dashboard, and
    // never /api/v1/companies (MANAGER has no Empresas card).
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/clients`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks`).flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Actividad reciente');
    expect(fixture.nativeElement.textContent).not.toContain('Operaciones por estado');
  });
});