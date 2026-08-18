import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { UserFormComponent } from './user-form.component';

function configureWithRouteParam(id: string | null) {
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
});
