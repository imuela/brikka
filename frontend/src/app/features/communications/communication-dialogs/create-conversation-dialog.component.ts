import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { CONVERSATION_TYPE_LABELS } from '../../../shared/labels/status-labels';
import { StatusLabelPipe } from '../../../shared/pipes/status-label.pipe';
import { CaseClient } from '../../cases/case.model';
import { Conversation } from '../communication.model';
import { CommunicationService } from '../communication.service';

export interface CreateConversationDialogData {
  caseId: string;
  clients: CaseClient[];
}

/** type is CLIENT or INTERNAL — SYSTEM is never created by any endpoint (ConversationController
 * javadoc), so it is never offered here. clientIds is required and non-empty for CLIENT
 * (ADR-COMMS-002: never created without participants) and ignored for INTERNAL. */
@Component({
  selector: 'app-create-conversation-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
    StatusLabelPipe,
  ],
  templateUrl: './create-conversation-dialog.component.html',
})
export class CreateConversationDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly communicationService = inject(CommunicationService);
  private readonly dialogRef = inject(MatDialogRef<CreateConversationDialogComponent, Conversation>);
  private readonly data = inject<CreateConversationDialogData>(MAT_DIALOG_DATA);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly conversationTypeLabels = CONVERSATION_TYPE_LABELS;
  readonly clients = this.data.clients;

  readonly form = this.fb.nonNullable.group({
    type: ['INTERNAL', Validators.required],
    clientIds: this.fb.nonNullable.control<string[]>([]),
  });

  /** Explicit signal (rather than reading form.controls.type.value directly in the template) so
   * the conditional client picker reliably updates under zoneless change detection. */
  readonly isClientType = signal(false);

  onTypeChange(type: string): void {
    this.isClientType.set(type === 'CLIENT');
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    if (value.type === 'CLIENT' && value.clientIds.length === 0) {
      this.error.set('Selecciona al menos un cliente para una conversación de tipo Cliente.');
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.communicationService
      .createConversation(this.data.caseId, {
        type: value.type,
        clientIds: value.type === 'CLIENT' ? value.clientIds : null,
      })
      .subscribe({
        next: (conversation) => this.dialogRef.close(conversation),
        error: (err: ApiError) => {
          this.loading.set(false);
          this.error.set(friendlyErrorMessage(err));
        },
      });
  }

  cancel(): void {
    this.dialogRef.close();
  }
}
