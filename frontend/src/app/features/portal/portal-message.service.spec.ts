import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

import { environment } from '../../../environments/environment';
import { PortalMessageService } from './portal-message.service';
import { Message, MessageAttachment } from '../communications/communication.model';

describe('PortalMessageService', () => {
  let service: PortalMessageService;
  let httpMock: HttpTestingController;

  const message: Message = {
    id: 'm1',
    conversationId: 'c1',
    senderUserId: null,
    senderClientId: 'cl1',
    body: 'Hello broker',
    createdAt: '2026-08-18T10:00:00Z',
    editedAt: null,
  };
  const attachment: MessageAttachment = {
    id: 'a1',
    messageId: 'm1',
    originalFilename: 'note.pdf',
    mimeType: 'application/pdf',
    sizeBytes: 100,
    createdAt: '2026-08-18T10:00:00Z',
    url: 'https://storage.test/note.pdf',
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(PortalMessageService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('listMessages(caseId) calls GET /api/v1/portal/cases/{id}/messages', () => {
    service.listMessages('k1').subscribe((result) => expect(result).toEqual([message]));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/portal/cases/k1/messages`);
    expect(req.request.method).toBe('GET');
    req.flush([message]);
  });

  it('sendMessage(caseId, body) POSTs {body}', () => {
    service.sendMessage('k1', 'Hello broker').subscribe((result) => expect(result).toEqual(message));
    const req = httpMock.expectOne(`${environment.apiBaseUrl}/api/v1/portal/cases/k1/messages`);
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({ body: 'Hello broker' });
    req.flush(message);
  });

  it('uploadAttachment(messageId, file) POSTs multipart form data', () => {
    const file = new File(['content'], 'note.pdf', { type: 'application/pdf' });
    service
      .uploadAttachment('m1', file)
      .subscribe((result) => expect(result).toEqual(attachment));

    const req = httpMock.expectOne(
      `${environment.apiBaseUrl}/api/v1/portal/messages/m1/attachments`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toBeInstanceOf(FormData);
    req.flush(attachment);
  });

  it('listAttachments(messageId) calls GET /api/v1/portal/messages/{id}/attachments', () => {
    service
      .listAttachments('m1')
      .subscribe((result) => expect(result).toEqual([attachment]));
    const req = httpMock.expectOne(
      `${environment.apiBaseUrl}/api/v1/portal/messages/m1/attachments`,
    );
    expect(req.request.method).toBe('GET');
    req.flush([attachment]);
  });
});
