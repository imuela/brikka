import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { SessionStore } from '../../../core/session/session.store';
import { ClientDetailComponent } from './client-detail.component';

describe('ClientDetailComponent', () => {
  let httpMock: HttpTestingController;
  let sessionStore: SessionStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ClientDetailComponent],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: { snapshot: { paramMap: convertToParamMap({ id: 'c1' }) } },
        },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    sessionStore = TestBed.inject(SessionStore);
  });

  afterEach(() => httpMock.verify());

  it('shows the client fetched from the backend', () => {
    const fixture = TestBed.createComponent(ClientDetailComponent);
    fixture.detectChanges();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/clients/c1`)
      .flush({ id: 'c1', companyId: 'co1', firstName: 'Ada', lastName: 'Lovelace', email: 'ada@brika.test', phone: '600000000', status: 'ACTIVE' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Ada Lovelace');
    expect(fixture.nativeElement.textContent).toContain('ada@brika.test');
  });

  it('hides the "Editar" link without CLIENT_UPDATE and shows it once granted', () => {
    const fixture = TestBed.createComponent(ClientDetailComponent);
    fixture.detectChanges();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/clients/c1`)
      .flush({ id: 'c1', companyId: 'co1', firstName: 'Ada', lastName: 'Lovelace', email: 'ada@brika.test', phone: '600000000', status: 'ACTIVE' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Editar');

    sessionStore.setPermissions(['CLIENT_UPDATE']);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Editar');
  });

  it('shows the not-found error for a client in another tenant', () => {
    const fixture = TestBed.createComponent(ClientDetailComponent);
    fixture.detectChanges();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/clients/c1`)
      .flush({ code: 'CLIENT_NOT_FOUND', message: 'Client not found.', requestId: 'r1' }, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No se ha encontrado el cliente solicitado.');
  });
});
