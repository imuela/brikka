import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { Plan } from '../../plans/plan.model';
import { CompanySubscription } from '../company.model';
import {
  ChangeSubscriptionDialogComponent,
  ChangeSubscriptionDialogData,
} from './change-subscription-dialog.component';

const plan: Plan = { id: 'p1', code: 'PRO', name: 'Plan Pro', status: 'ACTIVE' };
const subscription: CompanySubscription = { id: 's1', companyId: 'co1', planId: 'p1', status: 'ACTIVE' };

function configure(data: ChangeSubscriptionDialogData) {
  const dialogRef = { close: vi.fn() };
  TestBed.configureTestingModule({
    imports: [ChangeSubscriptionDialogComponent, NoopAnimationsModule],
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      { provide: MatDialogRef, useValue: dialogRef },
      { provide: MAT_DIALOG_DATA, useValue: data },
    ],
  });
  return dialogRef;
}

describe('ChangeSubscriptionDialogComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => httpMock.verify());

  it('starts empty when there is no current subscription and submits an upsert', () => {
    const dialogRef = configure({ companyId: 'co1', plans: [plan], currentSubscription: null });
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(ChangeSubscriptionDialogComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.form.value.status).toBe('ACTIVE');
    fixture.componentInstance.form.patchValue({ planId: 'p1' });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/companies/co1/subscription`);
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ planId: 'p1', status: 'ACTIVE' });
    req.flush(subscription);

    expect(dialogRef.close).toHaveBeenCalledWith(subscription);
  });

  it('prefills the form when there is a current subscription', () => {
    configure({ companyId: 'co1', plans: [plan], currentSubscription: subscription });
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(ChangeSubscriptionDialogComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.form.value).toEqual({ planId: 'p1', status: 'ACTIVE' });
  });

  it('does not submit an invalid form', () => {
    configure({ companyId: 'co1', plans: [plan], currentSubscription: null });
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(ChangeSubscriptionDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.patchValue({ planId: '' });
    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/companies/co1/subscription`);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('shows the backend error when the request fails', () => {
    configure({ companyId: 'co1', plans: [plan], currentSubscription: subscription });
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(ChangeSubscriptionDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.submit();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/companies/co1/subscription`)
      .flush(
        { code: 'PLAN_NOT_FOUND', message: 'Unknown planId.', requestId: 'r1' },
        { status: 400, statusText: 'Bad Request' },
      );

    expect(fixture.componentInstance.error()).toBe('No se ha encontrado el plan solicitado.');
  });

  it('cancel() closes the dialog without a result', () => {
    const dialogRef = configure({ companyId: 'co1', plans: [plan], currentSubscription: null });
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(ChangeSubscriptionDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();

    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
