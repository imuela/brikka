import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { MatDialog, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { SessionStore } from '../../../core/session/session.store';
import { BankListComponent } from './bank-list.component';
import { CreateBankDialogComponent } from '../bank-dialogs/create-bank-dialog.component';

describe('BankListComponent', () => {
  let httpMock: HttpTestingController;
  let sessionStore: SessionStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [BankListComponent],
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

  it('loads and renders the bank list', () => {
    const fixture = TestBed.createComponent(BankListComponent);
    fixture.detectChanges();

    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/banks`)
      .flush([{ id: 'b1', code: 'DEVBANK', name: 'Banco Demo Desarrollo', status: 'ACTIVE', metadata: {} }]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Banco Demo Desarrollo');
  });

  it('gates "Nuevo banco" by BANK_CREATE', () => {
    const fixture = TestBed.createComponent(BankListComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks`).flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Nuevo banco');

    sessionStore.setPermissions(['BANK_CREATE']);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Nuevo banco');
  });

  it('openCreateBank opens the dialog and reloads the list on close with a result', () => {
    const fixture = TestBed.createComponent(BankListComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks`).flush([]);
    fixture.detectChanges();

    const dialog = TestBed.inject(MatDialog);
    const openSpy = vi.spyOn(dialog, 'open').mockReturnValue({
      afterClosed: () =>
        of({ id: 'b1', code: 'DEVBANK', name: 'Banco Demo Desarrollo', status: 'ACTIVE', metadata: {} }),
    } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.openCreateBank();

    expect(openSpy).toHaveBeenCalledWith(CreateBankDialogComponent, expect.objectContaining({ width: '400px' }));
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/banks`).flush([]);
  });
});
