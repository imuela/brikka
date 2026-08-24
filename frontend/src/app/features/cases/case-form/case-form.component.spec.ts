import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { CaseFormComponent } from './case-form.component';

function configureWithRouteParam(id: string | null) {
  TestBed.configureTestingModule({
    imports: [CaseFormComponent, NoopAnimationsModule],
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

const theCase = {
  id: 'k1',
  companyId: 'co1',
  reference: 'REF-1',
  status: 'PRESTUDY',
  operationType: 'PURCHASE',
  createdBy: 'u1',
  createdAt: '2026-08-17T10:00:00Z',
  requestedAmount: null,
    description: null,
    cancelledAt: null,
};

describe('CaseFormComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => httpMock.verify());

  it('create mode posts the form and navigates to the new case on success', () => {
    configureWithRouteParam(null);
    httpMock = TestBed.inject(HttpTestingController);
    const router = TestBed.inject(Router);
    const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    const fixture = TestBed.createComponent(CaseFormComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.isEditMode).toBe(false);

    fixture.componentInstance.form.setValue({
      operationType: 'PURCHASE',
      requestedAmount: 250000,
      description: 'refinance',
    });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases`);
    expect(req.request.method).toBe('POST');
    req.flush(theCase);

    expect(navigateSpy).toHaveBeenCalledWith(['/app/cases', 'k1']);
  });

  it('edit mode loads the existing case then PATCHes on submit', () => {
    configureWithRouteParam('k1');
    httpMock = TestBed.inject(HttpTestingController);
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const fixture = TestBed.createComponent(CaseFormComponent);
    fixture.detectChanges();
    expect(fixture.componentInstance.isEditMode).toBe(true);

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1`).flush(theCase);
    fixture.detectChanges();

    expect(fixture.componentInstance.form.value.operationType).toBe('PURCHASE');

    fixture.componentInstance.submit();
    const patchReq = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1`);
    expect(patchReq.request.method).toBe('PATCH');
    patchReq.flush(theCase);
  });

  it('does not submit an invalid form', () => {
    configureWithRouteParam(null);
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CaseFormComponent);
    fixture.detectChanges();

    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/cases`);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('shows a mat-error explaining why an invalid field is invalid (Sprint 36: D36-1b, a silently-blocked submit gave no visible reason)', () => {
    configureWithRouteParam(null);
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CaseFormComponent);
    fixture.detectChanges();

    const operationType = fixture.componentInstance.form.controls.operationType;
    operationType.markAsTouched();
    fixture.detectChanges();

    const errorText = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(errorText).toContain('Selecciona un tipo de operación.');
  });

  it('shows the backend error message when the request fails', () => {
    configureWithRouteParam(null);
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CaseFormComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({
      operationType: 'PURCHASE',
      requestedAmount: 250000,
      description: 'refinance',
    });
    fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases`)
      .flush({ code: 'VALIDATION_ERROR', message: 'Invalid operation type.', requestId: 'r1' }, { status: 400, statusText: 'Bad Request' });

    expect(fixture.componentInstance.error()).toBe('No se han podido guardar los cambios. Revisa los datos introducidos.');
  });
});
