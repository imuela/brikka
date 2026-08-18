import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { SessionStore } from '../../../core/session/session.store';
import { Company } from '../company.model';
import { CompanyListComponent } from './company-list.component';

const company: Company = {
  id: 'co1',
  legalName: 'Brika Demo SL',
  tradeName: 'Brika',
  taxId: 'B12345678',
  status: 'ACTIVE',
};

describe('CompanyListComponent', () => {
  let httpMock: HttpTestingController;
  let sessionStore: SessionStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CompanyListComponent],
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

  it('loads and renders the company list', () => {
    const fixture = TestBed.createComponent(CompanyListComponent);
    fixture.detectChanges();

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/companies`).flush([company]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Brika Demo SL');
  });

  it('gates "Nueva empresa" by COMPANY_CREATE', () => {
    const fixture = TestBed.createComponent(CompanyListComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/companies`).flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Nueva empresa');

    sessionStore.setPermissions(['COMPANY_CREATE']);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nueva empresa');
  });

  it('renders exactly one row when the backend returns only the caller own company (MANAGER)', () => {
    const fixture = TestBed.createComponent(CompanyListComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/companies`).flush([company]);
    fixture.detectChanges();

    expect(fixture.componentInstance.companies()!.length).toBe(1);
  });
});
