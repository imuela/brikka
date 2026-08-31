import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { Simulation } from '../financing.model';
import { CreateSimulationDialogComponent } from './create-simulation-dialog.component';

const fixedSimulation: Simulation = {
  id: 's1',
  caseId: 'k1',
  principal: 200000,
  interestRate: 3.2,
  termMonths: 300,
  estimatedPayment: 969.32,
  interestType: 'FIXED',
  baseInterestRate: 3.5,
  finalInterestRate: 3.2,
  euriborRate: null,
  spreadRate: null,
  fixedPeriodMonths: null,
  fixedPeriodRate: null,
  icoGuarantee: false,
  bonifications: [{ code: 'PAYROLL', label: 'Domiciliación de nómina', rate: 0.3, active: true }],
  variablePhase: null,
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

  it('submits a FIXED simulation with its bonifications and shows the computed result', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateSimulationDialogComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.form.patchValue({ principal: '200000', termMonths: '300', fixedRate: '3.5' });
    component.addBonification('PAYROLL');
    component.bonifications.at(0).patchValue({ rate: '0.30', active: true });

    // client-side preview (trivial arithmetic, not a payment calc)
    expect(component.baseRatePreview()).toBe(3.5);
    expect(component.finalRatePreview()).toBeCloseTo(3.2, 5);

    component.submit();
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/simulations`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      interestType: 'FIXED',
      principal: 200000,
      termMonths: 300,
      fixedRate: 3.5,
      euriborRate: null,
      spreadRate: null,
      fixedPeriodMonths: null,
      fixedPeriodRate: null,
      bonifications: [
        { code: 'PAYROLL', label: 'Domiciliación de nómina', rate: 0.3, active: true },
      ],
      icoGuarantee: false,
      metadata: {},
    });
    req.flush(fixedSimulation);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Simulación creada.');
    expect(text).toContain('Cuota estimada');
    expect(dialogRef.close).not.toHaveBeenCalled();

    component.close();
    expect(dialogRef.close).toHaveBeenCalledWith(fixedSimulation);
  });

  it('adapts the fields to VARIABLE and sends euribor + spread', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateSimulationDialogComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.form.controls.interestType.setValue('VARIABLE');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Euríbor (%)');
    expect(fixture.nativeElement.textContent).not.toContain('Tipo fijo (%)');

    component.form.patchValue({
      principal: '180000',
      termMonths: '360',
      euriborRate: '2.10',
      spreadRate: '0.99',
    });
    expect(component.baseRatePreview()).toBeCloseTo(3.09, 5);

    component.submit();
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/simulations`);
    expect(req.request.body).toMatchObject({
      interestType: 'VARIABLE',
      principal: 180000,
      termMonths: 360,
      euriborRate: 2.1,
      spreadRate: 0.99,
      fixedRate: null,
    });
    req.flush({ ...fixedSimulation, interestType: 'VARIABLE' });
  });

  it('adapts the fields to MIXED and shows the variable-phase payment in the result', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateSimulationDialogComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.form.controls.interestType.setValue('MIXED');
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Meses del tramo fijo');
    expect(fixture.nativeElement.textContent).toContain('Euríbor (%)');

    component.form.patchValue({
      principal: '220000',
      termMonths: '360',
      fixedPeriodMonths: '120',
      fixedPeriodRate: '2.80',
      euriborRate: '2.00',
      spreadRate: '0.80',
    });
    component.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/simulations`);
    expect(req.request.body).toMatchObject({
      interestType: 'MIXED',
      fixedPeriodMonths: 120,
      fixedPeriodRate: 2.8,
      euriborRate: 2,
      spreadRate: 0.8,
    });
    req.flush({
      ...fixedSimulation,
      interestType: 'MIXED',
      variablePhase: {
        baseInterestRate: 2.8,
        finalInterestRate: 2.6,
        outstandingBalanceAtSwitch: 170000,
        monthlyPayment: 912.34,
      },
    });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('tramo variable');
  });

  it('sends icoGuarantee true when the box is checked', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateSimulationDialogComponent);
    fixture.detectChanges();
    const component = fixture.componentInstance;

    component.form.patchValue({
      principal: '90000',
      termMonths: '240',
      fixedRate: '3.0',
      icoGuarantee: true,
    });
    component.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/simulations`);
    expect(req.request.body).toMatchObject({ icoGuarantee: true });
    req.flush(fixedSimulation);
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

  it('shows the backend validation error', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreateSimulationDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.patchValue({
      principal: '100000',
      termMonths: '240',
      fixedRate: '3.0',
    });
    fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/simulations`)
      .flush(
        {
          code: 'SIMULATION_INTEREST_MODEL_MISMATCH',
          message: 'mismatch',
          requestId: 'r1',
        },
        { status: 400, statusText: 'Bad Request' },
      );

    expect(fixture.componentInstance.error()).toBe(
      'Los datos del tipo de interés no encajan con el tipo elegido (revisa euríbor, diferencial o tramo fijo).',
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
