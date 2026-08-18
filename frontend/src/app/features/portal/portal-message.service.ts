import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/http/api-client';
import { Message, MessageAttachment } from '../communications/communication.model';

/** Thin wrapper over PortalMessageController — reuses the exact same Message/MessageAttachment
 * shapes as the internal CommunicationService (the backend returns the same MessageResponse/
 * MessageAttachmentResponse DTOs either way). "The" conversation for a case is resolved entirely
 * server-side (most recent CLIENT conversation this client actively participates in) — the API
 * takes no conversationId, matching the real contract (07_PORTAL_CLIENTE.md, ADR-COMMS-002). */
@Injectable({ providedIn: 'root' })
export class PortalMessageService {
  private readonly apiClient = inject(ApiClient);

  listMessages(caseId: string): Observable<Message[]> {
    return this.apiClient.get<Message[]>(`/api/v1/portal/cases/${caseId}/messages`);
  }

  sendMessage(caseId: string, body: string): Observable<Message> {
    return this.apiClient.post<Message>(`/api/v1/portal/cases/${caseId}/messages`, { body });
  }

  uploadAttachment(messageId: string, file: File): Observable<MessageAttachment> {
    const formData = new FormData();
    formData.append('file', file);
    return this.apiClient.post<MessageAttachment>(
      `/api/v1/portal/messages/${messageId}/attachments`,
      formData,
    );
  }

  listAttachments(messageId: string): Observable<MessageAttachment[]> {
    return this.apiClient.get<MessageAttachment[]>(
      `/api/v1/portal/messages/${messageId}/attachments`,
    );
  }
}
