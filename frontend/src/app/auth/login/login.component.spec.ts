import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../environments/environment';
import { authInterceptor } from '../../core/http/auth.interceptor';
import { errorInterceptor } from '../../core/http/error.interceptor';
import { LoginComponent } from './login.component';

describe('LoginComponent', () => {
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [LoginComponent, NoopAnimationsModule],
      providers: [
        provideHttpClient(withInterceptors([authInterceptor, errorInterceptor])),
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

  it('hydrates the session after a successful login before navigating to /app', async () => {
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();

    fixture.componentInstance.form.setValue({
      email: 'manager@brika.test',
      password: 'correct-horse',
    });
    const submitPromise = fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/auth/login`)
      .flush({ accessToken: 'access-123', refreshToken: 'refresh-123', expiresInSeconds: 900 });
    await tick();

    // Hydration runs before navigation: /me + /me/permissions are requested (Bearer attached).
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/me`)
      .flush({ id: 'u1', email: 'manager@brika.test', role: 'MANAGER', companyId: 'c1', entitlements: {} });
    await tick();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/me/permissions`)
      .flush({ permissions: ['CASE_READ'] });
    await submitPromise;

    expect(navigateSpy).toHaveBeenCalledWith('/app');
    expect(sessionStorage.getItem('brika.session.refreshToken')).toBe('refresh-123');
  });

  it('shows an error and does not navigate when hydration fails after login', async () => {
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    const fixture = TestBed.createComponent(LoginComponent);
    fixture.detectChanges();

    fixture.componentInstance.form.setValue({
      email: 'manager@brika.test',
      password: 'correct-horse',
    });
    const submitPromise = fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/auth/login`)
      .flush({ accessToken: 'access-123', refreshToken: 'refresh-123', expiresInSeconds: 900 });
    await tick();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/me`)
      .flush({ code: 'SERVER_ERROR', message: 'boom', requestId: 'r-1' }, { status: 500, statusText: 'Server Error' });
    await tick();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/me/permissions`)
      .flush({ permissions: [] });
    await submitPromise;

    expect(navigateSpy).not.toHaveBeenCalled();
    expect(fixture.componentInstance.error()).toContain('servidor');
  });
});