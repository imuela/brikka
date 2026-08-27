import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { vi } from 'vitest';

import { environment } from '../../../../../environments/environment';
import { errorInterceptor } from '../../../../core/http/error.interceptor';
import { Case } from '../../../cases/case.model';
import { Company } from '../../../companies/company.model';
import { User } from '../../../users/user.model';
import { SuperadminDashboardComponent } from './superadmin-dashboard.component';

describe('SuperadminDashboardComponent', () => {
  let httpMock: HttpTestingController;

  const companies: Company[] = [
    { id: 'c1', legalName: 'Brika Demo S.L.', tradeName: 'Brika', taxId: 'A1', status: 'ACTIVE' },
  ];
  const users: User[] = [
    {
      id: 'u1',
      companyId: null,
      email: 'a@brika.local',
      firstName: 'A',
      lastName: 'A',
      role: 'SUPERADMIN',
      status: 'ACTIVE',
    },
    {
      id: 'u2',
      companyId: 'c1',
      email: 'b@brika.local',
      firstName: 'B',
      lastName: 'B',
      role: 'MANAGER',
      status: 'DISABLED',
    },
  ];
  const cases: Case[] = [
    {
      id: 'k1',
      companyId: 'c1',
      reference: 'C-1',
      status: 'PRESTUDY',
      operationType: 'PURCHASE',
      requestedAmount: 100000,
      description: null,
      createdBy: 'u1',
      createdAt: '2026-01-01T00:00:00Z',
      cancelledAt: null,
    },
    {
      id: 'k2',
      companyId: 'c1',
      reference: 'C-2',
      status: 'COMPLETED',
      operationType: 'PURCHASE',
      requestedAmount: 200000,
      description: null,
      createdBy: 'u1',
      createdAt: '2026-01-01T00:00:00Z',
      cancelledAt: null,
    },
    {
      id: 'k3',
      companyId: 'c1',
      reference: 'C-3',
      status: 'CANCELLED',
      operationType: 'PURCHASE',
      requestedAmount: 50000,
      description: null,
      createdBy: 'u1',
      createdAt: '2026-01-01T00:00:00Z',
      cancelledAt: null,
    },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [SuperadminDashboardComponent],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function flushAll(): void {
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/companies`).flush(companies);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`).flush(users);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/clients`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases`).flush(cases);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks`).flush([]);
  }

  it('shows the 8 metric cards with real counts derived from the existing list endpoints', () => {
    const fixture = TestBed.createComponent(SuperadminDashboardComponent);
    fixture.detectChanges();
    flushAll();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Empresas');
    expect(text).toContain('Usuarios');
    expect(text).toContain('Clientes');
    expect(text).toContain('Casos');
    expect(text).toContain('Bancos');
    expect(text).toContain('Casos activos');
    expect(text).toContain('Casos éxito');
    expect(text).toContain('Usuarios activos');

    const cards = fixture.nativeElement.querySelectorAll('.metric-card');
    expect(cards.length).toBe(8);
  });

  it('renders the 8 cards in the exact requested order (fila 1: Empresas/Usuarios/Usuarios activos/Bancos; fila 2: Clientes/Casos/Casos activos/Casos éxito)', () => {
    const fixture = TestBed.createComponent(SuperadminDashboardComponent);
    fixture.detectChanges();
    flushAll();
    fixture.detectChanges();

    const labels = Array.from(fixture.nativeElement.querySelectorAll('.metric-card__label')).map(
      (el) => (el as HTMLElement).textContent?.trim(),
    );
    expect(labels).toEqual([
      'Empresas',
      'Usuarios',
      'Usuarios activos',
      'Bancos',
      'Clientes',
      'Casos',
      'Casos activos',
      'Casos éxito',
    ]);
  });

  it('derives active/success case counts and active user counts from real status fields, not invented values', () => {
    const fixture = TestBed.createComponent(SuperadminDashboardComponent);
    fixture.detectChanges();
    flushAll();
    fixture.detectChanges();

    const metrics = fixture.componentInstance.metrics()!;
    expect(metrics.find((m) => m.key === 'cases')!.value).toBe(3);
    expect(metrics.find((m) => m.key === 'activeCases')!.value).toBe(1);
    expect(metrics.find((m) => m.key === 'successCases')!.value).toBe(1);
    expect(metrics.find((m) => m.key === 'users')!.value).toBe(2);
    expect(metrics.find((m) => m.key === 'activeUsers')!.value).toBe(1);
  });

  it('navigates to the real existing route when a card with an associated view is clicked', () => {
    const fixture = TestBed.createComponent(SuperadminDashboardComponent);
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');
    fixture.detectChanges();
    flushAll();
    fixture.detectChanges();

    const companiesCard = Array.from(fixture.nativeElement.querySelectorAll('.metric-card')).find(
      (el) => (el as HTMLElement).textContent?.includes('Empresas'),
    ) as HTMLElement;
    companiesCard.click();

    expect(navigateSpy).toHaveBeenCalledWith('/app/companies');
  });

  it('does not navigate when a derived metric card without an associated view is clicked', () => {
    const fixture = TestBed.createComponent(SuperadminDashboardComponent);
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');
    fixture.detectChanges();
    flushAll();
    fixture.detectChanges();

    const activeCasesCard = Array.from(fixture.nativeElement.querySelectorAll('.metric-card')).find(
      (el) => (el as HTMLElement).textContent?.includes('Casos activos'),
    ) as HTMLElement;
    activeCasesCard.click();

    expect(navigateSpy).not.toHaveBeenCalled();
  });

  it('shows an error message when a list request fails', () => {
    const fixture = TestBed.createComponent(SuperadminDashboardComponent);
    fixture.detectChanges();

    // forkJoin subscribes to all 5 sources eagerly, then fails fast the moment one errors —
    // whether it manages to cancel the other 4 in-flight requests before this test's next line
    // runs is a timing detail, so capture all 5 up front and only flush whichever ones are still
    // live afterward, instead of assuming a specific cancellation outcome.
    const companiesReq = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/companies`);
    const otherReqs = ['users', 'clients', 'cases', 'banks'].map((path) =>
      httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/${path}`),
    );

    companiesReq.flush(
      { code: 'FORBIDDEN', message: 'Access denied.', requestId: 'r1' },
      { status: 403, statusText: 'Forbidden' },
    );
    fixture.detectChanges();

    otherReqs.forEach((req) => {
      if (!req.cancelled) {
        req.flush([]);
      }
    });

    expect(fixture.nativeElement.textContent).toContain(
      'No tienes permisos para realizar esta acción.',
    );
  });
});
