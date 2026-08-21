import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { errorInterceptor } from '../../../core/http/error.interceptor';
import { SessionStore } from '../../../core/session/session.store';
import { UserMenuComponent } from './user-menu.component';

describe('UserMenuComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [UserMenuComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionStore).setUser({
      id: 'u1',
      email: 'manager@brika.test',
      role: 'MANAGER',
      companyId: 'co-1',
      entitlements: {},
    });
  });

  afterEach(() => httpMock.verify());

  it('logout() clears the session and navigates to /login immediately', () => {
    const navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(UserMenuComponent);
    fixture.detectChanges();

    fixture.componentInstance.logout();

    // best-effort server-side revoke; irrelevant to the redirect, but AuthService still fires it
    httpMock.match(() => true).forEach((req) => req.flush({}));

    expect(TestBed.inject(SessionStore).isHydrated()).toBe(false);
    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
  });
});
