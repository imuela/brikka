import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { SessionStore } from '../../../core/session/session.store';
import { BankDetailComponent } from './bank-detail.component';

const bank = { id: 'b1', code: 'DEVBANK', name: 'Banco Demo Desarrollo', status: 'ACTIVE', metadata: {} };

describe('BankDetailComponent', () => {
  let httpMock: HttpTestingController;
  let sessionStore: SessionStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [BankDetailComponent],
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: convertToParamMap({ id: 'b1' }) } } },
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
    sessionStore = TestBed.inject(SessionStore);
  });

  afterEach(() => httpMock.verify());

  function flushInitialLoad() {
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks/b1`).flush(bank);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks/b1/products`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks/b1/criteria`).flush([]);
  }

  it('loads and renders the bank', () => {
    const fixture = TestBed.createComponent(BankDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Banco Demo Desarrollo');
    expect(fixture.nativeElement.textContent).toContain('Sin productos todavía.');
    expect(fixture.nativeElement.textContent).toContain('Sin criterios todavía.');
  });

  it('gates product/criteria creation by BANK_UPDATE / BANK_CRITERIA_MANAGE', () => {
    const fixture = TestBed.createComponent(BankDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Nuevo producto');
    expect(fixture.nativeElement.textContent).not.toContain('Nueva versión de criterios');

    sessionStore.setPermissions(['BANK_UPDATE', 'BANK_CRITERIA_MANAGE']);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nuevo producto');
    expect(fixture.nativeElement.textContent).toContain('Nueva versión de criterios');
  });

  it('openCreateProduct reloads products after the dialog closes with a result', () => {
    const fixture = TestBed.createComponent(BankDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const dialog = TestBed.inject(MatDialog);
    vi.spyOn(dialog, 'open').mockReturnValue({
      afterClosed: () =>
        of({ id: 'p1', bankId: 'b1', code: 'HIP-30', name: 'Hipoteca 30 años', status: 'ACTIVE', metadata: {} }),
    } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.openCreateProduct();

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks/b1/products`).flush([]);
  });

  it('openCreateCriteria reloads criteria after the dialog closes with a result', () => {
    const fixture = TestBed.createComponent(BankDetailComponent);
    fixture.detectChanges();
    flushInitialLoad();
    fixture.detectChanges();

    const dialog = TestBed.inject(MatDialog);
    vi.spyOn(dialog, 'open').mockReturnValue({
      afterClosed: () =>
        of({
          id: 'c1',
          bankId: 'b1',
          version: 'v1',
          status: 'ACTIVE',
          effectiveFrom: '2026-08-18T00:00:00Z',
          effectiveTo: null,
          rules: { rules: [] },
        }),
    } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.openCreateCriteria();

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks/b1/criteria`).flush([]);
  });
});
