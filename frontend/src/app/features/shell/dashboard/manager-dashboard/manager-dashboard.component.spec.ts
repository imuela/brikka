import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { vi } from 'vitest';

import { environment } from '../../../../../environments/environment';
import { errorInterceptor } from '../../../../core/http/error.interceptor';
import { Case } from '../../../cases/case.model';
import { User } from '../../../users/user.model';
import { ManagerDashboardComponent } from './manager-dashboard.component';

describe('ManagerDashboardComponent', () => {
  let httpMock: HttpTestingController;

  const users: User[] = [
    {
      id: 'u1',
      companyId: 'c1',
      email: 'a@brika.local',
      firstName: 'A',
      lastName: 'A',
      role: 'MANAGER',
      status: 'ACTIVE',
    },
    {
      id: 'u2',
      companyId: 'c1',
      email: 'b@brika.local',
      firstName: 'B',
      lastName: 'B',
      role: 'BROKER',
      status: 'DISABLED',
    },
    {
      id: 'u3',
      companyId: 'c1',
      email: 'd@brika.local',
      firstName: 'D',
      lastName: 'D',
      role: 'BROKER',
      status: 'ACTIVE',
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
    {
      id: 'k4',
      companyId: 'c1',
      reference: 'C-4',
      status: 'ANALYSIS',
      operationType: 'PURCHASE',
      requestedAmount: 75000,
      description: null,
      createdBy: 'u1',
      createdAt: '2026-01-01T00:00:00Z',
      cancelledAt: null,
    },
  ];

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ManagerDashboardComponent],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function flushAll(): void {
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`).flush(users);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/clients`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases`).flush(cases);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks`).flush([]);
  }

  it('shows the 7 metric cards with real counts derived from the existing list endpoints', () => {
    const fixture = TestBed.createComponent(ManagerDashboardComponent);
    fixture.detectChanges();
    flushAll();
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent as string;
    expect(text).toContain('Usuarios');
    expect(text).toContain('Usuarios activos');
    expect(text).toContain('Bancos');
    expect(text).toContain('Clientes');
    expect(text).toContain('Casos');
    expect(text).toContain('Casos activos');
    expect(text).toContain('Casos éxito');

    const cards = fixture.nativeElement.querySelectorAll('.metric-card');
    expect(cards.length).toBe(7);
  });

  it('renders row 1 with exactly 4 cards (Usuarios/Usuarios activos/Bancos/Clientes) and row 2 with exactly 3 (Casos/Casos activos/Casos éxito)', () => {
    const fixture = TestBed.createComponent(ManagerDashboardComponent);
    fixture.detectChanges();
    flushAll();
    fixture.detectChanges();

    const rows = fixture.nativeElement.querySelectorAll('.metric-row');
    expect(rows.length).toBe(2);

    const row1Labels = Array.from(rows[0].querySelectorAll('.metric-card__label')).map((el) =>
      (el as HTMLElement).textContent?.trim(),
    );
    expect(row1Labels).toEqual(['Usuarios', 'Usuarios activos', 'Bancos', 'Clientes']);

    const row2Labels = Array.from(rows[1].querySelectorAll('.metric-card__label')).map((el) =>
      (el as HTMLElement).textContent?.trim(),
    );
    expect(row2Labels).toEqual(['Casos', 'Casos activos', 'Casos éxito']);
  });

  it('derives active/success case counts and active user counts from real status fields, not invented values', () => {
    const fixture = TestBed.createComponent(ManagerDashboardComponent);
    fixture.detectChanges();
    flushAll();
    fixture.detectChanges();

    const metrics = fixture.componentInstance.metrics()!;
    expect(metrics.find((m) => m.key === 'users')!.value).toBe(3);
    expect(metrics.find((m) => m.key === 'activeUsers')!.value).toBe(2);
    expect(metrics.find((m) => m.key === 'cases')!.value).toBe(4);
    // Active = NOT IN (COMPLETED, CANCELLED): PRESTUDY + ANALYSIS = 2.
    expect(metrics.find((m) => m.key === 'activeCases')!.value).toBe(2);
    // Success = COMPLETED (the only non-cancelled terminal state): 1.
    expect(metrics.find((m) => m.key === 'successCases')!.value).toBe(1);
  });

  it('navigates to the real existing route when a card with an associated view is clicked', () => {
    const fixture = TestBed.createComponent(ManagerDashboardComponent);
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl');
    fixture.detectChanges();
    flushAll();
    fixture.detectChanges();

    const usersCard = Array.from(fixture.nativeElement.querySelectorAll('.metric-card')).find(
      (el) =>
        (el as HTMLElement).querySelector('.metric-card__label')?.textContent?.trim() ===
        'Usuarios',
    ) as HTMLElement;
    usersCard.click();

    expect(navigateSpy).toHaveBeenCalledWith('/app/users');
  });

  it('does not navigate when a derived metric card without an associated view is clicked', () => {
    const fixture = TestBed.createComponent(ManagerDashboardComponent);
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
    const fixture = TestBed.createComponent(ManagerDashboardComponent);
    fixture.detectChanges();

    const usersReq = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`);
    const otherReqs = ['clients', 'cases', 'banks'].map((path) =>
      httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/${path}`),
    );

    usersReq.flush(
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
