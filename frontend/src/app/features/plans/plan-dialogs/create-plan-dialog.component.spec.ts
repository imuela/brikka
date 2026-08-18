import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { Plan } from '../plan.model';
import { CreatePlanDialogComponent } from './create-plan-dialog.component';

const createdPlan: Plan = { id: 'p1', code: 'PRO', name: 'Plan Pro', status: 'ACTIVE' };

function configure() {
  const dialogRef = { close: vi.fn() };
  TestBed.configureTestingModule({
    imports: [CreatePlanDialogComponent, NoopAnimationsModule],
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      { provide: MatDialogRef, useValue: dialogRef },
    ],
  });
  return dialogRef;
}

describe('CreatePlanDialogComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => httpMock.verify());

  it('submits and closes with the returned plan', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreatePlanDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ code: 'PRO', name: 'Plan Pro', status: 'ACTIVE' });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/plans`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ code: 'PRO', name: 'Plan Pro', status: 'ACTIVE' });
    req.flush(createdPlan);

    expect(dialogRef.close).toHaveBeenCalledWith(createdPlan);
  });

  it('does not submit with missing required fields', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreatePlanDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.patchValue({ code: '', name: '' });
    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/plans`);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('shows the backend error when the request fails', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreatePlanDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ code: 'PRO', name: 'Plan Pro', status: 'ACTIVE' });
    fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/plans`)
      .flush(
        { code: 'VALIDATION_ERROR', message: 'Invalid code.', requestId: 'r1' },
        { status: 400, statusText: 'Bad Request' },
      );

    expect(fixture.componentInstance.error()).toBe(
      'No se han podido guardar los cambios. Revisa los datos introducidos.',
    );
  });

  it('cancel() closes the dialog without a result', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(CreatePlanDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();

    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
