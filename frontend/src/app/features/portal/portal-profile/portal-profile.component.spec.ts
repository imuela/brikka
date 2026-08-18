import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { PortalSessionStore } from '../../../portal-auth/portal-session.store';
import { PortalProfileComponent } from './portal-profile.component';

const me = {
  clientId: 'cl1',
  firstName: 'Ada',
  lastName: 'Client',
  email: 'ada@client.test',
  phone: '600000000',
  accountStatus: 'ACTIVE',
  lastLoginAt: null,
};

describe('PortalProfileComponent', () => {
  let httpMock: HttpTestingController;
  let sessionStore: PortalSessionStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PortalProfileComponent],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    sessionStore = TestBed.inject(PortalSessionStore);
    sessionStore.setClient(me);
  });

  afterEach(() => httpMock.verify());

  it('prefills the form with the current session values', () => {
    const fixture = TestBed.createComponent(PortalProfileComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.form.value).toEqual({
      email: 'ada@client.test',
      phone: '600000000',
    });
  });

  it('submit PATCHes the profile and updates the session store', () => {
    const fixture = TestBed.createComponent(PortalProfileComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ email: 'new@client.test', phone: '611111111' });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/portal/profile`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ email: 'new@client.test', phone: '611111111' });
    req.flush({ ...me, email: 'new@client.test', phone: '611111111' });

    expect(sessionStore.client()?.email).toBe('new@client.test');
    expect(fixture.componentInstance.saved()).toBe(true);
  });

  it('does not submit an invalid form', () => {
    const fixture = TestBed.createComponent(PortalProfileComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.patchValue({ email: 'not-an-email' });
    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/portal/profile`);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('shows the backend error message when the update fails', () => {
    const fixture = TestBed.createComponent(PortalProfileComponent);
    fixture.detectChanges();
    fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/portal/profile`)
      .flush(
        { code: 'VALIDATION_ERROR', message: 'bad', requestId: 'r1' },
        { status: 400, statusText: 'Bad Request' },
      );

    expect(fixture.componentInstance.error()).toBe(
      'No se han podido guardar los cambios. Revisa los datos introducidos.',
    );
  });
});
