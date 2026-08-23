import { TestBed } from '@angular/core/testing';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { errorInterceptor } from '../../../core/http/error.interceptor';
import { SessionStore } from '../../../core/session/session.store';
import { AssignableUser, CaseClient } from '../../cases/case.model';
import { Conversation, ConversationParticipant, Message } from '../communication.model';
import { AddParticipantDialogComponent } from './add-participant-dialog.component';
import { ConversationDetailDialogComponent } from './conversation-detail-dialog.component';

const clientRecord: CaseClient = {
  clientId: 'c1',
  firstName: 'Ada',
  lastName: 'Byron',
  participationType: 'HOLDER',
  isPrimary: true,
};
const user: AssignableUser = {
  id: 'u1',
  email: 'u1@brika.test',
  firstName: 'Grace',
  lastName: 'Hopper',
  role: 'BROKER',
};
const internalConversation: Conversation = {
  id: 'conv1',
  caseId: 'k1',
  type: 'INTERNAL',
  status: 'ACTIVE',
  createdAt: '2026-08-18T10:00:00Z',
  updatedAt: '2026-08-18T10:00:00Z',
};
const clientConversation: Conversation = { ...internalConversation, id: 'conv2', type: 'CLIENT' };
const message: Message = {
  id: 'm1',
  conversationId: 'conv1',
  senderUserId: 'u1',
  senderClientId: null,
  body: 'Hola',
  createdAt: '2026-08-18T10:00:00Z',
  editedAt: null,
};
const participant: ConversationParticipant = {
  id: 'p1',
  conversationId: 'conv2',
  clientId: 'c1',
  createdAt: '2026-08-18T10:00:00Z',
};

function configure(conversation: Conversation) {
  const dialogRef = { close: vi.fn() };
  TestBed.configureTestingModule({
    imports: [ConversationDetailDialogComponent, NoopAnimationsModule],
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      { provide: MatDialogRef, useValue: dialogRef },
      {
        provide: MAT_DIALOG_DATA,
        useValue: { conversation, clients: [clientRecord], assignableUsers: [user] },
      },
    ],
  });
  return dialogRef;
}

describe('ConversationDetailDialogComponent', () => {
  let httpMock: HttpTestingController;
  let sessionStore: SessionStore;
  let dialogRef: { close: ReturnType<typeof vi.fn> };

  afterEach(() => httpMock.verify());

  // Sprint 34: this test is entirely synchronous (no timers, no async work of its own) and passes
  // in ~10ms run alone — the observed 5000ms-default timeout only ever triggered under the full
  // 449-test suite's worker-pool contention, never from this test's own logic. A longer timeout is
  // the correct fix for that class of flakiness (vs. a real hang, which raising the timeout would
  // just delay rather than fix).
  it(
    'loads messages on init for an INTERNAL conversation and does not load participants',
    () => {
      dialogRef = configure(internalConversation);
      httpMock = TestBed.inject(HttpTestingController);

      const fixture = TestBed.createComponent(ConversationDetailDialogComponent);
      fixture.detectChanges();

      httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/conversations/conv1/messages`).flush([message]);
      fixture.detectChanges();

      expect(fixture.nativeElement.textContent).toContain('Hola');
      expect(fixture.nativeElement.textContent).toContain('Grace Hopper');
      httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/conversations/conv1/participants`);
    },
    15000,
  );

  it('loads participants on init for a CLIENT conversation', () => {
    dialogRef = configure(clientConversation);
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(ConversationDetailDialogComponent);
    fixture.detectChanges();

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/conversations/conv2/messages`).flush([]);
    httpMock
      .expectOne(`${environment.apiBaseUrl}/api/v1/conversations/conv2/participants`)
      .flush([participant]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Ada Byron');
  });

  it('sendMessage() posts the body and reloads messages', () => {
    dialogRef = configure(internalConversation);
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(ConversationDetailDialogComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/conversations/conv1/messages`).flush([]);

    fixture.componentInstance.messageForm.setValue({ body: 'Nuevo mensaje' });
    fixture.componentInstance.sendMessage();

    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/conversations/conv1/messages`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ body: 'Nuevo mensaje' });
    req.flush(message);

    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/conversations/conv1/messages`).flush([message]);
  });

  it('toggleAttachments() lazily loads attachments once per message', () => {
    dialogRef = configure(internalConversation);
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(ConversationDetailDialogComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/conversations/conv1/messages`).flush([message]);

    fixture.componentInstance.toggleAttachments(message);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/messages/m1/attachments`).flush([]);

    expect(fixture.componentInstance.attachmentsByMessage()['m1']).toEqual([]);

    fixture.componentInstance.toggleAttachments(message);
    expect(fixture.componentInstance.expandedMessageId()).toBeNull();

    fixture.componentInstance.toggleAttachments(message);
    httpMock.expectNone(`${environment.apiBaseUrl}/api/v1/messages/m1/attachments`);
  });

  it('openAddParticipant opens the dialog with clients not already participants excluded and reloads', () => {
    dialogRef = configure(clientConversation);
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(ConversationDetailDialogComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/conversations/conv2/messages`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/conversations/conv2/participants`).flush([participant]);

    // Spied on the prototype rather than the TestBed-resolved instance: this component both is a
    // dialog (imports MatDialogModule for its own template) and opens a nested dialog (injects
    // MatDialog) — see the Sprint 16 precedent (matching-result-detail-dialog.component.spec.ts).
    const openSpy = vi.spyOn(MatDialog.prototype, 'open').mockReturnValue({
      afterClosed: () => of(undefined),
    } as MatDialogRef<unknown, unknown>);

    fixture.componentInstance.openAddParticipant();

    expect(openSpy).toHaveBeenCalledWith(
      AddParticipantDialogComponent,
      expect.objectContaining({
        data: { conversationId: 'conv2', clients: [clientRecord], existingClientIds: ['c1'] },
      }),
    );
  });

  it('gates "Añadir participante" by CONVERSATION_PARTICIPANT_MANAGE', () => {
    dialogRef = configure(clientConversation);
    httpMock = TestBed.inject(HttpTestingController);
    sessionStore = TestBed.inject(SessionStore);

    const fixture = TestBed.createComponent(ConversationDetailDialogComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/conversations/conv2/messages`).flush([]);
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/conversations/conv2/participants`).flush([]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).not.toContain('Añadir participante');

    sessionStore.setPermissions(['CONVERSATION_PARTICIPANT_MANAGE']);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Añadir participante');
  });

  it('close() closes the dialog', () => {
    dialogRef = configure(internalConversation);
    httpMock = TestBed.inject(HttpTestingController);

    const fixture = TestBed.createComponent(ConversationDetailDialogComponent);
    fixture.detectChanges();
    httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/conversations/conv1/messages`).flush([]);

    fixture.componentInstance.close();

    expect(dialogRef.close).toHaveBeenCalled();
  });
});
