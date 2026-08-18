import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { environment } from '../../../environments/environment';
import { AuthService } from '../../auth/auth.service';
import { authInterceptor } from './auth.interceptor';
import { SKIP_AUTH } from './http-context';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let authService: AuthService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    authService = TestBed.inject(AuthService);
  });

  afterEach(() => httpMock.verify());

  it('attaches the Bearer token to requests targeting the Brika API', () => {
    vi.spyOn(authService, 'accessToken').mockReturnValue('token-123');

    http.get(`${environment.apiBaseUrl}/api/v1/me`).subscribe();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/me`);
    expect(req.request.headers.get('Authorization')).toBe('Bearer token-123');
    req.flush({});
  });

  it('does not attach a header when there is no token', () => {
    vi.spyOn(authService, 'accessToken').mockReturnValue(null);

    http.get(`${environment.apiBaseUrl}/api/v1/me`).subscribe();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/me`);
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('never attaches the token to a request marked SKIP_AUTH', () => {
    vi.spyOn(authService, 'accessToken').mockReturnValue('token-123');

    http.get('http://localhost:18081/realms/brika/protocol/openid-connect/token', {
      context: new HttpContext().set(SKIP_AUTH, true),
    }).subscribe();

    const req = httpMock.expectOne(
      'http://localhost:18081/realms/brika/protocol/openid-connect/token',
    );
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('never attaches the token to a request outside the Brika API origin', () => {
    vi.spyOn(authService, 'accessToken').mockReturnValue('token-123');

    http.get('https://example.test/data').subscribe();

    const req = httpMock.expectOne('https://example.test/data');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('never attaches the internal token to a Portal request (Sprint 19, ADR-PROCESS-007)', () => {
    vi.spyOn(authService, 'accessToken').mockReturnValue('token-123');

    http.get(`${environment.apiBaseUrl}/api/v1/portal/me`).subscribe();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/portal/me`);
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });
});
