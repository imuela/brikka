import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { CaseClient } from '../../cases/case.model';
import { ConversationParticipant } from '../communication.model';
import { AddParticipantDialogComponent } from './add-participant-dialog.component';

const clientA: CaseClient = {
  clientId: 'c1',
  firstName: 'Ada',
  lastName: 'Byron',
  participationType: 'HOLDER',
  isPrimary: true,
};
const clientB: CaseClient = {
  clientId: 'c2',
  firstName: 'Grace',
  lastName: 'Hopper',
  participationType: 'CO_HOLDER',
  isPrimary: false,
};
const participant: ConversationParticipant = {
  id: 'p1',
  conversationId: 'conv1',
  clientId: 'c2',
  createdAt: '2026-08-18T10:00:00Z',
};

function configure() {
  const dialogRef = { close: vi.fn() };
  TestBed.configureTestingModule({
    imports: [AddParticipantDialogComponent, NoopAnimationsModule],
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      { provide: MatDialogRef, useValue: dialogRef },
      {
        provide: MAT_DIALOG_DATA,
        useValue: { conversationId: 'conv1', clients: [clientA, clientB], existingClientIds: ['c1'] },
      },
    ],
  });
  return dialogRef;
}

describe('AddParticipantDialogComponent', () => {
  let httpMock: HttpTestingController;

  afterEach(() => httpMock.verify());

  it('excludes already-added clients from the picker', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(AddParticipantDialogComponent);
    fixture.detectChanges();

    expect(fixture.componentInstance.availableClients()).toEqual([clientB]);
  });

  it('submits and closes with the added participant', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(AddParticipantDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.form.setValue({ clientId: 'c2' });
    fixture.componentInstance.submit();

    const req = httpMock.expectOne(
      `${environment.apiBaseUrl}/api/v1/conversations/conv1/participants`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ clientId: 'c2' });
    req.flush(participant);

    expect(dialogRef.close).toHaveBeenCalledWith(participant);
  });

  it('does not submit without a selected client', () => {
    configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(AddParticipantDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.submit();

    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/conversations/conv1/participants`);
    expect(fixture.componentInstance.form.invalid).toBe(true);
  });

  it('cancel() closes the dialog without a result', () => {
    const dialogRef = configure();
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(AddParticipantDialogComponent);
    fixture.detectChanges();
    fixture.componentInstance.cancel();

    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
