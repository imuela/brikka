import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { PortalDashboardComponent } from './portal-dashboard.component';

const theCase = {
  id: 'k1',
  reference: 'REF-1',
  status: 'PRESTUDY',
  operationType: 'MORTGAGE',
  createdAt: '2026-08-18T10:00:00Z',
};
const notification = {
  id: 'n1',
  type: 'case.status_changed',
  payload: {},
  readAt: null,
  createdAt: '2026-08-18T10:00:00Z',
};

describe('PortalDashboardComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PortalDashboardComponent],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('loads and renders own cases and notifications', () => {
    const fixture = TestBed.createComponent(PortalDashboardComponent);
    fixture.detectChanges();

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/portal/cases`).flush([theCase]);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/portal/notifications`)
      .flush([notification]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('REF-1');
    expect(fixture.nativeElement.textContent).toContain('case.status_changed');
    expect(fixture.nativeElement.textContent).toContain('Marcar como leída');
  });

  it('shows the honest empty state when there are no cases or notifications', () => {
    const fixture = TestBed.createComponent(PortalDashboardComponent);
    fixture.detectChanges();

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/portal/cases`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/portal/notifications`).flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No tienes operaciones todavía.');
    expect(fixture.nativeElement.textContent).toContain('Sin notificaciones todavía.');
  });

  it('markNotificationRead calls PATCH and reloads the list', () => {
    const fixture = TestBed.createComponent(PortalDashboardComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/portal/cases`).flush([]);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/portal/notifications`)
      .flush([notification]);
    fixture.detectChanges();

    fixture.componentInstance.markNotificationRead(notification);

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/portal/notifications/n1/read`)
      .flush({ ...notification, readAt: '2026-08-18T10:05:00Z' });
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/portal/notifications`)
      .flush([{ ...notification, readAt: '2026-08-18T10:05:00Z' }]);
  });
});
