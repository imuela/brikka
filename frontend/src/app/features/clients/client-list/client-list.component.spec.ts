import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { SessionStore } from '../../../core/session/session.store';
import { ClientListComponent } from './client-list.component';

describe('ClientListComponent', () => {
  let httpMock: HttpTestingController;
  let sessionStore: SessionStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ClientListComponent],
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

  it('renders the clients returned by the API', () => {
    const fixture = TestBed.createComponent(ClientListComponent);
    fixture.detectChanges();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/clients`)
      .flush([
        { id: 'c1', companyId: 'co1', firstName: 'Ada', lastName: 'Lovelace', email: 'a@b.test', phone: '1', documentType: null, documentNumber: null, dateOfBirth: null, nationality: null, address: null, employmentStatus: null, status: 'ACTIVE' },
      ]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Ada');
    expect(fixture.nativeElement.textContent).toContain('Lovelace');
  });

  it('shows an error message when the list request fails', () => {
    const fixture = TestBed.createComponent(ClientListComponent);
    fixture.detectChanges();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/clients`)
      .flush({ code: 'FORBIDDEN', message: 'Access denied.', requestId: 'r1' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No tienes permisos para realizar esta acción.');
  });

  it('hides "Nuevo cliente" without CLIENT_CREATE and shows it once granted', () => {
    const fixture = TestBed.createComponent(ClientListComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/clients`).flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Nuevo cliente');

    sessionStore.setPermissions(['CLIENT_CREATE']);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nuevo cliente');
  });

  it('hides "Nuevo cliente" for a GLOBAL SUPERADMIN even with CLIENT_CREATE', () => {
    const fixture = TestBed.createComponent(ClientListComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/clients`).flush([]);
    fixture.detectChanges();

    sessionStore.setPermissions(['CLIENT_CREATE']);
    sessionStore.setUser({
      id: 's1',
      email: 's@brika.test',
      role: 'SUPERADMIN',
      companyId: null,
      entitlements: {},
    });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Nuevo cliente');
  });
});
