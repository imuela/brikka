import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { errorInterceptor } from '../../../core/http/error.interceptor';
import { PortalSessionStore } from '../../../portal-auth/portal-session.store';
import { PortalShellComponent } from './portal-shell.component';

describe('PortalShellComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PortalShellComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    TestBed.inject(PortalSessionStore).setClient({
      clientId: 'c1',
      firstName: 'Ada',
      lastName: 'Byron',
      email: 'ada@brika.test',
      phone: null,
      accountStatus: 'ACTIVE',
      lastLoginAt: null,
    });
  });

  afterEach(() => httpMock.verify());

  it('logout() clears the Portal session and navigates to /portal/login immediately, never touching the internal session', () => {
    const navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(PortalShellComponent);
    fixture.detectChanges();

    fixture.componentInstance.logout();

    httpMock.match(() => true).forEach((req) => req.flush({}));

    expect(TestBed.inject(PortalSessionStore).isHydrated()).toBe(false);
    expect(navigateSpy).toHaveBeenCalledWith(['/portal/login']);
  });
});
