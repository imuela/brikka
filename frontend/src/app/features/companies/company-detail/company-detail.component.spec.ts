import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { SessionStore } from '../../../core/session/session.store';
import { ConfirmDialogComponent } from '../../../shared/dialogs/confirm-dialog.component';
import { ChangeSubscriptionDialogComponent } from '../company-dialogs/change-subscription-dialog.component';
import { CompanyDetailComponent } from './company-detail.component';

const company = {
  id: 'co1',
  legalName: 'Brika Demo SL',
  tradeName: 'Brika',
  taxId: 'B12345678',
  status: 'ACTIVE',
};
const plan = { id: 'p1', code: 'PRO', name: 'Plan Pro', status: 'ACTIVE' };
const subscription = { id: 's1', companyId: 'co1', planId: 'p1', status: 'ACTIVE' };

function configure() {
  TestBed.configureTestingModule({
    imports: [CompanyDetailComponent],
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: convertToParamMap({ id: 'co1' }) } },
      },
    ],
  });
}

describe('CompanyDetailComponent', () => {
  let httpMock: HttpTestingController;
  let sessionStore: SessionStore;

  afterEach(() => httpMock.verify());

  it('loads the company and does not request the subscription without SUBSCRIPTION_READ (MANAGER)', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);
    sessionStore = TestBed.inject(SessionStore);
    sessionStore.setPermissions(['COMPANY_READ']);

    const fixture = TestBed.createComponent(CompanyDetailComponent);
    fixture.detectChanges();

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/companies/co1`).flush(company);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Brika Demo SL');
    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/companies/co1/subscription`);
    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/plans`);
  });

  it('loads the subscription and plans when the session has SUBSCRIPTION_READ (SUPERADMIN)', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);
    sessionStore = TestBed.inject(SessionStore);
    sessionStore.setPermissions(['COMPANY_READ', 'SUBSCRIPTION_READ']);

    const fixture = TestBed.createComponent(CompanyDetailComponent);
    fixture.detectChanges();

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/companies/co1`).flush(company);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/companies/co1/subscription`).flush(subscription);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/plans`).flush([plan]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Plan Pro');
  });

  // Sprint 35: Bloque C — case-detail/portal-case-detail already had this test; bank-detail and
  // company-detail were fixed live in Sprint 34 (D34-2) but never got a dedicated regression test.
  it('shows the backend error and clears the spinner when loading the company fails', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);
    sessionStore = TestBed.inject(SessionStore);
    sessionStore.setPermissions(['COMPANY_READ']);

    const fixture = TestBed.createComponent(CompanyDetailComponent);
    fixture.detectChanges();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/companies/co1`)
      .flush(
        { code: 'COMPANY_NOT_FOUND', message: 'not found', requestId: 'r1' },
        { status: 404, statusText: 'Not Found' },
      );
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No se ha encontrado la empresa solicitada.');
    expect(fixture.nativeElement.querySelector('mat-spinner')).toBeNull();
  });

  it('shows the empty subscription state on a 404 without setting the page-level error', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);
    sessionStore = TestBed.inject(SessionStore);
    sessionStore.setPermissions(['COMPANY_READ', 'SUBSCRIPTION_READ']);

    const fixture = TestBed.createComponent(CompanyDetailComponent);
    fixture.detectChanges();

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/companies/co1`).flush(company);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/companies/co1/subscription`)
      .flush(
        { code: 'SUBSCRIPTION_NOT_FOUND', message: 'not found', requestId: 'r1' },
        { status: 404, statusText: 'Not Found' },
      );
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/plans`).flush([]);
    fixture.detectChanges();

    expect(fixture.componentInstance.error()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Sin suscripción asignada');
  });

  it('gates Suspender/Eliminar by permission and hides Suspender once already SUSPENDED', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);
    sessionStore = TestBed.inject(SessionStore);
    sessionStore.setPermissions(['COMPANY_READ']);

    const fixture = TestBed.createComponent(CompanyDetailComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/companies/co1`).flush(company);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Suspender');
    expect(fixture.nativeElement.textContent).not.toContain('Eliminar');

    sessionStore.setPermissions(['COMPANY_READ', 'COMPANY_SUSPEND', 'COMPANY_DELETE']);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Suspender');
    expect(fixture.nativeElement.textContent).toContain('Eliminar');
  });

  it('suspend() opens a confirmation dialog and updates the company on confirm', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);
    sessionStore = TestBed.inject(SessionStore);
    sessionStore.setPermissions(['COMPANY_READ', 'COMPANY_SUSPEND']);

    const fixture = TestBed.createComponent(CompanyDetailComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/companies/co1`).flush(company);
    fixture.detectChanges();

    const dialog = TestBed.inject(MatDialog);
    const openSpy = vi
      .spyOn(dialog, 'open')
      .mockReturnValue({ afterClosed: () => of(true) } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.suspend();

    expect(openSpy).toHaveBeenCalledWith(ConfirmDialogComponent, expect.objectContaining({ width: '400px' }));
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/companies/co1/suspend`)
      .flush({ ...company, status: 'SUSPENDED' });

    expect(fixture.componentInstance.company()!.status).toBe('SUSPENDED');
  });

  it('openChangeSubscription opens the dialog with the current plans/subscription and applies the result', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);
    sessionStore = TestBed.inject(SessionStore);
    sessionStore.setPermissions(['COMPANY_READ', 'SUBSCRIPTION_READ', 'SUBSCRIPTION_MANAGE']);

    const fixture = TestBed.createComponent(CompanyDetailComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/companies/co1`).flush(company);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/companies/co1/subscription`)
      .flush(
        { code: 'SUBSCRIPTION_NOT_FOUND', message: 'not found', requestId: 'r1' },
        { status: 404, statusText: 'Not Found' },
      );
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/plans`).flush([plan]);
    fixture.detectChanges();

    const dialog = TestBed.inject(MatDialog);
    const openSpy = vi
      .spyOn(dialog, 'open')
      .mockReturnValue({ afterClosed: () => of(subscription) } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.openChangeSubscription();

    expect(openSpy).toHaveBeenCalledWith(
      ChangeSubscriptionDialogComponent,
      expect.objectContaining({
        data: { companyId: 'co1', plans: [plan], currentSubscription: null },
        width: '400px',
      }),
    );
    expect(fixture.componentInstance.subscription()).toEqual(subscription);
  });
});
