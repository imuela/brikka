import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { AppNotification } from './notification.model';
import { NotificationService } from './notification.service';

describe('NotificationService', () => {
  let service: NotificationService;
  let httpMock: HttpTestingController;

  const notification: AppNotification = {
    id: 'n1',
    type: 'document.rejected',
    payload: { documentId: 'd1' },
    readAt: null,
    createdAt: '2026-08-18T10:00:00Z',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(NotificationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('list() calls GET /api/v1/notifications', () => {
    service.list().subscribe((result) => expect(result).toEqual([notification]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/notifications`);
    expect(req.request.method).toBe('GET');
    req.flush([notification]);
  });

  it('markRead() PATCHes /api/v1/notifications/{id}/read', () => {
    service.markRead('n1').subscribe((result) => expect(result).toEqual({ ...notification, readAt: '2026-08-18T11:00:00Z' }));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/notifications/n1/read`);
    expect(req.request.method).toBe('PATCH');
    req.flush({ ...notification, readAt: '2026-08-18T11:00:00Z' });
  });
});
