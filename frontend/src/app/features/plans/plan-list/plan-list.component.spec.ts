import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { SessionStore } from '../../../core/session/session.store';
import { CreatePlanDialogComponent } from '../plan-dialogs/create-plan-dialog.component';
import { EditPlanDialogComponent } from '../plan-dialogs/edit-plan-dialog.component';
import { Plan } from '../plan.model';
import { PlanListComponent } from './plan-list.component';

const plan: Plan = { id: 'p1', code: 'PRO', name: 'Plan Pro', status: 'ACTIVE' };

describe('PlanListComponent', () => {
  let httpMock: HttpTestingController;
  let sessionStore: SessionStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [PlanListComponent],
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

  it('loads and renders the plan list', () => {
    const fixture = TestBed.createComponent(PlanListComponent);
    fixture.detectChanges();

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/plans`).flush([plan]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Plan Pro');
  });

  it('gates "Nuevo plan" and "Editar" by PLAN_MANAGE', () => {
    const fixture = TestBed.createComponent(PlanListComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/plans`).flush([plan]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Nuevo plan');
    expect(fixture.nativeElement.querySelector('[aria-label="Editar plan"]')).toBeNull();

    sessionStore.setPermissions(['PLAN_READ', 'PLAN_MANAGE']);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nuevo plan');
    expect(fixture.nativeElement.querySelector('[aria-label="Editar plan"]')).not.toBeNull();
  });

  it('openCreate opens the dialog and reloads the list on close with a result', () => {
    const fixture = TestBed.createComponent(PlanListComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/plans`).flush([]);
    fixture.detectChanges();

    const dialog = TestBed.inject(MatDialog);
    const openSpy = vi
      .spyOn(dialog, 'open')
      .mockReturnValue({ afterClosed: () => of(plan) } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.openCreate();

    expect(openSpy).toHaveBeenCalledWith(CreatePlanDialogComponent, expect.objectContaining({ width: '400px' }));
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/plans`).flush([plan]);
  });

  it('openEdit opens the dialog with the plan and reloads on close with a result', () => {
    const fixture = TestBed.createComponent(PlanListComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/plans`).flush([plan]);
    fixture.detectChanges();

    const dialog = TestBed.inject(MatDialog);
    const openSpy = vi
      .spyOn(dialog, 'open')
      .mockReturnValue({ afterClosed: () => of(plan) } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.openEdit(plan);

    expect(openSpy).toHaveBeenCalledWith(
      EditPlanDialogComponent,
      expect.objectContaining({ data: { plan }, width: '400px' }),
    );
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/plans`).flush([plan]);
  });
});
