import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { CompanyFormComponent } from './company-form.component';

function configureWithRouteParam(id: string | null) {
  TestBed.configureTestingModule({
    imports: [CompanyFormComponent],
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

const company = {
  id: 'co1',
  legalName: 'Brika Demo SL',
  tradeName: 'Brika',
  taxId: 'B12345678',
  status: 'ACTIVE',
};

describe('CompanyFormComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => httpMock.verify());

  it('create mode posts the form and navigates to the new company detail on success', () => {
    configureWithRouteParam(null);
    httpMock = TestBed.inject(HttpTestingController);
    const navigateSpy = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const fixture = TestBed.createComponent(CompanyFormComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.isEditMode).toBe(false);

    fixture.componentInstance.form.setValue({
      legalName: 'Brika Demo SL',
      tradeName: 'Brika',
      taxId: 'B12345678',
    });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/companies`);
    expect(req.request.method).toBe('POST');
    req.flush(company);

    expect(navigateSpy).toHaveBeenCalledWith(['/app/companies', 'co1']);
  });

  it('edit mode loads the existing company then PATCHes on submit', () => {
    configureWithRouteParam('co1');
    httpMock = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const fixture = TestBed.createComponent(CompanyFormComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.isEditMode).toBe(true);

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/companies/co1`).flush(company);
    fixture.detectChanges();

    expect(fixture.componentInstance.form.value.legalName).toBe('Brika Demo SL');

    fixture.componentInstance.submit();
    const patchReq = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/companies/co1`);
    expect(patchReq.request.method).toBe('PATCH');
    patchReq.flush(company);
  });

  it('does not submit an invalid form', () => {
    configureWithRouteParam(null);
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CompanyFormComponent);
    fixture.detectChanges();
    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/companies`);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('shows the backend error message when the request fails', () => {
    configureWithRouteParam(null);
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CompanyFormComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({
      legalName: 'Brika Demo SL',
      tradeName: 'Brika',
      taxId: 'B12345678',
    });
    fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/companies`)
      .flush(
        { code: 'VALIDATION_ERROR', message: 'Invalid tax id.', requestId: 'r1' },
        { status: 400, statusText: 'Bad Request' },
      );

    expect(fixture.componentInstance.error()).toBe(
      'No se han podido guardar los cambios. Revisa los datos introducidos.',
    );
  });
});
