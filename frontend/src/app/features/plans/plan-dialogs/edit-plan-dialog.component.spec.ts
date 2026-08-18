import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { Plan } from '../plan.model';
import { EditPlanDialogComponent } from './edit-plan-dialog.component';

const plan: Plan = { id: 'p1', code: 'PRO', name: 'Plan Pro', status: 'ACTIVE' };

function configure() {
  const dialogRef = { close: vi.fn() };
  TestBed.configureTestingModule({
    imports: [EditPlanDialogComponent, NoopAnimationsModule],
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      { provide: MatDialogRef, useValue: dialogRef },
      { provide: MAT_DIALOG_DATA, useValue: { plan } },
    ],
  });
  return dialogRef;
}

describe('EditPlanDialogComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => httpMock.verify());

  it('prefills the form with the existing plan values', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(EditPlanDialogComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.form.value).toEqual({ name: 'Plan Pro', status: 'ACTIVE' });
  });

  it('submits and closes with the returned plan', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(EditPlanDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ name: 'Plan Pro Plus', status: 'ACTIVE' });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/plans/p1`);
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ name: 'Plan Pro Plus', status: 'ACTIVE' });
    req.flush({ ...plan, name: 'Plan Pro Plus' });

    expect(dialogRef.close).toHaveBeenCalledWith({ ...plan, name: 'Plan Pro Plus' });
  });

  it('cancel() closes the dialog without a result', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(EditPlanDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();

    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
