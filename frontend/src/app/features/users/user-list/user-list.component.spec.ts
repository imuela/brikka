import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { SessionStore } from '../../../core/session/session.store';
import { ConfirmDialogComponent } from '../../../shared/dialogs/confirm-dialog.component';
import { User } from '../user.model';
import { UserListComponent } from './user-list.component';

const user: User = {
  id: 'u1',
  companyId: 'co1',
  email: 'broker@brika.test',
  firstName: 'Demo',
  lastName: 'Broker',
  role: 'BROKER',
  status: 'ACTIVE',
};

describe('UserListComponent', () => {
  let httpMock: HttpTestingController;
  let sessionStore: SessionStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [UserListComponent],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    sessionStore = TestBed.inject(SessionStore);
  });

  afterEach(() => httpMock.verify());

  it('loads and renders the user list', () => {
    const fixture = TestBed.createComponent(UserListComponent);
    fixture.detectChanges();

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`).flush([user]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Demo Broker');
    expect(fixture.nativeElement.textContent).toContain('broker@brika.test');
  });

  it('shows the friendly error message when the backend denies access (e.g. SUPERADMIN without SUPPORT_SESSION)', () => {
    const fixture = TestBed.createComponent(UserListComponent);
    fixture.detectChanges();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/users`)
      .flush(
        { code: 'FORBIDDEN', message: 'Access denied.', requestId: 'r1' },
        { status: 403, statusText: 'Forbidden' },
      );
    fixture.detectChanges();

    expect(fixture.componentInstance.error()).toBe('No tienes permisos para realizar esta acción.');
  });

  it('gates "Nuevo usuario" by USER_CREATE and row actions by USER_UPDATE/USER_DISABLE', () => {
    const fixture = TestBed.createComponent(UserListComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`).flush([user]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Nuevo usuario');
    expect(fixture.nativeElement.querySelector('[aria-label="Editar usuario"]')).toBeNull();
    expect(fixture.nativeElement.querySelector('[aria-label="Deshabilitar usuario"]')).toBeNull();

    sessionStore.setPermissions(['USER_READ', 'USER_CREATE', 'USER_UPDATE', 'USER_DISABLE']);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nuevo usuario');
    expect(fixture.nativeElement.querySelector('[aria-label="Editar usuario"]')).not.toBeNull();
    expect(
      fixture.nativeElement.querySelector('[aria-label="Deshabilitar usuario"]'),
    ).not.toBeNull();
  });

  it('hides "Deshabilitar" for an already-disabled user', () => {
    sessionStore.setPermissions(['USER_READ', 'USER_DISABLE']);
    const fixture = TestBed.createComponent(UserListComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`).flush([{ ...user, status: 'DISABLED' }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('[aria-label="Deshabilitar usuario"]')).toBeNull();
  });

  it('disable() opens a confirmation dialog and reloads the list when confirmed', () => {
    const fixture = TestBed.createComponent(UserListComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`).flush([user]);
    fixture.detectChanges();

    const dialog = TestBed.inject(MatDialog);
    const openSpy = vi
      .spyOn(dialog, 'open')
      .mockReturnValue({ afterClosed: () => of(true) } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.disable(user);

    expect(openSpy).toHaveBeenCalledWith(ConfirmDialogComponent, expect.objectContaining({ width: '400px' }));
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users/u1/disable`).flush({ ...user, status: 'DISABLED' });
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`).flush([{ ...user, status: 'DISABLED' }]);
  });

  it('disable() does nothing when the confirmation is cancelled', () => {
    const fixture = TestBed.createComponent(UserListComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`).flush([user]);
    fixture.detectChanges();

    const dialog = TestBed.inject(MatDialog);
    vi.spyOn(dialog, 'open').mockReturnValue({ afterClosed: () => of(false) } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.disable(user);

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/users/u1/disable`);
  });
});
