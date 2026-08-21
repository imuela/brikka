import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { SessionStore } from '../../../core/session/session.store';
import { UserFormComponent } from './user-form.component';

function configureWithRouteParam(id: string | null, role: 'BROKER' | 'SUPERADMIN' = 'BROKER') {
  TestBed.configureTestingModule({
    imports: [UserFormComponent, NoopAnimationsModule],
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: convertToParamMap(id ? { id } : {}) } },
      },
    ],
  });
  TestBed.inject(SessionStore).setUser({
    id: 'caller-1',
    email: 'caller@brika.test',
    role,
    companyId: role === 'SUPERADMIN' ? null : 'caller-co',
    entitlements: {},
  });
}

const user = {
  id: 'u1',
  companyId: 'co1',
  email: 'broker@brika.test',
  firstName: 'Demo',
  lastName: 'Broker',
  role: 'BROKER',
  status: 'ACTIVE',
};

describe('UserFormComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => httpMock.verify());

  it('create mode posts the full CreateUserApiRequest and navigates to the list on success', () => {
    configureWithRouteParam(null);
    httpMock = TestBed.inject(HttpTestingController);
    const navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const fixture = TestBed.createComponent(UserFormComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.isEditMode).toBe(false);

    fixture.componentInstance.form.setValue({
      email: 'broker@brika.test',
      firstName: 'Demo',
      lastName: 'Broker',
      role: 'BROKER',
      externalIdentityId: 'ext-1',
      companyId: '',
    });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      email: 'broker@brika.test',
      firstName: 'Demo',
      lastName: 'Broker',
      role: 'BROKER',
      externalIdentityId: 'ext-1',
    });
    req.flush(user);

    expect(navigateSpy).toHaveBeenCalledWith(['/app/users']);
  });

  it('edit mode loads the existing user, disables create-only fields, then PATCHes only firstName/lastName', () => {
    configureWithRouteParam('u1');
    httpMock = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const fixture = TestBed.createComponent(UserFormComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.isEditMode).toBe(true);

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users/u1`).flush(user);
    fixture.detectChanges();

    expect(fixture.componentInstance.form.controls.email.disabled).toBe(true);
    expect(fixture.componentInstance.form.controls.role.disabled).toBe(true);
    expect(fixture.componentInstance.form.getRawValue().firstName).toBe('Demo');

    fixture.componentInstance.form.patchValue({ firstName: 'Demo Updated' });
    fixture.componentInstance.submit();

    const patchReq = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users/u1`);
    expect(patchReq.request.method).toBe('PATCH');
    expect(patchReq.request.body).toEqual({ firstName: 'Demo Updated', lastName: 'Broker' });
    patchReq.flush(user);
  });

  it('does not submit an invalid form', () => {
    configureWithRouteParam(null);
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(UserFormComponent);
    fixture.detectChanges();
    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/users`);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('shows the backend error message when creation fails', () => {
    configureWithRouteParam(null);
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(UserFormComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({
      email: 'broker@brika.test',
      firstName: 'Demo',
      lastName: 'Broker',
      role: 'BROKER',
      externalIdentityId: 'ext-1',
      companyId: '',
    });
    fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/users`)
      .flush(
        { code: 'INVALID_ROLE_ASSIGNMENT', message: 'bad role', requestId: 'r1' },
        { status: 400, statusText: 'Bad Request' },
      );

    expect(fixture.componentInstance.error()).toBe('No es posible asignar ese rol en esta operación.');
  });

  it('SUPERADMIN sees a required company picker and companyId reaches the request body', () => {
    configureWithRouteParam(null, 'SUPERADMIN');
    httpMock = TestBed.inject(HttpTestingController);
    const navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const fixture = TestBed.createComponent(UserFormComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.isSuperadmin).toBe(true);

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/companies`)
      .flush([{ id: 'co-9', legalName: 'Co 9', tradeName: 'Co 9', taxId: 'T9', status: 'ACTIVE' }]);

    expect(fixture.componentInstance.form.controls.companyId.hasError('required')).toBe(true);

    fixture.componentInstance.form.setValue({
      email: 'new@brika.test',
      firstName: 'New',
      lastName: 'User',
      role: 'BROKER',
      externalIdentityId: 'ext-2',
      companyId: 'co-9',
    });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/users`);
    expect(req.request.body.companyId).toBe('co-9');
    req.flush({ ...user, id: 'u2', companyId: 'co-9' });

    expect(navigateSpy).toHaveBeenCalledWith(['/app/users']);
  });
});
