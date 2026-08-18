import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { ClientFormComponent } from './client-form.component';

function configureWithRouteParam(id: string | null) {
  TestBed.configureTestingModule({
    imports: [ClientFormComponent, NoopAnimationsModule],
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

describe('ClientFormComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => httpMock.verify());

  it('create mode posts the form and navigates to the new client on success', () => {
    configureWithRouteParam(null);
    httpMock = TestBed.inject(HttpTestingController);
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    const fixture = TestBed.createComponent(ClientFormComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.isEditMode).toBe(false);

    fixture.componentInstance.form.setValue({
      firstName: 'Ada',
      lastName: 'Lovelace',
      email: 'ada@brika.test',
      phone: '600000000',
    });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/clients`);
    expect(req.request.method).toBe('POST');
    req.flush({ id: 'c1', companyId: 'co1', firstName: 'Ada', lastName: 'Lovelace', email: 'ada@brika.test', phone: '600000000', status: 'ACTIVE' });

    expect(navigateSpy).toHaveBeenCalledWith(['/app/clients', 'c1']);
  });

  it('edit mode loads the existing client then PATCHes on submit', () => {
    configureWithRouteParam('c1');
    httpMock = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const fixture = TestBed.createComponent(ClientFormComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.isEditMode).toBe(true);

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/clients/c1`)
      .flush({ id: 'c1', companyId: 'co1', firstName: 'Ada', lastName: 'Lovelace', email: 'ada@brika.test', phone: '600000000', status: 'ACTIVE' });
    fixture.detectChanges();

    expect(fixture.componentInstance.form.value.firstName).toBe('Ada');

    fixture.componentInstance.submit();
    const patchReq = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/clients/c1`);
    expect(patchReq.request.method).toBe('PATCH');
    patchReq.flush({ id: 'c1', companyId: 'co1', firstName: 'Ada', lastName: 'Lovelace', email: 'ada@brika.test', phone: '600000000', status: 'ACTIVE' });
  });

  it('does not submit an invalid form', () => {
    configureWithRouteParam(null);
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(ClientFormComponent);
    fixture.detectChanges();

    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/clients`);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('shows the backend error message when the request fails', () => {
    configureWithRouteParam(null);
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(ClientFormComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({
      firstName: 'Ada',
      lastName: 'Lovelace',
      email: 'ada@brika.test',
      phone: '600000000',
    });
    fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/clients`)
      .flush({ code: 'VALIDATION_ERROR', message: 'Invalid email.', requestId: 'r1' }, { status: 400, statusText: 'Bad Request' });

    expect(fixture.componentInstance.error()).toBe('No se han podido guardar los cambios. Revisa los datos introducidos.');
  });
});
