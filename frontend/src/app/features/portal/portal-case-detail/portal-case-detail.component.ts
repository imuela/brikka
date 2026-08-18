import { DatePipe } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatTableModule } from '@angular/material/table';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import {
  CASE_STATUS_LABELS,
  DOCUMENT_REQUEST_STATUS_LABELS,
  OPERATION_TYPE_LABELS,
} from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { MessageAttachment } from '../../communications/communication.model';
import { PortalCase } from '../portal-case.model';
import { PortalCaseService } from '../portal-case.service';
import { PortalDocument, PortalDocumentRequest } from '../portal-document.model';
import { PortalDocumentService } from '../portal-document.service';
import { PortalMessageService } from '../portal-message.service';
import { Message } from '../../communications/communication.model';

/**
 * Portal counterpart of case-detail — deliberately narrower: only the fields
 * PortalCaseResponse/PortalDocumentResponse expose (no scoring/banking/notes). "Solicitudes de
 * documentación" (Sprint 19, ADR-PROCESS-007) is an explicit view, kept separate from the upload
 * flow's existing opportunistic auto-fulfill side effect — uploading from a request row still goes
 * through the same PortalDocumentService.upload() the standalone flow would use; the request list
 * is simply reloaded afterwards to reflect the new FULFILLED status.
 */
@Component({
  selector: 'app-portal-case-detail',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    DatePipe,
    MatCardModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatTableModule,
    MatProgressSpinnerModule,
    StatusLabelPipe,
  ],
  templateUrl: './portal-case-detail.component.html',
})
export class PortalCaseDetailComponent {
  private readonly fb = inject(FormBuilder);
  private readonly portalCaseService = inject(PortalCaseService);
  private readonly portalDocumentService = inject(PortalDocumentService);
  private readonly portalMessageService = inject(PortalMessageService);
  private readonly route = inject(ActivatedRoute);

  private readonly caseId = this.route.snapshot.paramMap.get('id')!;

  readonly theCase = signal<PortalCase | null>(null);
  readonly documents = signal<PortalDocument[] | null>(null);
  readonly documentRequests = signal<PortalDocumentRequest[] | null>(null);
  readonly messages = signal<Message[] | null>(null);
  readonly attachmentsByMessage = signal<Record<string, MessageAttachment[]>>({});
  readonly expandedMessageId = signal<string | null>(null);
  readonly uploadingRequestId = signal<string | null>(null);
  readonly sending = signal(false);
  readonly error = signal<string | null>(null);

  readonly caseStatusLabels = CASE_STATUS_LABELS;
  readonly documentRequestStatusLabels = DOCUMENT_REQUEST_STATUS_LABELS;
  readonly operationTypeLabels = OPERATION_TYPE_LABELS;
  readonly documentColumns = ['type', 'version', 'publishedAt'];
  readonly requestColumns = ['type', 'status', 'dueAt', 'actions'];

  readonly messageForm = this.fb.nonNullable.group({
    body: ['', Validators.required],
  });

  constructor() {
    this.portalCaseService.get(this.caseId).subscribe({
      next: (theCase) => this.theCase.set(theCase),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
    this.loadDocuments();
    this.loadDocumentRequests();
    this.loadMessages();
  }

  private loadDocuments(): void {
    this.portalDocumentService.list(this.caseId).subscribe({
      next: (documents) => this.documents.set(documents),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  private loadDocumentRequests(): void {
    this.portalDocumentService.listDocumentRequests(this.caseId).subscribe({
      next: (requests) => this.documentRequests.set(requests),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  private loadMessages(): void {
    this.portalMessageService.listMessages(this.caseId).subscribe({
      next: (messages) => this.messages.set(messages),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
  }

  uploadForRequest(request: PortalDocumentRequest, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    this.uploadingRequestId.set(request.id);
    this.portalDocumentService.upload(this.caseId, request.documentTypeId, file).subscribe({
      next: () => {
        this.uploadingRequestId.set(null);
        this.loadDocuments();
        this.loadDocumentRequests();
      },
      error: (err: ApiError) => {
        this.uploadingRequestId.set(null);
        this.error.set(friendlyErrorMessage(err));
      },
    });
    input.value = '';
  }

  sendMessage(): void {
    if (this.messageForm.invalid) {
      this.messageForm.markAllAsTouched();
      return;
    }
    this.sending.set(true);
    this.error.set(null);
    this.portalMessageService.sendMessage(this.caseId, this.messageForm.getRawValue().body).subscribe({
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
      this.portalMessageService.listAttachments(message.id).subscribe({
        next: (attachments) =>
          this.attachmentsByMessage.update((map) => ({ ...map, [message.id]: attachments })),
        error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
      });
    }
  }

  uploadMessageAttachment(message: Message, event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    if (!file) {
      return;
    }
    this.portalMessageService.uploadAttachment(message.id, file).subscribe({
      next: (attachment) =>
        this.attachmentsByMessage.update((map) => ({
          ...map,
          [message.id]: [...(map[message.id] ?? []), attachment],
        })),
      error: (err: ApiError) => this.error.set(friendlyErrorMessage(err)),
    });
    input.value = '';
  }
}
