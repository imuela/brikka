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

const baseClient = {
  id: 'c1',
  companyId: 'co1',
  firstName: 'Ada',
  lastName: 'Lovelace',
  email: 'ada@brika.test',
  phone: '600000000',
  documentType: null,
  documentNumber: null,
  dateOfBirth: null,
  nationality: null,
  address: null,
  employmentStatus: null,
  status: 'ACTIVE',
};

const baseFormValue = {
  firstName: 'Ada',
  lastName: 'Lovelace',
  email: 'ada@brika.test',
  phone: '600000000',
  documentType: '',
  documentNumber: '',
  dateOfBirth: '',
  nationality: '',
  address: '',
  employmentStatus: '',
  employerName: '',
  yearsEmployed: null,
};

function financialProfile(overrides: Partial<Record<string, unknown>> = {}) {
  return {
    id: 'fp1',
    companyId: 'co1',
    clientId: 'c1',
    maritalStatus: null,
    dependents: null,
    employmentType: null,
    contractType: null,
    employerName: null,
    yearsEmployed: null,
    monthlyIncome: null,
    savings: null,
    otherDebtsMonthlyPayment: null,
    creditCardDebt: null,
    source: 'BROKER',
    status: 'PENDING',
    evidenceDocumentVersionId: null,
    updatedBy: 'u1',
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

describe('ClientFormComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => httpMock.verify());

  it('create mode posts the form and navigates to the new client on success (no financial-profile fields entered → no extra call)', () => {
    configureWithRouteParam(null);
    httpMock = TestBed.inject(HttpTestingController);
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    const fixture = TestBed.createComponent(ClientFormComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.isEditMode).toBe(false);

    fixture.componentInstance.form.setValue(baseFormValue);
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/clients`);
    expect(req.request.method).toBe('POST');
    req.flush(baseClient);

    // Nothing entered for Empresa actual/Antigüedad — no financial-profile call should fire at all.
    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/clients/c1/financial-profile`);
    expect(navigateSpy).toHaveBeenCalledWith(['/app/clients', 'c1']);
  });

  it('create mode with Empresa actual/Antigüedad filled in also creates a minimal financial profile before navigating', () => {
    configureWithRouteParam(null);
    httpMock = TestBed.inject(HttpTestingController);
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    const fixture = TestBed.createComponent(ClientFormComponent);
    fixture.detectChanges();

    fixture.componentInstance.form.setValue({
      ...baseFormValue,
      documentType: 'DNI',
      employmentStatus: 'Empleado',
      employerName: 'Acme Corp',
      yearsEmployed: 3,
    });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/clients`);
    req.flush(baseClient);

    const profileReq = httpMock.expectOne(
      `${environment.apiBaseUrl}/api/v1/clients/c1/financial-profile`,
    );
    expect(profileReq.request.method).toBe('PUT');
    expect(profileReq.request.body).toEqual({
      maritalStatus: null,
      dependents: null,
      employmentType: null,
      contractType: null,
      employerName: 'Acme Corp',
      yearsEmployed: 3,
      monthlyIncome: null,
      savings: null,
      otherDebtsMonthlyPayment: null,
      creditCardDebt: null,
      source: null,
      status: null,
      evidenceDocumentVersionId: null,
    });
    profileReq.flush(financialProfile({ employerName: 'Acme Corp', yearsEmployed: 3 }));

    expect(navigateSpy).toHaveBeenCalledWith(['/app/clients', 'c1']);
  });

  it('edit mode loads the existing client and financial profile, then PATCHes on submit', () => {
    configureWithRouteParam('c1');
    httpMock = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const fixture = TestBed.createComponent(ClientFormComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.isEditMode).toBe(true);

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/clients/c1`).flush(baseClient);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/clients/c1/financial-profile`)
      .flush(
        { code: 'FINANCIAL_PROFILE_NOT_FOUND', message: 'None yet.', requestId: 'r1' },
        { status: 404, statusText: 'Not Found' },
      );
    fixture.detectChanges();

    expect(fixture.componentInstance.form.value.firstName).toBe('Ada');

    fixture.componentInstance.submit();
    const patchReq = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/clients/c1`);
    expect(patchReq.request.method).toBe('PATCH');
    patchReq.flush(baseClient);

    // No financial-profile snapshot and nothing entered — no financial-profile write on submit.
    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/clients/c1/financial-profile`);
  });

  it('edit mode preserves every other financial-profile field when only Antigüedad is changed', () => {
    configureWithRouteParam('c1');
    httpMock = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const fixture = TestBed.createComponent(ClientFormComponent);
    fixture.detectChanges();

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/clients/c1`).flush(baseClient);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/clients/c1/financial-profile`)
      .flush(
        financialProfile({
          maritalStatus: 'Casado',
          monthlyIncome: 2500,
          employerName: 'Acme Corp',
          yearsEmployed: 2,
          source: 'CLIENT',
          status: 'CONFIRMED',
        }),
      );
    fixture.detectChanges();

    expect(fixture.componentInstance.form.value.employerName).toBe('Acme Corp');
    expect(fixture.componentInstance.form.value.yearsEmployed).toBe(2);

    fixture.componentInstance.form.patchValue({ yearsEmployed: 3 });
    fixture.componentInstance.submit();

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/clients/c1`).flush(baseClient);

    const profileReq = httpMock.expectOne(
      `${environment.apiBaseUrl}/api/v1/clients/c1/financial-profile`,
    );
    expect(profileReq.request.method).toBe('PUT');
    expect(profileReq.request.body).toEqual({
      maritalStatus: 'Casado',
      dependents: null,
      employmentType: null,
      contractType: null,
      employerName: 'Acme Corp',
      yearsEmployed: 3,
      monthlyIncome: 2500,
      savings: null,
      otherDebtsMonthlyPayment: null,
      creditCardDebt: null,
      source: 'CLIENT',
      status: 'CONFIRMED',
      evidenceDocumentVersionId: null,
    });
    profileReq.flush(financialProfile({ yearsEmployed: 3 }));
  });

  it('still navigates when the financial-profile sync fails (the client itself already saved successfully)', () => {
    configureWithRouteParam(null);
    httpMock = TestBed.inject(HttpTestingController);
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    const fixture = TestBed.createComponent(ClientFormComponent);
    fixture.detectChanges();

    fixture.componentInstance.form.setValue({ ...baseFormValue, employerName: 'Acme Corp' });
    fixture.componentInstance.submit();

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/clients`).flush(baseClient);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/clients/c1/financial-profile`)
      .flush(
        { code: 'INTERNAL_ERROR', message: 'Boom.', requestId: 'r1' },
        { status: 500, statusText: 'Internal Server Error' },
      );

    expect(navigateSpy).toHaveBeenCalledWith(['/app/clients', 'c1']);
  });

  it('renders documentType and employmentStatus as real dropdowns with the closed catalog values', () => {
    configureWithRouteParam(null);
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(ClientFormComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.documentTypes).toEqual(['DNI', 'NIE', 'PASAPORTE']);
    expect(fixture.componentInstance.employmentStatuses).toEqual([
      'Empleado',
      'Autónomo',
      'Funcionario',
      'Desempleado',
      'Jubilado',
      'Estudiante',
    ]);

    const selects = fixture.nativeElement.querySelectorAll('mat-select');
    expect(selects.length).toBe(2);
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

  it('shows a mat-error explaining why an invalid field is invalid (Sprint 36: D36-1b, a silently-blocked submit gave no visible reason)', () => {
    configureWithRouteParam(null);
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(ClientFormComponent);
    fixture.detectChanges();

    const email = fixture.componentInstance.form.controls.email;
    email.markAsTouched();
    email.setValue('not-an-email');
    fixture.detectChanges();

    const errorText = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(errorText).toContain('Introduce un email válido.');
  });

  it('shows a mat-error when Antigüedad is negative', () => {
    configureWithRouteParam(null);
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(ClientFormComponent);
    fixture.detectChanges();

    const yearsEmployed = fixture.componentInstance.form.controls.yearsEmployed;
    yearsEmployed.markAsTouched();
    yearsEmployed.setValue(-1);
    fixture.detectChanges();

    const errorText = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(errorText).toContain('La antigüedad no puede ser negativa.');
  });

  it('shows the backend error message when the request fails', () => {
    configureWithRouteParam(null);
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(ClientFormComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue(baseFormValue);
    fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/clients`)
      .flush(
        { code: 'VALIDATION_ERROR', message: 'Invalid email.', requestId: 'r1' },
        { status: 400, statusText: 'Bad Request' },
      );

    expect(fixture.componentInstance.error()).toBe(
      'No se han podido guardar los cambios. Revisa los datos introducidos.',
    );
  });
});
