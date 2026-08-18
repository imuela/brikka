/** Mirrors backend com.brika.platform.communication.web.ConversationResponse
 * (17_API_SPECIFICATION_DETAILED.md §18). type is CLIENT or INTERNAL — SYSTEM is never produced by
 * any endpoint (ConversationController javadoc). */
export interface Conversation {
  id: string;
  caseId: string;
  type: string;
  status: string;
  createdAt: string;
  updatedAt: string;
}

/** Mirrors ConversationParticipantResponse. Participants only ever exist for CLIENT-type
 * conversations — INTERNAL authorization stays implicit via CASE ASSIGNMENT (ADR-COMMS-002). */
export interface ConversationParticipant {
  id: string;
  conversationId: string;
  clientId: string;
  createdAt: string;
}

/** Mirrors MessageResponse. Exactly one of senderUserId/senderClientId is set — client-sent
 * messages (via Portal Cliente, out of Sprint 17 scope) surface here with senderClientId set even
 * though this app cannot create them. */
export interface Message {
  id: string;
  conversationId: string;
  senderUserId: string | null;
  senderClientId: string | null;
  body: string;
  createdAt: string;
  editedAt: string | null;
}

/** Mirrors MessageAttachmentResponse — url is a backend-mediated presigned download URL, never a
 * direct storage URL. */
export interface MessageAttachment {
  id: string;
  messageId: string;
  originalFilename: string;
  mimeType: string;
  sizeBytes: number;
  createdAt: string;
  url: string;
}

/** Mirrors CreateConversationApiRequest. clientIds is required and non-empty for CLIENT, ignored
 * for INTERNAL. */
export interface CreateConversationRequest {
  type: string;
  clientIds: string[] | null;
}
