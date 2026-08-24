import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { Simulation } from '../financing.model';
import { CreateSimulationDialogComponent } from './create-simulation-dialog.component';

const createdSimulation: Simulation = {
  id: 's1',
  caseId: 'k1',
  principal: 200000,
  interestRate: 3.5,
  termMonths: 300,
  estimatedPayment: 950.25,
  metadata: {},
  createdBy: 'u1',
  createdAt: '2026-08-17T10:00:00Z',
};

function configure() {
  const dialogRef = { close: vi.fn() };
  TestBed.configureTestingModule({
    imports: [CreateSimulationDialogComponent, NoopAnimationsModule],
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      { provide: MatDialogRef, useValue: dialogRef },
      { provide: MAT_DIALOG_DATA, useValue: { caseId: 'k1' } },
    ],
  });
  return dialogRef;
}

describe('CreateSimulationDialogComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => httpMock.verify());

  it('submits and closes with the returned simulation', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateSimulationDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({
      principal: '200000',
      interestRate: '3.5',
      termMonths: '300',
      estimatedPayment: '950.25',
    });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/simulations`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      principal: 200000,
      interestRate: 3.5,
      termMonths: 300,
      estimatedPayment: 950.25,
      metadata: {},
    });
    req.flush(createdSimulation);

    expect(dialogRef.close).toHaveBeenCalledWith(createdSimulation);
  });

  it('does not submit with missing required fields', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateSimulationDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/cases/k1/simulations`);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('shows a mat-error explaining why an invalid field is invalid (Sprint 36: D36-1b, a silently-blocked submit gave no visible reason)', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateSimulationDialogComponent);
    fixture.detectChanges();

    const principal = fixture.componentInstance.form.controls.principal;
    principal.markAsTouched();
    fixture.detectChanges();

    const errorText = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(errorText).toContain('El importe es obligatorio.');
  });

  it('shows the backend error when the request fails', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateSimulationDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({
      principal: '200000',
      interestRate: '3.5',
      termMonths: '300',
      estimatedPayment: '950.25',
    });
    fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/simulations`)
      .flush(
        { code: 'VALIDATION_ERROR', message: 'Invalid simulation.', requestId: 'r1' },
        { status: 400, statusText: 'Bad Request' },
      );

    expect(fixture.componentInstance.error()).toBe(
      'No se han podido guardar los cambios. Revisa los datos introducidos.',
    );
  });

  it('cancel() closes the dialog without a result', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateSimulationDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();

    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
