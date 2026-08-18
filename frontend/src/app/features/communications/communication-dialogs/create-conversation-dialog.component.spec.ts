import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { CaseClient } from '../../cases/case.model';
import { Conversation } from '../communication.model';
import { CreateConversationDialogComponent } from './create-conversation-dialog.component';

const client: CaseClient = {
  clientId: 'c1',
  firstName: 'Ada',
  lastName: 'Byron',
  participationType: 'HOLDER',
  isPrimary: true,
};
const conversation: Conversation = {
  id: 'conv1',
  caseId: 'k1',
  type: 'INTERNAL',
  status: 'ACTIVE',
  createdAt: '2026-08-18T10:00:00Z',
  updatedAt: '2026-08-18T10:00:00Z',
};

function configure() {
  const dialogRef = { close: vi.fn() };
  TestBed.configureTestingModule({
    imports: [CreateConversationDialogComponent, NoopAnimationsModule],
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      { provide: MatDialogRef, useValue: dialogRef },
      { provide: MAT_DIALOG_DATA, useValue: { caseId: 'k1', clients: [client] } },
    ],
  });
  return dialogRef;
}

describe('CreateConversationDialogComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => httpMock.verify());

  it('submits an INTERNAL conversation with clientIds null', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(CreateConversationDialogComponent);
    fixture.detectChanges();

    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/conversations`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ type: 'INTERNAL', clientIds: null });
    req.flush(conversation);

    expect(dialogRef.close).toHaveBeenCalledWith(conversation);
  });

  it('reveals the client picker and requires at least one client for CLIENT type', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(CreateConversationDialogComponent);
    fixture.detectChanges();

    fixture.componentInstance.onTypeChange('CLIENT');
    fixture.componentInstance.form.patchValue({ type: 'CLIENT' });
    fixture.detectChanges();

    expect(fixture.componentInstance.isClientType()).toBe(true);

    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/cases/k1/conversations`);
    expect(fixture.componentInstance.error()).toContain('Selecciona al menos un cliente');
  });

  it('submits a CLIENT conversation with the selected clientIds', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(CreateConversationDialogComponent);
    fixture.detectChanges();

    fixture.componentInstance.onTypeChange('CLIENT');
    fixture.componentInstance.form.setValue({ type: 'CLIENT', clientIds: ['c1'] });

    fixture.componentInstance.submit();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/conversations`);
    expect(req.request.body).toEqual({ type: 'CLIENT', clientIds: ['c1'] });
    req.flush({ ...conversation, type: 'CLIENT' });

    expect(dialogRef.close).toHaveBeenCalledWith({ ...conversation, type: 'CLIENT' });
  });

  it('cancel() closes the dialog without a result', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(CreateConversationDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();

    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
