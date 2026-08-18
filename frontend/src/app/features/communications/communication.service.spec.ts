import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { Conversation, ConversationParticipant, Message, MessageAttachment } from './communication.model';
import { CommunicationService } from './communication.service';

describe('CommunicationService', () => {
  let service: CommunicationService;
  let httpMock: HttpTestingController;

  const conversation: Conversation = {
    id: 'conv1',
    caseId: 'k1',
    type: 'INTERNAL',
    status: 'ACTIVE',
    createdAt: '2026-08-18T10:00:00Z',
    updatedAt: '2026-08-18T10:00:00Z',
  };
  const participant: ConversationParticipant = {
    id: 'p1',
    conversationId: 'conv1',
    clientId: 'c1',
    createdAt: '2026-08-18T10:00:00Z',
  };
  const message: Message = {
    id: 'm1',
    conversationId: 'conv1',
    senderUserId: 'u1',
    senderClientId: null,
    body: 'Hola',
    createdAt: '2026-08-18T10:00:00Z',
    editedAt: null,
  };
  const attachment: MessageAttachment = {
    id: 'a1',
    messageId: 'm1',
    originalFilename: 'nomina.pdf',
    mimeType: 'application/pdf',
    sizeBytes: 1024,
    createdAt: '2026-08-18T10:00:00Z',
    url: 'https://storage.test/a1',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(CommunicationService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listConversations() calls GET /api/v1/cases/{caseId}/conversations', () => {
    service.listConversations('k1').subscribe((result) => expect(result).toEqual([conversation]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/conversations`);
    expect(req.request.method).toBe('GET');
    req.flush([conversation]);
  });

  it('createConversation() POSTs the exact CreateConversationApiRequest shape', () => {
    const request = { type: 'INTERNAL', clientIds: null };
    service.createConversation('k1', request).subscribe((result) => expect(result).toEqual(conversation));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/cases/k1/conversations`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush(conversation);
  });

  it('listParticipants() calls GET /api/v1/conversations/{id}/participants', () => {
    service.listParticipants('conv1').subscribe((result) => expect(result).toEqual([participant]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/conversations/conv1/participants`);
    expect(req.request.method).toBe('GET');
    req.flush([participant]);
  });

  it('addParticipant() POSTs { clientId }', () => {
    service.addParticipant('conv1', 'c1').subscribe((result) => expect(result).toEqual(participant));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/conversations/conv1/participants`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ clientId: 'c1' });
    req.flush(participant);
  });

  it('removeParticipant() DELETEs /api/v1/conversations/{id}/participants/{participantId}', () => {
    service.removeParticipant('conv1', 'p1').subscribe();
    const req = httpMock.expectOne(
      `${environment.apiBaseUrl}/api/v1/conversations/conv1/participants/p1`,
    );
    expect(req.request.method).toBe('DELETE');
    req.flush(null);
  });

  it('listMessages() calls GET /api/v1/conversations/{id}/messages', () => {
    service.listMessages('conv1').subscribe((result) => expect(result).toEqual([message]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/conversations/conv1/messages`);
    expect(req.request.method).toBe('GET');
    req.flush([message]);
  });

  it('sendMessage() POSTs { body }', () => {
    service.sendMessage('conv1', 'Hola').subscribe((result) => expect(result).toEqual(message));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/conversations/conv1/messages`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ body: 'Hola' });
    req.flush(message);
  });

  it('uploadAttachment() POSTs multipart form data', () => {
    const file = new File(['content'], 'nomina.pdf', { type: 'application/pdf' });
    service.uploadAttachment('m1', file).subscribe((result) => expect(result).toEqual(attachment));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/messages/m1/attachments`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBe(true);
    req.flush(attachment);
  });

  it('listAttachments() calls GET /api/v1/messages/{id}/attachments', () => {
    service.listAttachments('m1').subscribe((result) => expect(result).toEqual([attachment]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/messages/m1/attachments`);
    expect(req.request.method).toBe('GET');
    req.flush([attachment]);
  });
});
