import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { AppNotification } from '../notification.model';
import { NotificationListComponent } from './notification-list.component';

const notification: AppNotification = {
  id: 'n1',
  type: 'document.rejected',
  payload: { documentId: 'd1' },
  readAt: null,
  createdAt: '2026-08-18T10:00:00Z',
};

describe('NotificationListComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [NotificationListComponent],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('shows the real empty state when the backend returns no notifications', () => {
    const fixture = TestBed.createComponent(NotificationListComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/notifications`).flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Sin notificaciones todavía.');
  });

  it('renders a notification with its payload and an unread action', () => {
    const fixture = TestBed.createComponent(NotificationListComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/notifications`).flush([notification]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('document.rejected');
    expect(fixture.nativeElement.textContent).toContain('Marcar como leída');
  });

  it('renders "Leída" instead of the action once readAt is set', () => {
    const fixture = TestBed.createComponent(NotificationListComponent);
    fixture.detectChanges();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/notifications`)
      .flush([{ ...notification, readAt: '2026-08-18T11:00:00Z' }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Leída');
    expect(fixture.nativeElement.textContent).not.toContain('Marcar como leída');
  });

  it('markRead() patches and reloads the list', () => {
    const fixture = TestBed.createComponent(NotificationListComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/notifications`).flush([notification]);
    fixture.detectChanges();

    fixture.componentInstance.markRead(notification);

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/notifications/n1/read`)
      .flush({ ...notification, readAt: '2026-08-18T11:00:00Z' });
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/notifications`).flush([]);
  });

  it('formatPayload renders null/undefined as an em dash and objects as compact JSON', () => {
    const fixture = TestBed.createComponent(NotificationListComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/notifications`).flush([]);

    expect(fixture.componentInstance.formatPayload(null)).toBe('—');
    expect(fixture.componentInstance.formatPayload(undefined)).toBe('—');
    expect(fixture.componentInstance.formatPayload({})).toBe('—');
    expect(fixture.componentInstance.formatPayload({ documentId: 'd1' })).toBe('{"documentId":"d1"}');
    expect(fixture.componentInstance.formatPayload('texto')).toBe('texto');
  });
});
