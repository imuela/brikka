import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { PortalNotificationService } from './portal-notification.service';
import { PortalNotification } from './portal-notification.model';

describe('PortalNotificationService', () => {
  let service: PortalNotificationService;
  let httpMock: HttpTestingController;

  const notification: PortalNotification = {
    id: 'n1',
    type: 'case.status_changed',
    payload: {},
    readAt: null,
    createdAt: '2026-08-18T10:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PortalNotificationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('list() calls GET /api/v1/portal/notifications', () => {
    service.list().subscribe((result) => expect(result).toEqual([notification]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/portal/notifications`);
    expect(req.request.method).toBe('GET');
    req.flush([notification]);
  });

  it('markRead(id) PATCHes /api/v1/portal/notifications/{id}/read', () => {
    const read = { ...notification, readAt: '2026-08-18T10:05:00Z' };
    service.markRead('n1').subscribe((result) => expect(result).toEqual(read));
    const req = httpMock.expectOne(
      `${environment.apiBaseUrl}/api/v1/portal/notifications/n1/read`,
    );
    expect(req.request.method).toBe('PATCH');
    req.flush(read);
  });
});
