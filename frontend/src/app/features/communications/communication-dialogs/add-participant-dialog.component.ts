import { Component, computed, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { ApiError } from '../../../core/http/api-error';
import { friendlyErrorMessage } from '../../../core/http/error-messages';
import { CaseClient } from '../../cases/case.model';
import { ConversationParticipant } from '../communication.model';
import { CommunicationService } from '../communication.service';

export interface AddParticipantDialogData {
  conversationId: string;
  clients: CaseClient[];
  existingClientIds: string[];
}

@Component({
  selector: 'app-add-participant-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatProgressSpinnerModule,
  ],
  templateUrl: './add-participant-dialog.component.html',
})
export class AddParticipantDialogComponent {
  private readonly fb = inject(FormBuilder);
  private readonly communicationService = inject(CommunicationService);
  private readonly dialogRef = inject(
    MatDialogRef<AddParticipantDialogComponent, ConversationParticipant>,
  );
  private readonly data = inject<AddParticipantDialogData>(MAT_DIALOG_DATA);

  readonly loading = signal(false);
  readonly error = signal<string | null>(null);
  readonly availableClients = computed(() =>
    this.data.clients.filter((c) => !this.data.existingClientIds.includes(c.clientId)),
  );

  readonly form = this.fb.nonNullable.group({
    clientId: ['', Validators.required],
  });

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.loading.set(true);
    this.error.set(null);
    this.communicationService
      .addParticipant(this.data.conversationId, this.form.getRawValue().clientId)
      .subscribe({
        next: (participant) => this.dialogRef.close(participant),
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
