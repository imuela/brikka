import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';

import { environment } from '../../../environments/environment';
import { AuthService } from '../../auth/auth.service';
import { errorInterceptor } from './error.interceptor';
import { ApiError } from './api-error';

describe('errorInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authService: AuthService;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  afterEach(() => httpMock.verify());

  it('on 401, clears the session and redirects to /login without assuming a JSON body', async () => {
    const clearSpy = vi.spyOn(authService, 'clearSession');
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    let caught: ApiError | undefined;
    http.get(`${environment.apiBaseUrl}/api/v1/me`).subscribe({
      error: (err: ApiError) => (caught = err),
    });

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/me`)
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(clearSpy).toHaveBeenCalled();
    expect(navigateSpy).toHaveBeenCalledWith(['/login']);
    expect(caught?.status).toBe(401);
  });

  it('normalizes a standard {code,message,requestId} error body', () => {
    let caught: ApiError | undefined;
    http.get(`${environment.apiBaseUrl}/api/v1/companies/x`).subscribe({
      error: (err: ApiError) => (caught = err),
    });

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/companies/x`)
      .flush(
        { code: 'COMPANY_NOT_FOUND', message: 'Company not found.', requestId: 'req-1' },
        { status: 404, statusText: 'Not Found' },
      );

    expect(caught).toEqual({
      status: 404,
      code: 'COMPANY_NOT_FOUND',
      message: 'Company not found.',
      requestId: 'req-1',
    });
  });
});
