import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog, MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';

import { HasPermissionDirective } from '../../../shared/directives/has-permission.directive';
import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { ConfirmDialogComponent } from '../../../shared/dialogs/confirm-dialog.component';
import {
  CONVERSATION_STATUS_LABELS,
  CONVERSATION_TYPE_LABELS,
} from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { AssignableUser, CaseClient } from '../../cases/case.model';
import {
  Conversation,
  ConversationParticipant,
  Message,
  MessageAttachment,
} from '../communication.model';
import { CommunicationService } from '../communication.service';
import { AddParticipantDialogComponent } from './add-participant-dialog.component';

export interface ConversationDetailDialogData {
  conversation: Conversation;
  clients: CaseClient[];
  assignableUsers: AssignableUser[];
}

/** Hub view for a single conversation: participants (CLIENT type only), messages, and per-message
 * attachments. Attachments are keyed by messageId (not conversationId) in the real contract, so
 * they can only ever be attached to a message that already exists — the upload control lives on
 * each message row, not on the composer. Attachment lists are lazy-loaded per message on request
 * rather than eagerly for every message on open, to avoid an unbounded N+1 burst of requests. */
@Component({
  selector: 'app-conversation-detail-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DatePipe,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatIconModule,
    MatTableModule,
    MatProgressSpinnerModule,
    HasPermissionDirective,
    StatusLabelPipe,
  ],
  templateUrl: './conversation-detail-dialog.component.html',
})
export class ConversationDetailDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly communicationService = inject(CommunicationService);
  private readonly dialog = inject(MatDialog);
  private readonly dialogRef = inject(MatDialogRef<ConversationDetailDialogComponent, void>);
  readonly data = inject<ConversationDetailDialogData>(MAT_DIALOG_DATA);

  readonly conversationTypeLabels = CONVERSATION_TYPE_LABELS;
  readonly conversationStatusLabels = CONVERSATION_STATUS_LABELS;
  readonly isClientType = this.data.conversation.type === 'CLIENT';

  readonly participants = signal<ConversationParticipant[] | null>(null);
  readonly messages = signal<Message[] | null>(null);
  readonly attachmentsByMessage = signal<Record<string, MessageAttachment[]>>({});
  readonly expandedMessageId = signal<string | null>(null);
  readonly sending = signal(false);
  readonly error = signal<string | null>(null);

  readonly messageForm = this.fb.nonNullable.group({
    body: ['', Validators.required],
  });

  constructor() {
    this.loadMessages();
    if (this.isClientType) {
      this.loadParticipants();
    }
  }

  private loadMessages(): void {
    this.communicationService.listMessages(this.data.conversation.id).subscribe({
      next: (messages) => this.messages.set(messages),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  private loadParticipants(): void {
    this.communicationService.listParticipants(this.data.conversation.id).subscribe({
      next: (participants) => this.participants.set(participants),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  senderName(message: Message): string {
    if (message.senderUserId) {
      const user = this.data.assignableUsers.find((u) => u.id === message.senderUserId);
      return user ? `${user.firstName} ${user.lastName}` : message.senderUserId;
    }
    if (message.senderClientId) {
      const client = this.data.clients.find((c) => c.clientId === message.senderClientId);
      return client ? `${client.firstName} ${client.lastName} (cliente)` : 'Cliente';
    }
    return '—';
  }

  participantName(participant: ConversationParticipant): string {
    const client = this.data.clients.find((c) => c.clientId === participant.clientId);
    return client ? `${client.firstName} ${client.lastName}` : participant.clientId;
  }

  sendMessage(): void {
    if (this.messageForm.invalid) {
      this.messageForm.markAllAsTouched();
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    this.communicationService
      .sendMessage(this.data.conversation.id, this.messageForm.getRawValue().body)
      .subscribe({
        next: () => {
          this.sending.set(false);
          this.messageForm.reset({ body: '' });
          this.loadMessages();
        },
        error: (err: ApiError) => {
          this.sending.set(false);
          this.error.set(friendlyErrorMessage(err));
        },
      });
  }

  toggleAttachments(message: Message): void {
    if (this.expandedMessageId() === message.id) {
      this.expandedMessageId.set(null);
      return;
    }
    this.expandedMessageId.set(message.id);
    if (!this.attachmentsByMessage()[message.id]) {
      this.communicationService.listAttachments(message.id).subscribe({
        next: (attachments) =>
          this.attachmentsByMessage.update((map) => ({ ...map, [message.id]: attachments })),
        error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
      });
    }
  }

  uploadAttachment(message: Message, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    this.communicationService.uploadAttachment(message.id, file).subscribe({
      next: (attachment) =>
        this.attachmentsByMessage.update((map) => ({
          ...map,
          [message.id]: [...(map[message.id] ?? []), attachment],
        })),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
    input.value = '';
  }

  openAddParticipant(): void {
    this.dialog
      .open(AddParticipantDialogComponent, {
        data: {
          conversationId: this.data.conversation.id,
          clients: this.data.clients,
          existingClientIds: (this.participants() ?? []).map((p) => p.clientId),
        },
        width: '400px',
      })
      .afterClosed()
      .subscribe((result: ConversationParticipant | undefined) => {
        if (result) {
          this.loadParticipants();
        }
      });
  }

  removeParticipant(participant: ConversationParticipant): void {
    this.dialog
      .open(ConfirmDialogComponent, {
        data: {
          title: 'Quitar participante',
          message: `¿Seguro que quieres quitar a ${this.participantName(participant)} de esta conversación?`,
          confirmLabel: 'Quitar',
        },
        width: '400px',
      })
      .afterClosed()
      .subscribe((confirmed: boolean | undefined) => {
        if (!confirmed) {
          return;
        }
        this.communicationService
          .removeParticipant(this.data.conversation.id, participant.id)
          .subscribe({
            next: () => this.loadParticipants(),
            error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
          });
      });
  }

  close(): void {
    this.dialogRef.close();
  }
}
