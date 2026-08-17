import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { authGuard } from './auth.guard';
import { AuthService } from './auth.service';

describe('authGuard', () => {
  let authService: AuthService;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    authService = TestBed.inject(AuthService);
    router = TestBed.inject(Router);
  });

  it('allows navigation when authenticated', () => {
    vi.spyOn(authService, 'isAuthenticated').mockReturnValue(true);

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as never, { url: '/app' } as never),
    );

    expect(result).toBe(true);
  });

  it('redirects to /login with returnUrl when not authenticated', () => {
    vi.spyOn(authService, 'isAuthenticated').mockReturnValue(false);

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as never, { url: '/app/dashboard' } as never),
    );

    expect(result).toBeInstanceOf(UrlTree);
    const tree = result as UrlTree;
    expect(router.serializeUrl(tree)).toBe('/login?returnUrl=%2Fapp%2Fdashboard');
  });
});
