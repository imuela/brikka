import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { SessionStore } from '../../../core/session/session.store';
import { CaseListComponent } from './case-list.component';

describe('CaseListComponent', () => {
  let httpMock: HttpTestingController;
  let sessionStore: SessionStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CaseListComponent],
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

  it('renders the cases returned by the API', () => {
    const fixture = TestBed.createComponent(CaseListComponent);
    fixture.detectChanges();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases`)
      .flush([
        {
          id: 'k1',
          companyId: 'co1',
          reference: 'REF-1',
          status: 'PRESTUDY',
          operationType: 'MORTGAGE',
          createdBy: 'u1',
          createdAt: '2026-08-17T10:00:00Z',
          requestedAmount: null,
          description: null,
          cancelledAt: null,
        },
      ]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('REF-1');
    expect(fixture.nativeElement.textContent).toContain('MORTGAGE');
  });

  it('shows an error message when the list request fails', () => {
    const fixture = TestBed.createComponent(CaseListComponent);
    fixture.detectChanges();
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases`)
      .flush({ code: 'FORBIDDEN', message: 'Access denied.', requestId: 'r1' }, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No tienes permisos para realizar esta acción.');
  });

  it('hides "Nuevo caso" without CASE_CREATE and shows it once granted', () => {
    const fixture = TestBed.createComponent(CaseListComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases`).flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Nuevo caso');

    sessionStore.setPermissions(['CASE_CREATE']);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nuevo caso');
  });
});
