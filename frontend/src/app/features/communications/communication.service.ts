import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ApiClient } from '../../core/http/api-client';
import {
  Conversation,
  ConversationParticipant,
  CreateConversationRequest,
  Message,
  MessageAttachment,
} from './communication.model';

/** Thin wrapper over the real communications contract (17_API_SPECIFICATION_DETAILED.md §18) — no
 * fields, endpoints or business rules beyond what ConversationController/MessageAttachmentController
 * expose. Attachments are keyed by messageId, not conversationId — an attachment can only be
 * uploaded to a message that already exists. */
@Injectable({ providedIn: 'root' })
export class CommunicationService {
  private readonly apiClient = inject(ApiClient);

  listConversations(caseId: string): Observable<Conversation[]> {
    return this.apiClient.get<Conversation[]>(`/api/v1/cases/${caseId}/conversations`);
  }

  createConversation(
    caseId: string,
    request: CreateConversationRequest,
  ): Observable<Conversation> {
    return this.apiClient.post<Conversation>(`/api/v1/cases/${caseId}/conversations`, request);
  }

  listParticipants(conversationId: string): Observable<ConversationParticipant[]> {
    return this.apiClient.get<ConversationParticipant[]>(
      `/api/v1/conversations/${conversationId}/participants`,
    );
  }

  addParticipant(conversationId: string, clientId: string): Observable<ConversationParticipant> {
    return this.apiClient.post<ConversationParticipant>(
      `/api/v1/conversations/${conversationId}/participants`,
      { clientId },
    );
  }

  removeParticipant(conversationId: string, participantId: string): Observable<void> {
    return this.apiClient.delete<void>(
      `/api/v1/conversations/${conversationId}/participants/${participantId}`,
    );
  }

  listMessages(conversationId: string): Observable<Message[]> {
    return this.apiClient.get<Message[]>(`/api/v1/conversations/${conversationId}/messages`);
  }

  sendMessage(conversationId: string, body: string): Observable<Message> {
    return this.apiClient.post<Message>(`/api/v1/conversations/${conversationId}/messages`, {
      body,
    });
  }

  uploadAttachment(messageId: string, file: File): Observable<MessageAttachment> {
    const formData = new FormData();
    formData.append('file', file);
    return this.apiClient.post<MessageAttachment>(
      `/api/v1/messages/${messageId}/attachments`,
      formData,
    );
  }

  listAttachments(messageId: string): Observable<MessageAttachment[]> {
    return this.apiClient.get<MessageAttachment[]>(`/api/v1/messages/${messageId}/attachments`);
  }
}
