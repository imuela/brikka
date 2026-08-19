import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../environments/environment';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { portalAuthInterceptor } from '../../core/http/portal-auth.interceptor';
import { PortalLoginComponent } from './portal-login.component';

describe('PortalLoginComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PortalLoginComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptors([portalAuthInterceptor, errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap({}) } } },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    sessionStorage.clear();
  });

  afterEach(() => httpMock.verify());

  const tick = () => new Promise<void>((resolve) => setTimeout(resolve));

  it('hydrates the Portal session after login before navigating to /portal', async () => {
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(PortalLoginComponent);
    fixture.detectChanges();

    fixture.componentInstance.form.setValue({
      email: 'client@brika.test',
      password: 'correct-horse',
    });
    const submitPromise = fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/portal/auth/login`)
      .flush({ accessToken: 'access-p1', refreshToken: 'refresh-p1', expiresInSeconds: 900 });
    await tick();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/portal/me`)
      .flush({ clientId: 'cl1', firstName: 'Ada', lastName: 'Client', email: 'ada@client.test', phone: null, accountStatus: 'ACTIVE', lastLoginAt: null });
    await submitPromise;

    expect(navigateSpy).toHaveBeenCalledWith('/portal');
    expect(sessionStorage.getItem('brika.portal.session.refreshToken')).toBe('refresh-p1');
    expect(sessionStorage.getItem('brika.session.refreshToken')).toBeNull();
  });

  it('shows an error and does not navigate when Portal hydration fails after login', async () => {
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(PortalLoginComponent);
    fixture.detectChanges();

    fixture.componentInstance.form.setValue({
      email: 'client@brika.test',
      password: 'correct-horse',
    });
    const submitPromise = fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/portal/auth/login`)
      .flush({ accessToken: 'access-p1', refreshToken: 'refresh-p1', expiresInSeconds: 900 });
    await tick();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/portal/me`)
      .flush({ code: 'SERVER_ERROR', message: 'boom', requestId: 'r-1' }, { status: 500, statusText: 'Server Error' });
    await submitPromise;

    expect(navigateSpy).not.toHaveBeenCalled();
    expect(fixture.componentInstance.error()).toContain('servidor');
  });
});