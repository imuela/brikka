import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { portalAuthGuard } from './portal-auth.guard';
import { PortalAuthService } from './portal-auth.service';

describe('portalAuthGuard', () => {
  let portalAuthService: PortalAuthService;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    portalAuthService = TestBed.inject(PortalAuthService);
    router = TestBed.inject(Router);
  });

  it('allows navigation when authenticated', () => {
    vi.spyOn(portalAuthService, 'isAuthenticated').mockReturnValue(true);

    const result = TestBed.runInInjectionContext(() =>
      portalAuthGuard({} as never, { url: '/portal' } as never),
    );

    expect(result).toBe(true);
  });

  it('redirects to /portal/login with returnUrl when not authenticated', () => {
    vi.spyOn(portalAuthService, 'isAuthenticated').mockReturnValue(false);

    const result = TestBed.runInInjectionContext(() =>
      portalAuthGuard({} as never, { url: '/portal/cases/c1' } as never),
    );

    expect(result).toBeInstanceOf(UrlTree);
    const tree = result as UrlTree;
    expect(router.serializeUrl(tree)).toBe('/portal/login?returnUrl=%2Fportal%2Fcases%2Fc1');
  });
});
