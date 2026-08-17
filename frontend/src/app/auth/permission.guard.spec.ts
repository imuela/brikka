import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, provideRouter } from '@angular/router';

import { permissionGuard } from './permission.guard';
import { SessionStore } from '../core/session/session.store';

describe('permissionGuard', () => {
  let sessionStore: SessionStore;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    sessionStore = TestBed.inject(SessionStore);
    router = TestBed.inject(Router);
  });

  it('allows navigation when no permission is required', () => {
    const result = TestBed.runInInjectionContext(() =>
      permissionGuard({ data: {} } as never, {} as never),
    );
    expect(result).toBe(true);
  });

  it('allows navigation when the session has the required permission', () => {
    sessionStore.setPermissions(['CASE_READ']);

    const result = TestBed.runInInjectionContext(() =>
      permissionGuard({ data: { permission: 'CASE_READ' } } as never, {} as never),
    );

    expect(result).toBe(true);
  });

  it('redirects to /app/forbidden when the permission is missing', () => {
    sessionStore.setPermissions([]);

    const result = TestBed.runInInjectionContext(() =>
      permissionGuard({ data: { permission: 'COMPANY_DELETE' } } as never, {} as never),
    );

    expect(result).toBeInstanceOf(UrlTree);
    expect(router.serializeUrl(result as UrlTree)).toBe('/app/forbidden');
  });
});
